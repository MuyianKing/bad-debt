/**
 * 分类数据：默认分类 + 本地存储 + 图标/颜色查询
 * 分类结构：
 *   { name, icon, subs?: [{ name, icon }] }
 * icon 为 common/icons.js 里的线性图标名（不再是 emoji）。
 * 账单里的 costType 存的是"叶子名"（选了子类存子类名，否则存一级名）；
 * 排行统计时通过 topNameOf() 归并到一级分类。
 */

const STORAGE_KEY = 'muyian_categories_v2' // v2：icon 从 emoji 改为线性图标名

export const PALETTE = [
  '#F5A623', '#F0663F', '#3EB575', '#A8D046', '#4A90F5', '#35A3E8',
  '#2BB7A0', '#7ED3B2', '#9B7DF7', '#F06292', '#64B5F6', '#8BC34A',
  '#FFB74D', '#4DB6AC', '#BA68C8', '#E57373', '#7986CB', '#FFD54F'
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
