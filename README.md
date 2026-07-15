# ScrcpyForAndroid-enhanced

> 原项目 [Miuzarte/ScrcpyForAndroid](https://github.com/Miuzarte/ScrcpyForAndroid)

## 下载

\> [releases](https://github.com/aaxianyu/ScrcpyForAndroid/releases)

## 修改内容

除 scrcpy 原有功能外，此分支新增/增强以下功能：

- **USB 有线 ADB 连接**：USB 设备插拔监听、自动连接、隧道封装
- **应用管理**：安装/卸载/导出/停用/启用/清除数据/打开应用，应用搜索/收藏/置顶排序
- **实用工具**：截图、系统重启、DPI/分辨率修改、进程管理、无线 ADB 开关、设备信息面板、熄屏待机
- **悬浮球增强**：滑动悬浮球、滑动排序、虚拟按键二选一
- **局域网 IP 扫描**：本机 IP 显示、局域网设备扫描
- **命令书签**：终端命令书签持久化、自动回车开关
- **Snackbar 显示时长**：500-5000ms 可配置（默认 3000ms）
- **自定义分辨率**：连接时修改分辨率，退出时自动还原
- **ADB 连接保活**：前台服务通知保活 + 连接守护协程自动重连
- **Android 5/6 兼容性**：ps 命令兼容、ls 目录列表解析、动态初始路径
- **文件管理器增强**：切换设备自动刷新、未连接时连接后自动跳转内部存储、输入框光标保持
- **应用管理增强**：错误页支持下拉刷新、未连接设备时显示"返回设备页"按钮
- **低延迟音频开关修复**：切换低延迟音频等配置不再导致 Scrcpy 实例重建、无法进入全屏
- **ADB 重连自动检测与恢复**：连接中断自动重连
- **深色模式修复**：状态栏图标深色/浅色正确切换
- **语言切换修复**：跟随系统/手动中英文，不重建 Activity
- **全局配置名动态翻译**：设置页配置名实时翻译

以上改进由 [muyun] 开发，更多细节见 [DIFF.md](DIFF.md)。

## 截图

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

## 构建说明

本项目使用 [miuix](https://github.com/compose-miuix-ui/miuix) 作为 Git 子模块（submodule）

### 克隆项目

```bash
git clone --recursive https://github.com/aaxianyu/ScrcpyForAndroid.git
```

如果已克隆但子模块为空：

```bash
git submodule update --init --recursive
```

### 构建环境

- JDK 17
- Android SDK（API 35）
- NDK（CMake 构建原生库）

### 构建命令

```bash
./gradlew assembleRelease
```

> **注意**：请勿直接下载 ZIP 源码包构建，ZIP 不包含子模块内容。必须使用 `git clone --recursive`。




## 原项目

完整文档和功能说明请访问：[Miuzarte/ScrcpyForAndroid](https://github.com/Miuzarte/ScrcpyForAndroid)
