/**
 * 分类数据：默认分类 + 本地存储 + 图标/颜色查询
 * 分类结构：
 *   { name, icon, subs?: [{ name, icon }] }
 * icon 为 common/icons.js 里的线性图标名（不再是 emoji）。
 * 账单里的 costType 存的是"叶子名"（选了子类存子类名，否则存一级名）；
 * 环图统计时通过 topNameOf() 归并到一级分类。
 */

import { theme } from './theme.js'

const STORAGE_KEY = 'muyian_categories_v2' // v2：icon 从 emoji 改为线性图标名

// 高饱和糖果色板：按默认分类顺序排列（三餐→零食→衣服→…→电器数码），改顺序会让已有分类换色
export const PALETTE = [
  '#FF653F', '#4489FE', '#00AAF5', '#B487FE', '#29C184', '#FFC94D',
  '#95D46B', '#8B9FFF', '#F7707A', '#5C7CFA', '#80CBC4', '#F5A65B',
  '#FF85C0', '#4FC3F7', '#FF5E5E', '#FFA83E', '#01BFA3', '#C77DFF',
  '#FA66D4'
]

/** 自建分类的默认图标 */
export const DEFAULT_ICON = 'grid'

/** 图标选择器可选项（分类管理弹窗用） */
export const ICON_CHOICES = [
  'utensils', 'cookie', 'coffee', 'apple', 'shirt', 'bus', 'compass', 'smile',
  'paw', 'phone', 'wine', 'book-open', 'cart', 'home', 'lipstick', 'pill',
  'red-packet', 'gift', 'fuel', 'gamepad', 'camera', 'activity', 'grid',
  'droplet', 'wallet', 'award', 'trending-up', 'briefcase', 'rotate-ccw',
  'star', 'bell', 'clock', 'flag'
]

const DEFAULT_EXPENSE = [
  { name: '三餐', icon: 'utensils' },
  { name: '零食', icon: 'cookie', subs: [{ name: '水果', icon: 'apple' }, { name: '奶茶', icon: 'coffee' }] },
  { name: '衣服', icon: 'shirt' },
  { name: '交通', icon: 'bus' },
  { name: '旅行', icon: 'compass' },
  { name: '孩子', icon: 'smile' },
  { name: '宠物', icon: 'paw' },
  { name: '话费网费', icon: 'phone' },
  { name: '烟酒', icon: 'wine' },
  { name: '学习', icon: 'book-open' },
  { name: '日用品', icon: 'cart' },
  { name: '住房', icon: 'home' },
  { name: '美妆', icon: 'lipstick' },
  { name: '医疗', icon: 'pill' },
  { name: '发红包', icon: 'red-packet' },
  { name: '汽车/加油', icon: 'fuel' },
  { name: '娱乐', icon: 'gamepad' },
  { name: '请客送礼', icon: 'gift' },
  { name: '电器数码', icon: 'camera' },
  { name: '运动', icon: 'activity' },
  { name: '其它', icon: 'grid' },
  { name: '水电煤', icon: 'droplet' }
]

const DEFAULT_INCOME = [
  { name: '工资', icon: 'wallet' },
  { name: '奖金', icon: 'award' },
  { name: '理财', icon: 'trending-up' },
  { name: '兼职', icon: 'briefcase' },
  { name: '红包', icon: 'red-packet' },
  { name: '退款', icon: 'rotate-ccw' },
  { name: '其它收入', icon: 'grid' }
]

/** 读取分类（首次自动播种默认数据） */
export function getCategories() {
  let data = null
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (raw) data = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    data = null
  }
  if (!data || !Array.isArray(data.expense) || !Array.isArray(data.income)) {
    data = { expense: clone(DEFAULT_EXPENSE), income: clone(DEFAULT_INCOME) }
    saveCategories(data)
  }
  return data
}

export function saveCategories(data) {
  try {
    uni.setStorageSync(STORAGE_KEY, JSON.stringify(data))
  } catch (e) {
    // ignore
  }
}

export function listByType(type) {
  const data = getCategories()
  return type === 'income' ? data.income : data.expense
}

function clone(v) {
  return JSON.parse(JSON.stringify(v))
}

function findIn(list, name) {
  for (let i = 0; i < list.length; i++) {
    const top = list[i]
    if (top.name === name) return { top, index: i, sub: null }
    if (top.subs) {
      for (const s of top.subs) {
        if (s.name === name) return { top, index: i, sub: s }
      }
    }
  }
  return null
}

/**
 * 账单 costType -> 线性图标名（找不到用默认 grid）
 */
export function iconOf(name, type) {
  if (!name) return DEFAULT_ICON
  if (name === '收入') return 'wallet'
  if (type === undefined) {
    for (const t of ['expense', 'income']) {
      const h = findIn(listByType(t), name)
      if (h) return (h.sub || h.top).icon || h.top.icon || DEFAULT_ICON
    }
    return DEFAULT_ICON
  }
  const hit = findIn(listByType(type), name)
  if (hit) return (hit.sub || hit.top).icon || hit.top.icon || DEFAULT_ICON
  return DEFAULT_ICON
}

/** 账单 costType -> 归属一级分类名（子类归并到父级） */
export function topNameOf(name, type) {
  if (!name) return '其它'
  const hit = findIn(listByType(type || 'expense'), name)
  if (hit) return hit.top.name
  if (type === undefined) {
    for (const t of ['expense', 'income']) {
      const h = findIn(listByType(t), name)
      if (h) return h.top.name
    }
  }
  return name
}

/** 一级分类 -> 调色板颜色（按存储顺序取色，保证稳定） */
export function colorOf(name, type) {
  const list = listByType(type || 'expense')
  for (let i = 0; i < list.length; i++) {
    if (list[i].name === name) return PALETTE[i % PALETTE.length]
  }
  // 兜底：按名字 hash 取色，保证同名同色
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0
  return PALETTE[hash % PALETTE.length]
}

/* ---------- 颜色工具（页面/组件共用的彩色图标底座） ---------- */
function rgbOf(hex) {
  const raw = String(hex || '#c8ccd4').replace('#', '')
  const h = raw.length === 3 ? raw.split('').map(c => c + c).join('') : raw
  return {
    r: parseInt(h.slice(0, 2), 16) || 0,
    g: parseInt(h.slice(2, 4), 16) || 0,
    b: parseInt(h.slice(4, 6), 16) || 0
  }
}

function mixColor(hex, target, ratio) {
  const a = rgbOf(hex)
  const b = rgbOf(target)
  return {
    r: Math.round(a.r + (b.r - a.r) * ratio),
    g: Math.round(a.g + (b.g - a.g) * ratio),
    b: Math.round(a.b + (b.b - a.b) * ratio)
  }
}

/** 分类色 -> 极淡同色底（图标块/选中行底色）。深色主题向黑色混合，否则亮色块压不住白字 */
export function tintOf(hex, ratio = 0.88) {
  const c = mixColor(hex, theme.value.dark ? '#000000' : '#ffffff', ratio)
  return 'rgb(' + c.r + ',' + c.g + ',' + c.b + ')'
}

/** 分类色 -> 同色系渐变块（彩色图标统一底座，白图标压在上面） */
export function gradOf(hex, angle = 140) {
  const base = rgbOf(hex)
  const light = mixColor(hex, '#ffffff', 0.24)
  const dark = mixColor(hex, '#000000', 0.12)
  return (
    'linear-gradient(' + angle + 'deg, rgb(' + light.r + ',' + light.g + ',' + light.b + ') 0%, ' +
    'rgb(' + base.r + ',' + base.g + ',' + base.b + ') 55%, ' +
    'rgb(' + dark.r + ',' + dark.g + ',' + dark.b + ') 100%)'
  )
}

/** 判断名字是否为某类型下的子类，返回其父名；不是子类返回空串 */
export function parentOf(name, type) {
  const list = listByType(type || 'expense')
  for (const top of list) {
    if (top.subs) {
      for (const s of top.subs) {
        if (s.name === name) return top.name
      }
    }
  }
  return ''
}

/** 指定类型下是否存在同名一级分类 */
export function exists(name, type) {
  return !!findIn(listByType(type), name)
}
