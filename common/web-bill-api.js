/**
 * H5/Web 端账单 API 实现（localStorage 持久化）
 *
 * 与 UTS 插件 interface.uts 协议完全一致：JSON 字符串进出、
 * 金额支出为负收入为正、账单字段同名。页面通过条件编译选择实现：
 *   #ifdef APP-PLUS  → 原生插件
 *   #ifndef APP-PLUS → 本文件
 * 仅供 H5 预览与联调，App 端数据仍以原生 SQLite 为准（两端数据各自独立）。
 */

const KEY_BILLS = 'muyian_web_bills'
const KEY_SEQ = 'muyian_web_seq'

function pad2(n) {
  n = Number(n) || 0
  return n < 10 ? '0' + n : '' + n
}

function loadBills() {
  try {
    const raw = uni.getStorageSync(KEY_BILLS)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch (e) {
    return []
  }
}

function saveBills(list) {
  try {
    uni.setStorageSync(KEY_BILLS, JSON.stringify(list))
  } catch (e) {
    // ignore
  }
}

function nextId() {
  let seq = 0
  try {
    seq = Number(uni.getStorageSync(KEY_SEQ)) || 0
  } catch (e) {
    seq = 0
  }
  seq += 1
  try {
    uni.setStorageSync(KEY_SEQ, String(seq))
  } catch (e) {
    // ignore
  }
  return seq
}

function parseDate(dateStr) {
  const t = new Date()
  if (dateStr) {
    const m = String(dateStr).match(/(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/)
    if (m) {
      const mo = Number(m[2])
      const d = Number(m[3])
      if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
        return { y: Number(m[1]), mo, d }
      }
    }
  }
  return { y: t.getFullYear(), mo: t.getMonth() + 1, d: t.getDate() }
}

function amountOf(money) {
  const a = Number(String(money == null ? '' : money).trim())
  return isFinite(a) ? a : NaN
}

function buildBill(opts) {
  const { date, amount, isIncome, bankName, costType, orderRemark, reimb, starred, source } = opts
  const { y, mo, d } = parseDate(date)
  const now = new Date()
  return {
    id: nextId(),
    year: y,
    month: mo,
    day: d,
    clock: mo + '月' + d + '日 ' + pad2(now.getHours()) + ':' + pad2(now.getMinutes()),
    money: isIncome ? amount : -amount,
    bankName: bankName || '手动',
    costType: isIncome ? '收入' : (costType || '消费'),
    orderRemark: orderRemark || '',
    source: source || 'manual',
    sourcePkg: '',
    createdAt: Date.now(),
    reimb: reimb ? 1 : 0,
    starred: starred ? 1 : 0
  }
}

/* ---------- 生命周期 ---------- */

export function initBillListener() {}

/** H5 无无障碍概念，按已开启处理，避免首页出现误导性引导横幅 */
export function isAccessibilityEnabled() {
  return true
}

export function openAccessibilitySettings() {}

/** H5 无自恢复概念：无 WRITE_SECURE_SETTINGS，服务按已开启处理（与 isAccessibilityEnabled 口径一致） */
export function getSelfRepairState() {
  return '{"hasPermission":false,"serviceEnabled":true,"packageName":""}'
}

/** H5 恒为无操作成功，保证设置页与首页自愈逻辑两端行为一致 */
export function repairAccessibilityService() {
  return '{"success":true,"serviceEnabled":true}'
}

/** H5 无通知监听渠道，恒为未开启（仅设置页展示用） */
export function isNotificationListenerEnabled() {
  return false
}

export function openNotificationListenerSettings() {}

/** H5 由浏览器接管通知权限，这里恒按已开启处理，避免设置页误显示"请求权限" */
export function areNotificationsEnabled() {
  return true
}

/* ---------- 后台运行（H5 无对应端能力，仅保证接口对齐） ---------- */

export function isKeepAliveEnabled() {
  return false
}

export function setKeepAliveEnabled(enabled) {
  return Boolean(enabled)
}

/* ---------- 底部确认弹层（H5 无端能力，仅保证接口对齐） ---------- */

export function hasOverlayPermission() {
  return false
}

export function requestOverlayPermission() {
  return false
}

/** H5 没有原生弹层，主题色由 CSS 变量接管，这里只保持接口协议一致 */
export function setCardThemeColor(color) {
  return true
}

/** H5 不存在系统电池优化，按"不限制"处理，避免出现误导性提示 */
export function isIgnoringBatteryOptimizations() {
  return true
}

export function requestIgnoreBatteryOptimizations() {
  return '{"success":false,"granted":false,"error":"h5 unsupported"}'
}

export function openAppSettings() {}

/* ---------- 记账 ---------- */

export function saveManualBill(money, isIncome, bankName, costType, orderRemark) {
  return saveBillEx('', money, isIncome, bankName, costType, orderRemark, false, false)
}

export function saveBillEx(date, money, isIncome, bankName, costType, orderRemark, reimb, starred) {
  const amount = amountOf(money)
  if (!(amount > 0)) return '{"success":false,"error":"invalid money"}'
  const bill = buildBill({
    date, amount, isIncome, bankName, costType, orderRemark, reimb, starred,
    source: 'manual'
  })
  const bills = loadBills()
  bills.unshift(bill)
  saveBills(bills)
  return '{"success":true,"id":' + bill.id + '}'
}

export function updateBill(id, date, money, isIncome, bankName, costType, orderRemark, reimb, starred) {
  const amount = amountOf(money)
  if (!(amount > 0)) return '{"success":false,"error":"invalid money"}'
  const { y, mo, d } = parseDate(date)
  const bills = loadBills()
  const bill = bills.find(b => Number(b.id) === Number(id))
  if (!bill) return '{"success":false}'
  bill.year = y
  bill.month = mo
  bill.day = d
  bill.money = isIncome ? amount : -amount
  bill.bankName = bankName || bill.bankName
  bill.costType = isIncome ? '收入' : (costType || '消费')
  bill.orderRemark = orderRemark || ''
  bill.reimb = reimb ? 1 : 0
  bill.starred = starred ? 1 : 0
  saveBills(bills)
  return '{"success":true}'
}

/* ---------- 查询 ---------- */

export function getMonthBills(year, month) {
  const y = Number(year)
  const m = Number(month)
  const list = loadBills()
    .filter(b => Number(b.year) === y && Number(b.month) === m)
    .sort((a, b) => Number(b.id) - Number(a.id))
  return JSON.stringify(list)
}

export function getYearBills(year) {
  const y = Number(year)
  const list = loadBills()
    .filter(b => Number(b.year) === y)
    .sort((a, b) => Number(b.id) - Number(a.id))
  return JSON.stringify(list)
}

export function deleteBill(id) {
  const bills = loadBills()
  const idx = bills.findIndex(b => Number(b.id) === Number(id))
  if (idx < 0) return false
  bills.splice(idx, 1)
  saveBills(bills)
  return true
}

/* ---------- 测试注入 ---------- */

/** H5 没有原生确认弹层，无法完成"支付后弹层"闭环；仅保持接口协议一致 */
export function emitTestBill(text) {
  return '{"accepted":false,"reason":"h5 unsupported"}'
}
