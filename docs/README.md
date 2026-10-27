# docs · 约束文档索引

`AGENTS.md` 只保留跨模块的通用底线，业务与实现细节按主题拆在本目录。这样 AI 会话与协作者可以按改动范围只读相关文档，避免把上百条约束一次性塞进上下文。

## 索引

| 文档 | 覆盖范围 | 什么时候读 |
| --- | --- | --- |
| [00-product.md](00-product.md) | 产品定位、账单确认闭环、隐私、采集通道 | 涉及入账流程、权限、数据来源、任何新依赖时 |
| [10-build.md](10-build.md) | HBuilderX、依赖、ABI、基座、最近任务、启动图、H5 | 改工程配置、manifest、打包、运行方式时 |
| [20-ui-basics.md](20-ui-basics.md) | 组件库、导航、布尔 prop、rpx、图标、令牌、纯色、统计面板、图表 | 写页面或改任何样式前 |
| [21-theme-and-glass.md](21-theme-and-glass.md) | 三主题、深色主题、彩色图标、毛玻璃 | 改配色、卡片、浮层、主题相关代码时 |
| [22-layout-interactions.md](22-layout-interactions.md) | 首页抽屉、角色系统、沉浸式顶栏、光斑、分类拖拽 | 改首页布局、角色/表情、导航栏、拖拽交互时 |
| [30-modules.md](30-modules.md) | `common/*`、`components/*` 的职责与唯一来源 | 想新增工具函数/组件/常量前 |
| [40-data.md](40-data.md) | SQLite 表结构、金额符号、分类存储、旧库兼容 | 改数据库、统计口径、账单字段时 |
| [50-uts-plugin.md](50-uts-plugin.md) | 插件 API、确认弹层、`hybrid.kt` 写法与解析规则 | 动 `uni_modules/muyian-bill-listener` 时 |
| [60-conditional-compile.md](60-conditional-compile.md) | `#ifdef`/`#ifndef`、onShow 加载、服务自愈顺序 | 页面调用插件 API 或加数据加载时 |
| [70-datetime.md](70-datetime.md) | 日期字段、日期字符串、`clock`、周边界 | 处理日期、分组、统计区间时 |
| [80-checklist.md](80-checklist.md) | 提交前 9 项自查 | 每次交付/提交前 |

## 维护约定

- 一个主题一篇文档；新增约束先判断是否跨模块，通用底线写回 `AGENTS.md`，其余写进对应文档并更新本索引。
- 文档用确定事实描述（文件路径、函数名、storage key、表字段），不要写「相关代码」这类模糊指代。
- 代码里的约定发生变化（键名、函数名、storage key、表结构、页面路径）必须同步改对应文档，否则视为改动未完成。
- 文档之间不要复制整段内容；需要时用相对链接引用，避免两处口径漂移。
