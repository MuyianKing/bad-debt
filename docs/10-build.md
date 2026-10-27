# 工程形态与构建

> 通用约束见根目录 `AGENTS.md`。本文件覆盖运行方式、依赖、ABI、基座、最近任务、启动图与 H5。

## 工程与依赖

- 用 **HBuilderX 打开整个项目目录**运行/打包（非 CLI 工程）。禁止引入 vite/cli 脚手架改造、禁止改 manifest.json 的 appid。
- npm 仅用于装 uni-app 兼容的前端依赖（当前只有 `wot-design-uni`）。装依赖前确认它支持 HBuilderX 非 cli 用法（easycom 引用，不需要额外构建配置）。
- `package.json` 必须保持 `"type": "commonjs"`（不影响 uni-app 编译，只影响本地 node 工具对 ESM 的解析）。
- 目标平台：仅 Android 真机。iOS 侧只维护 `app-ios/index.uts` 桩文件，不做功能投入。

## ABI 与自定义基座

- **Android 只出 `arm64-v8a` 包**：`manifest.json` 的 `abiFilters` 只保留 arm64-v8a（已放弃 armeabi-v7a，APK 约 20MB → 14MB）；离线打包时 Android Studio 侧同样只能保留 arm64 的 .so，不要补回 32 位。改 ABI 后需重新制作自定义调试基座。
- 运行验证必须用**自定义调试基座**（含 UTS 插件，标准基座跑不了）。改动 `hybrid.kt`/`interface.uts`/`index.uts` 后必须重新制作基座。

## 相机模块（打包配置）

- 承担功能：记一笔备注图片的拍照入口、设置页头图相册选择，均走 `uni.chooseImage`；H5 端由浏览器自己的文件选择兜底，不读本配置。
- `manifest.json` 的 `app-plus.modules.Camera` 对应 HBuilderX「App 模块配置 → Camera&Gallery(相机和相册)」，值为空对象；同时在 `distribute.android.permissions` 显式声明 `android.permission.CAMERA`。**不要只在模块界面勾选而不提交 manifest**，两者要保持一致，否则换机/重做基座后会漏配置。
- 首次拍照时由系统弹运行时权限；设置页头图只从相册选，不会触发相机权限。只申请 CAMERA，不额外申请存储/位置等与本功能无关的权限。
- 模块与权限属于**原生打包配置**：改完必须重新制作自定义调试基座，云打包才会生效；H5/Web 运行看不到差异。

## 最近任务隐藏

- **最近任务隐藏**：根目录 `AndroidManifest.xml` 是云打包合并用的应用清单（HBuilderX 3.6+ 支持），给 `io.dcloud.PandoraEntry`（正式包里的 LAUNCHER 任务根）和 `io.dcloud.PandoraEntryActivity` 加 `excludeFromRecents="true"` 作为基线；但**实测 HyperOS 会忽略清单里的这个标记**，所以 `common/recents.js` 会在 `App.onLaunch/onShow` 用 `AppTask.setExcludeFromRecents` 再设置一次。设置页「后台运行 → 从多任务页面隐藏」是开关（storage key `muyian_hide_recents_v1`，默认开启），切换后立即生效。根清单只加这个属性，禁止在里面配置 `theme`/`screenOrientation`/`configChanges`/`windowSoftInputMode`/`launchMode`（DCloud 明确禁止）；改动只在云端打包或重做自定义调试基座后生效。隐藏后最近任务里看不到应用，返回只能靠桌面图标或常驻通知，属预期行为，不要当成 bug 修。

## 启动界面

- **启动界面**：DCloud 默认启动图带雪花等待动画，已用 `app-plus.splashscreen.waiting:false` 关闭；启动图由 `design/splash/build.py` 生成 9-patch（`unpackage/res/splashscreen/{hdpi,xhdpi,xxhdpi}.9.png`，由 manifest 的 `distribute.splashscreen` 引用），品牌块固定、只拉伸四周纯色空白，禁止改回普通 PNG（长屏会把图标拉扁）。背景必须取 `--c-bg`(#f7f4ec) 与首页无缝；改完需重新打包并重做自定义调试基座。Android 12+ 的系统启动画面（居中 App 图标）属系统行为，uni-app 配置关不掉。

## H5 / Web 运行

- **H5/Web 运行的两个必需品，删任何一个 H5 编译即失败**：根目录 `index.html`（vue3 模板入口，含 viewport 脚本与 `/main.js` 引用）；项目 devDependencies 的 `sass`（wot 组件 SCSS 编译用，固定 `sass@1.78.0`）。H5 启动：`D:\HBuilderX\cli.exe launch web --project E:\project\muyian\muyian-app`（默认端口 5173，被占自动顺延）。
