<!-- markdownlint-disable MD033 -->

# Scrcpy for Android (Enhanced)

<a href="https://github.com/Genymobile/scrcpy/blob/master/app/data/icon.svg" title="Modified from the original version">
  <img src="app/src/main/assets/icon/icon.svg" width="128" height="128" alt="scrcpy" align="right" />
</a>

[scrcpy](https://github.com/Genymobile/scrcpy) android client

从通过
[ADB Wireless](https://developer.android.com/tools/adb?hl=zh-cn#connect-to-a-device-over-wi-fi)
连接的 Android 设备镜像视频与音频，并允许使用触摸屏与键盘鼠标进行控制

不需要 root 权限，也无需在设备上安装应用程序

> [!NOTE]
> 本项目基于 scrcpy，但并非其官方版本，与原作者及维护团队不存在任何隶属或合作关系
> 原项目 [Miuzarte/ScrcpyForAndroid](https://github.com/Miuzarte/ScrcpyForAndroid)

## 下载

\> [releases](https://github.com/aaxianyu/ScrcpyForAndroid/releases)

## 截图

<p align="center">
  <img src="https://github.com/user-attachments/assets/077d4aaa-f81a-44ef-8987-d6cd4ba66cd1" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/ad586c34-a1fd-4c9c-b3a8-838b33e1cd79" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/8b3f982f-51e7-4206-8426-8535894a9cf0" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/d15daef4-ec37-437e-9dc8-49419f426745" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/9889aeab-d7a8-415e-b95e-fb86048a8071" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/372373cd-164c-48f3-b1ee-ce0e83cef59c" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/ba4282fe-d4f0-4f1c-8b37-426ce449763b" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/e124c60c-617f-474d-ae0e-4fc390e778da" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/03cae984-aef2-4a85-a7a8-4b2ae8ca8fe9" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/476846e2-d459-4ba3-a3ba-5f54c7c067cf" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/f894852d-6897-4e4a-8fcd-a1966e66f649" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/63b6d003-5259-49ea-8c2d-7e3035864697" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/9dd48404-2b67-4cad-b155-443a3223ef9e" height="300" alt="截图" />
  <img src="https://github.com/user-attachments/assets/a05c13c4-be5c-483a-bc51-07da1d13b16a" height="300" alt="截图" />
</p>

## Features

### 原版功能

- 控制时可拉起本机输入法，且支持输入中文
- 剪贴板同步
- 低延迟音频链路 (默认未启用)
- 带生物认证的锁屏密码自动填充 (入口位于虚拟按钮中)
- 多配置切换，设备绑定配置，连接后直接进入全屏
- 可替换 scrcpy-server
- 利用 mDNS 服务实现自动连接启用无线调试的设备、自动发现等待配对设备的IP与端口
- 自动横竖屏切换
- 横屏布局（仅屏幕比例小于 16:9 的设备）
- 全屏下映射返回键到远程
- 画中画
- 双向文件传输
- 流式 adb 终端
- 内置录制

### 增强功能

除 scrcpy 原有功能外，此分支新增/增强以下功能：

- **USB 有线 ADB 连接**：USB 设备插拔监听、自动连接、隧道封装
- **应用管理**：安装/卸载/导出/停用/启用/清除数据，应用搜索/收藏/置顶排序
- **实用工具**：截图、系统重启、DPI/分辨率修改、进程管理、无线 ADB 开关、设备信息面板、熄屏待机
- **悬浮球增强**：滑动悬浮球、滑动排序、虚拟按键二选一
- **局域网 IP 扫描**：本机 IP 显示、局域网设备扫描
- **命令书签**：终端命令书签持久化、自动回车开关
- **低延迟视频开关（实验性）**：独立开关，与原版低延迟音频分离
- **Snackbar 显示时长**：500-5000ms 可配置（默认 3000ms）
- **自定义分辨率**：连接时修改分辨率，退出时自动还原
- **ADB 重连**：连接中断自动检测与恢复
- **深色模式修复**：状态栏图标深色/浅色正确切换
- **语言切换修复**：跟随系统/手动中英文，不重建 Activity，HyperOS 状态栏沉浸修复
- **全局配置名动态翻译**：设置页配置名实时翻译
- **MTK 解码器兼容**：自动检测解码器能力，不支持时自动降级分辨率（上游 v0.4.5_pre1）

以上增强功能基于上游 v0.4.5_pre1，更多细节见 [DIFF.md](DIFF.md)。

## 已知问题

- 刚开始串流时有概率丢失关键帧导致花屏，等待一段时间后重新接收关键帧即可
- 因为没有设备用于 (也懒得) 测试，应用可能无法正常运行在安卓版本较低的设备上，特别是画中画功能，非常取决于国产 ROM 的实现
- 关闭画中画后不会停止 scrcpy 串流，仍然需要回到应用中点击停止
- 跨设备输入中文
  - 实现方式为利用剪贴板同步，会导致受控机剪贴板历史被填充输入历史
  - 不知道为什么有时候会上屏失败
- 虚拟按键的截图实现方式为发送
`keycode 120`，安卓官方([keycodes.h#349](https://android.googlesource.com/platform/frameworks/native/+/master/include/android/keycodes.h#349))的定义为
`System Request / Print Screen key.`，不同的厂商有不同的实现，在某些类原生(`AxionOS`) 上的行为是软重启

## TODO

\> [TODO.md](TODO.md)

## NOT-TODO

- 低版本安卓适配
  - 项目的 `minSdk` 为 26 / Android 8，98.4% 的设备都能成功安装上，~~只是出问题不管~~
  出问题了 (特别是崩溃闪退) 带 logcat 来尽量修
- 更多的文件操作，包括不限于 `删除`, `重命名` 等
  - 可以长按复制路径去终端用 `rm`, `mv`
- 录制的进一步优化
  - `MediaCodec` 限制太大，再继续做要引入 `ffmpeg`，ofc no way
- ADB 安装应用 / adb install
  - 需要大改 JNI 的实现因此不做
  - 可以推送文件之后使用终端安装或手动控制安装
- 有线控制 / fastboot
  - 左转甲壳虫

## Change Log

\> [CHANGELOG.md](CHANGELOG.md)

## 建议搭配模块

- 密码锁屏无法捕获: [LSPosed/DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure)
- 开机自动启用 adb: [gist/906291](https://gist.github.com/Miuzarte/9062915f1615d5eebd363c759fda496c)

## FAQ

0. 控制不了
   - [Genymobile/scrcpy/FAQ.md#control-issues](https://github.com/Genymobile/scrcpy/blob/master/FAQ.md#control-issues)

1. 切到后台后 ADB 断连
   - 将国产 ROM 中的 `省电策略` 调整至 `无限制`
   - 将安卓原生设置中的 `允许后台使用` 启用并设置为 `无限制` (应用设置页中有入口)

2. 虚拟屏不显示输入法 / 输入法显示在主屏幕
   - 将 `--display-ime-policy` 设置为 `local`
   - 自行在悬浮球中拉起本机输入法

3. 码率只能拉到 40Mbps 嫌低
   - 每个 Slider 选项的标题都可以点开自己输入值

4. 录制/下载的文件在哪
   - /sdcard/Movies/Scrcpy/
   - /sdcard/Download/Scrcpy/

5. 横屏模式对左撇子不太友好
   - 右上角有按钮可以对调方向

6. MIUI 虚拟屏没有桌面 / 虚拟屏白屏
   - 装个第三方桌面

## 构建说明

本项目使用 [miuix](https://github.com/compose-miuix-ui/miuix) 作为 Git 子模块（submodule）。

### 克隆项目

```bash
git clone --recursive https://github.com/aaxianyu/ScrcpyForAndroid.git
```

如果已克隆但子模块为空：

```bash
git submodule update --init --recursive
```

### 构建环境

- JDK 17+
- Android SDK (`compileSdk 37` / `buildTools 37.0.0`)
- Android NDK `29.0.14206865`

### 构建命令

```bash
./gradlew assembleRelease
```

> **注意**：请勿直接下载 ZIP 源码包构建，ZIP 不包含子模块内容。必须使用 `git clone --recursive`。

specific abi:

```bash
./gradlew assembleRelease -PabiList=arm64-v8a
```

### miuix 子模块版本

本项目已将 miuix 子模块锁定到 `fd5362ce`（2026-07-08），请勿手动更新到 miuix main 分支的最新版本，否则可能出现兼容性问题。

如需验证 miuix 版本：

```bash
cd submodule/miuix
git log -1 --pretty=format:"%h %ci %s"
```

预期输出：
```
fd5362ce 2026-07-08 17:22:12 +0800 feat: implement BreadcrumbBar (#371)
```

## Credits

- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) (包括图标)
- JNI ADB 实现: [rikkaapps/shizuku](https://github.com/rikkaapps/shizuku), [vvb2060/ndk.boringssl](https://github.com/vvb2060), [lsposed/libcxx](https://github.com/lsposed/libcxx)
- 界面组件: [YuKongA/miuix](https://github.com/compose-miuix-ui/miuix)
- 界面设计参考: [tiann/KernelSU/manager](https://github.com/tiann/KernelSU/tree/main/manager), [miuix/example](https://github.com/compose-miuix-ui/miuix/tree/main/example)
- 画中画实现参考: [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android)
- 原生应用设置页跳转: [YifePlayte/WOMMO](https://github.com/YifePlayte/WOMMO)
- 终端实现: [reapercanuk39/termux-kotlin-app](https://github.com/reapercanuk39/termux-kotlin-app) (仅 Apache 2.0 部分)

## License

[Apache License 2.0](LICENSE)

## Star History

<a href="https://www.star-history.com/?repos=aaxianyu%2FScrcpyForAndroid&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=aaxianyu/ScrcpyForAndroid&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=aaxianyu/ScrcpyForAndroid&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=aaxianyu/ScrcpyForAndroid&type=date&legend=top-left" />
 </picture>
</a>
