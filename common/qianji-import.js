/**
 * 钱迹 Excel 账单的纯数据处理（不依赖 uni / 原生插件）。
 *
 * 原生插件只负责把 xlsx 读成「表头 + 原始单元格行」，本模块负责账单语义：
 * 时间/类型/金额校验、汇总统计、分类计数、钱迹分类 → 本应用叶子名映射、
 * 入库账单拼装。这样数据逻辑可以直接在 H5 / Node 里验证，不必改原生、重做基座。
 */

/** 单次导入上限，与原生读表缓存保持一致 */
export const MAX_ROWS = 20000

const TIME_RE = /(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?/

function pad2(n) {
  n = Number(n) || 0
  return n < 10 ? '0' + n : '' + n
}

function cell(row, index) {
  if (index < 0 || !row || index >= row.length) return ''
  return String(row[index] == null ? '' : row[index]).trim()
}

/** 表头按中文名定位列；找不到返回 -1 */
export function columnIndex(header) {
  const idx = { time: -1, type: -1, amount: -1, cat: -1, sub: -1, remark: -1, id: -1 }
  const names = {
    时间: 'time',
    类型: 'type',
    金额: 'amount',
    分类: 'cat',
    二级分类: 'sub',
    备注: 'remark',
    ID: 'id'
  }
  const list = Array.isArray(header) ? header : []
  for (let i = 0; i < list.length; i++) {
    const key = names[String(list[i] == null ? '' : list[i]).trim()]
    if (key && idx[key] < 0) idx[key] = i
  }
  return idx
}

/**
 * 解析原始表格（原生已经把 xlsx 解压成二维文本）。
 * @param {string[]} header 表头
 * @param {string[][]} rawRows 数据行，列序与表头一致
 * @returns {{ok:true,total:number,expense:number,income:number,skipped:number,minDay:string,maxDay:string,cats:Array,rows:Array}|{ok:false,error:string}}
 */
export function parseSheet(header, rawRows) {
  const cols = columnIndex(header)
  if (cols.time < 0 || cols.type < 0 || cols.amount < 0) {
    return { ok: false, error: '表头缺少 时间/类型/金额 列，请确认是钱迹导出的 Excel' }
  }
  let total = 0
  let skipped = 0
  let expense = 0
  let income = 0
  let minDay = ''
  let maxDay = ''
  const catCount = new Map()
  const rows = []
  const list = Array.isArray(rawRows) ? rawRows : []
  for (let i = 0; i < list.length; i++) {
    const raw = list[i] || []
    const time = cell(raw, cols.time)
    const type = cell(raw, cols.type)
    const amountRaw = cell(raw, cols.amount)
    if (!time && !type && !amountRaw) continue
    total += 1
    const m = TIME_RE.exec(time)
    const amount = Number(amountRaw)
    if (!m || (type !== '支出' && type !== '收入') || !isFinite(amount) || amount <= 0) {
      skipped += 1
      continue
    }
    const y = Number(m[1])
    const mo = Number(m[2])
    const d = Number(m[3])
    const day = pad2(y) + '-' + pad2(mo) + '-' + pad2(d)
    if (!minDay || day < minDay) minDay = day
    if (day > maxDay) maxDay = day
    if (type === '支出') expense += 1
    else income += 1
    const cat = cell(raw, cols.cat)
    const sub = cell(raw, cols.sub)
    const key = type + '|' + cat + '|' + sub
    catCount.set(key, (catCount.get(key) || 0) + 1)
    rows.push({
      time,
      type,
      cat,
      sub,
      amount: amountRaw,
      remark: cell(raw, cols.remark),
      id: cell(raw, cols.id),
      day,
      hh: m[4],
      mm: m[5]
    })
    if (rows.length >= MAX_ROWS) break
  }
  const cats = []
  catCount.forEach((count, key) => {
    const parts = key.split('|')
    cats.push({ type: parts[0], cat: parts[1] || '', sub: parts[2] || '', count })
  })
  return { ok: true, total, expense, income, skipped, minDay, maxDay, cats, rows }
}

/** 钱迹分类 → 本应用分类叶子名的默认映射：子类同名优先，其次一级同名，兜底「其它」 */
export function defaultLeaf(cat, sub, leafColumns) {
  const values = new Set((leafColumns || []).map(o => o.value))
  if (sub && values.has(sub)) return sub
  if (cat && values.has(cat)) return cat
  return '其它'
}

/** 生成分类映射行（只处理支出；收入统一记「收入」，不参与映射） */
export function buildMappings(cats, leafColumns) {
  const list = leafColumns || []
  const values = new Set(list.map(o => o.value))
  const arr = []
  for (const c of cats || []) {
    if (c.type !== '支出') continue
    let leaf = defaultLeaf(c.cat, c.sub, list)
    if (!values.has(leaf)) leaf = list.length ? list[0].value : '其它'
    arr.push({
      key: c.type + '|' + c.cat + '|' + c.sub,
      label: c.sub ? c.cat + ' · ' + c.sub : (c.cat || '未分类'),
      count: c.count || 0,
      target: leaf
    })
  }
  arr.sort((a, b) => b.count - a.count)
  return arr
}

/** "YYYY-MM-DD HH:mm[:ss]" / "YYYY/MM/DD ..." → { y, mo, d, hh, mm } */
export function parseTime(s) {
  const m = TIME_RE.exec(String(s || ''))
  if (!m) return null
  const y = Number(m[1])
  const mo = Number(m[2])
  const d = Number(m[3])
  if (mo < 1 || mo > 12 || d < 1 || d > 31) return null
  return { y, mo, d, hh: m[4], mm: m[5] }
}

/** 有效行 → 入库账单（支出为负、收入为正；orderNo 存钱迹账单 ID） */
export function buildBills(rows, mappings) {
  const map = {}
  for (const m of mappings || []) map[m.key] = m.target
  const bills = []
  for (const r of rows || []) {
    const income = r.type === '收入'
    const t = parseTime(r.time)
    if (!t) continue
    const amount = Number(r.amount)
    if (!(amount > 0)) continue
    bills.push({
      year: t.y,
      month: t.mo,
      day: t.d,
      clock: t.mo + '月' + t.d + '日 ' + t.hh + ':' + t.mm,
      money: income ? amount : -amount,
      bankName: '钱迹',
      costType: income ? '收入' : (map[r.type + '|' + r.cat + '|' + r.sub] || '其它'),
      orderRemark: r.remark || '',
      reimb: 0,
      starred: 0,
      createdAt: new Date(t.y, t.mo - 1, t.d, Number(t.hh) || 0, Number(t.mm) || 0).getTime(),
      orderNo: r.id || ''
    })
  }
  return bills
}

/** 有效行里的钱迹单号，供原生统计库内重复条数（保留原始顺序与重复项） */
export function orderNosOf(rows) {
  const out = []
  for (const r of rows || []) {
    const id = String(r.id || '').trim()
    if (id) out.push(id)
  }
  return out
}
