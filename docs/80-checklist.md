# 提交前自查清单

> 改动后逐项过一遍，任何一项不满足不交付。

1. `pages.json` 已注册新页面、easycom 规则未被破坏；
2. 新插件 API 三文件同步（`interface.uts` / `app-android/index.uts` / `app-android/hybrid.kt`），页面侧有 `#ifdef`/`#ifndef` 成对 stub 且签名一致，`utssdk/web/index.js` 桩与 `common/web-bill-api.js` 的 H5 实现已补；
3. wd 组件布尔 prop 全部显式绑定（全局搜裸 `left-arrow|placeholder|safe-area-inset-top|is-link|hide-text|clickable`）；
4. 金额符号约定（支出负/收入正）、`costType` 叶子名约定未被新代码破坏；统计口径经过 `topNameOf` 归并；
5. 涉及 DB 的改动带旧库 ALTER 兼容（`openDb()` + `billJson()`）；
6. 涉及 `hybrid.kt` 或根目录 `AndroidManifest.xml` 的改动，提示用户重新制作自定义调试基座；
7. 无新增网络请求、无隐私越权（见 `00-product.md` 隐私红线）；
8. 跑一次真机流程：记一笔（含改日期/再记）→ 首页/日历/统计数据一致 → 分类增删排序后记一笔页与归并仍正确 → 测试底部确认弹层的保存/取消闭环（含返回键、60 秒超时、熄屏三种丢弃路径）；
9. 改到配色/组件样式时，三个主题（悟空黄、哪吒红、沙僧黑）各过一遍：首页 + 抽屉、记一笔、统计（含分类弹层）、日历、分类（含图标选择弹窗）、设置（含主题卡与操作菜单），重点看深色主题下的白底、浅色文字与键帽对比度。
