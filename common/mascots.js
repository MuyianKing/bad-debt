/**
 * 角色（吉祥物）唯一来源。
 *
 * 当前哪吒已启用；悟空按「大圣归来」大圣的方向预置了配置（ready: false，
 * 素材没到位时 activeMascot 会回退到哪吒）；沙僧待定。素材放齐后把对应角色的
 * ready 改成 true，首页、抽屉、空态、统计、设置页会自动切换，不需要改页面。
 *
 * 页面用法：
 *   import { activeMascot, activeMascotBaseStyle, refreshMascots } from '@/common/mascots.js'
 *   onShow(() => { refreshMascots() })
 *   <mascot-face mood="empty" :size="160" />
 *
 * 新增角色：
 *   1. 素材放 static/<mascot>/，每张表情补 src 与 headSrc（方形头像裁切）
 *   2. MASCOTS 里注册 id / name / defaultStyleId / styles / ready
 *   3. THEME_MASCOT 把对应主题指到新角色
 *   4. 素材没齐时 ready: false，运行时会回退到 FALLBACK_MASCOT_ID
 *   5. 设置页标题与表情列表自动跟随 activeMascot，不需要改
 *
 * 表情语义（mood）：idle 常态、success 成功、empty 空态、warn 预警（可用 moodAliases 复用已有表情）。
 * 用户选的是「基础表情」；开启「跟随场景」后，非 idle 场景自动换 mood 对应的图。
 */

import { computed, ref } from 'vue'
import { theme } from '@/common/theme.js'

const STYLE_KEY_PREFIX = 'muyian_mascot_style_v1:'
const AUTO_MOOD_KEY = 'muyian_mascot_auto_mood_v1'
// 旧版只存哪吒，保留兼容：首次读取时迁移到带角色 id 的新 key
const LEGACY_NEZHA_KEY = 'muyian_nezha_v1'

export const MASCOTS = {
  nezha: {
    id: 'nezha',
    name: '哪吒',
    defaultStyleId: 'grin',
    // 同一个表情可以服务多个场景：结余为负/超支统一用「不服」
    moodAliases: { warn: 'grumpy' },
    styles: [
      {
        id: 'grin',
        mood: 'idle',
        name: '坏笑',
        desc: '牙尖嘴利',
        src: '/static/nezha/grin.webp',
        headSrc: '/static/nezha/head-grin.webp',
        // 头图里的显示宽度与上提量（rpx）：坏笑这张头更大，上提让嘴露出概览卡
        widthRpx: 236,
        bottomRpx: 34
      },
      {
        id: 'cheer',
        mood: 'success',
        name: '大笑',
        desc: '举拳大笑',
        src: '/static/nezha/cheer.webp',
        headSrc: '/static/nezha/head-cheer.webp',
        widthRpx: 236,
        bottomRpx: 0
      },
      {
        id: 'grumpy',
        mood: 'empty',
        name: '不服',
        desc: '撇嘴斜眼',
        src: '/static/nezha/grumpy.webp',
        headSrc: '/static/nezha/head-grumpy.webp',
        widthRpx: 236,
        bottomRpx: 20
      }
    ]
  },
  wukong: {
    id: 'wukong',
    name: '悟空',
    defaultStyleId: 'grin',
    // 结余为负/超支复用「不服」
    moodAliases: { warn: 'grumpy' },
    // 方向：《西游记之大圣归来》的大圣（3D 国漫，和哪吒同一套渲染气质）。
    // 三张原图放 design/hero/assets/wukong-{grin,cheer,grumpy}.png；
    // 生成头像后把 ready 改成 true，悟空黄主题就会自动切到悟空。
    ready: false,
    styles: [
      {
        id: 'grin',
        mood: 'idle',
        name: '坏笑',
        desc: '扛棒挑眉',
        src: '/static/wukong/grin.webp',
        headSrc: '/static/wukong/head-grin.webp',
        widthRpx: 236,
        bottomRpx: 34
      },
      {
        id: 'cheer',
        mood: 'success',
        name: '大笑',
        desc: '举棒大笑',
        src: '/static/wukong/cheer.webp',
        headSrc: '/static/wukong/head-cheer.webp',
        widthRpx: 236,
        bottomRpx: 0
      },
      {
        id: 'grumpy',
        mood: 'empty',
        name: '不服',
        desc: '抱臂撇嘴',
        src: '/static/wukong/grumpy.webp',
        headSrc: '/static/wukong/head-grumpy.webp',
        widthRpx: 236,
        bottomRpx: 20
      }
    ]
  }
}

/**
 * 主题 -> 角色 id。
 * 悟空配置已预置（ready: false），素材到位前 activeMascot 会回退到哪吒；
 * 沙僧素材未定，先继续用哪吒。页面只认 activeMascot，不要按 theme.id 写 if/else。
 */
export const THEME_MASCOT = {
  wukong: 'wukong',
  nezha: 'nezha',
  shaseng: 'nezha'
}

export const FALLBACK_MASCOT_ID = 'nezha'

function styleExists(mascot, id) {
  return !!id && mascot.styles.some(s => s.id === id)
}

function readStyleId(mascot) {
  try {
    const own = String(uni.getStorageSync(STYLE_KEY_PREFIX + mascot.id) || '')
    if (styleExists(mascot, own)) return own
    // 旧版 key 迁移：只对哪吒生效，写入新 key 后旧 key 继续保留一份
    if (mascot.id === 'nezha') {
      const legacy = String(uni.getStorageSync(LEGACY_NEZHA_KEY) || '')
      if (styleExists(mascot, legacy)) {
        try { uni.setStorageSync(STYLE_KEY_PREFIX + mascot.id, legacy) } catch (e) {}
        return legacy
      }
    }
  } catch (e) {}
  return mascot.defaultStyleId || (mascot.styles[0] && mascot.styles[0].id) || ''
}

function readAllStyleIds() {
  const out = {}
  for (const id of Object.keys(MASCOTS)) out[id] = readStyleId(MASCOTS[id])
  return out
}

function readAutoMood() {
  try {
    const raw = uni.getStorageSync(AUTO_MOOD_KEY)
    return raw === '' || raw === null || raw === undefined ? true : !!raw
  } catch (e) {
    return true
  }
}

// 每个角色各自记住用户选的基础表情；换主题时不会串
const styleIds = ref(readAllStyleIds())

/** 是否按场景自动换表情（默认开） */
export const autoMood = ref(readAutoMood())

/** 当前主题对应的角色；角色素材没齐（ready: false）时回退到哪吒 */
export const activeMascot = computed(() => {
  const themeId = (theme.value && theme.value.id) || ''
  const id = THEME_MASCOT[themeId] || FALLBACK_MASCOT_ID
  const candidate = MASCOTS[id]
  if (candidate && candidate.ready !== false) return candidate
  return MASCOTS[FALLBACK_MASCOT_ID]
})

/** 当前角色、用户选中的基础表情（头图、抽屉头像、手动模式都用它） */
export const activeMascotBaseStyle = computed(() => {
  const mascot = activeMascot.value
  const id = styleIds.value[mascot.id]
  return mascot.styles.find(s => s.id === id)
    || mascot.styles.find(s => s.id === mascot.defaultStyleId)
    || mascot.styles[0]
    || null
})

/**
 * 按场景解析表情。
 * @param {string} mood idle / success / empty / 自定义
 * @param {string} mode auto = 跟随场景；base = 固定用基础表情
 */
export function resolveMascotStyle(mood = 'idle', mode = 'auto') {
  const mascot = activeMascot.value
  const base = activeMascotBaseStyle.value
  if (!mascot || !base) return null
  if (mode === 'base' || !autoMood.value || !mood || mood === 'idle') return base
  const aliasId = mascot.moodAliases && mascot.moodAliases[mood]
  if (aliasId) return mascot.styles.find(s => s.id === aliasId) || base
  return mascot.styles.find(s => s.mood === mood) || base
}

/** 从 storage 同步一次（页面 onShow 调用；设置页切换后其他页面立即跟上） */
export function refreshMascots() {
  styleIds.value = readAllStyleIds()
  autoMood.value = readAutoMood()
}

/** 切换当前角色的基础表情并持久化 */
export function setMascotStyle(styleId) {
  const mascot = activeMascot.value
  const target = mascot.styles.find(s => s.id === styleId) || mascot.styles[0]
  if (!target) return null
  styleIds.value = { ...styleIds.value, [mascot.id]: target.id }
  try { uni.setStorageSync(STYLE_KEY_PREFIX + mascot.id, target.id) } catch (e) {}
  // 旧版本只认这个 key；保留写入，回滚或旧页面仍能读到哪吒的选择
  if (mascot.id === 'nezha') {
    try { uni.setStorageSync(LEGACY_NEZHA_KEY, target.id) } catch (e) {}
  }
  return target
}

/** 开关「跟随场景」 */
export function setAutoMood(enabled) {
  autoMood.value = !!enabled
  try { uni.setStorageSync(AUTO_MOOD_KEY, autoMood.value) } catch (e) {}
  return autoMood.value
}
