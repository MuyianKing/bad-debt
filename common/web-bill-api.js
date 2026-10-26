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
const KEY_DRAFTS = 'muyian_web_drafts'
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

function loadDrafts() {
  try {
    const raw = uni.getStorageSync(KEY_DRAFTS)
    const map = raw ? JSON.parse(raw) : {}
    return map && typeof map === 'object' ? map : {}
  } catch (e) {
    return {}
  }
}

function saveDrafts(map) {
  try {
    uni.setStorageSync(KEY_DRAFTS, JSON.stringify(map))
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

/* ---------- 草稿 ---------- */

export function getPendingDrafts() {
  const map = loadDrafts()
  const list = Object.keys(map).map(k => map[k])
  list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
  return JSON.stringify(list)
}

export function getDraft(token) {
  const d = loadDrafts()[token]
  return d ? JSON.stringify(d) : ''
}

export function resolveDraft(token, money, isIncome, bankName, costType, orderRemark) {
  const amount = amountOf(money)
  if (!(amount > 0)) return '{"success":false,"error":"invalid money"}'
  const map = loadDrafts()
  const d = map[token]
  if (!d) return '{"success":false,"error":"draft not found"}'
  const bill = buildBill({
    date: d.year + '-' + pad2(d.month) + '-' + pad2(d.day),
    amount,
    isIncome,
    bankName: bankName || d.bankName,
    costType,
    orderRemark,
    reimb: false,
    starred: false,
    source: 'screen'
  })
  bill.createdAt = d.createdAt || bill.createdAt
  const bills = loadBills()
  bills.unshift(bill)
  saveBills(bills)
  delete map[token]
  saveDrafts(map)
  return '{"success":true,"id":' + bill.id + '}'
}

export function discardDraft(token) {
  const map = loadDrafts()
  if (!map[token]) return false
  delete map[token]
  saveDrafts(map)
  return true
}

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

/** 简易"支付成功"页解析：完成标记 + 金额，5 分钟内同金额去重 */
export function emitTestBill(text) {
  const content = String(text || '')
  if (!/(支付成功|付款成功|收款成功|交易成功)/.test(content)) {
    return '{"accepted":false,"reason":"not a payment page"}'
  }
  const m = content.match(/(?:￥|¥|金额)\s*([0-9]+(?:\.[0-9]{1,2})?)|([0-9]+(?:\.[0-9]{1,2})?)\s*元/)
  if (!m) {
    return '{"accepted":false,"reason":"not a payment page"}'
  }
  const amount = parseFloat(m[1] || m[2])
  // 与原生 parseBill 同规则：收入词与支出词同时出现按支出算，退款词优先
  const hasExpense = /(支出|付款|支付|消费|扣款|转出|提现|买单|代扣)/.test(content)
  const hasIncome = /(退款|退回|返还|收入|收款|到账|入账|转入)/.test(content)
  const isRefund = /(退款|退回|返还)/.test(content)
  const income = isRefund || (hasIncome && !hasExpense)
  const now = Date.now()
  const map = loadDrafts()
  // 草稿级去重：同金额 2 分钟窗口
  for (const k of Object.keys(map)) {
    const d = map[k]
    if (Math.abs(Math.abs(d.money) - amount) < 0.01 && now - (d.createdAt || 0) < 2 * 60 * 1000) {
      return '{"accepted":false,"reason":"duplicate capture"}'
    }
  }
  const t = new Date()
  const draft = {
    token: 'web-' + now + '-' + Math.floor(Math.random() * 100000),
    money: income ? amount : -amount,
    bankName: '微信',
    costType: income ? '收入' : '消费',
    orderRemark: '',
    clock: (t.getMonth() + 1) + '月' + t.getDate() + '日 ' + pad2(t.getHours()) + ':' + pad2(t.getMinutes()),
    year: t.getFullYear(),
    month: t.getMonth() + 1,
    day: t.getDate(),
    sourcePkg: 'com.tencent.mm',
    createdAt: now,
    notificationId: nextId()
  }
  map[draft.token] = draft
  saveDrafts(map)
  return JSON.stringify({ accepted: true, draft })
}
