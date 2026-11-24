<!-- markdownlint-disable MD033 -->

# Scrcpy For Android-LLM



[miuzarte/ScrcpyForAndroid](https://github.com/miuzarte/ScrcpyForAndroid) 的增强分支

通过 ADB 连接的 Android 设备镜像视频与音频，并允许使用触摸屏与键盘鼠标进行控制

不需要 root 权限，也无需在被控设备上安装应用程序

> [!NOTE]
> 本项目基于 [miuzarte/ScrcpyForAndroid](https://github.com/miuzarte/ScrcpyForAndroid)，
>
> 本分支的包名与上游保持一致，但签名不一致，因此不可直接覆盖安装上游版本

## 下载

> [releases](https://github.com/aaxianyu/ScrcpyForAndroid/releases)

| 架构 | 适用设备 |
| ---- | -------- |
| arm64-v8a | 绝大多数现代手机 |
| armeabi-v7a | 32 位 ARM 老设备 |
| universal | 不确定架构时选它，体积最大 |
| x86 / x86_64 | 模拟器 |

版本号带 `e` 后缀（如 `v0.7.2e`）的才是本分支的发布，不带后缀的是上游原版；预发布再带 `_preN`（如 `v0.7.3e_pre1`）。

> [!警告]
> **从旧版 0.5.x（0.5.4e）升级的用户请先卸载旧版，或清理应用数据后再安装。**
> 本分支基于上游 0.7.x 完全重写，旧的设置项、缓存结构与旧版不兼容，
> 直接覆盖安装后可能出现异常。清理数据会一并清掉设备列表与 ADB 授权，需要重新添加。
>
> 详见 [CHANGELOG.md](CHANGELOG.md) 的迁移说明。

## 本分支增强

以下功能在上游的基础上新增，括号内为引入的大致模块：

- **应用管理**
  - 设备应用清单：安装、卸载、停用/启用、清除数据、导出 APK，支持批量操作
  - 应用详情页：单独查看应用信息，动作底栏直接操作，更多菜单收纳低频操作
  - 应用对比：两台设备的应用并排对比（版本更新 / 仅单侧存在），可跨设备安装与导出
  - 分包应用（split APK）可安装、批量导出时为 ZIP
  - 应用图标与清单本地缓存，按设备分区，连接后静默刷新
  - 搜索、拼音排序、快速滚动条
- **实用工具**
  - 截图、高级重启（recovery / bootloader / fastboot 等）、屏幕 DPI、屏幕分辨率
  - 无线调试开关、一键激活（Shizuku / 冰箱 / 黑阈 / 小黑屋）、设备信息面板、进程管理、熄屏待机
- **设备信息**
  - 独立页面展示处理器主频、屏幕 DPI、屏幕尺寸等硬件信息
- **连接与投屏**
  - 局域网发现可连接的 ADB 设备
  - ADB 连接保活（前台服务 + 守护协程自动重连）
  - 预览卡点击直接进入全屏
  - 投屏时修改分辨率与DPI
- **交互**
  - MIUI 风格滑动悬浮球（边缘吸附、无操作渐隐、展开动画、动作排序）
  - 终端命令书签
  - 锁屏密码自动填充支持填充后自动回车
  - Snackbar 显示时长可调（500-5000ms）
- **所有应用**
  - 置顶、搜索、图标显示、滚动条
  - 最近任务图标显示
- **其他**
  - 关于页区分「本分支」与「上游项目」，更新检查跟踪本分支发布

## 与上游同步

本分支以上游 [miuzarte/ScrcpyForAndroid](https://github.com/miuzarte/ScrcpyForAndroid) 的 release tag 为基线，不定期同步上游更新；当前已同步到上游 0.7.3 系列的最新提交。

## 截图

**应用管理**

<img src="https://github.com/user-attachments/assets/951fb651-ee39-45d3-8346-5e1ed9b68ab6" height="300" alt="应用管理">
<img src="https://github.com/user-attachments/assets/1dce63e0-6207-4646-8b4a-eef7367db3d6" height="300" alt="应用详情">
<img src="https://github.com/user-attachments/assets/1d97ea77-f0d5-48ca-a017-ec2ace7ebf3c" height="300" alt="应用详情-更多菜单">
<img src="https://github.com/user-attachments/assets/faf09244-b146-451e-bfdb-ad418cc84cc7" height="300" alt="应用对比">
<img src="https://github.com/user-attachments/assets/c96349dc-16a3-4245-8dcd-e01f4ad660e7" height="300" alt="所有应用-置顶与搜索">

**实用工具**

<img src="https://github.com/user-attachments/assets/a9bfba66-341a-4f4f-b423-a2230354d5f3" height="300" alt="实用工具">
<img src="https://github.com/user-attachments/assets/7403e424-dab2-4f5a-b2de-c3d386843ddb" height="300" alt="一键激活">
<img src="https://github.com/user-attachments/assets/b9acef23-6aef-43b8-b089-0ea417da6b67" height="300" alt="设备信息">
<img src="https://github.com/user-attachments/assets/03b70758-f668-450d-ba63-49fc7c5e5970" height="300" alt="修改分辨率">

**连接与投屏**

<img src="https://github.com/user-attachments/assets/57ce0162-321b-4737-8086-a53ded6b204e" height="300" alt="局域网扫描">
<img src="https://github.com/user-attachments/assets/5f67f601-2a63-42df-8cdf-2cde5df70e40" height="300" alt="投屏时修改显示">

**交互**

<img src="https://github.com/user-attachments/assets/e474eaa0-d0d2-402e-964b-cfbab9a8dd8e" height="300" alt="投屏内悬浮球展开">
<img src="https://github.com/user-attachments/assets/c7da5d6c-365d-42b4-b60c-16d0ffecb337" height="300" alt="悬浮球排序">
<img src="https://github.com/user-attachments/assets/c20551b5-4965-45fc-a41f-d9179f4685d2" height="300" alt="命令书签">
<img src="https://github.com/user-attachments/assets/ac4c1a05-52bb-4396-bd87-2f316152356e" height="300" alt="密码自动回车">

> 截图来自 [issues/1](https://github.com/aaxianyu/ScrcpyForAndroid/issues/1)，点击图片可查看原图。

## 功能

上游已有的功能，本分支全部保留：

- 控制时可拉起本机输入法，且支持输入中文
- 剪贴板同步
- 低延迟音频链路 (默认未启用)
- 带生物认证的锁屏密码自动填充
- 多配置切换，设备绑定配置，连接后直接进入全屏
- 可替换 scrcpy-server
- 利用 mDNS 服务实现自动连接启用无线调试的设备、自动发现等待配对设备的IP与端口
- 二维码配对
- 自动横竖屏切换
- 横屏布局
- 全屏下映射返回键到远程
- 画中画
- 双向文件传输
- 流式 adb 终端
- 内置录制

## 本分支已知问题

- 本分支由 AI 参与开发与维护，改动量大，遇到问题时请附上 logcat
- 应用管理与工具页的部分操作依赖设备侧探针，个别 ROM 可能被限制
- 本分支功能不保证在低版本安卓设备（主控/被控 Android10以下）可用


## Change Log

> [CHANGELOG.md](CHANGELOG.md)


## 常见问题
1. 为什么版本号后面有个 `e`
   - `e` 表示这是本分支（enhanced）的发布，与上游同号版本区分开
   - 应用内更新检查只看带 `e` 的 release，不会把上游版本当成新版

2. 能不能覆盖上游版本
   - 不可以，虽然包名一致，但签名与上游不一致。
   - 不能覆盖安装，也无法与上游共存

3. 连接容易断开
   - 在设置里找到调整电池策略
   - 本分支新增了后台保活开关，仅需通知权限（不给通知权限也能增强保活）

## 构建

- JDK 21
- Android SDK (`compileSdk 37` / `buildTools 37.0.0`)
- Android NDK `30.0.16248370` (需要与 `libcxx` 一致)

```bash
git clone --recursive https://github.com/aaxianyu/ScrcpyForAndroid.git
cd ScrcpyForAndroid
./gradlew assembleDebug
```

已克隆但没有拉取子模块：

```bash
git submodule update --init --recursive
```

specific abi:

```bash
./gradlew assembleRelease -PabiList=arm64-v8a
```

发布签名走仓库根目录的 `.env`（`RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` /
`RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`），文件不入库；缺失时 release 变体不签名。

## 致谢

- 上游项目: [miuzarte/ScrcpyForAndroid](https://github.com/miuzarte/ScrcpyForAndroid)
- 界面组件: [YuKongA/miuix](https://github.com/compose-miuix-ui/miuix)

## License

[Apache License 2.0](LICENSE)
