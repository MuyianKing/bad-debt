/**
 * 主题（换肤）唯一来源。
 *
 * 配色令牌本体在 App.vue：`page` 是默认的「悟空黄」，`.theme-nezha` 覆盖为「哪吒红」。
 * 本文件只负责：主题清单（设置页展示 + 名称）、当前主题的响应式状态与持久化，
 * 以及给 SVG 图标 / canvas 这类不支持 CSS 变量、只能吃字面色的地方提供色值。
 *
 * 页面用法：
 *   <view :class="['page', themeClass]">
 *   import { theme, themeClass, refreshTheme } from '@/common/theme.js'
 *   onShow(() => { refreshTheme(); ... })
 * 新增主题：在 App.vue 加一组 `.theme-<id>` 令牌覆盖，并在下面的 THEMES 里补一条。
 */

import { computed, ref } from 'vue'

const THEME_KEY = 'muyian_theme_v1'
const DEFAULT_THEME_ID = 'wukong'

export const THEMES = [
  {
    id: 'wukong',
    name: '悟空黄',
    tagline: '金箍棒 · 暖金',
    // 供 SVG 图标 / canvas 使用的字面色值（CSS 变量在这些场景无效）
    primary: '#f7a600',
    primaryDeep: '#d97f00',
    primaryFill: 'rgba(247, 166, 0, 0.14)',
    // 原生悬浮记账卡的强调色（不认 CSS 变量）：深色主题要和页面强调色分开给
    cardColor: '#f7a600',
    // 中性图标字面色（app-icon 只能吃字面色）：深色主题要换成浅色墨
    ink: '#1d2129',
    inkSub: '#5b6170',
    // 设置页主题预览缩略图的渐变（与 App.vue 的 --grad-primary 一致）
    grad: 'linear-gradient(135deg, #ffc63f 0%, #f9aa00 52%, #ec8c00 100%)'
  },
  {
    id: 'nezha',
    name: '哪吒红',
    tagline: '混天绫 · 朱红',
    primary: '#e0362e',
    primaryDeep: '#bf271f',
    primaryFill: 'rgba(224, 54, 46, 0.14)',
    cardColor: '#e0362e',
    ink: '#1d2129',
    inkSub: '#5b6170',
    grad: 'linear-gradient(135deg, #ff7d63 0%, #e8402f 52%, #c9211b 100%)'
  },
  {
    id: 'shaseng',
    name: '沙僧黑',
    tagline: '降妖宝杖 · 纯黑',
    // 深色主题的强调色用亮银（柱条/选中描边/图标），填充面另见 App.vue 的 --c-primary-fill
    primary: '#e6e6ec',
    primaryDeep: '#ffffff',
    primaryFill: 'rgba(230, 230, 236, 0.14)',
    // 原生弹层是浅色底：强调色必须给深色，否则白字保存键与选中分类都看不清
    cardColor: '#1a1a1d',
    ink: '#f2f2f4',
    inkSub: '#a2a2aa',
    grad: 'linear-gradient(135deg, #4a4a52 0%, #1c1c20 55%, #000000 100%)',
    // 深色主题：页面根节点额外挂 wot-theme-dark，让 wot 组件切到深色底
    dark: true
  }
]

/** 收入/支出语义色字面值（SVG 图标、canvas 画笔用；与 App.vue 的 --c-in / --c-out 一致） */
export const COLOR_IN = '#22b877'
export const COLOR_OUT = '#f0655e'

function readStoredTheme() {
  try {
    const id = String(uni.getStorageSync(THEME_KEY) || '')
    return THEMES.some(t => t.id === id) ? id : DEFAULT_THEME_ID
  } catch (e) {
    return DEFAULT_THEME_ID
  }
}

const themeId = ref(readStoredTheme())

/** 当前主题对象（含 name / primary 等字面色值） */
export const theme = computed(() => THEMES.find(t => t.id === themeId.value) || THEMES[0])

/** 页面根节点用的主题类名，配合 App.vue 的 .theme-<id> 令牌覆盖；深色主题再挂 wot-theme-dark */
export const themeClass = computed(() => {
  const t = theme.value
  return 'theme-' + t.id + (t.dark ? ' wot-theme-dark' : '')
})

/**
 * 从本地存储同步一次当前主题。
 * 页面 onShow 调用：换肤发生在设置页，返回其他页面（或不同 webview 各自持有一份
 * 模块状态）时都能立刻跟上。
 */
export function refreshTheme() {
  const id = readStoredTheme()
  if (themeId.value !== id) themeId.value = id
}

/** 切换并持久化主题，返回选中的主题对象 */
export function setTheme(id) {
  const target = THEMES.find(t => t.id === id) || THEMES[0]
  themeId.value = target.id
  try {
    uni.setStorageSync(THEME_KEY, target.id)
  } catch (e) {
    // 存储失败时本次会话仍然生效
  }
  return target
}
