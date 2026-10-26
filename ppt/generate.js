/* 慕沿记账 · 产品功能全览 PPT 生成脚本
 * 设计令牌：BG #F5F8FC / 卡片白 / 墨 #1E2B3C / 蓝 #1B62D6·#1E80FF / 支出红 #E05252 / 收入绿 #1F9D6B
 * 版式分配：P1封面#1 P2目录#2 P3左图右文#4 P4右文左图#5 P5图文混排#13 P6左图右文#4
 *           P7数据KPI#14 P8数据图#18 P9环形图#19 P10右文左图#5 P11表格页 P12图文混排#13 P13流程图#11 P14金句#12
 */
const pptxgen = require("pptxgenjs");
const path = require("path");

const A = p => path.join(__dirname, "assets", p);

// ---------- 设计令牌 ----------
const C = {
  bg: "F5F8FC", card: "FFFFFF", line: "E4EAF3",
  ink: "1E2B3C", sub: "5D6B80", faint: "8B98AB",
  blue: "1B62D6", blueBright: "1E80FF", blueTint: "E9F2FF",
  red: "E05252", redTint: "FDEDED",
  green: "1F9D6B", greenTint: "E7F6EF",
  navy: "16223A",
};
const FONT = "Microsoft YaHei";
const PW = 13.333, PH = 7.5, MX = 0.55;          // 页宽高 + 左右安全边距
const SAFE_R = 12.83, SAFE_B = 7.1;

const pptx = new pptxgen();
pptx.defineLayout({ name: "W", width: PW, height: PH });
pptx.layout = "W";
pptx.author = "慕沿记账";
pptx.title = "慕沿记账 · 产品功能全览";

// ---------- 通用组件 ----------
let pageNo = 0;
const TOTAL = 14;

function newSlide(notes) {
  const s = pptx.addSlide();
  s.background = { color: C.bg };
  pageNo += 1;
  if (notes) s.addNotes(notes);
  return s;
}

function footer(s) {
  s.addText("慕沿记账 · 产品功能全览", { x: 0.55, y: 7.14, w: 3.5, h: 0.26, fontFace: FONT, fontSize: 9, color: C.faint, margin: 0, valign: "middle" });
  s.addText(String(pageNo).padStart(2, "0") + " / " + TOTAL, { x: 11.6, y: 7.14, w: 1.18, h: 0.26, fontFace: FONT, fontSize: 9, color: C.faint, align: "right", margin: 0, valign: "middle" });
}

function header(s, kicker, title) {
  s.addShape(pptx.ShapeType.roundRect, { x: 0.55, y: 0.42, w: 0.4, h: 0.4, rectRadius: 0.09, fill: { color: C.blueBright } });
  s.addText("慕", { x: 0.55, y: 0.42, w: 0.4, h: 0.4, fontFace: FONT, fontSize: 15, bold: true, color: "FFFFFF", align: "center", valign: "middle", margin: 0 });
  s.addText(kicker, { x: 1.08, y: 0.42, w: 5.5, h: 0.24, fontFace: FONT, fontSize: 10.5, bold: true, color: C.blue, charSpacing: 2, margin: 0, valign: "middle" });
  s.addText(title, { x: 1.08, y: 0.66, w: 11.0, h: 0.62, fontFace: FONT, fontSize: 27, bold: true, color: C.ink, margin: 0, valign: "middle" });
}

function card(s, x, y, w, h, fill = C.card, radius = 0.1) {
  s.addShape(pptx.ShapeType.roundRect, {
    x, y, w, h, rectRadius: radius, fill: { color: fill },
    shadow: { type: "outer", angle: 90, blur: 8, color: "27405E", offset: 2, opacity: 0.12 },
  });
}

// 手机壳 + 截图（img 为 1200x2540 去状态栏圆角图）
function phone(s, img, cx, cy, h) {
  const iw = h * (1200 / 2540);
  const bw = iw + 0.14, bh = h + 0.14;
  s.addShape(pptx.ShapeType.roundRect, {
    x: cx, y: cy, w: bw, h: bh, rectRadius: 0.2, fill: { color: C.navy },
    shadow: { type: "outer", angle: 90, blur: 12, color: "16223A", offset: 4, opacity: 0.3 },
  });
  s.addImage({ path: A(img), x: cx + 0.07, y: cy + 0.07, w: iw, h: h });
  return { right: cx + bw, bottom: cy + bh };
}

// 左侧要点行：蓝色小方块 + 加粗导语 + 说明
function row(s, x, y, w, lead, desc, descH = 0.5) {
  s.addShape(pptx.ShapeType.roundRect, { x, y: y + 0.09, w: 0.13, h: 0.13, rectRadius: 0.04, fill: { color: C.blueBright } });
  s.addText(lead, { x: x + 0.27, y, w: w - 0.27, h: 0.32, fontFace: FONT, fontSize: 15.5, bold: true, color: C.ink, margin: 0, valign: "middle" });
  s.addText(desc, { x: x + 0.27, y: y + 0.33, w: w - 0.27, h: descH, fontFace: FONT, fontSize: 12.5, color: C.sub, margin: 0, valign: "top", lineSpacingMultiple: 1.15 });
}

// 金句条
function quoteBar(s, x, y, w, text, h = 0.66) {
  s.addShape(pptx.ShapeType.roundRect, { x, y, w, h, rectRadius: 0.09, fill: { color: C.blueTint } });
  s.addShape(pptx.ShapeType.roundRect, { x: x + 0.22, y: y + h / 2 - 0.14, w: 0.07, h: 0.28, rectRadius: 0.03, fill: { color: C.blueBright } });
  s.addText(text, { x: x + 0.45, y, w: w - 0.65, h, fontFace: FONT, fontSize: 13.5, bold: true, color: C.blue, margin: 0, valign: "middle" });
}

// 手机展示面板（浅蓝底 + 居中手机）
function phonePanel(s, img, px, py, pw, ph) {
  card(s, px, py, pw, ph, "EAF1FA", 0.16);
  const h = ph - 0.5;
  const iw = h * (1200 / 2540);
  phone(s, img, px + (pw - iw - 0.14) / 2, py + 0.25, h);
}

/* ============================================================
   P1 封面（版式 #1）
============================================================ */
(() => {
  const s = newSlide("封面：慕沿记账产品功能全览。主打语：通知监听自动记账 × 全场景手动记账。");
  s.addShape(pptx.ShapeType.ellipse, { x: 7.6, y: -1.8, w: 7.4, h: 7.4, fill: { color: "DEEBFB" } });
  s.addShape(pptx.ShapeType.ellipse, { x: 8.9, y: 4.9, w: 1.0, h: 1.0, fill: { color: C.redTint } });

  s.addShape(pptx.ShapeType.roundRect, { x: 0.9, y: 1.5, w: 2.5, h: 0.46, rectRadius: 0.23, fill: { color: C.blueTint } });
  s.addText("产品功能全览 · 2026", { x: 0.9, y: 1.5, w: 2.5, h: 0.46, fontFace: FONT, fontSize: 12, bold: true, color: C.blue, align: "center", valign: "middle", margin: 0 });

  s.addText("慕沿记账", { x: 0.86, y: 2.1, w: 6.4, h: 1.15, fontFace: FONT, fontSize: 58, bold: true, color: C.ink, margin: 0, valign: "middle" });
  s.addText("通知监听自动记账 × 全场景手动记账", { x: 0.9, y: 3.32, w: 6.4, h: 0.45, fontFace: FONT, fontSize: 19, bold: true, color: C.blue, margin: 0, valign: "middle" });
  s.addShape(pptx.ShapeType.roundRect, { x: 0.92, y: 4.0, w: 1.1, h: 0.07, rectRadius: 0.03, fill: { color: C.blueBright } });
  s.addText("从记一笔到看全年：23 个支出分类、日历账单、统计报表、两级分类管理，外加一颗通知监听内核——一本装进口袋的完整账本。", {
    x: 0.9, y: 4.25, w: 5.9, h: 1.3, fontFace: FONT, fontSize: 14.5, color: C.sub, margin: 0, valign: "top", lineSpacingMultiple: 1.4,
  });
  s.addText("慕沿 muyian · Android · 2026.10", { x: 0.9, y: 6.55, w: 5.0, h: 0.3, fontFace: FONT, fontSize: 11, color: C.faint, margin: 0, valign: "middle" });

  phone(s, "phone_home.png", 9.0, 0.95, 5.75);
  // 浮动徽章
  card(s, 8.35, 1.75, 1.45, 0.5, C.card, 0.25);
  s.addText("自动记账", { x: 8.35, y: 1.75, w: 1.45, h: 0.5, fontFace: FONT, fontSize: 12, bold: true, color: C.blue, align: "center", valign: "middle", margin: 0 });
  card(s, 10.9, 5.5, 1.75, 0.5, C.card, 0.25);
  s.addText("23 个支出分类", { x: 10.9, y: 5.5, w: 1.75, h: 0.5, fontFace: FONT, fontSize: 12, bold: true, color: C.blue, align: "center", valign: "middle", margin: 0 });
})();

/* ============================================================
   P2 功能地图（版式 #2 目录变体）
============================================================ */
(() => {
  const s = newSlide("功能地图：六个界面 + 一个自动记账内核，先给观众一张全景图。");
  header(s, "CONTENTS · 功能地图", "六个界面，装下一本完整账本");

  const mods = [
    ["01", "首页总览", "月度头图 + 7 日趋势 + 按日流水"],
    ["02", "记一笔", "23 个分类 + 计算器键盘"],
    ["03", "日历视图", "每日收支上墙 + 农历节日"],
    ["04", "统计报表", "总览 KPI + 每日柱状 + 环形图"],
    ["05", "日报表", "一天一行，收支结余流水"],
    ["06", "分类管理", "两级分类，长按排序单击改"],
  ];
  const cw = 3.9, ch = 1.72, gap = 0.25, x0 = 0.55, y0 = 1.82;
  mods.forEach((m, i) => {
    const x = x0 + (i % 3) * (cw + gap), y = y0 + Math.floor(i / 3) * (ch + 0.24);
    card(s, x, y, cw, ch);
    s.addShape(pptx.ShapeType.ellipse, { x: x + 0.24, y: y + 0.24, w: 0.5, h: 0.5, fill: { color: C.blueTint } });
    s.addText(m[0], { x: x + 0.24, y: y + 0.24, w: 0.5, h: 0.5, fontFace: FONT, fontSize: 14, bold: true, color: C.blue, align: "center", valign: "middle", margin: 0 });
    s.addText(m[1], { x: x + 0.88, y: y + 0.26, w: cw - 1.1, h: 0.42, fontFace: FONT, fontSize: 16.5, bold: true, color: C.ink, margin: 0, valign: "middle" });
    s.addText(m[2], { x: x + 0.26, y: y + 0.92, w: cw - 0.52, h: 0.6, fontFace: FONT, fontSize: 11.5, color: C.sub, margin: 0, valign: "top", lineSpacingMultiple: 1.2 });
  });

  s.addShape(pptx.ShapeType.roundRect, { x: 0.55, y: 5.85, w: 12.2, h: 0.95, rectRadius: 0.12, fill: { color: C.blue }, shadow: { type: "outer", angle: 90, blur: 8, color: "1B62D6", offset: 2, opacity: 0.25 } });
  s.addText([
    { text: "内核 · 通知监听自动记账", options: { fontSize: 15.5, bold: true, color: "FFFFFF" } },
    { text: "    微信 / 支付通知自动解析 → 待确认 → 用户点头才入账", options: { fontSize: 12, color: "D8E8FF" } },
  ], { x: 0.95, y: 5.85, w: 11.5, h: 0.95, fontFace: FONT, margin: 0, valign: "middle" });
  footer(s);
})();

/* ============================================================
   P3 首页（版式 #4 左图右文）
============================================================ */
(() => {
  const s = newSlide("首页：头图式月度总览、最近7日柱状图、按日分组流水、悬浮记一笔。");
  header(s, "01 · 首页总览", "打开 App，先看见这个月");
  phonePanel(s, "phone_home.png", 0.55, 1.72, 3.6, 5.32);

  const rx = 4.55, rw = 8.25;
  row(s, rx, 1.9, rw, "头图式月度总览", "月支出 5,508.00 / 月收入 0.00 / 本月结余 -5,508.00，第一屏就定调");
  row(s, rx, 2.92, rw, "最近 7 日支出柱状图", "总计 1,056.60：周二 368、周日 395 的峰值一眼可见");
  row(s, rx, 3.94, rw, "按日分组流水", "今天 / 昨天 / 前天逐日排布，每天右侧汇总「支:¥395.00」");
  row(s, rx, 4.96, rw, "一键记一笔", "右下角悬浮 + 按钮，随时开记，不打断浏览");
  quoteBar(s, rx, 6.05, rw, "账本的第一屏，就该回答「这个月花了多少」。");
  footer(s);
})();

/* ============================================================
   P4 记一笔 · 分类（版式 #5 右文左图）
============================================================ */
(() => {
  const s = newSlide("记一笔：支出收入双Tab、23个分类图标网格即点即选、支持自定义分类。");
  header(s, "02 · 记一笔", "23 个分类，接住生活的每一笔");
  phonePanel(s, "phone_record.png", 9.18, 1.72, 3.6, 5.32);

  const rx = 0.55, rw = 8.25;
  row(s, rx, 1.9, rw, "支出 / 收入双 Tab", "顶部一键切换，同一套交互零学习成本");
  row(s, rx, 2.92, rw, "23 个支出分类", "三餐、零食、交通、旅行、宠物、医疗……从柴米油盐到数码娱乐全覆盖");
  row(s, rx, 3.94, rw, "图标网格即点即选", "选中分类高亮放大，减少误触，不用打字就能选");
  row(s, rx, 4.96, rw, "支持自定义", "内置分类不够用？随手加一个——比如给订阅服务建个「ai」");
  // 三枚小数据卡
  const stats = [["23", "一级支出分类"], ["2", "分类层级可扩"], ["∞", "自定义分类上限"]];
  stats.forEach((t, i) => {
    const x = 0.55 + i * 2.83;
    card(s, x, 6.0, 2.6, 0.92);
    s.addText([
      { text: t[0] + "  ", options: { fontSize: 22, bold: true, color: C.blue } },
      { text: t[1], options: { fontSize: 11, color: C.sub } },
    ], { x: x + 0.2, y: 6.0, w: 2.3, h: 0.92, fontFace: FONT, margin: 0, valign: "middle" });
  });
  footer(s);
})();

/* ============================================================
   P5 记一笔 · 输入效率（版式 #13 图文混排）
============================================================ */
(() => {
  const s = newSlide("输入效率：备注、快捷标签、计算器键盘、再记连续记账、保存一键入账。");
  header(s, "02 · 记一笔", "像发消息一样，3 秒记完一笔");

  const rx = 0.55, rw = 5.55;
  row(s, rx, 1.9, rw, "备注随手补", "「点此输入备注…」，给这笔钱留下上下文");
  row(s, rx, 2.86, rw, "快捷标签", "今天 / 报销 / 图片 / 旗标，一趟点完不进二级页");
  row(s, rx, 3.82, rw, "计算器键盘", "+ − 连算多笔合并，退格即改，不必先算再填");
  row(s, rx, 4.78, rw, "再记 / 保存", "连续消费不停手，「再记」留页面，「保存」即入账");
  s.addShape(pptx.ShapeType.roundRect, { x: rx, y: 6.0, w: 0.07, h: 0.42, rectRadius: 0.03, fill: { color: C.blueBright } });
  s.addText("输入越短，记账越久。", { x: rx + 0.22, y: 6.0, w: rw - 0.22, h: 0.42, fontFace: FONT, fontSize: 15, bold: true, color: C.blue, margin: 0, valign: "middle" });

  // 右侧两张特写
  s.addImage({ path: A("crop_keyboard.png"), x: 6.45, y: 1.85, w: 3.66, h: 2.96 });
  s.addImage({ path: A("crop_categories.png"), x: 10.28, y: 1.85, w: 2.5, h: 2.63 });
  card(s, 6.45, 5.05, 6.33, 1.5);
  s.addText([
    { text: "键盘即计算器", options: { fontSize: 13.5, bold: true, color: C.ink, breakLine: true } },
    { text: "金额栏实时显示 0.0；报销、图片、旗标标记随点随加，一笔账一次成型。", options: { fontSize: 11.5, color: C.sub } },
  ], { x: 6.72, y: 5.2, w: 5.85, h: 1.2, fontFace: FONT, margin: 0, valign: "top", lineSpacingMultiple: 1.3 });
  footer(s);
})();

/* ============================================================
   P6 日历视图（版式 #4 左图右文 · 第2次）
============================================================ */
(() => {
  const s = newSlide("日历视图：每日净收支上墙、农历节日、四种显示模式、点日期看明细、月汇总。");
  header(s, "03 · 日历视图", "把整个月的账，摊开在日历上");
  phonePanel(s, "phone_calendar.png", 0.55, 1.72, 3.6, 5.32);

  const rx = 4.55, rw = 8.25;
  row(s, rx, 1.86, rw, "每日净收支上墙", "1 号 -1806、18 号 -1142，超支的日子在日历上藏不住");
  row(s, rx, 2.78, rw, "农历与节日", "廿二、初八、万圣节——记账的同时不忘日子");
  row(s, rx, 3.7, rw, "四种显示模式", "收&支 / 结余 / 收入 / 支出，想看哪面切哪面");
  row(s, rx, 4.62, rw, "点日期看明细", "选中 10.25，下方直接列出当天三笔共 -395.00");
  row(s, rx, 5.54, rw, "月汇总一行", "月收入 0.00 · 月支出 5,508.00 · 月结余 -5,508.00");
  quoteBar(s, rx, 6.4, rw, "哪天超支，日历比账单更诚实。", 0.6);
  footer(s);
})();

/* ============================================================
   P7 统计 · 收支总览（版式 #14 数据 KPI）
============================================================ */
(() => {
  const s = newSlide("收支总览：支出/收入/结余/日均支出四个KPI，月年切换+筛选。");
  header(s, "04 · 统计报表", "收支总览：四个数字说完一个月");

  const kpis = [
    ["支出", "5,508.00", "本月累计", C.red],
    ["收入", "0.00", "本月累计", C.green],
    ["结余", "-5,508.00", "收入 − 支出", C.ink],
    ["日均支出", "220.32", "截至 10-25", C.blue],
  ];
  kpis.forEach((k, i) => {
    const x = 0.65 + i * 3.05, w = 2.85;
    card(s, x, 1.88, w, 1.95);
    s.addText(k[0], { x: x + 0.25, y: 2.08, w: w - 0.5, h: 0.3, fontFace: FONT, fontSize: 13, color: C.sub, margin: 0 });
    s.addText(k[1], { x: x + 0.25, y: 2.42, w: w - 0.5, h: 0.62, fontFace: FONT, fontSize: 27, bold: true, color: k[3], margin: 0, valign: "middle" });
    s.addText(k[2], { x: x + 0.25, y: 3.14, w: w - 0.5, h: 0.28, fontFace: FONT, fontSize: 10.5, color: C.faint, margin: 0 });
  });

  s.addImage({ path: A("crop_overview.png"), x: 0.65, y: 4.32, w: 5.3, h: 2.72 });
  const rx = 6.5, rw = 6.3;
  row(s, rx, 4.42, rw, "月 / 年双粒度", "口径随场景切换，报月度看月度，盘全年看全年", 0.42);
  row(s, rx, 5.4, rw, "筛选器加持", "右上角漏斗，只看想看的那部分账", 0.42);
  row(s, rx, 6.38, rw, "四数互证", "支出、收入、结余、日均同屏，不做单边报表", 0.42);
  footer(s);
})();

/* ============================================================
   P8 统计 · 每日统计（版式 #18 数据图页）
============================================================ */
(() => {
  const s = newSlide("每日统计：整月每日支出柱状图，峰值与空档都可见，支持全屏。");
  header(s, "04 · 统计报表", "每日统计：消费的节奏看得见");
  s.addImage({ path: A("crop_daily.png"), x: 0.6, y: 1.85, w: 6.85, h: 5.08 });

  const rx = 7.95, rw = 4.85;
  row(s, rx, 2.0, rw, "峰值即事件", "10-01 冲上 1.8K，10-18 单日 1,142——大额支出无所遁形", 0.66);
  row(s, rx, 3.3, rw, "空档也是信息", "没有柱子的日子，就是没有消费的日子", 0.66);
  row(s, rx, 4.5, rw, "一键全屏", "右上角放大图标，整月节奏铺满看", 0.66);
  quoteBar(s, rx, 5.8, rw, "看见峰值，才能看见习惯。");
  footer(s);
})();

/* ============================================================
   P9 统计 · 分类占比（版式 #19 环形图，原生图表）
============================================================ */
(() => {
  const s = newSlide("分类占比环形图：汽车加油28.7%、三餐21.4%、旅行20.0%，前三类合计70.1%。");
  header(s, "04 · 统计报表", "支出都去哪儿了：一张环形图说清");

  const labels = ["汽车/加油", "三餐", "旅行", "零食", "宠物", "衣服", "娱乐", "日用品", "话费网费"];
  const values = [28.7, 21.4, 20.0, 7.8, 6.2, 5.1, 3.5, 2.8, 2.7];
  const colors = ["F5A742", "EE6B5B", "2BB673", "8DC63F", "3B82F6", "38B6E0", "34B3A0", "7CC7B8", "6B7FD7"];
  s.addChart(pptx.ChartType.doughnut, [{ name: "支出比例", labels, values }], {
    x: 0.55, y: 1.85, w: 5.5, h: 4.9, holeSize: 62, chartColors: colors,
    showLegend: false, showValue: false, showPercent: false, showTitle: false,
    dataBorder: { pt: 2, color: "FFFFFF" },
  });
  s.addText([
    { text: "支出比例", options: { fontSize: 14, bold: true, color: C.ink, breakLine: true } },
    { text: "2026-10", options: { fontSize: 10.5, color: C.faint } },
  ], { x: 2.5, y: 3.95, w: 1.6, h: 0.7, fontFace: FONT, align: "center", margin: 0, valign: "middle" });
  s.addText("数据来自产品演示截图 · 支持手指旋转饼图，小于 2% 不显示", { x: 0.55, y: 6.78, w: 5.5, h: 0.26, fontFace: FONT, fontSize: 9.5, color: C.faint, align: "center", margin: 0 });

  // 右侧排行
  const rank = [
    ["汽车/加油", 28.7, "1,582.00", "F5A742"],
    ["三餐", 21.4, "1,176.00", "EE6B5B"],
    ["旅行", 20.0, "1,100.00", "2BB673"],
    ["零食", 7.8, "429.00", "8DC63F"],
    ["宠物", 6.2, "344.00", "3B82F6"],
    ["其余 4 类", 15.9, "877.00", "C3CDD9"],
  ];
  const rx = 6.75, rw = 6.0;
  rank.forEach((r, i) => {
    const y = 1.95 + i * 0.56;
    s.addShape(pptx.ShapeType.ellipse, { x: rx, y: y + 0.1, w: 0.16, h: 0.16, fill: { color: r[3] } });
    s.addText(r[0], { x: rx + 0.3, y, w: 1.5, h: 0.36, fontFace: FONT, fontSize: 12.5, bold: true, color: C.ink, margin: 0, valign: "middle" });
    s.addShape(pptx.ShapeType.roundRect, { x: rx + 1.85, y: y + 0.11, w: 2.0, h: 0.14, rectRadius: 0.06, fill: { color: "EDF1F7" } });
    s.addShape(pptx.ShapeType.roundRect, { x: rx + 1.85, y: y + 0.11, w: Math.max(0.14, 2.0 * r[1] / 30), h: 0.14, rectRadius: 0.06, fill: { color: r[3] } });
    s.addText(r[1].toFixed(1) + "%", { x: rx + 3.95, y, w: 0.85, h: 0.36, fontFace: FONT, fontSize: 12, color: C.sub, align: "right", margin: 0, valign: "middle" });
    s.addText(r[2], { x: rx + 4.85, y, w: 1.15, h: 0.36, fontFace: FONT, fontSize: 12, bold: true, color: C.ink, align: "right", margin: 0, valign: "middle" });
  });
  card(s, rx, 5.5, rw, 1.15, C.blueTint, 0.1);
  s.addText([
    { text: "前三类合计 70.1%", options: { fontSize: 14.5, bold: true, color: C.blue, breakLine: true } },
    { text: "管住汽车/加油、三餐、旅行，就管住了七成支出。", options: { fontSize: 11.5, color: C.sub } },
  ], { x: rx + 0.28, y: 5.66, w: rw - 0.56, h: 0.85, fontFace: FONT, margin: 0, valign: "top", lineSpacingMultiple: 1.25 });
  footer(s);
})();

/* ============================================================
   P10 统计 · 分类排行环比（版式 #5 右文左图 · 第2次）
============================================================ */
(() => {
  const s = newSlide("分类排行：图标+占比+环比涨跌+金额+进度条；月年切换、支出收入两视角。");
  header(s, "04 · 统计报表 · 分类排行", "每个分类，都在和上个月较劲");
  phonePanel(s, "phone_rank.png", 9.18, 1.72, 3.6, 5.32);

  const rx = 0.55, rw = 8.25;
  row(s, rx, 1.9, rw, "一行读完一个分类", "图标 + 占比 + 环比 + 金额，进度条比出体量");
  row(s, rx, 2.92, rw, "涨跌两色分明", "▲ 汽车/加油 +1,082.00；▼ 日用品 -806.00，红涨绿降");
  row(s, rx, 3.94, rw, "月 / 年 × 支出 / 收入", "两个维度自由组合，还能叠加筛选条件");
  row(s, rx, 4.96, rw, "长榜也清爽", "占比 <2% 自动折叠显示，不给小项添乱");
  quoteBar(s, rx, 6.05, rw, "环比不是数字游戏，是消费习惯的体温计。");
  footer(s);
})();

/* ============================================================
   P11 日报表（原生表格页）
============================================================ */
(() => {
  const s = newSlide("日报表：日期/收入/支出/结余流水表，日均支出220.32，负结余标红。");
  header(s, "05 · 日报表", "日报表：一天一行，笔笔对得上");

  const rows = [
    ["10-25", "0.00", "395.00", "-395.00"],
    ["10-24", "0.00", "47.00", "-47.00"],
    ["10-23", "0.00", "121.00", "-121.00"],
    ["10-21", "0.00", "88.00", "-88.00"],
    ["10-20", "0.00", "368.00", "-368.00"],
    ["10-19", "0.00", "37.60", "-37.60"],
    ["10-18", "0.00", "1,142.00", "-1,142.00"],
  ];
  const head = ["日期", "收入", "支出", "结余"].map(t => ({
    text: t, options: { fill: { color: C.blue }, color: "FFFFFF", bold: true, fontSize: 12.5, align: t === "日期" ? "left" : "right", valign: "middle" },
  }));
  const body = rows.map((r, i) => r.map((t, j) => ({
    text: t,
    options: {
      fill: { color: i % 2 ? "F2F6FB" : "FFFFFF" },
      color: j === 3 ? C.red : j === 2 ? C.ink : C.sub,
      bold: j === 3, fontSize: 12, align: j === 0 ? "left" : "right", valign: "middle",
    },
  })));
  s.addTable([head, ...body], {
    x: 0.55, y: 1.9, w: 7.15, colW: [1.75, 1.7, 1.8, 1.9], rowH: 0.52,
    fontFace: FONT, border: { type: "solid", pt: 0.5, color: C.line }, margin: 0.08,
  });
  s.addText("……更长流水上下滑动查看 · 结余为负自动标红 · 数据来自产品演示截图", { x: 0.55, y: 6.35, w: 7.15, h: 0.28, fontFace: FONT, fontSize: 10, color: C.faint, margin: 0 });

  const rx = 8.2, rw = 4.6;
  card(s, rx, 1.9, rw, 1.2);
  s.addText([
    { text: "日均支出  ", options: { fontSize: 12.5, color: C.sub } },
    { text: "220.32", options: { fontSize: 26, bold: true, color: C.blue } },
  ], { x: rx + 0.28, y: 1.9, w: rw - 0.56, h: 1.2, fontFace: FONT, margin: 0, valign: "middle" });
  row(s, rx, 3.45, rw, "三列并排核账", "收入 / 支出 / 结余同表，当天对账零回忆", 0.42);
  row(s, rx, 4.45, rw, "月份左右切换", "翻月就是翻账本，历史月份随时回看", 0.42);
  row(s, rx, 5.45, rw, "负结余标红", "风险日自己浮出来，不用逐行找", 0.42);
  quoteBar(s, rx, 6.35, rw, "对账的最好状态，是不需要回忆。", 0.6);
  footer(s);
})();

/* ============================================================
   P12 分类管理（版式 #13 图文混排 · 第2次）
============================================================ */
(() => {
  const s = newSlide("分类管理：一级/二级分类、长按排序单击修改、添加子类、完全自定义。");
  header(s, "06 · 分类管理", "分类不是写死的，是长出来的");

  s.addImage({ path: A("crop_subcat.png"), x: 0.55, y: 1.95, w: 6.1, h: 2.54 });
  s.addText("零食 → 水果 / 奶茶：两级分类现场", { x: 0.55, y: 4.56, w: 6.1, h: 0.28, fontFace: FONT, fontSize: 10.5, color: C.faint, margin: 0 });
  row(s, 0.55, 5.05, 6.1, "两级结构", "一级管大类，二级管细节，账本更干净", 0.42);
  row(s, 0.55, 5.95, 6.1, "排序与修改", "长按一级或二级分类排序，单击即可改名换图标", 0.42);
  s.addShape(pptx.ShapeType.roundRect, { x: 0.55, y: 6.85, w: 0.07, h: 0.24, rectRadius: 0.03, fill: { color: C.blueBright } });
  s.addText("账本的结构，应该长成你的样子。", { x: 0.77, y: 6.8, w: 5.9, h: 0.34, fontFace: FONT, fontSize: 12.5, bold: true, color: C.blue, margin: 0, valign: "middle" });

  phonePanel(s, "phone_cats.png", 8.85, 1.72, 3.6, 5.32);
  s.addText("「+ 添加子类」随手扩充，完全自定义（比如建一个「ai」）", { x: 6.85, y: 6.8, w: 5.9, h: 0.34, fontFace: FONT, fontSize: 10.5, color: C.sub, margin: 0, valign: "middle", align: "right" });
  footer(s);
})();

/* ============================================================
   P13 自动记账内核（版式 #11 流程图）
============================================================ */
(() => {
  const s = newSlide("内核：通知监听→白名单→正则解析→三层去重→待确认→确认入账；错误拦截在入库前。");
  header(s, "内核 · 自动记账", "手动记账之外，还有一张自动的网");
  s.addText("慕沿的差异点：Android 通知监听自动记账（来自产品设计文档）", { x: 1.08, y: 1.32, w: 10.5, h: 0.28, fontFace: FONT, fontSize: 11.5, color: C.sub, margin: 0 });

  const steps = [
    ["系统通知", "微信 · 支付渠道"],
    ["白名单过滤", "只听候选 App"],
    ["正则解析", "金额 · 收支方向"],
    ["三层去重", "通知·草稿·账单"],
    ["待确认通知", "先弹通知不入库"],
    ["确认入账", "一点入账可编辑"],
  ];
  const nw = 1.72, gap = 0.31, x0 = 0.75, y0 = 2.15;
  steps.forEach((t, i) => {
    const x = x0 + i * (nw + gap);
    card(s, x, y0, nw, 1.2, i === 5 ? C.blue : C.card, 0.12);
    s.addText(t[0], { x: x + 0.08, y: y0 + 0.2, w: nw - 0.16, h: 0.36, fontFace: FONT, fontSize: 13.5, bold: true, color: i === 5 ? "FFFFFF" : C.ink, align: "center", margin: 0, valign: "middle" });
    s.addText(t[1], { x: x + 0.08, y: y0 + 0.62, w: nw - 0.16, h: 0.32, fontFace: FONT, fontSize: 9.5, color: i === 5 ? "D8E8FF" : C.faint, align: "center", margin: 0, valign: "middle" });
    if (i < 5) s.addText("›", { x: x + nw - 0.015, y: y0 + 0.3, w: gap + 0.03, h: 0.6, fontFace: FONT, fontSize: 20, bold: true, color: C.blueBright, align: "center", valign: "middle", margin: 0 });
  });

  const feats = [
    ["错误拦截在入库前", "解析成功不自动入账，先弹「待确认」；看一眼再点头，错了直接丢弃。"],
    ["三层去重防重复", "通知级 / 草稿级 / 账单级三道闸，微信服务通知反复推送也不怕。"],
    ["端侧解析零上传", "正则解析全部在本地完成，账单数据不出手机，隐私先守住。"],
  ];
  feats.forEach((f, i) => {
    const x = 0.65 + i * 4.09;
    card(s, x, 4.15, 3.9, 1.62);
    s.addText(f[0], { x: x + 0.26, y: 4.38, w: 3.4, h: 0.34, fontFace: FONT, fontSize: 14.5, bold: true, color: C.blue, margin: 0 });
    s.addText(f[1], { x: x + 0.26, y: 4.78, w: 3.4, h: 0.85, fontFace: FONT, fontSize: 11.5, color: C.sub, margin: 0, valign: "top", lineSpacingMultiple: 1.25 });
  });
  quoteBar(s, 0.65, 6.15, 12.07, "自动的是采集，人工的是确认——错误在入库之前就被拦下。");
  footer(s);
})();

/* ============================================================
   P14 总结 · 致谢（版式 #12 引用金句）
============================================================ */
(() => {
  const s = newSlide("总结：记得快、看得清、管得住、会自动；金句收尾致谢。");
  s.addShape(pptx.ShapeType.ellipse, { x: -2.2, y: 3.9, w: 6.4, h: 6.4, fill: { color: "DEEBFB" } });
  s.addShape(pptx.ShapeType.ellipse, { x: 10.9, y: -2.4, w: 5.4, h: 5.4, fill: { color: "DEEBFB" } });

  const chips = [
    ["记得快", "3 秒记完一笔"],
    ["看得清", "日历 · 统计 · 日报表"],
    ["管得住", "两级分类 + 环比"],
    ["会自动", "通知监听内核"],
  ];
  chips.forEach((c, i) => {
    const x = 0.85 + i * 3.0;
    card(s, x, 1.35, 2.8, 1.25);
    s.addText(c[0], { x: x + 0.24, y: 1.55, w: 2.3, h: 0.4, fontFace: FONT, fontSize: 17, bold: true, color: C.blue, margin: 0 });
    s.addText(c[1], { x: x + 0.24, y: 1.99, w: 2.3, h: 0.34, fontFace: FONT, fontSize: 11.5, color: C.sub, margin: 0 });
  });

  s.addText("“ 把复杂留给自己，把简单交给用户。”", { x: 1.17, y: 3.35, w: 11.0, h: 0.9, fontFace: FONT, fontSize: 30, bold: true, color: C.ink, align: "center", margin: 0, valign: "middle" });
  s.addText("—— 慕沿记账的设计纲领", { x: 1.17, y: 4.35, w: 11.0, h: 0.35, fontFace: FONT, fontSize: 12.5, color: C.faint, align: "center", margin: 0 });

  s.addText("感 谢 观 看", { x: 1.17, y: 5.35, w: 11.0, h: 0.7, fontFace: FONT, fontSize: 26, bold: true, color: C.blue, align: "center", margin: 0, valign: "middle" });
  s.addText("慕沿 muyian · 产品功能全览 · 2026.10", { x: 1.17, y: 6.15, w: 11.0, h: 0.32, fontFace: FONT, fontSize: 11, color: C.faint, align: "center", margin: 0 });
})();

// ---------- 输出 ----------
const OUT = path.join(__dirname, "慕沿记账-产品功能全览.pptx");
pptx.writeFile({ fileName: OUT }).then(() => console.log("written:", OUT));
