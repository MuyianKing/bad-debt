# 孽账 · Android 版

**Kotlin + Jetpack Compose** 的 Android 自动记账应用。界面、交互与数据口径以本工程为准。

## 构建

环境：JDK 17+（本机用 21）、Android SDK Platform 36、Build-Tools 36.0.0。

```bash
cd android
# local.properties 里写 sdk.dir，例如：
# sdk.dir=D\:\\AndroidDev\\sdk
./gradlew assembleDebug
# 或
./gradlew assembleRelease
```

- `compileSdk 36` / `minSdk 23` / `targetSdk 33`，`applicationId = com.muyian.debt`。
- 工程没有 NDK 与 `.so`，不需要 ABI 过滤。
- release 默认只开 R8/资源裁剪，**没有配置签名**；正式分发请自行配置 `signingConfig` 或用 `apksigner` 对产物签名。

## 包名、签名与数据迁移

本工程沿用 `com.muyian.debt`。如果你的设备上装过同包名的旧版 App，签名不同会导致**无法直接覆盖安装**。迁移数据有两种方式：

1. 在旧版 App「备份·恢复」里导出 JSON，卸载旧版，安装本版，再从「备份·恢复」导入；
2. 调试包（debuggable）可以用 adb 原样搬运：
   ```bash
   adb shell am force-stop com.muyian.debt
   adb push 旧备份/muyian.db /data/local/tmp/muyian.db
   adb push 旧备份/DCStorage /data/local/tmp/DCStorage
   # 安装本版并至少启动一次后：
   adb shell "run-as com.muyian.debt sh -c 'mkdir -p files databases; cp /data/local/tmp/muyian.db files/muyian.db; cp /data/local/tmp/DCStorage databases/DCStorage'"
   ```
   首次启动会从 `databases/DCStorage` 只读迁移分类、主题、角色表情、头图等旧版配置（`Prefs.migrateLegacyStorage()`）。

> 用 release 包时 `run-as` 不可用，建议走备份 JSON 导入。
>
> **换包名/换签名后系统授权不会继承**：无障碍服务、通知使用权、悬浮窗、MIUI「后台弹出界面」、电池优化白名单、`WRITE_SECURE_SETTINGS` 自恢复都需要在新包上重新授权一次。

## 代码结构

```
app/src/main/java/com/muyian/debt/
├── MainActivity.kt          # 单 Activity + Compose 导航 + 系统栏
├── data/                    # Bill/Category/Prefs、格式化、农历、钱迹映射、AppState
├── ui/theme/Theme.kt        # 三主题设计令牌（Palette）
├── ui/icons/                # 24x24 图标路径 + Compose 绘制
├── ui/components/           # 玻璃卡、角色图、账单列表、底部弹层、滚轮、动效
└── ui/screens/              # 首页/记一笔/统计/日历/分类/设置/导入/备份
app/src/main/java/muyian/bill/listener/hybrid.kt   # 系统级原生核心
```

## 已实现

- 首页：渐变头图、角色、吸顶玻璃导航、跟手侧滑抽屉、最近 7 日柱状图、按日账单、FAB；不做权限判断（权限状态与授权统一在设置页）；
- 记一笔：5 列分类宫格 + 子类插行、计算键盘、日期滚轮、报销/星标/图片、编辑模式、保存/再记反馈；
- 统计：月/年、柱状/折线、分类环形图与明细弹层、日报表/月报表、分类明细；
- 日历：农历/节日角标、四种口径、选中日摘要 + 账单合并卡、编辑/删除；
- 分类管理：长按拖拽、父类/子类实时让位、增删改、图标选择、子类面板；
- 设置：三主题、角色表情/跟随场景、头图、无障碍/通知/悬浮窗/MIUI 后台弹出/自恢复/电池/保活/最近任务/自启动；
- 导入/备份：钱迹 xlsx 映射预览与分批入库、本应用 JSON 导出/合并恢复、缺失分类补全；
- 主题与原生确认弹层颜色同步。

## 动效约定

统一入口在 `ui/components/Motion.kt`，不要在页面里各写一套时长/曲线。

- **页面转场**：`MainActivity` 的 `AnimatedContent` 按 `AppState.navForward` 区分进栈/出栈。进栈 = 淡入 + 从底部上浮 + 轻微放大，出栈反向；「记一笔」位移更大（0.18 屏高），其余页面 0.07。时长 260~300ms，不可再加回纯淡入淡出。
- **底部弹层**：一律用 `AnimatedSheet(value, onDismiss) { ... }`，遮罩淡入 + 面板从底部滑入，关闭时先播退场再释放内容；不要再用 `if (state != null)` 直接渲染。
- **居中弹窗**：用 `AnimatedCenter(value, onDismiss) { ... }`（缩放 0.9→1）；弹窗卡片本身会拦截点击，点标题附近不会误关。
- **列表/分组入场**：用 `RiseIn(index, key) { ... }`，淡入 + 上浮并按下标错峰；账单行、设置分组、日历当日摘要都用它。`RiseIn` 内部是 `Column`，可以同时包住标题和卡片。
- **数字与图表**：金额用 `countUp()`；柱状图/环形图用 `Animatable` 0→1 配合 `stagger(progress, index, count)` 错峰生长。
- 只做位移、缩放、透明度与颜色补间，统一用减速曲线；不引入弹跳、光斑、循环呼吸等额外特效。

## 注意

- **启动画面**：不设品牌启动页。Android 12+ 强制的那一帧系统 SplashScreen 只留 `#F7F4EC` 底色、图标透明（`values-v31/themes.xml`），首帧直接是首页；首页第一帧再用同色覆盖并在 100ms 内淡出，避免 hero 的橙/红硬切。
  `onCreate` 里按用户主题 `setTheme` 成 `Theme.Muyian.{Wukong,Nezha,Shaseng}`，应用窗口底色跟随主题；系统启动帧本身用的是清单里的静态主题，不能按应用内主题变，设成透明在 HyperOS 上会闪白。选沙僧黑时系统启动帧仍是浅色，会先亮一下再切黑。
- **状态栏图标**：在 `MainActivity` 根节点统一按「当前页面 + 首页滚动 + 主题深浅」设置 `LIGHT_STATUS_BAR`；个别模拟器/ROM 的 SystemUI 可能不刷新透明状态栏的图标颜色，真机 MIUI 已验证首页为白色图标。
- **只能在真机/模拟器运行**。
- **图标尺寸用 dp，不用 rpx**：图标一律写成 `22.dp` 这类固定 dp；`rpx()` / `rsp()` 只用于布局间距与字号。
- **隐私约束**：无网络请求，账单/分类只存本机 SQLite 与 SharedPreferences。
- `hybrid.kt` 的确认弹层、无障碍采集属于系统级行为，换 UI 不影响其逻辑；改 `hybrid.kt` 或清单后需重新安装应用。
