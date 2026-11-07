import android.os.Build;
import android.os.IBinder;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 被控设备侧的熄屏待机执行程序。
 *
 * 由客户端通过 {@code CLASSPATH=<dex> app_process <dir> ScreenStandby <off|on>} 推送到
 * /data/local/tmp 临时执行，向 stdout 输出一行 JSON：{@code {"ok":true}} 或
 * {@code {"ok":false,"error":"..."}}；用完由客户端删除（无论成败）。
 *
 * 机制：{@code android.view.SurfaceControl.setDisplayPowerMode(token, mode)}（隐藏 API）。
 * ADB shell 天然是 shell uid，持有 ACCESS_SURFACE_FLINGER，不需要 root/Shizuku。改变
 * 的只是显示面板的电源状态：设备保持唤醒、不锁屏——这正是「熄屏待机」与注入电源键的
 * 区别；点亮屏幕同样只恢复面板电源，不依赖系统唤醒流程。
 *
 * 取显示 token 按可用性依次尝试（不硬编版本号）：
 * 1. {@code SurfaceControl.getPhysicalDisplayIds/ getPhysicalDisplayToken}（Android 10–13，
 *    部分 14 ROM 仍保留）；
 * 2. Android 14+ 起 token 接口移到了 {@code com.android.server.display.DisplayControl}，
 *    它是 systemserver 的类：用 {@code com.android.internal.os.ClassLoaderFactory} 以
 *    SYSTEMSERVERCLASSPATH 造类加载器加载它，并预载原生库 android_servers；
 * 3. 单屏回退：{@code getInternalDisplayToken()}（10+）或 {@code getBuiltInDisplay(0)}（更早）。
 *
 * 另：Honor 的 Android 14 机型对「遍历全部物理屏」有兼容问题，退化为只操作内建屏；该分支
 * 拿不到内建屏时直接失败，不回退枚举。
 *
 * 机制与分版本策略参考了 scrcpy（Genymobile, Apache-2.0）的公开实现思路；本文件为独立实现，
 * 未复制其代码。
 */
public final class ScreenStandby {

    private static final int POWER_MODE_OFF = 0;
    private static final int POWER_MODE_NORMAL = 2;

    private ScreenStandby() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8.name());
        try {
            applyToAllDisplays(powerModeFor(args));
            out.println("{\"ok\":true}");
        } catch (Throwable error) {
            out.println("{\"ok\":false,\"error\":" + jsonString(describe(error)) + "}");
        }
        // Android 14+ 路径会预载 android_servers，之后走正常退出会在运行时收尾阶段崩溃
        // （退出码 139）——JSON 已经输出，但退出码会污染调用方。一次性进程没有需要收尾的
        // 东西，直接结束进程：退出码恒为 0，成败只以 JSON 为准。
        out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static int powerModeFor(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ScreenStandby <off|on>");
        }
        switch (args[0]) {
            case "off":
                return POWER_MODE_OFF;
            case "on":
                return POWER_MODE_NORMAL;
            default:
                throw new IllegalArgumentException("unknown action: " + args[0]);
        }
    }

    /** 给所有可达的显示屏设置电源模式；一个 token 都拿不到时如实报错，而不是假装成功。 */
    private static void applyToAllDisplays(int mode) throws Exception {
        List<IBinder> tokens = collectDisplayTokens();
        if (tokens.isEmpty()) {
            throw new IllegalStateException("no display token available (sdk="
                    + Build.VERSION.SDK_INT + ", brand=" + Build.BRAND + ")");
        }
        Method setPowerMode = Class.forName("android.view.SurfaceControl")
                .getMethod("setDisplayPowerMode", IBinder.class, int.class);
        Throwable firstFailure = null;
        int failed = 0;
        for (IBinder token : tokens) {
            try {
                setPowerMode.invoke(null, token, mode);
            } catch (Throwable error) {
                if (firstFailure == null) {
                    // 真实原因常被包在 InvocationTargetException 里，先解包再留档；
                    // 反射异常之外的 RuntimeException/Error 同样逐屏计数，不能整批跳出去
                    firstFailure = error instanceof InvocationTargetException && error.getCause() != null
                            ? error.getCause()
                            : error;
                }
                failed++;
            }
        }
        if (failed > 0) {
            // 部分失败也按失败报：多屏设备上「有的屏还亮着」不能当成功
            throw new IllegalStateException("setDisplayPowerMode failed on " + failed
                    + "/" + tokens.size() + " display(s): " + firstFailure, firstFailure);
        }
    }

    /**
     * 收集要操作的显示屏 token。
     *
     * 顺序：Honor 14 特例 → SurfaceControl 多物理屏 → DisplayControl 多物理屏 → 单屏回退。
     * 每一步拿不到就交给下一步，全部落空才算失败；Honor 14 特例是例外——拿不到内建屏时
     * 直接报错，不回退枚举（那正是已知不生效的路径，「成功但没熄屏」比失败更糟）。
     */
    private static List<IBinder> collectDisplayTokens() {
        List<IBinder> tokens = new ArrayList<>();

        // Honor 的 Android 14 机型遍历全部物理屏会出问题（熄屏不生效），只碰内建屏；
        // 拿不到内建屏时不能回退枚举——那会走回已知不生效的路径，还可能上报成功
        if (Build.VERSION.SDK_INT >= 34 && "honor".equalsIgnoreCase(Build.BRAND)) {
            IBinder builtin = builtinDisplayToken();
            if (builtin == null) {
                throw new IllegalStateException("built-in display token unavailable on honor device (sdk="
                        + Build.VERSION.SDK_INT + ", brand=" + Build.BRAND + ")");
            }
            tokens.add(builtin);
            return tokens;
        }

        Class<?> surfaceControl = surfaceControlOrNull();
        if (surfaceControl != null) {
            tokens.addAll(physicalDisplayTokens(surfaceControl));
        }
        if (tokens.isEmpty()) {
            Class<?> displayControl = displayControlOrNull();
            if (displayControl != null) {
                tokens.addAll(physicalDisplayTokens(displayControl));
            }
        }
        if (tokens.isEmpty()) {
            IBinder builtin = builtinDisplayToken();
            if (builtin != null) {
                tokens.add(builtin);
            }
        }
        return tokens;
    }

    /** 从给定的类上枚举全部物理显示屏 token；方法组不可用时返回空表，交给调用方回退。 */
    private static List<IBinder> physicalDisplayTokens(Class<?> api) {
        List<IBinder> tokens = new ArrayList<>();
        try {
            long[] ids = (long[]) api.getMethod("getPhysicalDisplayIds").invoke(null);
            if (ids == null) {
                return tokens;
            }
            Method tokenOf = api.getMethod("getPhysicalDisplayToken", long.class);
            for (long id : ids) {
                IBinder token = (IBinder) tokenOf.invoke(null, id);
                if (token != null) {
                    tokens.add(token);
                }
            }
        } catch (Throwable ignored) {
            // 这组方法在这台设备上不可用：返回空表，交给调用方继续回退
        }
        return tokens;
    }

    /** 单屏回退：Android 10+ 用 getInternalDisplayToken()，更早用 getBuiltInDisplay(0)。 */
    private static IBinder builtinDisplayToken() {
        Class<?> surfaceControl = surfaceControlOrNull();
        if (surfaceControl == null) {
            return null;
        }
        try {
            IBinder token = (IBinder) surfaceControl.getMethod("getInternalDisplayToken").invoke(null);
            if (token != null) {
                return token;
            }
        } catch (Throwable ignored) {
            // 10 以下没有这个方法，继续试更早的
        }
        try {
            return (IBinder) surfaceControl.getMethod("getBuiltInDisplay", int.class).invoke(null, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> surfaceControlOrNull() {
        try {
            return Class.forName("android.view.SurfaceControl");
        } catch (Throwable error) {
            return null;
        }
    }

    /**
     * Android 14+：DisplayControl 在 systemserver 的类路径里，普通反射够不到。经
     * ClassLoaderFactory 以 SYSTEMSERVERCLASSPATH 造一个类加载器去加载它，再预载
     * 原生库 android_servers（它的 native 方法由该库注册）。任一环节失败返回 null，
     * 由调用方走回退路径。
     */
    private static Class<?> displayControlOrNull() {
        try {
            String systemServerClasspath = System.getenv("SYSTEMSERVERCLASSPATH");
            if (systemServerClasspath == null || systemServerClasspath.isEmpty()) {
                return null;
            }
            Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method createClassLoader = factory.getDeclaredMethod("createClassLoader",
                    String.class, String.class, String.class, ClassLoader.class, int.class,
                    boolean.class, String.class);
            ClassLoader loader = (ClassLoader) createClassLoader.invoke(
                    null, systemServerClasspath, null, null, ClassLoader.getSystemClassLoader(),
                    0, true, null);
            Class<?> displayControl = loader.loadClass("com.android.server.display.DisplayControl");
            Method loadLibrary0 = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            loadLibrary0.setAccessible(true);
            loadLibrary0.invoke(Runtime.getRuntime(), displayControl, "android_servers");
            return displayControl;
        } catch (Throwable error) {
            return null;
        }
    }

    /** 反射异常的真实原因常被包在 InvocationTargetException 里，逐层解包后再拼描述。 */
    private static String describe(Throwable error) {
        StringBuilder sb = new StringBuilder(String.valueOf(error));
        Throwable root = error;
        int depth = 0;
        while (root.getCause() != null && root.getCause() != root && depth < 8) {
            root = root.getCause();
            depth++;
            sb.append(" <- ").append(root);
        }
        StackTraceElement[] frames = root.getStackTrace();
        int limit = Math.min(frames.length, 5);
        for (int i = 0; i < limit; i++) {
            sb.append(" @ ").append(frames[i]);
        }
        return sb.toString();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
