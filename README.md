<div align="center">

# 悬浮提词 · Hover Prompt

一个轻量、原生 Android 悬浮提词器，适合直播、口播、录课、视频拍摄和演讲发言。

<p>
  <img src="https://img.shields.io/badge/platform-Android%2026%2B-3DDC84?style=flat-square&logo=android" alt="Android 26+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin 2.0.21">
  <img src="https://img.shields.io/badge/version-0.1.0-111821?style=flat-square" alt="version 0.1.0">
</p>

</div>

## 项目简介

悬浮提词将一段台词显示在其他应用上层。打开悬浮窗后，可以切换到相机、抖音、微信或其他录屏应用，台词窗口仍保持在屏幕上方。

项目采用原生 Android + Jetpack Compose 实现主界面，使用前台服务维持悬浮窗运行，悬浮窗本体使用 Canvas 绘制，以便在不同屏幕尺寸和方向下保持可控的布局与触控区域。

> 当前版本是可运行的基础版本。`AI 跟随`目前是模式入口，尚未接入语音识别或根据语音定位台词的服务；`匀速滚动`是当前可用模式。

## 功能

| 功能 | 说明 |
| --- | --- |
| 台词编辑 | 在主界面输入或粘贴台词，显示字数统计 |
| 台词历史 | 点击打开悬浮提词时保存最近 8 条台词，可重新载入、单条删除或清空 |
| 实时预览 | 主界面预览会根据播放状态和速度滚动 |
| 匀速滚动 | 按设定速度自动向上滚动台词 |
| 播放控制 | 点击台词区域或底部播放按钮即可播放/暂停 |
| 手势滚动 | 悬浮窗内上下拖动台词可手动查看前后内容，不会改变当前播放状态 |
| 速度调节 | 支持 `8–60 dp/s`，运行中调整会同步到悬浮窗 |
| 字号调节 | 支持 `16–36 sp`，主界面和悬浮窗设置均可调整 |
| 设置记忆 | 速度、字号、颜色、透明度、循环和倒计时会保存上次选择 |
| 倒计时 | 播放前的 3 秒倒计时可在主界面或悬浮窗设置中开关 |
| 循环播放 | 读完后自动回到台词开头 |
| 外观设置 | 文字颜色、背景颜色和背景透明度 |
| 悬浮窗移动 | 拖动顶部六点区域移动窗口 |
| 悬浮窗缩放 | 从上、下、左、右任意边缘拖动调整窗口大小 |
| 90°旋转 | 点击悬浮窗底部旋转按钮，按 90° 步进循环调整台词内容方向 |
| 横竖屏适配 | 系统屏幕方向变化时，悬浮窗重新计算可用布局；主应用默认保持竖屏 |
| 前台服务 | 通过前台通知保持悬浮窗在其他应用上层运行 |

## 快速开始

### 运行环境

- Android Studio（建议使用当前稳定版）
- JDK 17
- Android SDK 35
- Android 设备或模拟器，Android 8.0 / API 26 及以上
- 真机调试时需要打开 USB 调试

项目使用以下主要版本：

| 组件 | 版本 |
| --- | --- |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.21 |
| Jetpack Compose BOM | 2024.09.03 |

### Android Studio 运行

1. 在 Android Studio 中打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 设备或启动模拟器。
4. 选择 `app` 配置并运行。
5. 首次点击“打开悬浮提词”时，在系统设置中允许“显示在其他应用上层”。
6. Android 13 及以上如果出现通知权限提示，请允许，以便看到悬浮提词的运行状态通知。

### 命令行构建

仓库当前不包含 Gradle Wrapper，因此命令行构建需要本机安装 Gradle 8.7，或直接使用 Android Studio 的 Gradle 集成。进入项目根目录后执行：

```powershell
gradle --offline :app:assembleDebug
```

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果本机没有 Gradle，可在 Android Studio 中执行同一个 `assembleDebug` 任务。

### 安装到已连接的手机

确认 `adb devices` 能看到设备并且手机已解锁，然后执行：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果手机弹出安装确认窗口，请在手机上确认；如果手机处于锁屏状态，安装会被系统拒绝。

## 使用说明

### 1. 编辑台词

在“当前台词”文本框中输入或粘贴内容。主界面会显示字数，复制按钮可以把当前台词复制到系统剪贴板，清空按钮会移除文本。

点击“打开悬浮提词”后，当前台词会进入“最近使用的台词”列表。点击历史记录可重新载入；每条记录右侧的删除按钮可以单独删除，也可以点击“清空”删除全部历史。

### 2. 调整设置

在“提词器设置”中可以调整：

- 提词模式：当前使用“匀速滚动”；“AI 跟随”暂为预留入口。
- 滚动速度：速度单位为 `dp/s`，数值越大滚动越快。
- 字号大小：调整悬浮窗中文字大小。
- 背景透明度、文字颜色和背景颜色。
- 循环播放：读完台词后是否从头开始。
- 3 秒倒计时：播放前是否显示 3、2、1 倒计时。

悬浮窗运行期间，主界面的速度、字号、颜色、透明度、循环和倒计时设置会实时同步；台词内容也会同步更新。
这些设置会保存到应用私有存储，下次重新打开应用或悬浮窗时继续使用上次选择。

### 3. 打开悬浮提词

点击“打开悬浮提词”。窗口显示后，可以切换到相机或其他应用使用。悬浮窗不会要求输入焦点，因此不会挡住其他应用的文本输入。

### 4. 悬浮窗控制

| 位置 / 图标 | 操作 |
| --- | --- |
| 顶部六点 | 拖动窗口 |
| 右上角 `×` | 将悬浮窗收起为右侧圆泡，不结束前台服务 |
| 右侧圆泡 | 点击恢复完整悬浮窗 |
| 台词区域 | 点击播放 / 暂停；上下拖动可手动查看台词，不会自动暂停 |
| `↶` | 将台词滚动位置复位到开头 |
| `☷` | 在悬浮窗内打开设置面板，可调速度、字号和倒计时 |
| `▶` / `Ⅱ` | 播放或暂停 |
| `↻` / `↺` | 每次旋转 90°，循环切换悬浮窗内容方向 |
| `∞` | 开关循环播放 |
| 窗口四条边 | 从上、下、左、右边缘拖动调整窗口大小 |

旋转只作用于悬浮窗的内容和窗口布局，不会把主应用强制切换成横屏。主应用默认使用竖屏；需要横向观看台词时，直接点击悬浮窗的旋转按钮即可。

## 权限说明

| 权限 | 用途 | 是否必须 |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | 在相机、抖音等其他应用上层显示悬浮提词 | 必须 |
| `FOREGROUND_SERVICE` | 维持悬浮窗前台服务 | 必须 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 的特殊用途前台服务声明 | 必须 |
| `POST_NOTIFICATIONS` | 显示悬浮提词正在运行的通知 | Android 13+ 建议允许 |

应用不会申请相机、麦克风或网络权限。当前版本的提词滚动不依赖相机画面，也没有启用语音识别。

## 项目结构

```text
HoverPrompt/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/hoverprompt/
│     │  ├─ MainActivity.kt             # Compose 主界面、设置与服务启动
│     │  ├─ PromptSettingsStore.kt      # 设置持久化读写
│     │  └─ OverlayService.kt            # 前台服务、悬浮窗布局、绘制与触控
│     └─ res/
│        ├─ drawable/ic_stat_prompt.xml
│        └─ values/styles.xml
├─ build.gradle.kts
├─ gradle.properties
├─ settings.gradle.kts
└─ README.md
```

### 核心运行流程

```text
主界面输入台词与设置
        │
        ├─ 打开悬浮提词
        │       │
        │       └─ OverlayService（前台服务）
        │                 │
        │                 └─ TYPE_APPLICATION_OVERLAY + PromptOverlayView
        │
        └─ 设置变化 ── ACTION_UPDATE ──> 更新正在运行的悬浮窗
```

- `MainActivity`负责 Compose UI、权限检查和把配置转成 `Intent` extras。
- `OverlayService`负责前台通知、悬浮窗生命周期、屏幕方向变化和窗口尺寸。
- `PromptOverlayView`负责 Canvas 绘制、文字滚动、设置面板、旋转变换以及触控命中区域。
- `PromptSettingsStore`负责在主界面和悬浮服务之间共享并持久化用户设置。
- `PromptHistoryStore`负责保存最近使用的台词，并提供重新载入、单条删除和清空操作。
- 旋转按钮按照 90° 步进切换内容坐标系；窗口会同步交换旋转后的包围尺寸，主 Activity 不参与旋转。

## 故障排查

### 点击打开后看不到悬浮窗

1. 打开系统设置，确认“悬浮提词”已允许“显示在其他应用上层”。
2. 关闭可能阻止后台运行的省电策略。
3. 重新点击“打开悬浮提词”。
4. Android 13+ 检查通知权限是否允许；通知权限不影响悬浮窗绘制，但会影响前台服务通知的可见性。

### 安装时显示 `User rejected permissions`

解锁手机并确认系统安装提示。Android 会在锁屏时拒绝通过 ADB 安装或更新应用。

### 模拟器出现 `crashpad_handler.exe - 应用程序错误`

这是模拟器或宿主机图形加速组件的错误，不代表应用本身一定崩溃。可以尝试：

- 在模拟器设置中切换图形渲染为软件或兼容模式。
- 更新模拟器和显卡驱动。
- 直接使用已开启 USB 调试的实体 Android 手机验证。

### 旋转后方向不对

悬浮窗底部的旋转按钮是应用内的 90° 旋转，不等同于系统状态栏的“自动旋转”。如果只希望跟随系统方向，请打开系统自动旋转；如果需要固定角度，请在悬浮窗内点击旋转按钮。

### 速度看起来没有变化

速度单位是 `dp/s`。建议先暂停后分别选择接近 `8 dp/s` 和 `60 dp/s`，再播放比较；运行中的设置修改会实时同步到悬浮窗。文字较少时，滚动距离有限，视觉差异会不明显。

### 台词无法上下查看

在悬浮窗台词区域按住并上下拖动即可手动滚动；一次轻触仍然用于播放或暂停。拖动不会改变当前播放状态，点击底部 `↶` 可以回到开头。

点击右上角 `×` 会把完整窗口收起成贴在屏幕右侧的小圆泡，悬浮服务仍然运行；点击圆泡即可恢复原来的窗口位置和大小。vivo/OriginOS 不同版本可能会对悬浮窗边缘手势或后台保活进行额外限制。

## 已知限制

- `AI 跟随`尚未接入 SpeechRecognizer、麦克风识别或 AI 定位服务。
- 台词正文不会作为当前草稿自动恢复，应用重新打开后仍会显示示例台词；点击打开悬浮提词后会保存到最近 8 条历史记录。速度、字号、颜色、透明度、循环和倒计时等设置会持久化。
- 当前版本未配置正式签名与自动发布流水线；GitHub Release 中的 APK 适合测试，不代表 Google Play 生产包。
- 不同 Android 厂商对悬浮窗权限、后台保活和通知权限的入口名称可能不同。

## 版本与发布

当前版本：`0.2.0`（`versionCode 2`）。

### 0.2.0 更新内容

- 修复四边拖动缩放时位置重复累加导致窗口飞出屏幕的问题，并限制窗口始终保持在屏幕范围内。
- 悬浮窗支持从上、下、左、右四个边缘调整大小。
- 台词区域上下拖动时不再自动暂停播放。
- 点击关闭按钮后收起为右侧圆泡，点击圆泡可以恢复悬浮窗。
- 打开悬浮提词时保存最近 8 条台词，支持重新载入、单条删除和清空历史。
- 速度、字号、颜色、透明度、循环和倒计时等设置会持久化保存。

本地创建测试 Release 的基本流程：

```powershell
gradle --offline :app:assembleDebug
git add .
git commit -m "feat: stabilize overlay resizing and add script history"
git push origin main
```

然后在 GitHub 仓库的 **Releases → Draft a new release** 中：

1. 创建标签 `v0.2.0`。
2. 上传 `app/build/outputs/apk/debug/app-debug.apk`。
3. 在 Release notes 中说明这是测试版 APK，并列出已知限制。

## License

仓库当前未声明开源许可证。如需公开分发或二次开发，请先补充合适的 License 文件。
