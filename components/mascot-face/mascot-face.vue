<template>
  <image
    v-if="style"
    class="mascot-face"
    :class="{ 'is-head': useHead }"
    :src="useHead ? style.headSrc : style.src"
    :mode="useHead ? 'aspectFill' : 'widthFix'"
    :style="faceStyle"
  ></image>
</template>

<script setup>
import { computed } from 'vue'
import { resolveMascotStyle } from '@/common/mascots.js'

/**
 * 通用角色表情。
 * mood：场景语义（idle / success / empty），具体用哪张图见 common/mascots.js
 * size：rpx；head=true 时是方形头像边长，否则是整图宽度
 * mode：auto 跟随场景 / base 固定用用户选的基础表情
 */
const props = defineProps({
  mood: { type: String, default: 'idle' },
  size: { type: [Number, String], default: 160 },
  head: { type: Boolean, default: false },
  mode: { type: String, default: 'auto' }
})

const style = computed(() => resolveMascotStyle(props.mood, props.mode))
const useHead = computed(() => props.head && !!(style.value && style.value.headSrc))
const faceStyle = computed(() => {
  const n = Number(props.size) || 160
  return useHead.value
    ? { width: n + 'rpx', height: n + 'rpx' }
    : { width: n + 'rpx' }
})
</script>

<style scoped>
.mascot-face {
  display: block;
}
.is-head {
  border-radius: 50%;
}
</style>
