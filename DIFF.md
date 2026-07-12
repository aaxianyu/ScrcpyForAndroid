# ScrcpyForAndroid Enhanced — 与上游 v0.4.5_pre1 差异对比

> 对比基准：`upstream/main` `a2937f5`（Miuzarte/ScrcpyForAndroid 上游主线）
> 对比目标：`open-source` 分支 HEAD（`73afd9e`）
> 生成日期：2026-07-13
> 说明：以下仅列出**新增/修改的功能**，不包含上游 v0.4.5_pre1 已存在的功能

---

## 一、总览

| 指标 | 数值 |
|------|------|
| 变更文件总数 | **70+** |
| 新增文件 | **24** |
| 修改文件 | **38+** |
| 代码新增行 | **10,000+** |

---

## 二、新增文件（24个）

### 2.1 USB 有线 ADB 连接
| 文件 | 说明 | 行数 |
|------|------|------|
| `nativecore/UsbAdbDeviceWatcher.kt` | USB 设备插拔监听器 | 375 |
| `nativecore/UsbAdbTunnel.kt` | USB ADB 隧道封装（端点读写、流包装） | 486 |
| `widgets/UsbDeviceCard.kt` | USB 设备卡片 UI 组件 | 189 |
| `res/xml/usb_device_filter.xml` | USB 设备过滤器（ADB 接口类 0xFF） | 16 |
| `res/drawable/ic_usb.xml` | USB 连接图标 | 9 |

### 2.2 应用管理
| 文件 | 说明 | 行数 |
|------|------|------|
| `pages/AppManagerScreen.kt` | 应用管理界面（安装/卸载/导出/停用/启用/清除数据） | 1,663 |
| `services/AppManagerService.kt` | 应用管理服务层 | 370 |
| `devicehelper/DeviceAppsHelper.java` | Java 辅助类（获取应用列表/图标/包信息） | 407 |
| `assets/bin/device_apps_helper.jar` | 编译后的设备端助手 JAR | ~6KB |

### 2.3 实用工具
| 文件 | 说明 | 行数 |
|------|------|------|
| `pages/UtilityToolsScreen.kt` | 实用工具页面（截图/重启/DPI/分辨率/激活应用/进程管理/无线ADB/设备信息） | 1,429 |

### 2.4 悬浮球排序
| 文件 | 说明 | 行数 |
|------|------|------|
| `pages/SwipeFloatingBallOrderScreen.kt` | 滑动悬浮球排序页面 | 248 |

### 2.5 设备与网络
| 文件 | 说明 | 行数 |
|------|------|------|
| `pages/LocalIpScreen.kt` | 本机 IP 显示页面 | 164 |
| `utils/LanDeviceScanner.kt` | 局域网设备扫描工具 | 116 |
| `utils/NetworkUtils.kt` | 网络工具类 | 92 |
| `widgets/LanScanDialog.kt` | 局域网扫描弹窗 | 242 |
| `widgets/DeviceInfoSheet.kt` | 设备信息面板 | 325 |

### 2.6 UI 组件
| 文件 | 说明 | 行数 |
|------|------|------|
| `widgets/CommandBookmarkBottomSheet.kt` | 命令书签弹窗 | 279 |
| `scaffolds/ImeAwareTextField.kt` | 输入法感知文本输入框 | 120 |

### 2.7 存储与配置
| 文件 | 说明 | 行数 |
|------|------|------|
| `storage/CommandBookmarkStore.kt` | 命令书签持久化存储 | 81 |

### 2.8 资源文件
| 文件 | 说明 |
|------|------|
| `res/xml/file_paths.xml` | FileProvider 路径配置 |

---

## 三、主要修改文件

### 3.1 构建配置
| 文件 | 变更 | 说明 |
|------|------|------|
| `app/build.gradle.kts` | +64/-1 | 添加国内镜像仓库、JAR 提取任务、JDK 路径跨平台支持、scrcpy-server 下载 |

### 3.2 ADB 连接层
| 文件 | 变更 | 说明 |
|------|------|------|
| `nativecore/DirectAdbClient.kt` | +240/-36 | 新增 `injectExternalConnection()` 支持 USB 连接注入；添加连接超时、取消功能；重连逻辑优化 |
| `nativecore/NativeAdbService.kt` | +105/-3 | 新增 `connectUsb()`/`disconnectIfUsbDevice()` 方法 |
| `services/DeviceAdbConnectionCoordinator.kt` | +84/-40 | 支持 USB/WiFi 连接类型统一协调、拔线自动断开 |

### 3.3 页面修改
| 文件 | 变更 | 说明 |
|------|------|------|
| `pages/DeviceTabScreen.kt` | +131/-13 | 集成 USB 设备卡片、扫描设备按钮、应用搜索/收藏 |
| `pages/DeviceTabViewModel.kt` | +422/-11 | 新增分辨率/唤醒/应用/DPI/连接状态管理等 |
| `pages/SettingsScreen.kt` | +265/-55 | 新增应用图标显示开关、低延迟视频开关、命令书签、语言选择、Snackbar 时长、熄屏待机、悬浮球透明度等 |
| `pages/MainScreen.kt` | +72/-15 | 新增应用管理入口、实用工具入口、守护进程模式 |
| `pages/FullscreenControlScreen.kt` | +167/-52 | 新增虚拟按钮显示图标、USB 断开按钮、唤醒检测优化 |
| `pages/ScrcpyAllOptionsScreen.kt` | +233/-27 | 新增应用收藏置顶、界面优化 |
| `pages/FileManagerScreen.kt` | +73/-9 | 新增应用管理入口、自动刷新 |

### 3.4 组件修改
| 文件 | 变更 | 说明 |
|------|------|------|
| `widgets/VirtualButtons.kt` | +501/-22 | 新增悬浮球菜单/排序、临时隐藏虚拟按键、图标更新 |
| `widgets/AppBottomSheets.kt` | +220/-23 | 新增应用图标显示、搜索/收藏/排序功能 |
| `widgets/DeviceWidgets.kt` | +165/-55 | ADB 连接卡片优化、状态检测 |

### 3.5 存储/模型修改
| 文件 | 变更 | 说明 |
|------|------|------|
| `storage/AppSettings.kt` | +126/-2 | 新增设置项（Snackbar 时长、熄屏待机、图标显示、USB 相关等） |
| `models/DeviceModels.kt` | +103/-11 | 新增 USB 连接类型枚举、连接目标字段、HDCP、Snackbar 时长等配置项 |

### 3.6 服务层
| 文件 | 变更 | 说明 |
|------|------|------|
| `services/AppRuntime.kt` | +44/-4 | 新增语言切换处理、USB 连接管理 |

### 3.7 字符串资源
| 文件 | 变更 |
|------|------|
| `res/values/strings.xml` | +267/-2（新增中英双语字符串） |
| `res/values-zh/strings.xml` | +267/-2（同） |

### 3.8 其他文件
| 文件 | 变更 |
|------|------|
| `AndroidManifest.xml` | +43/-1（USB host 权限、设备过滤器声明、Activity 启动标志等） |
| `.gitignore` | +16（添加构建产物忽略规则） |
| `scrcpy/Scrcpy.kt` | +101/-2（锁屏检测、唤醒逻辑、重连优化） |
| `StreamActivity.kt` | +20/-1（NEW_TASK + CLEAR_TOP 启动标志） |
| `MainActivity.kt` | +18/-0（语言切换、USB 监听器启动、adjustResize） |

---

## 四、新增/增强功能摘要

| 功能 | 说明 |
|------|------|
| USB 有线 ADB 连接 | 支持 USB 设备插拔监听、自动连接、隧道封装 |
| 应用管理 | 安装/卸载/导出/停用/启用/清除数据 |
| 实用工具 | 截图/重启/DPI/分辨率/激活应用/进程管理/无线ADB/设备信息 |
| 滑动悬浮球 | 位置持久化、透明度设置、项排序 |
| 局域网 IP 扫描 | 本机 IP 显示、局域网设备扫描 |
| 命令书签 | 终端命令书签持久化 |
| Snackbar 显示时长设置 | 500ms-5000ms 可调 |
| 熄屏待机功能 | 锁屏深睡后自动点亮屏幕 |
| ADB 重连自动检测与恢复 | 连接中断自动重连 |
| 自定义分辨率修改 | 连接时修改分辨率，退出时还原 |
| 深色模式修复 | 状态栏图标深色/浅色正确切换 |
| 语言切换支持 | 跟随系统 / 手动选择中英文 |
| 应用搜索/收藏/置顶排序 | 设备页应用列表增强 |
| 全局配置名称动态翻译 | 设置页配置名实时翻译 |
