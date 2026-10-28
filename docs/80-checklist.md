# 提交前自查清单

> 改动后逐项过一遍，任何一项不满足不交付。

1. 新页面已在 `state/AppState.kt` 的 `Screen` 注册，并在 `MainActivity` 的 `AnimatedContent` 中渲染；返回路径走 `app.pop()`，没有自己起 `Activity`；
2. 新增原生能力已由 `MuyianBillCore` 暴露为同步方法，复杂结构走 JSON 字符串，`data/` 侧有类型化解析封装；页面不直接调 `MuyianBillCore`；
3. 没有在页面里写死主题色/浅色值；改配色、组件样式或玻璃材质时三个主题（悟空黄、哪吒红、沙僧黑）各过一遍，毛玻璃/液态玻璃两档都看（液态玻璃再分有玻璃背景图/无背景图，确认无背景图时全透明且没有白色描边）：首页 + 抽屉、记一笔、统计（含分类弹层）、日历、分类（含图标选择弹窗）、设置；
4. 金额符号约定（支出负/收入正）、`costType` 叶子名约定未被新代码破坏；统计口径经过 `Cats.topNameOf()` 归并；
5. 涉及 DB 的改动带旧库 ALTER 兼容（`openDb()` + `billJson()`）；
6. 涉及 `muyian/bill/listener/` 下任意文件、`android/app/src/main/AndroidManifest.xml` 或 `res/xml/muyian_accessibility_config.xml` 的改动，重新构建安装并在真机回归；权限/服务声明变化后提示用户重新授权；
7. 无新增网络请求、无隐私越权（见 `00-product.md` 隐私红线）；
8. 跑一次真机流程：记一笔（含改日期/再记）→ 首页/日历/统计数据一致 → 分类增删排序后记一笔页与归并仍正确 → 测试底部确认弹层的保存/取消闭环（含返回键、60 秒超时、弹层显示后熄屏三种丢弃路径）→ 熄屏/锁屏或关闭「后台弹出界面」后付款，确认「待确认」通知能弹出、点开进入同一确认弹层、取消后通知同步消失（Android 13+ 先授予通知权限）→ 小米/Redmi/POCO 设置页显示「后台弹出界面（MIUI）」且状态与系统实际 AppOp 一致，非小米机型不显示该项；
9. 角色/表情改动确认 `THEME_MASCOT` 映射、`autoMood` 开关以及空态/成功态在三主题下的表现，新增角色只改 `data/Mascots.kt` 与 `app/src/main/assets/mascots/` 素材，不在页面写死角色名或路径。
