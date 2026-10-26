# 页面侧条件编译与加载

> 页面调用插件 API、写数据加载函数时必读。插件本身的 API 约定见 `50-uts-plugin.md`。

## 条件编译成对出现

- 所有插件调用必须包在 `// #ifdef APP-PLUS ... // #endif` 内导入原生实现，并**成对**提供 `// #ifndef APP-PLUS` 下从 `@/common/web-bill-api.js` 的同名导入——web-bill-api 是 H5 的 localStorage 真实现（协议与 interface.uts 一致），Web 端记账/统计全流程可用（H5 没有原生弹层，自动捕获闭环不可用）。
- 新增插件 API 时**三处一起加**：interface.uts 声明、web-bill-api.js 的 H5 实现、hybrid.kt 的 Kotlin 实现。

## 数据加载

- 页面内 `load()` 等数据加载函数**不要**再用 `#ifdef APP-PLUS` 包裹函数体（两端实现都已就绪，包裹会导致 Web 端不加载数据）；仅 `plus.*` 这类端能力调用保留 `#ifdef`。
- 页面数据加载统一在 `onShow` 里做（返回页面自动刷新）；首页**不再轮询/拉取待确认数据**（确认在原生弹层里完成），不要把轮询加回来。

## 无障碍服务自愈顺序

- **首页无损检测顺序**：MIUI/HyperOS 在强停/清理应用时会把本服务从 `enabled_accessibility_services` 里移除（`adb shell am force-stop` 可复现）。首页 `load()` 必须**先 `selfRepairQuietly()` 再 `isAccessibilityEnabled()`**，且自愈成功时同步把 `listenerEnabled` 置为已开启；顺序反了会出现"服务已静默补回、首页横幅却一直挂着"的误报。
