import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 被控设备侧的应用清单探测程序。
 *
 * 由客户端通过 {@code CLASSPATH=<dex> app_process <dir> AppProbe [包名...]} 推送到
 * /data/local/tmp 临时执行，向 stdout 输出 JSON：
 * {@code {"apps":[{"pkg":..,"label":..,"ver":..,"size":..,"split":..,"sys":..,"enabled":..,"iconBase64":..}],"iconFmt":"webp"}}
 * iconFmt 说明该批图标字节的实际编码（{@code webp} / {@code png}），客户端按它决定落盘后缀。
 * 用完由客户端删除（无论成败）。
 *
 * 带包名参数时只枚举这些包：进程管理这类只需要部分应用名与图标的场景不必把整台设备的
 * 清单都搬回来——图标本来就占输出的大头。不带参数时的行为不变。
 *
 * 只用公开 API；系统 Context 经反射获取（shell 进程不受 hidden API 限制）。
 */
public final class AppProbe {

    private static final int ICON_MAX_EDGE_PX = 96;
    private static final int ICON_QUALITY = 75;

    /** 首个图标失败原因，随 JSON 的 iconErr 字段回传（只为定位问题） */
    private static String iconError = null;

    private static void noteIconError(String message) {
        if (iconError == null) {
            iconError = message;
        }
    }

    private AppProbe() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8.name());
        // 空参数 = 枚举全部；带包名则只出这些包；首个参数 --meta = 只要清单不要图标
        boolean metaOnly = args.length > 0 && "--meta".equals(args[0]);
        int argStart = metaOnly ? 1 : 0;
        Set<String> wanted = args.length == argStart
                ? null
                : new HashSet<>(Arrays.asList(args).subList(argStart, args.length));
        try {
            Context context = systemContext();
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> infos = pm.getInstalledApplications(0);
            StringBuilder sb = new StringBuilder(1 << 20);
            sb.append("{\"apps\":[");
            boolean first = true;
            for (ApplicationInfo info : infos) {
                if (wanted != null && !wanted.contains(info.packageName)) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('{');
                sb.append("\"pkg\":").append(jsonString(info.packageName)).append(',');
                sb.append("\"label\":").append(jsonString(labelOf(pm, info))).append(',');
                sb.append("\"ver\":").append(jsonString(versionOf(pm, info.packageName))).append(',');
                sb.append("\"size\":").append(apkSizeOf(info)).append(',');
                sb.append("\"split\":").append(splitCountOf(info)).append(',');
                sb.append("\"sys\":").append(isSystem(info)).append(',');
                sb.append("\"enabled\":").append(info.enabled);
                if (!metaOnly) {
                    String icon = iconOf(pm, info);
                    if (icon != null) {
                        sb.append(',').append("\"iconBase64\":\"").append(icon).append('"');
                    }
                }
                sb.append('}');
            }
            sb.append("]");
            // 图标实际编码必须随字节一起报回去：客户端要按它决定落盘文件的后缀，
            // 后缀必须与字节的实际编码一致
            sb.append(",\"iconFmt\":").append(jsonString(iconFormatName()));
            // 图标批量失败时把首个原因带回来（客户端只调试日志用，不影响解析）
            if (iconError != null) {
                sb.append(",\"iconErr\":").append(jsonString(iconError));
            }
            sb.append("}");
            out.println(sb);
        } catch (Throwable error) {
            // 反射会把真实异常包在 InvocationTargetException 里，必须解包输出，
            // 否则客户端只看到包装类名，无法定位根因
            StringBuilder sb = new StringBuilder();
            sb.append("{\"error\":").append(jsonString(describe(error)));
            sb.append('}');
            out.println(sb);
        }
    }

    private static String describe(Throwable error) {
        StringBuilder sb = new StringBuilder(String.valueOf(error));
        Throwable root = error;
        int depth = 0;
        while (root.getCause() != null && root.getCause() != root && depth < 8) {
            root = root.getCause();
            depth++;
            sb.append(" <- ").append(String.valueOf(root));
        }
        StackTraceElement[] frames = root.getStackTrace();
        int limit = Math.min(frames.length, 5);
        for (int i = 0; i < limit; i++) {
            sb.append(" @ ").append(frames[i].toString());
        }
        return sb.toString();
    }

    private static Context systemContext() throws Exception {
        // systemMain() 内部会构造 ActivityThread$H（一个 Handler），
        // 要求当前线程已 prepare Looper；部分 ROM 上不 prepare 直接抛
        // "Can't create handler inside thread ... that has not called Looper.prepare()"
        android.os.Looper.prepare();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThread.getMethod("systemMain");
        Object thread = systemMain.invoke(null);
        Method getSystemContext = activityThread.getMethod("getSystemContext");
        return (Context) getSystemContext.invoke(thread);
    }

    private static String labelOf(PackageManager pm, ApplicationInfo info) {
        String label;
        try {
            label = String.valueOf(pm.getApplicationLabel(info));
        } catch (Throwable ignored) {
            label = null;
        }
        if (label == null || label.trim().isEmpty()) {
            // 应用名为空时客户端回退为包名最后一段；这里也兜一次，双保险
            label = info.packageName;
            int dot = label.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < label.length()) {
                label = label.substring(dot + 1);
            }
        }
        return label;
    }

    private static String versionOf(PackageManager pm, String packageName) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            if (info.versionName == null) {
                return "";
            }
            return info.versionName;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static long apkSizeOf(ApplicationInfo info) {
        long total = sizeOfFile(info.sourceDir);
        String[] splits = info.splitSourceDirs;
        if (splits != null) {
            for (String split : splits) {
                total += sizeOfFile(split);
            }
        }
        return total;
    }

    // 分包数量：客户端据此在确认框里就能说清导出产物是单 apk 还是 zip，不必再问一次设备
    private static int splitCountOf(ApplicationInfo info) {
        String[] splits = info.splitSourceDirs;
        return splits == null ? 0 : splits.length;
    }

    private static long sizeOfFile(String path) {
        if (path == null) {
            return 0L;
        }
        try {
            return new File(path).length();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static boolean isSystem(ApplicationInfo info) {
        int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (info.flags & mask) != 0;
    }

    /** 图标编码格式：按设备能力探测一次，整批图标复用。 */
    private static Bitmap.CompressFormat iconFormat;
    private static boolean iconFormatProbed;

    /**
     * 图标编码：用 [probeIconFormat] 选出的格式，取不到就返回 null。
     *
     * 注意：部分老设备的 framework.jar 里没有 WEBP_LOSSY 这个字段，**取字段本身**就会抛
     * NoSuchFieldError。所以字段访问必须包 try——否则异常在进入编码前抛出，被本方法的
     * 外层 catch 吞掉直接 return，回退逻辑永远走不到。
     *
     * 解码端 BitmapFactory 按文件头嗅探格式，落盘后缀不影响解码。
     */
    private static String iconOf(PackageManager pm, ApplicationInfo info) {
        try {
            Drawable drawable = pm.getApplicationIcon(info);
            if (drawable == null) {
                noteIconError("no drawable");
                return null;
            }
            Bitmap bitmap = toBitmap(drawable);
            if (bitmap == null) {
                noteIconError("no bitmap");
                return null;
            }
            try {
                Bitmap.CompressFormat format = probeIconFormat();
                if (format == null) {
                    noteIconError("no usable compress format");
                    return null;
                }
                return encodeIcon(bitmap, format);
            } finally {
                bitmap.recycle();
            }
        } catch (Throwable error) {
            noteIconError(String.valueOf(error));
            return null;
        }
    }

    /** WEBP_LOSSY 字段；老设备 framework 没这个字段，取字段本身就抛 NoSuchFieldError */
    private static Bitmap.CompressFormat webpLossyOrNull() {
        try {
            return Bitmap.CompressFormat.WEBP_LOSSY;
        } catch (Throwable error) {
            noteIconError("no WEBP_LOSSY field: " + error);
            return null;
        }
    }

    /**
     * 按设备能力选编码格式：WebP 优先（体积小），不可用回退 PNG，都不行返回 null。
     *
     * 两步筛选：先看字段在不在（老设备取 WEBP_LOSSY 就抛 NoSuchFieldError），再用 1x1
     * 位图实压一次验证编码器真的能出字节——字段在但编码器坏的情况也确实存在。
     * 结果按进程缓存：一批几百个图标只探测一次，不会每个图标各抛一次异常。
     */
    private static Bitmap.CompressFormat probeIconFormat() {
        if (iconFormatProbed) {
            return iconFormat;
        }
        iconFormatProbed = true;

        Bitmap.CompressFormat webp = webpLossyOrNull();
        if (webp != null && canCompress(webp)) {
            iconFormat = webp;
            return iconFormat;
        }
        if (canCompress(Bitmap.CompressFormat.PNG)) {
            iconFormat = Bitmap.CompressFormat.PNG;
            return iconFormat;
        }
        noteIconError("no compress format works");
        iconFormat = null;
        return null;
    }

    /**
     * 本批图标的实际编码名，客户端据此选落盘后缀。
     *
     * 取的是 [probeIconFormat] 缓存下来的那个值——枚举过程中每个图标都走同一个格式，
     * 所以一份就够。没探测过（一个图标都没取到）时返回空串，客户端按 WebP 兜底。
     */
    private static String iconFormatName() {
        if (!iconFormatProbed || iconFormat == null) {
            return "";
        }
        return iconFormat == Bitmap.CompressFormat.PNG ? "png" : "webp";
    }

    /** 用 1x1 位图实压一次，验证编码器在该设备上真的可用 */
    private static boolean canCompress(Bitmap.CompressFormat format) {
        Bitmap probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            return probe.compress(format, ICON_QUALITY, bytes) && bytes.size() > 0;
        } catch (Throwable error) {
            noteIconError("compress " + format + " failed: " + error);
            return false;
        } finally {
            probe.recycle();
        }
    }

    private static String encodeIcon(Bitmap bitmap, Bitmap.CompressFormat format) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!bitmap.compress(format, ICON_QUALITY, bytes)) {
                return null;
            }
            return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable error) {
            noteIconError(format + ": " + error);
            return null;
        } finally {
            try {
                bytes.close();
            } catch (Throwable ignored) {
                // 关闭失败不影响已取到的字节
            }
        }
    }

    private static Bitmap toBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = ICON_MAX_EDGE_PX;
            height = ICON_MAX_EDGE_PX;
        }
        float scale = Math.min(1f, (float) ICON_MAX_EDGE_PX / Math.max(width, height));
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        if (drawable instanceof BitmapDrawable) {
            Bitmap source = ((BitmapDrawable) drawable).getBitmap();
            if (source != null && !source.isRecycled()) {
                return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
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
