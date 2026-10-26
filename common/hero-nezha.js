/**
 * 首页头图右侧的哪吒表情（唯一来源）。
 *
 * 三张透明图在 static/nezha/，storage key：muyian_nezha_v1，默认「坏笑」。
 * 设置页「外观 → 首页哪吒」切换，首页 onShow 调 refreshNezhaStyle() 跟上。
 *
 * 页面用法：
 *   import { nezhaStyle, refreshNezhaStyle } from '@/common/hero-nezha.js'
 *   onShow(() => { refreshNezhaStyle() })
 *   <image :src="nezhaStyle.src" :style="{ width: nezhaStyle.widthRpx + 'rpx' }" mode="widthFix" />
 *
 * 新增表情：补一条 NEZHA_STYLES，图片放 static/nezha/，并在设置页的选择卡里自动出现。
 */

import { computed, ref } from 'vue'

const NEZHA_KEY = 'muyian_nezha_v1'
const DEFAULT_NEZHA_ID = 'grin'

export const NEZHA_STYLES = [
  {
    id: 'grin',
    name: '坏笑',
    desc: '牙尖嘴利',
    src: '/static/nezha/grin.webp',
    // 头图里的显示宽度与上提量（rpx）：坏笑这张头更大，上提让嘴露出概览卡
    widthRpx: 236,
    bottomRpx: 34
  },
  {
    id: 'cheer',
    name: '大笑',
    desc: '举拳大笑',
    src: '/static/nezha/cheer.webp',
    widthRpx: 236,
    bottomRpx: 0
  },
  {
    id: 'grumpy',
    name: '不服',
    desc: '撇嘴斜眼',
    src: '/static/nezha/grumpy.webp',
    widthRpx: 236,
    bottomRpx: 20
  }
]

function readStoredNezhaId() {
  try {
    const id = String(uni.getStorageSync(NEZHA_KEY) || '')
    return NEZHA_STYLES.some(n => n.id === id) ? id : DEFAULT_NEZHA_ID
  } catch (e) {
    return DEFAULT_NEZHA_ID
  }
}

const nezhaId = ref(readStoredNezhaId())

/** 当前选中的表情（含 src / widthRpx / name） */
export const nezhaStyle = computed(() => NEZHA_STYLES.find(n => n.id === nezhaId.value) || NEZHA_STYLES[0])

/** 从本地存储同步一次（页面 onShow 调用；设置页切换后回到首页立即生效） */
export function refreshNezhaStyle() {
  const id = readStoredNezhaId()
  if (nezhaId.value !== id) nezhaId.value = id
}

/** 切换并持久化，返回选中的表情对象 */
export function setNezhaStyle(id) {
  const target = NEZHA_STYLES.find(n => n.id === id) || NEZHA_STYLES[0]
  nezhaId.value = target.id
  try {
    uni.setStorageSync(NEZHA_KEY, target.id)
  } catch (e) {
    // 存储失败时本次会话仍然生效
  }
  return target
}
