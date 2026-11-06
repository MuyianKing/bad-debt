# 孽账 · 项目问题与技术债台账

> 整理时间：2026-11-06
> 复核基线：`main` @ `20207e4`，含当前未提交改动；`./gradlew :app:testDebugUnitTest --offline` 实测 13 个测试类 / 140 个用例全绿（exit 0，约 8s）。
> 性质：**分析台账，不是约束文档**。`AGENTS.md` 与 `docs/` 仍是唯一强约束来源；下列条目是待评审的观察与建议，不代表既定决策。放在仓库根目录而非 `docs/`，是为了避免被 `docs/README.md` 的索引当成约束文档消费。

## 0. 结论摘要

项目整体完成度较高：核心链路（无障碍为主 / 通知为辅 → 引擎判定 → 账本原子去重 → 确认弹层 → 用户保存才写库）边界清晰，隐私红线在清单层面有硬保证，140 个单测覆盖了最容易回归的纯逻辑层。

风险集中在三处：**一个未被文档化的关键平台决策（targetSdk 33）**、**规则驱动识别的长期维护成本**、**UI 层零自动化验证**。另有 4 处文档漂移和 1 处工作区隐私隐患，均可低成本修复。

共 9 项问题：P0 4 项、P1 4 项、P2 1 组（4 处）。

| 优先级 | 含义 |
| --- | --- |
| P0 | 影响核心链路正确性、隐私红线或长期可维护性，建议本轮处理 |
| P1 | 问题明确、影响可控，可排期 |
| P2 | 清理项，低风险，可顺手修 |

## 1. 问题总览

| 编号 | 优先级 | 类别 | 问题 | 主要证据 |
| --- | --- | --- | --- | --- |
| P0-1 | P0 | 平台 / 文档 | `targetSdk 33` 是弹层链路的关键依赖，但「为什么」没写进约束 | `app/build.gradle.kts:15`、`docs/00-product.md:11` |
| P0-2 | P0 | 可维护性 | 规则识别长尾，真机样本无法回流成回归语料 | `corpus.tsv` 14 行、`docs/55-capture-engine.md` §14.1 |
| P0-3 | P0 | 质量保障 | UI 8,332 行 + 原生弹层 636 行零自动化测试；lint 关闭、无 CI | 13 个测试类清单、`build.gradle.kts:68-69` |
| P0-4 | P0 | 隐私 / 工作区 | `.dbg/` 未被忽略，内含真实账单数据库副本 | 根 `.gitignore`、`git status` |
| P1-1 | P1 | 接口契约 | `MuyianBillCore` 同步 JSON 字符串 API 无 schema 契约测试 | `hybrid.kt:22`、`docs/50-native-core.md:7` |
| P1-2 | P1 | 架构 | 主界面 Compose + 确认弹层原生 View 双轨，主题需手工同步 | `BillSheetActivity.kt` 636 行、`setCardThemeColor` |
| P1-3 | P1 | 状态 | 「记一笔」草稿纯内存，配置变更可能丢失（待真机确认） | `NavState.kt:67`、`AndroidManifest.xml:33` |
| P1-4 | P1 | 上手 | `WRITE_SECURE_SETTINGS` 需 adb 授予，README 运行步骤未给命令 | `docs/10-build.md:33`、`README.md:50-57` |
| P2-1 | P2 | 文档 | 4 处口径漂移 | 见第 4 节 |

## 2. P0 详述

### P0-1 `targetSdk 33` 是刻意的地基，但没写成红线

**现状**：`targetSdk = 33` 而 `compileSdk = 36`（`app/build.gradle.kts:15,9`）。从 `docs/00-product.md:11`、`docs/50-native-core.md:58-64` 对「后台启动透明 Activity 弹层」与 MIUI AppOp 10021 的描述看，低 target 很可能是保住该能力的关键（Android 14+ 收紧后台启动 Activity）。但 `docs/10-build.md:10` 只陈述数值，`AGENTS.md` 也未把它列为不可动项。

**影响**：

- 后来者（含 AI 会话）容易当成版本滞后「顺手升级」。一旦升级，核心链路可能静默失效，而 140 个单测全绿无法发现——这条路径只能在真机验证。
- 无法上架 Google Play；未来 ROM 继续收紧后台启动时缺少应对预案。

**建议**：在 `docs/10-build.md:10` 下补一段「为什么是 33、升级前必须做什么」，在 `AGENTS.md` 通用约束里加一句「不得未经真机回归调整 targetSdk」；补一份升级验证矩阵（Android 13/14/15/16 × 小米 / 非小米 × 弹层能否拉起 / 待确认通知兜底）。**只写文档，不动代码。**

**验证**：按 `docs/80-checklist.md` 第 8 项跑一遍真机闭环。

---

### P0-2 规则识别长尾，与隐私红线形成迭代断层

**现状**：识别依赖 `CaptureRules` / `CaptureClassify` / `CaptureAmount` 的规则表与关键词表；回放语料 `app/src/test/resources/capture/corpus.tsv` 共 14 行（3 行注释，约 11 条样本），`CaptureCorpusTest` 只有 1 个用例。`docs/55-capture-engine.md` §14.1 记录了 30 多条「设计稿与落地偏差」，说明规则持续被真机样本打补丁。当前未提交的 `CaptureLearning.kt` 改动又新增了一批商户关键词表（麦当劳、瑞幸、蜜雪等）。

**张力**：`docs/50-native-core.md:69` 规定诊断文本默认不存、开启后也只存本机且可一键清空——隐私上完全正确，但导致真机发现的新页面 / 新文案无法回流成仓库里的回归语料，规则质量只能依赖作者个人真机覆盖。

**影响**：微信 / 支付宝 / 银行 App 改版或新增支付渠道时，漏记与误报不会被 CI 捕获；`§14.1` 的偏差清单会继续变长。

**建议**（不突破隐私红线）：

1. 在「识别记录」增加「把当前误判样本导出为脱敏语料行」的用户主动操作：手动触发、仅本机、不上传；
2. 约定脱敏格式，作者人工审核后再加入 `corpus.tsv`；
3. 给每类支付 App 设最低样本数，`CaptureCorpusTest` 按类别断言覆盖，避免规则改动只靠单测里的 fixture。

**验证**：新增语料后 `./gradlew :app:testDebugUnitTest` 全绿，并在真机「首页菜单 → 识别记录」核对原因码。

---

### P0-3 UI 层零自动化验证

**现状**：13 个测试类全部集中在 `data/` 纯逻辑、主题色纯函数与 `muyian.bill.listener` 的纯 Kotlin 模块；`ui/screens` 6,025 行 + `ui/components` 2,307 行 + `BillSheetActivity.kt` 636 行没有任何自动化测试，唯一沾 UI 的 `ThemeColorTest` 测的是颜色计算函数。`build.gradle.kts:68-69` 关掉了 lint 的 `abortOnError` 与 `checkReleaseBuilds`；仓库无 `.github/workflows`，无 ktlint / detekt。`docs/80-checklist.md` 第 3 项要求的人工矩阵是「三主题 × 亮暗 × 毛玻璃 / 液态玻璃 × 背景图有无 × 7 个页面」，组合量很大。

**影响**：主题、玻璃材质、动效改动只能靠肉眼——当前工作区就有 4 个 UI 文件改动。`docs/30-modules.md:37` 里已经记录过「透明度动画被打断会卡在 0 并留下占位空块」这类只能靠真机发现的问题。

**建议**（分两步，不追求一步到位）：

1. 最低成本：把主题矩阵参数化（三主题 × 亮暗 × 玻璃档位）做成 Compose 本地测试或纯函数快照断言；给 `MuyianBillCore` 的 JSON 出入参补契约测试（见 P1-1）。
2. 接一个最小 CI：只跑 `./gradlew :app:testDebugUnitTest` + `lintDebug`，先把「每次改动自动跑 140 个用例」固化下来；再逐步恢复 `abortOnError`。

**验证**：CI 首次运行应复现 140 个用例全绿；故意改坏一个金额断言能触发失败。

---

### P0-4 `.dbg/` 含真实账单数据且未被忽略

**现状**：根目录 `.gitignore` 只有 5 条（不含 `.dbg/`）。`.dbg/` 未被忽略，内含 `muyian.db`（约 1.5 MB）、`fresh.db`、`muyian.db-journal` 及两张截图，是含真实账单数据的调试副本；`git status` 显示 `?? .dbg/`。

（`android/.gitignore` 本身完整，构建产物与 `local.properties` 均未被跟踪，这一点没问题。）

**影响**：一次 `git add -A` 就会把真实账单数据提交进仓库并可能推送到远端，直接违反 `docs/00-product.md:15` 的隐私红线，且 Git 历史清理成本高。

**建议**：把 `.dbg/` 加入根 `.gitignore`；已生成的副本移出仓库或确认后删除（操作前先确认无保留需要）。若团队需要保留调试样本，改放仓库外的固定目录并在文档里写明路径。

> 本次分析未对 `.dbg/` 下任何文件做移动、改名或删除，仅报告。

## 3. P1 详述

### P1-1 JSON 字符串跨层无契约保护

`hybrid.kt:22` 的 `MuyianBillCore` 对外是 `@JvmStatic` 同步方法，复杂结构一律走 JSON 字符串（`docs/50-native-core.md:7`），页面侧靠 `data/` 的类型化封装消费。收益是原生层不感知 Kotlin 数据类，成本是字段改名 / 类型变化没有编译期保护，只能靠文档同步（`docs/README.md:25` 已把这条列为维护约定）。目前 JSON 面覆盖账单、4 张账本表的诊断输出、备份文件。

**建议**：为 `getCaptureDiagnostics` / `getAllBills` / 备份 JSON 补 schema 契约测试（固定 key 集合与类型），或至少把 key 集中成常量对象，减少手拼字符串。

### P1-2 双轨 UI

主界面是 Compose，`BillSheetActivity` 是 636 行 Android 框架 View；主题强调色还要通过 `MuyianBillCore.setCardThemeColor` → `SystemPerms` 手工同步。三主题 / 自定义主色 / 字色改一次要动两套，弹层也不在 Compose 预览与主题体系内。

**建议**：短期把弹层配色抽成单一数据源，并把「原生弹层配色」明确列入 `80-checklist.md` 第 3 项必测项；长期再评估是否用独立 Compose 窗口替换——注意不能破坏「后台启动透明 Activity」这一机制（见 P0-1）。

### P1-3 「记一笔」草稿在配置变更下可能丢失（待真机确认）

`NavState.kt:67` 的 `addDraft` 是纯内存 `mutableStateOf`，未见 `Prefs` 持久化；`AndroidManifest.xml:33` 的 `configChanges` 只覆盖 `orientation|screenSize|keyboardHidden|uiMode`，未包含 `smallestScreenSize|screenLayout|density|fontScale`。

**影响**：折叠屏展开、系统字体 / 显示密度变化会重建 Activity，正在编辑的「记一笔」内容可能丢失；进程被回收同理。

**建议**：用 `rememberSaveable` / `SavedStateHandle` 或把草稿落 Prefs；**先在真机改一次字体大小复现，确认后再改**。此项为静态阅读推断，未做真机验证。

### P1-4 上手门槛缺一步 adb 授权说明

`WRITE_SECURE_SETTINGS` 是签名级权限，需 `adb shell pm grant com.muyian.debt android.permission.WRITE_SECURE_SETTINGS` 才能启用无障碍掉开关自愈（`SystemPerms.kt:302-351`）。`docs/10-build.md:33` 只写「需 adb 授予」，`README.md:50-57` 的运行步骤既没有具体命令，也没提示「不授权时设置页的『立即恢复』会失效」。设置页本身已有命令拼接展示（`SystemPerms.kt:317`）。

**建议**：README 运行步骤后补一小节「可选：开启无障碍掉开关自愈」，给出命令与预期表现，两处口径保持一致。

## 4. 文档漂移（P2，可直接修）

| 位置 | 文档写法 | 实际情况 | 建议 |
| --- | --- | --- | --- |
| `docs/10-build.md:11` | `versionCode 100` | `app/build.gradle.kts:16` 为 `101` | 改成 101 |
| `README.md:36` | `source` 为 `screen` / `notify` / `manual` 三种 | 还有 `import`（`docs/40-data.md:33`、`docs/55-capture-engine.md` §15） | 补上 `import` |
| `docs/55-capture-engine.md:108` | 目标架构表列 `CaptureDiagnostics.kt` | 文件不存在，诊断已拆到 `CaptureLedger` / `CaptureStats`（§14.1 已说明） | 删除该行或标注「未落地」 |
| `AGENTS.md` 通用约束第 1 条 | 只点名 12 个左右 listener 文件，且称「`CaptureEngine` 负责门禁与编排」 | 实际 27 个文件；门禁在 `CaptureGates`，`CaptureEngine` 只做编排（`docs/30-modules.md:42`） | 改为「文件清单以 `docs/30-modules.md` 为准」，并修正职责描述 |

`docs/README.md:25` 已要求「代码里的约定发生变化必须同步改对应文档，否则视为改动未完成」，以上属该条款的待补项。

## 5. 建议处理顺序

1. **立即（低风险、可顺手做）**：P0-4 忽略 `.dbg/` + P2 文档漂移 4 处 + P1-4 README 补 adb 步骤
2. **本轮**：P0-1 把 targetSdk 理由写成红线（只写文档，不动代码）
3. **排期**：P0-3 最小 CI + P1-1 JSON 契约测试
4. **需讨论后决定**：P0-2 样本回流方案（涉及隐私交互设计）、P1-2 弹层 UI 统一
5. **真机确认后再定**：P1-3 草稿丢失

## 6. 复核基线（实测数据）

- HEAD `20207e4`（`main`）；工作区含 11 个已跟踪文件改动（+163 / −13）、3 个新增文件、`.dbg/` 未跟踪。本轮在途迭代主题为 `CapturePageMemory` + `CaptureLearning` 商户关键词 + 导航动效，并已同步改动 3 篇 docs。
- `./gradlew :app:testDebugUnitTest --offline`：13 个测试类、140 个用例、0 失败 0 错误，约 8 秒，exit 0。**该结果是在包含上述未提交改动的状态下取得的。**
- 业务 Kotlin 21,346 行（不含内置 `com/kyant/backdrop` 3,042 行）；测试 2,305 行；文档 1,171 行 / 12 篇。
- 清单无 `INTERNET` 权限，`allowBackup=false`、`usesCleartextTraffic=false`；全仓未发现网络调用（唯一 `http` 字样为 Apache license 注释）。
- 依赖仅 AndroidX / Compose / JUnit，无第三方 UI、图表、网络、统计库。
