import android.os.IBinder;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class DeviceAppsHelper {
    public static void main(String[] args) {
        Set<String> targetPkgs = new HashSet<>();
        boolean fetchIcons = true;
        for (String arg : args) {
            if ("--no-icons".equals(arg)) {
                fetchIcons = false;
                continue;
            }
            if (arg != null && !arg.isEmpty()) targetPkgs.add(arg);
        }

        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "package");
            if (binder == null) {
                System.out.println("{\"e\":\"ServiceManager.getService(\\\"package\\\") returned null\"}");
                return;
            }

            Class<?> stubClass = Class.forName("android.content.pm.IPackageManager$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object pm = asInterface.invoke(null, binder);

            long flagsLong = 0x00020000L | 0x00008000L;
            int flagsInt = 0x00020000 | 0x00008000;

            List<?> pkgInfos = null;
            List<?> appInfos = null;

            Method[] allMethods = pm.getClass().getMethods();
            for (Method m : allMethods) {
                String mn = m.getName();
                if (!mn.equals("getInstalledPackages")) continue;
                Class<?>[] pts = m.getParameterTypes();

                if (pts.length == 2 && (pts[0] == int.class || pts[0] == long.class) && pts[1] == int.class) {
                    Object invokeFlags = pts[0] == long.class ? (Object) flagsLong : (Object) flagsInt;
                    pkgInfos = unwrapList(m.invoke(pm, invokeFlags, 0));
                    break;
                } else if (pts.length == 1 && (pts[0] == int.class || pts[0] == long.class)) {
                    Object invokeFlags = pts[0] == long.class ? (Object) flagsLong : (Object) flagsInt;
                    pkgInfos = unwrapList(m.invoke(pm, invokeFlags));
                    break;
                }
            }

            if (pkgInfos == null) {
                for (Method m : allMethods) {
                    String mn = m.getName();
                    if (!mn.equals("getInstalledApplications")) continue;
                    Class<?>[] pts = m.getParameterTypes();

                    if (pts.length == 2 && (pts[0] == int.class || pts[0] == long.class) && pts[1] == int.class) {
                        Object invokeFlags = pts[0] == long.class ? (Object) flagsLong : (Object) flagsInt;
                        appInfos = unwrapList(m.invoke(pm, invokeFlags, 0));
                        break;
                    } else if (pts.length == 1 && (pts[0] == int.class || pts[0] == long.class)) {
                        Object invokeFlags = pts[0] == long.class ? (Object) flagsLong : (Object) flagsInt;
                        appInfos = unwrapList(m.invoke(pm, invokeFlags));
                        break;
                    }
                }
            }

            if (pkgInfos == null && appInfos == null) {
                System.out.println(buildErrorJson(allMethods));
                return;
            }

            Class<?> appInfoClass = Class.forName("android.content.pm.ApplicationInfo");
            Method getAppLabel = null;
            for (Method m : allMethods) {
                if (m.getName().equals("getApplicationLabel")) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 1 && pts[0] == appInfoClass) {
                        getAppLabel = m;
                        break;
                    }
                    if (pts.length == 2 && pts[0] == String.class && pts[1] == int.class) {
                        getAppLabel = m;
                        break;
                    }
                }
            }

            Field nonLocalLabel = null;
            try {
                nonLocalLabel = appInfoClass.getField("nonLocalizedLabel");
            } catch (NoSuchFieldException ignored) {}

            Field sourceDirField = appInfoClass.getField("sourceDir");
            Field labelResField = null;
            try {
                labelResField = appInfoClass.getField("labelRes");
            } catch (NoSuchFieldException ignored) {}

            Field iconField = null;
            try {
                iconField = appInfoClass.getField("icon");
            } catch (NoSuchFieldException ignored) {}

            Field versionNameField = null;
            try {
                Class<?> pkgInfoClass = Class.forName("android.content.pm.PackageInfo");
                versionNameField = pkgInfoClass.getField("versionName");
            } catch (Exception ignored) {}

            DisplayMetrics dm = new DisplayMetrics();
            dm.densityDpi = 480; // DENSITY_XXHIGH，加载高分辨率图标

            StringBuilder sb = new StringBuilder();
            sb.append("{\"apps\":[");

            if (pkgInfos != null) {
                boolean first = true;
                Field appInfoField = null;
                for (Field f : pkgInfos.get(0).getClass().getFields()) {
                    if (f.getName().equals("applicationInfo")) {
                        appInfoField = f;
                        break;
                    }
                }

                for (Object pkgInfo : pkgInfos) {
                    Field pkgField = pkgInfo.getClass().getField("packageName");
                    String pkgName = (String) pkgField.get(pkgInfo);

                    if (!targetPkgs.isEmpty() && !targetPkgs.contains(pkgName)) continue;

                    Object app = appInfoField != null ? appInfoField.get(pkgInfo) : null;
                    if (app == null) continue;

                    String label = null;
                    String iconBase64 = null;
                    String versionName = null;
                    long apkSize = 0L;

                    if (getAppLabel != null) {
                        try {
                            Class<?>[] pts = getAppLabel.getParameterTypes();
                            if (pts.length == 1) {
                                label = getAppLabel.invoke(pm, app).toString();
                            } else if (pts.length == 2) {
                                int uidField = app.getClass().getField("uid").getInt(app);
                                label = getAppLabel.invoke(pm, pkgName, uidField).toString();
                            }
                        } catch (Exception ignored) {}
                    }

                    String apkPath = null;
                    AssetManager am = null;
                    Resources res = null;

                    if (fetchIcons) {
                        try {
                            apkPath = (String) sourceDirField.get(app);
                            if (apkPath != null) {
                                am = AssetManager.class.newInstance();
                                Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                                addAssetPath.setAccessible(true);
                                int cookie = (Integer) addAssetPath.invoke(am, apkPath);
                                if (cookie != 0) {
                                    res = new Resources(am, dm, new Configuration());
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    try {
                        apkPath = (String) sourceDirField.get(app);
                        if (apkPath != null) {
                            File f = new File(apkPath);
                            if (f.exists()) apkSize = f.length();
                        }
                    } catch (Exception ignored) {}

                    if (label == null && nonLocalLabel != null) {
                        try {
                            CharSequence cs = (CharSequence) nonLocalLabel.get(app);
                            if (cs != null && cs.length() > 0) label = cs.toString();
                        } catch (Exception ignored) {}
                    }

                    if (label == null && labelResField != null && res != null) {
                        try {
                            int labelRes = labelResField.getInt(app);
                            if (labelRes != 0) {
                                label = res.getText(labelRes).toString();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (fetchIcons && iconBase64 == null && iconField != null && res != null) {
                        try {
                            int iconRes = iconField.getInt(app);
                            if (iconRes != 0) {
                                Drawable d = res.getDrawableForDensity(iconRes, 480, null);
                                if (d != null) {
                                    int iconSize = 144; // 3x of 48, 匹配 XXHDPI
                                    Bitmap bmp = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(bmp);
                                    d.setBounds(0, 0, iconSize, iconSize);
                                    d.draw(canvas);
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    bmp.compress(Bitmap.CompressFormat.WEBP, 80, baos);
                                    iconBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                                    bmp.recycle();
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (versionNameField != null) {
                        try {
                            Object vn = versionNameField.get(pkgInfo);
                            if (vn != null) versionName = vn.toString();
                        } catch (Exception ignored) {}
                    }

                    if (label == null) label = pkgName;

                    if (!first) sb.append(",");
                    sb.append("{\"p\":\"").append(escape(pkgName)).append("\"");
                    sb.append(",\"l\":\"").append(escape(label)).append("\"");
                    if (versionName != null) {
                        sb.append(",\"v\":\"").append(escape(versionName)).append("\"");
                    }
                    if (apkSize > 0) {
                        sb.append(",\"s\":").append(apkSize);
                    }
                    if (iconBase64 != null) {
                        sb.append(",\"i\":\"").append(escape(iconBase64)).append("\"");
                    }
                    sb.append("}");
                    first = false;
                }
            } else {
                boolean first = true;
                for (Object app : appInfos) {
                    Field pkgField = app.getClass().getField("packageName");
                    String pkgName = (String) pkgField.get(app);

                    if (!targetPkgs.isEmpty() && !targetPkgs.contains(pkgName)) continue;

                    String label = null;
                    String iconBase64 = null;
                    long apkSize = 0L;

                    if (getAppLabel != null) {
                        try {
                            Class<?>[] pts = getAppLabel.getParameterTypes();
                            if (pts.length == 1) {
                                label = getAppLabel.invoke(pm, app).toString();
                            } else if (pts.length == 2) {
                                int uidField = app.getClass().getField("uid").getInt(app);
                                label = getAppLabel.invoke(pm, pkgName, uidField).toString();
                            }
                        } catch (Exception ignored) {}
                    }

                    String apkPath = null;
                    AssetManager am = null;
                    Resources res = null;

                    if (fetchIcons) {
                        try {
                            apkPath = (String) sourceDirField.get(app);
                            if (apkPath != null) {
                                am = AssetManager.class.newInstance();
                                Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                                addAssetPath.setAccessible(true);
                                int cookie = (Integer) addAssetPath.invoke(am, apkPath);
                                if (cookie != 0) {
                                    res = new Resources(am, dm, new Configuration());
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    try {
                        apkPath = (String) sourceDirField.get(app);
                        if (apkPath != null) {
                            File f = new File(apkPath);
                            if (f.exists()) apkSize = f.length();
                        }
                    } catch (Exception ignored) {}

                    if (label == null && nonLocalLabel != null) {
                        try {
                            CharSequence cs = (CharSequence) nonLocalLabel.get(app);
                            if (cs != null && cs.length() > 0) label = cs.toString();
                        } catch (Exception ignored) {}
                    }

                    if (label == null && labelResField != null && res != null) {
                        try {
                            int labelRes = labelResField.getInt(app);
                            if (labelRes != 0) {
                                label = res.getText(labelRes).toString();
                            }
                        } catch (Exception ignored) {}
                    }

                    if (fetchIcons && iconBase64 == null && iconField != null && res != null) {
                        try {
                            int iconRes = iconField.getInt(app);
                            if (iconRes != 0) {
                                Drawable d = res.getDrawableForDensity(iconRes, 480, null);
                                if (d != null) {
                                    int iconSize = 144; // 3x of 48, 匹配 XXHDPI
                                    Bitmap bmp = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(bmp);
                                    d.setBounds(0, 0, iconSize, iconSize);
                                    d.draw(canvas);
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    bmp.compress(Bitmap.CompressFormat.WEBP, 80, baos);
                                    iconBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                                    bmp.recycle();
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (label == null) label = pkgName;

                    if (!first) sb.append(",");
                    sb.append("{\"p\":\"").append(escape(pkgName)).append("\"");
                    sb.append(",\"l\":\"").append(escape(label)).append("\"");
                    if (apkSize > 0) {
                        sb.append(",\"s\":").append(apkSize);
                    }
                    if (iconBase64 != null) {
                        sb.append(",\"i\":\"").append(escape(iconBase64)).append("\"");
                    }
                    sb.append("}");
                    first = false;
                }
            }

            sb.append("]}");
            System.out.println(sb.toString());
        } catch (Exception e) {
            System.out.println("{\"e\":\"" + escape(e.toString()) + "\"}");
        }
    }

    private static String buildErrorJson(Method[] allMethods) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"e\":\"No suitable getInstalled* method found. Available methods: ");
        boolean firstM = true;
        for (Method m : allMethods) {
            String mn = m.getName();
            if (mn.startsWith("getInstalled") || mn.startsWith("getApplication")) {
                if (!firstM) sb.append(", ");
                sb.append(mn).append("(");
                Class<?>[] pts = m.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(pts[i].getName());
                }
                sb.append(")");
                firstM = false;
            }
        }
        sb.append("\"}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<?> unwrapList(Object result) throws Exception {
        if (result instanceof List) {
            return (List<?>) result;
        }
        Class<?> cls = result.getClass();
        if (cls.getName().equals("android.content.pm.ParceledListSlice")) {
            Method getList = cls.getMethod("getList");
            return (List<?>) getList.invoke(result);
        }
        return (List<?>) result;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
    }
}
