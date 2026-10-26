<template>
  <view :class="['app-fab', 'app-fab-' + position]" :style="fabStyle" @tap="$emit('click')">
    <app-icon name="plus" color="#ffffff" :size="26" :stroke-width="2.6"></app-icon>
  </view>
</template>

<script>
/**
 * 主题色浮动按钮（黄色渐变），替代 wd-fab 以完全掌控配色。
 * position: bottom-center（记账入口）| right-bottom（列表新增）
 */
export default {
  name: 'app-fab',
  props: {
    position: { type: String, default: 'bottom-center' },
    bottom: { type: [Number, String], default: 0 } // 额外上移的 px
  },
  emits: ['click'],
  computed: {
    fabStyle() {
      const extra = Number(this.bottom) || 0
      return extra ? { bottom: `calc(${48 + extra}rpx + env(safe-area-inset-bottom))` } : {}
    }
  }
}
</script>

<style>
.app-fab {
  position: fixed;
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-float), inset 0 2rpx 0 rgba(255, 255, 255, 0.4);
  z-index: 99;
}
.app-fab:active {
  transform: scale(0.94);
}
.app-fab-bottom-center {
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(48rpx + env(safe-area-inset-bottom));
}
.app-fab-bottom-center:active {
  transform: translateX(-50%) scale(0.94);
}
.app-fab-right-bottom {
  right: 40rpx;
  bottom: calc(48rpx + env(safe-area-inset-bottom));
}
</style>
