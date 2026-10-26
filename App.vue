<script>
import { getCategories } from '@/common/cats.js'
import { refreshTheme, theme } from '@/common/theme.js'
import { applyRecentsVisibility } from '@/common/recents.js'
// #ifdef APP-PLUS
import { initBillListener, setCardThemeColor } from '@/uni_modules/muyian-bill-listener'
// #endif

export default {
  onLaunch() {
    // 播种默认分类（首次启动写入本地存储）
    try {
      getCategories()
    } catch (e) {
      console.error('seed categories failed', e)
    }
    // 恢复上次选择的主题；原生底部弹层不认 CSS 变量，用字面色单独同步一次
    refreshTheme()
    // #ifdef APP-PLUS
    try {
      setCardThemeColor(theme.value.cardColor || theme.value.primary)
    } catch (e) {
      console.error('sync card theme failed', e)
    }
    // #endif
    // #ifdef APP-PLUS
    try {
      initBillListener()
    } catch (e) {
      console.error('init bill listener failed', e)
    }
    // #endif
    // 按设置页开关应用“从多任务页面隐藏”（清单标记在 HyperOS 上会被忽略）
    applyRecentsVisibility()
  },
  onShow() {
    applyRecentsVisibility()
  }
}
</script>

<style>
page {
  /* ---- 设计令牌：黄色主题，改主题只动这里 ---- */
  --c-primary: #f7a600;
  --c-primary-deep: #d97f00;
  --c-primary-light: #ffc63f;
  --c-primary-weak: #fff4db;
  --c-primary-tint: rgba(247, 166, 0, 0.14);
  /* 实底主题面：承载白字的大面积填充（统计总览卡）；深色主题里与强调色分开 */
  --c-primary-fill: #f7a600;
  /* 主题面按下态（记一笔保存键的底边与按下底色） */
  --c-primary-press: #d97f00;
  /* 主题色填充面（按钮/头图/选中态）：一律渐变底 + 白字，禁止深色字压黄 */
  --grad-primary: linear-gradient(135deg, #ffc63f 0%, #f9aa00 52%, #ec8c00 100%);
  --grad-primary-strong: linear-gradient(165deg, #ffbb2b 0%, #f39a00 45%, #dd7b00 100%);
  --grad-primary-soft: linear-gradient(135deg, #fff8e8 0%, #ffedc2 100%);
  --c-ink-on-primary: #ffffff;
  --c-on-primary-sub: rgba(255, 255, 255, 0.82);
  --c-hero-from: #ffc63f;
  --c-hero-mid: #f9a400;
  --c-hero-to: #e88700;
  --c-out: #f0655e;
  --c-out-deep: #e14c46;
  --c-out-weak: #fef0ef;
  --c-in: #22b877;
  --c-in-weak: #e7f8f0;
  --c-gold: #ffb400;
  /* 强调色：功能图标/辅助色块的彩色来源 */
  --c-blue: #4489fe;
  --c-sky: #00aaf5;
  --c-violet: #9a6bff;
  --c-mint: #22b877;
  --c-rose: #ff6b81;
  --grad-blue: linear-gradient(135deg, #74adff 0%, #4489fe 100%);
  --grad-sky: linear-gradient(135deg, #4cc8ff 0%, #00aaf5 100%);
  --grad-violet: linear-gradient(135deg, #b795ff 0%, #9a6bff 100%);
  --grad-mint: linear-gradient(135deg, #5ad6a0 0%, #22b877 100%);
  --grad-rose: linear-gradient(135deg, #ff96a6 0%, #ff6b81 100%);
  --c-text: #1d2129;
  --c-sub: #8a90a0;
  --c-dim: #c2c6d1;
  --c-bg: #f7f4ec;
  /* 极淡的主题色页面底（首页用）：约 6% 主题色 + 白 */
  --c-page-wash: #fef9ed;
  --c-card: #ffffff;
  --c-line: #f2f0ea;
  /* 中性面与细分隔线：开关轨道、空状态底板、卡片内部行分隔 */
  --c-line-soft: rgba(29, 33, 41, 0.06);
  --c-fill: rgba(29, 33, 41, 0.05);
  /* 浮起面：分段滑块、底部弹层等需要比卡片更亮/更突出的中性面 */
  --c-raised: #ffffff;
  /* 记一笔键盘：键帽底色/按下底色/底边/数字色，功能键另有一套 */
  --c-key-bg: #ffffff;
  --c-key-press: #fafbfd;
  --c-key-edge: #e5e8ef;
  --c-key-fg: #2a2f3a;
  --c-key-fn-bg: #e9ebf2;
  --c-key-fn-edge: #dcdfe8;
  --c-key-fn-fg: #4a5060;
  --c-mask: rgba(29, 33, 41, 0.55);
  --shadow-card: 0 10rpx 30rpx rgba(94, 74, 22, 0.12);
  /* 中性软阴影：实底卡片用，比暖色 --shadow-card 更轻、更贴合现代面板 */
  --shadow-card-soft: 0 2rpx 12rpx rgba(29, 33, 41, 0.05);
  /* 玻璃令牌：全站所有毛玻璃表面统一从这里取值 */
  /* 玻璃要能明显透出底色，0.62 会白成实底；下限 0.5，再低正文对比度不够 */
  --glass-bg: rgba(255, 255, 255, 0.52);
  --glass-bg-strong: rgba(255, 255, 255, 0.88);
  --glass-border: rgba(255, 255, 255, 0.85);
  --glass-nav-bg: rgba(255, 255, 255, 0.72);
  --shadow-float: 0 14rpx 34rpx rgba(233, 138, 0, 0.34);
  --shadow-primary: 0 10rpx 26rpx rgba(233, 138, 0, 0.3);
  --shadow-primary-sm: 0 4rpx 12rpx rgba(233, 138, 0, 0.28);
  --shadow-primary-lg: 0 12rpx 30rpx rgba(233, 138, 0, 0.18);
  /* 非选中态柱条：纯色，随主题走 */
  --c-bar: #ffc24e;

  background-color: #f7f4ec;
  color: var(--c-text);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Helvetica Neue',
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}
/* ---- 主题：哪吒红（朱红/混天绫）；只覆盖与主题相关的令牌，页面样式无需改动 ---- */
.theme-nezha {
  --c-primary: #e0362e;
  --c-primary-deep: #bf271f;
  --c-primary-light: #ff7d63;
  --c-primary-weak: #ffecea;
  --c-primary-tint: rgba(224, 54, 46, 0.14);
  --c-primary-fill: #e0362e;
  --c-primary-press: #bf271f;
  --grad-primary: linear-gradient(135deg, #ff7d63 0%, #e8402f 52%, #c9211b 100%);
  --grad-primary-strong: linear-gradient(165deg, #ff6a4d 0%, #e33a2c 45%, #b81d18 100%);
  --grad-primary-soft: linear-gradient(135deg, #fff1ee 0%, #ffdad4 100%);
  --c-hero-from: #ff7d63;
  --c-hero-mid: #ea3f30;
  --c-hero-to: #bb1f19;
  --c-page-wash: #fdf1f0;
  --shadow-card: 0 10rpx 30rpx rgba(94, 22, 18, 0.12);
  --shadow-float: 0 14rpx 34rpx rgba(198, 36, 28, 0.34);
  --shadow-primary: 0 10rpx 26rpx rgba(198, 36, 28, 0.3);
  --shadow-primary-sm: 0 4rpx 12rpx rgba(198, 36, 28, 0.24);
  --shadow-primary-lg: 0 12rpx 30rpx rgba(198, 36, 28, 0.2);
  --c-bar: #ff9d8c;
}
/* ---- 主题：沙僧黑（纯黑底 + 亮银强调）；深色令牌全部在这里覆盖 ---- */
.theme-shaseng {
  /* 强调色（柱条/选中描边/图标字面色）：深底上用亮银，不能用填充色 */
  --c-primary: #e6e6ec;
  --c-primary-deep: #ffffff;
  --c-primary-light: #f2f2f6;
  --c-primary-weak: #1d1d21;
  --c-primary-tint: rgba(255, 255, 255, 0.1);
  /* 填充面：黑底石墨，保证压在上面的白字仍然可读 */
  --c-primary-fill: #191a1c;
  --c-primary-press: #000000;
  --grad-primary: linear-gradient(135deg, #3d3d44 0%, #232327 55%, #111113 100%);
  --grad-primary-strong: linear-gradient(165deg, #2a2a2f 0%, #151517 45%, #000000 100%);
  --grad-primary-soft: linear-gradient(135deg, #1b1b1e 0%, #111113 100%);
  --c-ink-on-primary: #ffffff;
  --c-on-primary-sub: rgba(255, 255, 255, 0.72);
  --c-hero-from: #2a2a2f;
  --c-hero-mid: #151517;
  --c-hero-to: #000000;
  /* 收支色：深底上提亮一档 */
  --c-out: #ff6f68;
  --c-out-deep: #ff8a84;
  --c-out-weak: #2a1716;
  --c-in: #35d492;
  --c-in-weak: #10241b;
  /* 文字与面板 */
  --c-text: #f2f2f4;
  --c-sub: #a2a2aa;
  --c-dim: #6a6a72;
  --c-bg: #000000;
  --c-page-wash: #000000;
  --c-card: #121214;
  --c-line: #232327;
  --c-line-soft: rgba(255, 255, 255, 0.07);
  --c-fill: rgba(255, 255, 255, 0.06);
  --c-mask: rgba(0, 0, 0, 0.66);
  --c-raised: #232327;
  /* 键盘：深色键帽 + 更深的底边 */
  --c-key-bg: #1c1c1f;
  --c-key-press: #2a2a2e;
  --c-key-edge: #09090a;
  --c-key-fg: #f2f2f4;
  --c-key-fn-bg: #151517;
  --c-key-fn-edge: #0b0b0c;
  --c-key-fn-fg: #c9c9d0;
  /* 阴影：深底下用纯黑，避免彩影发灰 */
  --shadow-card: 0 10rpx 30rpx rgba(0, 0, 0, 0.6);
  --shadow-card-soft: 0 2rpx 12rpx rgba(0, 0, 0, 0.5);
  --glass-bg: rgba(28, 28, 32, 0.62);
  --glass-bg-strong: rgba(28, 28, 32, 0.9);
  --glass-border: rgba(255, 255, 255, 0.1);
  --glass-nav-bg: rgba(10, 10, 11, 0.8);
  --shadow-float: 0 14rpx 34rpx rgba(0, 0, 0, 0.7);
  --shadow-primary: 0 10rpx 26rpx rgba(0, 0, 0, 0.6);
  --shadow-primary-sm: 0 4rpx 12rpx rgba(0, 0, 0, 0.55);
  --shadow-primary-lg: 0 12rpx 30rpx rgba(0, 0, 0, 0.5);
  /* 柱条：黑底上用浅灰 */
  --c-bar: #b9b9c2;
  /* wot 组件深色底（wd-cell / wd-action-sheet 等），跟随本主题的黑灰层级 */
  --wot-dark-background: #0d0d0f;
  --wot-dark-background2: #17171a;
  --wot-dark-color: #f2f2f4;
  --wot-dark-color3: rgba(242, 242, 244, 0.72);
}
/* ---- 毛玻璃：半透明 + 背景模糊；底色一律走令牌，深色主题随之变深 ---- */
.glass {
  /* !important：压过各页面卡片自带的白色背景，否则玻璃不生效。
     这里给不支持 backdrop-filter 的环境一个更实的兜底底色（--glass-bg-strong） */
  background-color: var(--glass-bg-strong) !important;
  border: 1rpx solid var(--glass-border);
}
@supports ((-webkit-backdrop-filter: blur(1px)) or (backdrop-filter: blur(1px))) {
  .glass {
    background-color: var(--glass-bg) !important;
    -webkit-backdrop-filter: blur(18px) saturate(1.6);
    backdrop-filter: blur(18px) saturate(1.6);
  }
}
.glass-nav,
.glass-nav .wd-navbar__content {
  background-color: var(--glass-nav-bg) !important;
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(18px) saturate(1.6);
}
/* ---- 底部操作菜单：铺满屏宽（去掉 wot 默认的 10px 侧边距与圆角），取消键做成整行页脚 ---- */
.wd-action-sheet {
  margin-left: 0 !important;
  margin-right: 0 !important;
  margin-bottom: 0 !important;
  border-radius: 0 !important;
}
.wd-action-sheet .wd-action-sheet__actions {
  padding: 0 !important;
}
.wd-action-sheet .wd-action-sheet__action + .wd-action-sheet__action {
  border-top: 1rpx solid var(--c-line);
}
.wd-action-sheet .wd-action-sheet__cancel {
  width: 100% !important;
  margin: 0 !important;
  border-radius: 0 !important;
  border-top: 1rpx solid var(--c-line);
  background-color: var(--c-card) !important;
  color: var(--c-text) !important;
  font-weight: 500 !important;
}
</style>
