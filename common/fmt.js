/**
 * 通用格式化与日期工具
 */

export function pad2(n) {
  n = Number(n) || 0
  return n < 10 ? '0' + n : '' + n
}

/** 金额：千分位 + 两位小数，如 5,508.00 / -395.00 */
export function fmtMoney(v) {
  const n = Number(v) || 0
  const neg = n < 0
  const abs = Math.abs(n).toFixed(2)
  const parts = abs.split('.')
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return (neg ? '-' : '') + parts.join('.')
}

/** 数字展示：去掉多余的 0，如 37.6 / 368 / 1,142 / -16.5 */
export function fmtNum(v) {
  const n = Number(v) || 0
  const abs = Math.abs(n)
  let s
  if (abs >= 1000) {
    s = fmtMoney(abs)
    if (s.indexOf('.') >= 0) s = s.replace(/\.?0+$/, '')
  } else {
    s = String(Math.round(abs * 100) / 100)
    if (s.indexOf('.') >= 0) s = s.replace(/\.?0+$/, '')
  }
  return (n < 0 ? '-' : '') + s
}

/** 大数刻度：1800 -> 1.8K */
export function fmtTick(v) {
  const n = Number(v) || 0
  if (Math.abs(n) >= 1000) {
    const k = Math.round(n / 100) / 10
    return (n < 0 ? '-' : '') + k + 'K'
  }
  return String(n)
}

export function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

export function daysInMonth(year, month) {
  return new Date(Number(year), Number(month), 0).getDate()
}

const WEEK_CN = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function weekdayCN(year, month, day) {
  const d = new Date(Number(year), Number(month) - 1, Number(day))
  return WEEK_CN[d.getDay()] || ''
}

/**
 * 日期列表头：今天/昨天/前天/周X
 * @param year, month, day 账单日期
 */
export function dayGroupTitle(year, month, day) {
  const t = new Date()
  const diffDays = Math.round(
    (new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime() -
      new Date(Number(year), Number(month) - 1, Number(day)).getTime()) / 86400000
  )
  let suffix
  if (diffDays === 0) suffix = '今天'
  else if (diffDays === 1) suffix = '昨天'
  else if (diffDays === 2) suffix = '前天'
  else suffix = weekdayCN(year, month, day)
  return Number(month) + '.' + Number(day) + ' ' + suffix
}

/**
 * 把账单列表按天分组（倒序）
 * @param {Array} bills 同月的账单数组
 * @param {number} year  账单年份
 * @param {number} month 账单月份
 */
export function groupByDay(bills, year, month) {
  const y = Number(year) || new Date().getFullYear()
  const m = Number(month) || new Date().getMonth() + 1
  const map = new Map()
  for (const b of bills) {
    const key = Number(b.day)
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(b)
  }
  const groups = []
  const days = Array.from(map.keys()).sort((a, b) => b - a)
  for (const d of days) {
    const list = map.get(d)
    let expense = 0
    let income = 0
    for (const b of list) {
      if (Number(b.money) < 0) expense += Math.abs(Number(b.money))
      else income += Number(b.money)
    }
    groups.push({
      day: d,
      title: dayGroupTitle(y, m, d),
      expense,
      income,
      bills: list
    })
  }
  return groups
}

/** picker 日期 change 事件值 -> {year, month, day} */
export function parsePickerDate(v) {
  if (!v) return null
  const parts = String(v).split('-')
  if (parts.length < 3) return null
  return { year: Number(parts[0]), month: Number(parts[1]), day: Number(parts[2]) }
}

/** 安全求值只含 + - 的金额表达式，返回数字（非法返回 NaN） */
export function evalExpr(expr) {
  const s = String(expr || '').trim()
  if (!s || /^[+\-.]+$/.test(s)) return NaN
  // 去掉首尾的运算符（保留首位的 - 号作负数）
  let body = s.replace(/[+]+$/, '')
  if (body === '' || /[+\-]$/.test(body.replace(/^-/, ''))) {
    // 结尾是运算符：去掉再算
    body = body.replace(/[+\-]+$/, '')
  }
  if (body === '' || body === '-') return NaN
  const tokens = body.match(/-?\d*\.?\d+|[+\-]/g)
  if (!tokens) return NaN
  let acc = 0
  let op = '+'
  for (const t of tokens) {
    if (t === '+' || t === '-') { op = t; continue }
    const v = parseFloat(t)
    if (!isFinite(v)) return NaN
    acc = op === '+' ? acc + v : acc - v
  }
  return Math.round(acc * 100) / 100
}
