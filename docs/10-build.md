# 工程形态与构建

> 通用约束见根目录 `AGENTS.md`。本文件覆盖 Gradle 工程、SDK/ABI、构建与签名、相机与权限、最近任务、启动画面。

## 工程与依赖

- 唯一工程在 `android/`，用 Android Studio 或命令行 Gradle 构建。根工程名 `MuyianNative`（`settings.gradle.kts`），模块 `:app`。
- 版本：Gradle 8.13、AGP 8.10.1、Kotlin 2.0.21、Compose BOM 2024.10.01。
- 依赖只有 AndroidX 与 Compose：`core-ktx`、`activity-compose`、`lifecycle-runtime-ktx`、`compose.ui`、`compose.foundation`、`compose.material3`。禁止引入第三方 UI/图表/网络/统计库；确需新增依赖必须在 `docs/` 写明理由，并确认不违反隐私红线。
- `compileSdk 36`、`buildToolsVersion 36.0.0`、`minSdk 23`、`targetSdk 33`；Java/JVM target 17。
- `applicationId = com.muyian.debt`，`versionCode 100`，`versionName 0.1.0`。
- 目标平台只有 Android 真机。工程没有 NDK 与 `.so`，不需要 `abiFilters`；产物是纯 Kotlin/DEX，任意 ABI 的真机都能安装。

## 构建与签名

- 环境：JDK 17+、Android SDK Platform 36、Build-Tools 36.0.0。`android/local.properties` 写 `sdk.dir`（本机示例：`sdk.dir=D\:\\AndroidDev\\sdk`）。
- 调试构建：
  ```bash
  cd android
  ./gradlew assembleDebug
  # 产物：app/build/outputs/apk/debug/app-debug.apk
  ```
- 发布构建：
  ```bash
  ./gradlew assembleRelease
  ```
  release 默认只开 R8 与资源裁剪，**没有配置签名**；正式分发请自行配置 `signingConfig`，或用 `apksigner` 对产物签名。
- 改 `muyian/bill/listener/` 下任意文件、`android/app/src/main/AndroidManifest.xml` 或资源后重新构建安装即可；没有自定义调试基座这一层。

## 相机与权限

- 记一笔备注图片的拍照入口用 `ActivityResultContracts.TakePicture()`；首次拍照前用 `RequestPermission()` 申请 `android.permission.CAMERA`。相册入口用 `ActivityResultContracts.GetContent()`，设置页背景图只从相册选，不触发相机权限。
- `android/app/src/main/AndroidManifest.xml` 声明的权限：`CAMERA`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`、`VIBRATE`、`QUERY_ALL_PACKAGES`（仅「识别记录 → 扫描其它应用」按需枚举已安装应用，不读取应用数据）、`WRITE_SECURE_SETTINGS`（无障碍自恢复，需 adb 授予）、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`（后台保活）、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
- 只申请与功能直接相关的权限，不额外申请存储/位置/通讯录等权限；文件选择走 SAF，不需要存储权限。识别应用列表默认只按内置包名查询，打开诊断页不会触发「读取已安装应用」类 ROM 提示。
- 权限属于安装时/运行时配置：清单增删权限后必须重新安装；`WRITE_SECURE_SETTINGS`、无障碍、通知使用权、悬浮窗、MIUI「后台弹出界面」、电池优化白名单在新包/新签名上都要重新授权。

## 最近任务隐藏

- `MainActivity` 在清单里带 `android:excludeFromRecents="true"` 作为基线；部分 ROM 会忽略清单标记，所以 `AppState.applyRecentsVisibility()` 还会用 `ActivityManager.appTasks` 的 `setExcludeFromRecents()` 再设置一次。
- 设置页「后台运行 → 从多任务页面隐藏」是开关，key `muyian_hide_recents_v1`（默认开启），切换后立即生效。隐藏后最近任务里看不到应用，返回只能靠桌面图标或常驻通知，属预期行为，不要当成 bug 修。
- 切换开关只改运行时任务标记与 SharedPreferences，不改清单。

## 启动画面

- 不设品牌启动页。Android 12+ 强制的一帧系统 SplashScreen 在 `values-v31/themes.xml` 里只留 `#F7F4EC` 底色、图标透明；首帧直接是首页。
- `MainActivity.onCreate` 按用户主题 `setTheme` 成 `Theme.Muyian.{Wukong,Nezha,Shaseng}`，应用窗口底色跟随主题（`values/themes.xml` + `values/colors.xml` 的 `hero_*` 平均色）；系统启动帧本身用清单里的静态主题，不能按应用内主题变。选沙僧黑时系统启动帧仍是浅色，会先亮一下再切黑，属预期。
- 首页第一帧用同色 `SplashColor` 覆盖并在 100ms 内淡出，避免 hero 的橙/红硬切；只做淡出，不额外增加等待时间。
