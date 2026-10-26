<template>
  <view class="app-icon" :style="style"></view>
</template>

<script>
import { icon } from '@/common/icons.js'

/**
 * 线性图标组件（SVG data-uri，Feather 风格）
 * <app-icon name="menu" color="#ffffff" :size="22" />
 */
export default {
  name: 'app-icon',
  props: {
    name: { type: String, required: true },
    color: { type: String, default: '#333333' },
    size: { type: [Number, String], default: 22 },
    strokeWidth: { type: [Number, String], default: 1.8 },
    solid: { type: Boolean, default: false }
  },
  computed: {
    style() {
      const s = Number(this.size) || 22
      return {
        width: s + 'px',
        height: s + 'px',
        flexShrink: 0,
        backgroundImage: icon(this.name, this.color, s, Number(this.strokeWidth) || 2, !!this.solid)
      }
    }
  }
}
</script>

<style>
.app-icon {
  display: inline-block;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 100% 100%;
}
</style>
