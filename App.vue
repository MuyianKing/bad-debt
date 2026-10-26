<script>
import { getCategories } from '@/common/cats.js'
// #ifdef APP-PLUS
import { initBillListener } from '@/uni_modules/muyian-bill-listener'
// #endif

export default {
  onLaunch() {
    // 播种默认分类（首次启动写入本地存储）
    try {
      getCategories()
    } catch (e) {
      console.error('seed categories failed', e)
    }
    // #ifdef APP-PLUS
    try {
      initBillListener()
    } catch (e) {
      console.error('init bill listener failed', e)
    }
    // #endif
  }
}
</script>

<style>
page {
  /* ---- 设计令牌：黄色主题，改主题只动这里 ---- */
  --c-primary: #f7a600;
  --c-primary-deep: #e08a00;
  --c-primary-weak: #fff4db;
  --c-ink-on-primary: #3a2c05;
  --c-hero-from: #ffd21e;
  --c-hero-mid: #ffb400;
  --c-hero-to: #ff9500;
  --c-out: #f0655e;
  --c-out-deep: #e14c46;
  --c-out-weak: #fef0ef;
  --c-in: #22b877;
  --c-in-weak: #e7f8f0;
  --c-gold: #ffb400;
  --c-text: #1d2129;
  --c-sub: #8a90a0;
  --c-dim: #c2c6d1;
  --c-bg: #f7f7f4;
  --c-card: #ffffff;
  --c-line: #f2f0ea;
  --c-mask: rgba(29, 33, 41, 0.55);
  --shadow-card: 0 8rpx 32rpx rgba(94, 74, 22, 0.13);
  /* 玻璃令牌：全站所有毛玻璃表面统一从这里取值 */
  --glass-bg: rgba(255, 255, 255, 0.62);
  --glass-bg-strong: rgba(255, 255, 255, 0.88);
  --glass-border: rgba(255, 255, 255, 0.85);
  --glass-nav-bg: rgba(255, 255, 255, 0.78);
  --shadow-float: 0 12rpx 36rpx rgba(240, 140, 0, 0.38);

  background-color: #f2f1ea;
  background-image: linear-gradient(168deg, #FFDF9E 0%, #FBEFD4 30%, #F1F2E9 66%, #EEF0E9 100%);
  color: var(--c-text);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Helvetica Neue',
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}
/* ---- 毛玻璃：半透明 + 背景模糊；不支持 backdrop-filter 的老 WebView 自动退化为更实的半透明底 ---- */
.glass {
  /* !important：压过各页面卡片自带的白色背景，否则玻璃不生效 */
  background-color: var(--glass-bg) !important;
  border: 1rpx solid var(--glass-border);
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(18px) saturate(1.6);
}
@supports ((-webkit-backdrop-filter: blur(1px)) or (backdrop-filter: blur(1px))) {
  .glass {
    background-color: rgba(255, 255, 255, 0.58) !important; /* 兜底环境略实 */
  }
}
@supports ((-webkit-backdrop-filter: blur(1px)) or (backdrop-filter: blur(1px))) {
  .glass {
    background-color: rgba(255, 255, 255, 0.62);
  }
}
.glass-nav,
.glass-nav .wd-navbar__content {
  background-color: var(--glass-nav-bg) !important;
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(18px) saturate(1.6);
}
@supports ((-webkit-backdrop-filter: blur(1px)) or (backdrop-filter: blur(1px))) {
  .glass-nav,
  .glass-nav .wd-navbar__content {
    background-color: rgba(255, 255, 255, 0.72) !important;
  }
}
</style>
