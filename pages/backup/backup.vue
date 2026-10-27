<template>
  <view :class="['page', themeClass]">
    <wd-navbar
      title="备份·恢复"
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      :bordered="navSolid"
      :custom-style="navStyle"
      @click-left="goBack"
    ></wd-navbar>

    <template v-if="step === 'idle'">
      <!-- 导出 -->
      <view class="group-title">导出备份</view>
      <view class="glass card">
        <view class="card-head">
          <view class="cell-ic ic-blue">
            <app-icon name="download" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="card-title">导出全部账单</text>
        </view>
        <text class="card-desc">生成一个 JSON 备份文件，包含全部账单和分类。保存位置由系统选择（下载目录、网盘等均可）。备注里的图片不会打包进备份。</text>
        <view :class="['main-btn', exporting || importing ? 'main-btn-disabled' : '']" @tap="onExport">
          <text class="main-btn-text">{{ exporting ? '保存中…' : '导出备份文件' }}</text>
        </view>
      </view>

      <!-- 恢复 -->
      <view class="group-title">恢复备份</view>
      <view class="glass card">
        <view class="card-head">
          <view class="cell-ic ic-mint">
            <app-icon name="rotate-ccw" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="card-title">从备份文件恢复</text>
        </view>
        <text class="card-desc">选择之前导出的 JSON 备份。合并恢复：已存在的账单（同 id 或同单号）自动跳过，不会覆盖现有数据；备份里缺失的分类会自动补全。</text>
        <view :class="['main-btn', picking || importing || exporting ? 'main-btn-disabled' : '']" @tap="onPickBackup">
          <text class="main-btn-text">{{ picking ? '等待选择文件…' : '选择备份文件' }}</text>
        </view>
        <text v-if="picking" class="pick-hint">已打开系统文件选择器，选完自动回来；取消或超时（90 秒）自动放弃</text>
      </view>

      <!-- 从钱迹导入 -->
      <view class="group-title">从钱迹导入</view>
      <view class="glass card">
        <view class="card-head">
          <view class="cell-ic ic-blue">
            <app-icon name="file-text" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="card-title">导入钱迹历史账单</text>
        </view>
        <text class="card-desc">选择钱迹 App 导出的 Excel，把历史账单批量记进来。只在手机本地解析，按单号去重，重复导入不会重复入账。</text>
        <view class="main-btn" @tap="goImport">
          <text class="main-btn-text">导入钱迹账单</text>
        </view>
      </view>
    </template>

    <!-- 恢复预览 -->
    <template v-if="step === 'preview'">
      <view class="group-title">备份内容</view>
      <view class="glass info-card">
        <view class="file-row">
          <view class="cell-ic cell-ic-sm ic-blue">
            <app-icon name="file-text" color="#ffffff" :size="15"></app-icon>
          </view>
          <text class="file-name">{{ backup.name || '孽账备份.json' }}</text>
        </view>
        <view class="stat-grid">
          <view class="stat-item">
            <text class="stat-num">{{ backupStats.total }}</text>
            <text class="stat-label">账单</text>
          </view>
          <view class="stat-item">
            <text class="stat-num stat-out">{{ backupStats.expense }}</text>
            <text class="stat-label">支出</text>
          </view>
          <view class="stat-item">
            <text class="stat-num stat-in">{{ backupStats.income }}</text>
            <text class="stat-label">收入</text>
          </view>
          <view class="stat-item">
            <text class="stat-num">{{ missingCatCount }}</text>
            <text class="stat-label">补全分类</text>
          </view>
        </view>
        <text class="range-text">{{ backupStats.minDay }} ~ {{ backupStats.maxDay }}</text>
        <text v-if="backup.exportedAt" class="range-text">导出时间：{{ formatTs(backup.exportedAt) }}</text>
        <text class="mode-note">恢复为合并模式：同 id 或同单号的账单会跳过，重复恢复不会产生重复数据</text>
      </view>

      <view class="actions">
        <view class="ghost-btn" @tap="reset">
          <text class="ghost-btn-text">重选文件</text>
        </view>
        <view :class="['main-btn', importing ? 'main-btn-disabled' : '']" @tap="startRestore">
          <text class="main-btn-text">{{ importing ? '恢复中 ' + restoredCount + '/' + backupStats.total : '开始恢复 ' + backupStats.total + ' 条' }}</text>
        </view>
      </view>
    </template>

    <!-- 完成 -->
    <template v-if="step === 'done'">
      <view class="glass done-card">
        <view class="done-head">
          <view class="cell-ic ic-mint">
            <app-icon name="check" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="done-title">{{ result.failed ? '恢复中断' : '恢复完成' }}</text>
        </view>
        <text class="done-text">{{ doneText }}</text>
        <text v-if="result.catAdded > 0" class="done-text">补全了 {{ result.catAdded }} 个分类</text>
        <text v-if="result.failed" class="done-warn">中途出错，已恢复的部分保留；可重新选择同一备份再次恢复，已存在的会按 id/单号自动跳过</text>
      </view>
      <view class="main-btn done-btn" @tap="goBack">
        <text class="main-btn-text">完成</text>
      </view>
    </template>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onHide, onPageScroll, onUnload } from '@dcloudio/uni-app'
import { getCategories, saveCategories } from '@/common/cats.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
// #ifdef APP-PLUS
import {
  getAllBills, openBackupExport, getBackupSaveState,
  openBackupPicker, getImportPickState, readBackupText, commitBackupBills
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  getAllBills, openBackupExport, getBackupSaveState,
  openBackupPicker, getImportPickState, readBackupText, commitBackupBills
} from '@/common/web-bill-api.js'
// #endif

const step = ref('idle') // idle | preview | done
const exporting = ref(false)
const picking = ref(false)
const importing = ref(false)
const restoredCount = ref(0)
const backup = ref({ bills: [], categories: {}, exportedAt: 0, name: '' })
const missingCatCount = ref(0)
const result = ref({ inserted: 0, skipped: 0, catAdded: 0, failed: false })

const navSolid = ref(false)
const navStyle = computed(() =>
  'transition: background-color 0.18s ease;' +
  (navSolid.value
    ? 'background-color: var(--glass-nav-bg);-webkit-backdrop-filter: blur(18px) saturate(1.6);backdrop-filter: blur(18px) saturate(1.6);'
    : 'background-color: transparent;')
)

const backupStats = computed(() => {
  const list = backup.value.bills || []
  let expense = 0
  let income = 0
  let minDay = ''
  let maxDay = ''
  for (const b of list) {
    if (Number(b.money) < 0) expense++
    else income++
    const day = b.year + '-' + pad2(b.month) + '-' + pad2(b.day)
    if (!minDay || day < minDay) minDay = day
    if (day > maxDay) maxDay = day
  }
  return { total: list.length, expense, income, minDay, maxDay }
})

const doneText = computed(() => {
  const r = result.value
  let s = '新增 ' + Number(r.inserted || 0) + ' 条账单'
  if (Number(r.skipped || 0) > 0) s += '，跳过 ' + Number(r.skipped) + ' 条（已存在或无效）'
  return s
})

let pollTimer = null
let pollStartAt = 0

function pad2(n) {
  n = Number(n) || 0
  return n < 10 ? '0' + n : '' + n
}

function formatTs(ts) {
  const d = new Date(Number(ts) || 0)
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
}

function stamp() {
  const d = new Date()
  return '' + d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate()) + '_' + pad2(d.getHours()) + pad2(d.getMinutes())
}

/* ---------- 导出 ---------- */

function onExport() {
  if (exporting.value || importing.value) return
  const all = []
  let total = 0
  let off = 0
  const CHUNK = 1000
  while (true) {
    let resp = {}
    try { resp = JSON.parse(getAllBills(String(off), String(CHUNK)) || '{}') } catch (e) { resp = {} }
    if (!Array.isArray(resp.rows)) {
      uni.showToast({ title: '读取账单失败，请重试', icon: 'none' })
      return
    }
    total = Number(resp.total) || 0
    for (const b of resp.rows) all.push(b)
    off += CHUNK
    if (off >= total || resp.rows.length === 0) break
  }
  if (!all.length) {
    uni.showToast({ title: '还没有账单可以导出', icon: 'none' })
    return
  }
  const payload = {
    app: 'muyian',
    kind: 'backup',
    version: 1,
    exportedAt: Date.now(),
    categories: getCategories(),
    bills: all
  }
  const name = '孽账备份_' + stamp() + '.json'
  let resp = {}
  try { resp = JSON.parse(openBackupExport(JSON.stringify(payload), name) || '{}') } catch (e) { resp = {} }
  if (!resp.success) {
    const err = String(resp.error || '')
    if (err === 'h5 unsupported') {
      uni.showToast({ title: '请在 App 内使用备份功能', icon: 'none' })
    } else if (!err || err.indexOf('start failed') === 0) {
      uni.showToast({ title: '无法打开保存界面：请重新制作自定义调试基座后再试', icon: 'none' })
    } else {
      uni.showToast({ title: '导出失败：' + err, icon: 'none' })
    }
    return
  }
  exporting.value = true
  pollStartAt = Date.now()
  pollSaveState()
}

function pollSaveState() {
  pollTimer = setTimeout(() => {
    let st = {}
    try { st = JSON.parse(getBackupSaveState() || '{}') } catch (e) { st = {} }
    if (st.state === 'saved') {
      exporting.value = false
      uni.showToast({ title: '已导出：' + (st.name || '备份文件'), icon: 'success' })
      return
    }
    if (st.state === 'cancelled') {
      exporting.value = false
      uni.showToast({ title: '已取消导出', icon: 'none' })
      return
    }
    if (st.state === 'error') {
      exporting.value = false
      uni.showToast({ title: '导出失败：' + (st.error || '未知原因'), icon: 'none' })
      return
    }
    if (Date.now() - pollStartAt > 120 * 1000) {
      exporting.value = false
      uni.showToast({ title: '保存超时，请重试', icon: 'none' })
      return
    }
    pollSaveState()
  }, 400)
}

/* ---------- 选择备份文件 ---------- */

function onPickBackup() {
  if (picking.value || importing.value || exporting.value) return
  let resp = {}
  try { resp = JSON.parse(openBackupPicker() || '{}') } catch (e) { resp = {} }
  if (!resp.success) {
    const err = String(resp.error || '')
    if (err === 'h5 unsupported') {
      uni.showToast({ title: '请在 App 内使用备份功能', icon: 'none' })
    } else if (!err || err === 'picking' || err.indexOf('start failed') === 0) {
      uni.showToast({ title: '无法打开文件选择器：请重新制作自定义调试基座后再试', icon: 'none' })
    } else {
      uni.showToast({ title: '无法打开文件选择器：' + err, icon: 'none' })
    }
    return
  }
  picking.value = true
  pollStartAt = Date.now()
  pollPickState()
}

function pollPickState() {
  pollTimer = setTimeout(() => {
    let st = {}
    try { st = JSON.parse(getImportPickState() || '{}') } catch (e) { st = {} }
    if (st.state === 'picked') {
      picking.value = false
      handlePicked(st.path || '', st.name || '')
      return
    }
    if (st.state === 'cancelled') {
      picking.value = false
      uni.showToast({ title: '已取消选择', icon: 'none' })
      return
    }
    if (st.state === 'error') {
      picking.value = false
      uni.showToast({ title: '选择文件失败：' + (st.error || '未知原因'), icon: 'none' })
      return
    }
    if (Date.now() - pollStartAt > 90 * 1000) {
      picking.value = false
      uni.showToast({ title: '等待超时，请重新选择', icon: 'none' })
      return
    }
    pollPickState()
  }, 500)
}

function handlePicked(path, name) {
  let r = {}
  try { r = JSON.parse(readBackupText(path) || '{}') } catch (e) { r = {} }
  if (!r.ok) {
    uni.showToast({ title: r.error || '读取备份失败', icon: 'none' })
    return
  }
  let data = {}
  try { data = JSON.parse(r.text || '{}') } catch (e) { data = {} }
  if (!data || data.app !== 'muyian' || !Array.isArray(data.bills)) {
    uni.showToast({ title: '不是孽账的备份文件', icon: 'none' })
    return
  }
  if (Number(data.version) !== 1) {
    uni.showToast({ title: '备份版本不支持：' + data.version, icon: 'none' })
    return
  }
  const bills = sanitizeBills(data.bills)
  if (!bills.length) {
    uni.showToast({ title: '备份里没有可恢复的账单', icon: 'none' })
    return
  }
  backup.value = {
    bills,
    categories: data.categories || {},
    exportedAt: Number(data.exportedAt) || 0,
    name: name || ''
  }
  missingCatCount.value = collectMissingCategories(backup.value.categories).length
  step.value = 'preview'
}

function sanitizeBills(list) {
  const out = []
  for (const b of list) {
    if (!b || typeof b !== 'object') continue
    const money = Number(b.money)
    if (!isFinite(money) || money === 0) continue
    const y = Number(b.year)
    const mo = Number(b.month)
    const d = Number(b.day)
    if (!(y >= 1970 && mo >= 1 && mo <= 12 && d >= 1 && d <= 31)) continue
    out.push({
      id: Number(b.id) || 0,
      year: y,
      month: mo,
      day: d,
      clock: String(b.clock || ''),
      money,
      bankName: String(b.bankName || ''),
      costType: String(b.costType || (money > 0 ? '收入' : '消费')),
      orderRemark: String(b.orderRemark || ''),
      source: String(b.source || 'manual'),
      sourcePkg: String(b.sourcePkg || ''),
      createdAt: Number(b.createdAt) || 0,
      reimb: Number(b.reimb) || 0,
      starred: Number(b.starred) || 0,
      orderNo: String(b.orderNo || '')
    })
  }
  return out
}

/* ---------- 分类补全 ---------- */

/** 备份里有、当前分类表没有的条目（只补不删不改） */
function collectMissingCategories(cats) {
  const cur = getCategories()
  const missing = []
  for (const type of ['expense', 'income']) {
    const src = (cats && cats[type]) || []
    const dst = cur[type] || []
    for (const top of src) {
      if (!top || !top.name) continue
      let hit = null
      for (const t of dst) {
        if (t.name === top.name) { hit = t; break }
      }
      if (!hit) {
        missing.push({ type, topName: top.name, topIcon: top.icon || 'grid', subName: '', subIcon: '' })
        for (const sub of top.subs || []) {
          if (sub && sub.name) {
            missing.push({ type, topName: top.name, topIcon: top.icon || 'grid', subName: sub.name, subIcon: sub.icon || 'grid' })
          }
        }
        continue
      }
      for (const sub of top.subs || []) {
        if (!sub || !sub.name) continue
        const has = (hit.subs || []).some(s => s.name === sub.name)
        if (!has) {
          missing.push({ type, topName: top.name, topIcon: top.icon || 'grid', subName: sub.name, subIcon: sub.icon || 'grid' })
        }
      }
    }
  }
  return missing
}

function applyMissingCategories(cats) {
  const missing = collectMissingCategories(cats)
  if (!missing.length) return 0
  const cur = getCategories()
  for (const m of missing) {
    const list = cur[m.type] || (cur[m.type] = [])
    let top = null
    for (const t of list) {
      if (t.name === m.topName) { top = t; break }
    }
    if (!top) {
      top = { name: m.topName, icon: m.topIcon }
      list.push(top)
    }
    if (m.subName) {
      if (!top.subs) top.subs = []
      const has = top.subs.some(s => s.name === m.subName)
      if (!has) top.subs.push({ name: m.subName, icon: m.subIcon })
    }
  }
  saveCategories(cur)
  return missing.length
}

/* ---------- 恢复 ---------- */

async function startRestore() {
  if (importing.value || !backup.value.bills.length) return
  importing.value = true
  restoredCount.value = 0
  const catAdded = applyMissingCategories(backup.value.categories || {})
  let inserted = 0
  let skipped = 0
  let failed = false
  const list = backup.value.bills
  const CHUNK = 400
  for (let i = 0; i < list.length; i += CHUNK) {
    const part = list.slice(i, i + CHUNK)
    let resp = {}
    try { resp = JSON.parse(commitBackupBills(JSON.stringify(part)) || '{}') } catch (e) { resp = {} }
    if (!resp.success) {
      failed = true
      break
    }
    inserted += resp.inserted || 0
    skipped += resp.skipped || 0
    restoredCount.value = Math.min(i + CHUNK, list.length)
    // 让出一帧，进度数字才能刷到界面上
    await new Promise(resolve => setTimeout(resolve, 30))
  }
  importing.value = false
  result.value = { inserted, skipped, catAdded, failed }
  step.value = 'done'
  if (!failed) uni.showToast({ title: '已恢复 ' + inserted + ' 条', icon: 'success' })
}

function reset() {
  step.value = 'idle'
  backup.value = { bills: [], categories: {}, exportedAt: 0, name: '' }
  missingCatCount.value = 0
}

/** 钱迹历史账单导入入口 */
function goImport() {
  uni.navigateTo({ url: '/pages/import/import' })
}

function goBack() {
  uni.navigateBack()
}

/** 深色主题黑底需要白色状态栏图标（与设置页一致） */
function applyStatusBarStyle() {
  // #ifdef APP-PLUS
  try {
    plus.navigator.setStatusBarStyle(theme.value.dark ? 'light' : 'dark')
  } catch (e) {}
  // #endif
}

onPageScroll((e) => {
  const top = Number(e.scrollTop) || 0
  if (top > 8 && !navSolid.value) navSolid.value = true
  else if (top <= 8 && navSolid.value) navSolid.value = false
})

onShow(() => {
  refreshTheme()
  applyStatusBarStyle()
})

onHide(() => {
  // #ifdef APP-PLUS
  try { plus.navigator.setStatusBarStyle('dark') } catch (e) {}
  // #endif
})

onUnload(() => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
})
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background-color: var(--c-bg);
}
.group-title {
  display: flex;
  flex-direction: row;
  align-items: center;
  font-size: 25rpx;
  color: var(--c-primary-deep);
  font-weight: 600;
  margin: 32rpx 32rpx 12rpx;
}
.group-title::before {
  content: '';
  width: 6rpx;
  height: 22rpx;
  border-radius: 3rpx;
  margin-right: 12rpx;
  background-color: var(--c-primary);
}
.cell-ic {
  width: 48rpx;
  height: 48rpx;
  border-radius: 15rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14rpx;
  flex-shrink: 0;
  box-shadow: 0 4rpx 10rpx rgba(60, 50, 20, 0.14);
}
.cell-ic-sm {
  width: 40rpx;
  height: 40rpx;
  border-radius: 12rpx;
}
.ic-blue { background-color: var(--c-blue); }
.ic-mint { background-color: var(--c-mint); }
.card {
  margin: 0 24rpx;
  border-radius: 20rpx;
  padding: 26rpx 28rpx;
  display: flex;
  flex-direction: column;
}
.card-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 14rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--c-text);
}
.card-desc {
  font-size: 24rpx;
  color: var(--c-sub);
  line-height: 1.8;
  margin-bottom: 22rpx;
}
.main-btn {
  height: 92rpx;
  border-radius: 24rpx;
  background-color: var(--c-primary-fill);
  box-shadow: var(--shadow-card-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.main-btn-text {
  font-size: 29rpx;
  font-weight: 600;
  color: var(--c-ink-on-primary);
  letter-spacing: 1rpx;
}
.main-btn-disabled {
  opacity: 0.55;
}
.pick-hint {
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--c-dim);
  text-align: center;
  line-height: 1.6;
}
.info-card {
  margin: 0 24rpx;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  display: flex;
  flex-direction: column;
}
.file-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 20rpx;
}
.file-name {
  font-size: 25rpx;
  color: var(--c-sub);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.stat-grid {
  display: flex;
  flex-direction: row;
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-num {
  font-size: 42rpx;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -1rpx;
}
.stat-out { color: var(--c-out); }
.stat-in { color: var(--c-in); }
.stat-label {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-dim);
}
.range-text {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: var(--c-sub);
  text-align: center;
}
.mode-note {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid var(--c-line-soft);
  font-size: 22rpx;
  color: var(--c-dim);
  line-height: 1.6;
  text-align: center;
}
.actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 36rpx 24rpx 0;
}
.ghost-btn {
  width: 200rpx;
  height: 92rpx;
  border-radius: 24rpx;
  background-color: var(--c-fill);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ghost-btn-text {
  font-size: 27rpx;
  color: var(--c-sub);
}
.actions .main-btn {
  flex: 1;
  margin-left: 20rpx;
}
.done-card {
  margin: 32rpx 24rpx 0;
  border-radius: 20rpx;
  padding: 26rpx 28rpx;
  display: flex;
  flex-direction: column;
}
.done-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 16rpx;
}
.done-title {
  font-size: 31rpx;
  font-weight: 700;
  color: var(--c-text);
}
.done-text {
  font-size: 26rpx;
  color: var(--c-sub);
  line-height: 1.7;
}
.done-warn {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: var(--c-out-deep);
  line-height: 1.6;
}
.done-btn {
  margin: 36rpx 24rpx 0;
}
.bottom-safe {
  height: calc(60rpx + env(safe-area-inset-bottom));
}
</style>
