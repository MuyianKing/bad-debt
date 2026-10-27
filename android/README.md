# 孽账 · 原生 Android 版

这是 `孽账` 的原生重写：**Kotlin + Jetpack Compose**，界面、交互、数据口径与仓库根目录的 uni-app 版本保持一致（部分视觉还做了收紧），旧工程保留原样用于对照/回滚。

## 和旧工程的关系

- 根目录仍是 uni-app 工程，未删除、未改动。
- 原生版复用了旧工程的**原生核心**：`hybrid.kt`（3,475 行纯 Android Kotlin）原样复制到
  `app/src/main/java/muyian/bill/listener/hybrid.kt`，包名与服务类名保持不变：
  无障碍服务、通知监听、解析/去重、SQLite、后台保活、确认弹层、SAF 选文件、xlsx 解析、备份恢复都还是同一份实现。
- UI、分类/主题/角色、格式化、农历、钱迹映射、导入/备份流程在 Kotlin 里重写。

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

- `compileSdk 36` / `minSdk 23` / `targetSdk 33`。
- 默认 `applicationId = com.muyian.debt`，与旧 App 同名。
- 需要和旧 App 并存调试时用 `-PappId` 覆盖：
  ```bash
  ./gradlew assembleDebug -PappId=com.muyian.native
  ```
- release 默认只开 R8/资源裁剪，**没有配置签名**；正式分发请自行配置 `signingConfig` 或对产物用 `apksigner` 签名。

## 包名、签名与数据迁移

旧 App（`com.muyian.debt`）是 DCloud 调试证书签名的，原生版默认包名与它相同，
但用的是本机 debug 证书（正式分发则是你自己的证书），**无法直接覆盖安装**。迁移数据有两种方式：

1. 在旧 App「备份·恢复」里导出 JSON，卸载旧 App，安装原生版，再从「备份·恢复」导入；
2. 调试包（debuggable）可以用 adb 原样搬运：
   ```bash
   adb shell am force-stop com.muyian.debt
   adb push 旧备份/muyian.db /data/local/tmp/muyian.db
   adb push 旧备份/DCStorage /data/local/tmp/DCStorage
   # 安装原生包并至少启动一次后：
   adb shell "run-as com.muyian.debt sh -c 'mkdir -p files databases; cp /data/local/tmp/muyian.db files/muyian.db; cp /data/local/tmp/DCStorage databases/DCStorage'"
   ```
   原生版首次启动会从 `databases/DCStorage` 只读迁移分类、主题、角色表情、头图等 uni.storage 配置。
   如果用 `-PappId=com.muyian.native` 打并存调试包，把上面的 `com.muyian.debt` 换成 `com.muyian.native`。

> 用 release 包时 `run-as` 不可用，建议走备份 JSON 导入。
>
> **换包名/换签名后系统授权不会继承**：无障碍服务、通知使用权、悬浮窗、MIUI「后台弹出界面」、电池优化白名单、`WRITE_SECURE_SETTINGS` 自恢复都需要在新包上重新授权一次。

## 代码结构

```
app/src/main/java/com/muyian/debt/
├── MainActivity.kt          # 单 Activity + Compose 导航 + 系统栏
├── data/                    # Bill/Category/Prefs、格式化、农历、钱迹映射、AppState
├── ui/theme/Theme.kt        # 三主题设计令牌（对应 App.vue 的 CSS 变量）
├── ui/icons/                # 由 common/icons.js 生成的 24x24 SVG 路径 + 解析器
├── ui/components/           # 玻璃卡、角色图、账单列表、底部弹层、滚轮选择器
└── ui/screens/              # 首页/记一笔/统计/日历/分类/设置/导入/备份
app/src/main/java/muyian/bill/listener/hybrid.kt   # 原样复用的原生核心
```

## 已实现

- 首页：渐变头图、角色、吸顶玻璃导航、跟手侧滑抽屉、最近 7 日柱状图、按日账单、FAB、无障碍/电池提示条；
- 记一笔：5 列分类宫格 + 子类插行、计算键盘、日期滚轮、报销/星标/图片、编辑模式、保存/再记反馈；
- 统计：月/年、柱状/折线、分类环形图与明细弹层、日报表/月报表、分类明细；
- 日历：农历/节日角标、四种口径、选中日摘要 + 账单合并卡、编辑/删除；
- 分类管理：长按拖拽、父类/子类实时让位、增删改、图标选择、子类面板；
- 设置：三主题、角色表情/跟随场景、头图、无障碍/通知/悬浮窗/MIUI 后台弹出/自恢复/电池/保活/最近任务/自启动；
- 导入/备份：钱迹 xlsx 映射预览与分批入库、本应用 JSON 导出/合并恢复、缺失分类补全；
- 主题与原生确认弹层颜色同步。

## 注意

- **启动画面**：不设品牌启动页。Android 12+ 强制的那一帧系统 SplashScreen 只留 `#F7F4EC` 底色、图标透明
  （`values-v31/themes.xml`），首帧直接是首页；首页第一帧再用同色覆盖并在 100ms 内淡出，避免 hero 的橙/红硬切。
  `onCreate` 里按用户主题 `setTheme` 成 `Theme.Muyian.{Wukong,Nezha,Shaseng}`，应用窗口底色跟随主题；
  系统启动帧本身用的是清单里的静态主题，不能按应用内主题变，设成透明在 HyperOS 上会闪白。
  选沙僧黑时系统启动帧仍是浅色，会先亮一下再切黑。
- **状态栏图标**：原生版在根节点统一按「当前页面 + 首页滚动 + 主题深浅」设置 `LIGHT_STATUS_BAR`；
  个别模拟器/ROM 的 SystemUI 可能不刷新透明状态栏的图标颜色，真机 MIUI 已验证首页为白色图标。
- **没有 H5 预览**：原生工程只能在真机/模拟器运行，旧工程的 H5 截图仍可作为视觉参考。
- **图标尺寸用 dp，不用 rpx**：旧版 `app-icon` 的 `size` 是 CSS px，原生侧一律写成 `22.dp` 这类固定 dp；
  `rpx` 只用于布局间距与容器。图标解析器里的 SVG 圆弧必须沿当前子路径 `lineTo` 继续，
  用 `moveTo` 会把实体剪影切成碎片、渲染成空心残缺轮廓。
- **隐私约束不变**：无网络请求，账单/分类只存本机 SQLite 与 SharedPreferences。
- `hybrid.kt` 的确认弹层、无障碍采集属于系统级行为，换 UI 不影响其逻辑；改 `hybrid.kt` 或清单后需重新安装应用。
