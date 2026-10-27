<template>
  <view :class="['page', themeClass]">
    <wd-navbar
      title="导入账单"
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      :bordered="navSolid"
      :custom-style="navStyle"
      @click-left="goBack"
    ></wd-navbar>

    <!-- 第一步：选择文件 -->
    <template v-if="step === 'pick'">
      <view class="group-title">从钱迹导入</view>
      <view class="glass intro-card">
        <view class="intro-head">
          <view class="cell-ic ic-blue">
            <app-icon name="file-text" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="intro-title">导入钱迹历史账单</text>
        </view>
        <text class="intro-text">1. 在钱迹 App「我的 → 数据导出」里导出 Excel 账单，把文件保存到手机（微信/QQ 传给自己后「用其他应用打开 → 保存」即可）</text>
        <text class="intro-text">2. 点下方按钮，在系统文件选择器里选中它</text>
        <text class="intro-text">3. 确认分类映射后开始导入，可随时取消</text>
        <text class="intro-note">全程只在手机本地解析，不联网；按钱迹单号去重，重复导入不会重复入账。同一笔如果之前已手动或自动记过，单号不同不会自动合并，导入后可在账单里删掉多余的。</text>
      </view>
      <view
        :class="['pick-btn', picking ? 'pick-btn-disabled' : '']"
        @tap="onPick"
      >
        <app-icon v-if="!picking" name="download" color="#ffffff" :size="20"></app-icon>
        <text class="pick-btn-text">{{ picking ? '等待选择文件…' : '选择钱迹 Excel 文件' }}</text>
      </view>
      <text v-if="picking" class="pick-hint">已打开系统文件选择器，选完自动回来；取消或超时（90 秒）自动放弃</text>
    </template>

    <!-- 第二步：解析结果 + 分类映射 + 预览 -->
    <template v-if="step === 'review'">
      <view class="group-title">解析结果</view>
      <view class="glass info-card">
        <view class="file-row">
          <view class="cell-ic cell-ic-sm ic-blue">
            <app-icon name="file-text" color="#ffffff" :size="15"></app-icon>
          </view>
          <text class="file-name">{{ fileName }}</text>
        </view>
        <view class="stat-grid">
          <view class="stat-item">
            <text class="stat-num">{{ rows.length }}</text>
            <text class="stat-label">账单</text>
          </view>
          <view class="stat-item">
            <text class="stat-num stat-out">{{ summary.expense }}</text>
            <text class="stat-label">支出</text>
          </view>
          <view class="stat-item">
            <text class="stat-num stat-in">{{ summary.income }}</text>
            <text class="stat-label">收入</text>
          </view>
          <view class="stat-item">
            <text class="stat-num">{{ summary.skipped }}</text>
            <text class="stat-label">跳过</text>
          </view>
        </view>
        <text class="range-text">{{ summary.minDay }} ~ {{ summary.maxDay }}</text>
        <text v-if="summary.dupInDb > 0" class="dup-text">库里已有 {{ summary.dupInDb }} 条同单号账单，导入时自动跳过</text>
      </view>

      <view class="group-title">分类映射</view>
      <view class="glass map-card">
        <view v-for="m in mappings" :key="m.key" class="map-row">
          <view class="map-left">
            <text class="map-cat">{{ m.label }}</text>
            <text class="map-count">{{ m.count }} 条</text>
          </view>
          <wd-picker
            v-model="m.target"
            :columns="leafColumns"
            :title="'「' + m.label + '」记入'"
            :close-on-click-modal="true"
          >
            <view class="map-target">
              <text class="map-target-text">{{ m.target }}</text>
              <app-icon name="chevron-down" :color="theme.inkSub" :size="14"></app-icon>
            </view>
          </wd-picker>
        </view>
        <view v-if="summary.income > 0" class="map-note">
          <text class="map-note-text">另有收入 {{ summary.income }} 条，按本应用口径统一记为「收入」</text>
        </view>
      </view>

      <view class="group-title">预览（最近 {{ previews.length }} 条）</view>
      <view class="glass preview-card">
        <view v-for="(b, i) in previews" :key="i" class="preview-row">
          <text class="preview-day">{{ b.day }}</text>
          <text class="preview-cat">{{ b.cat }}</text>
          <text :class="['preview-amount', b.income ? 'preview-in' : 'preview-out']">{{ b.income ? '+' : '-' }}{{ b.amount }}</text>
        </view>
      </view>

      <view class="actions">
        <view class="ghost-btn" @tap="reset">
          <text class="ghost-btn-text">重选文件</text>
        </view>
        <view :class="['main-btn', importing ? 'main-btn-disabled' : '']" @tap="startImport">
          <text class="main-btn-text">{{ importing ? '导入中 ' + importedCount + '/' + rows.length : '开始导入 ' + rows.length + ' 条' }}</text>
        </view>
      </view>
    </template>

    <!-- 第三步：完成 -->
    <template v-if="step === 'done'">
      <view class="glass done-card">
        <view class="done-head">
          <view class="cell-ic ic-mint">
            <app-icon name="check" color="#ffffff" :size="18"></app-icon>
          </view>
          <text class="done-title">{{ result.failed ? '导入中断' : '导入完成' }}</text>
        </view>
        <text class="done-text">{{ doneText }}</text>
        <text v-if="result.failed" class="done-warn">中途出错，已导入的部分保留；可重选文件再次导入，已入账的会按单号自动跳过</text>
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
import { listByType } from '@/common/cats.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
import { parseSheet, buildMappings, buildBills as mapBills, orderNosOf } from '@/common/qianji-import.js'
// #ifdef APP-PLUS
import {
  openImportPicker, getImportPickState,
  readImportSheet, getImportSheetRows, countExistingOrders, commitImportBills
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  openImportPicker, getImportPickState,
  readImportSheet, getImportSheetRows, countExistingOrders, commitImportBills
} from '@/common/web-bill-api.js'
// #endif

const step = ref('pick') // pick | review | done
const picking = ref(false)
const importing = ref(false)
const importedCount = ref(0)
const fileName = ref('')
const summary = ref({})
const mappings = ref([])
const rows = ref([])
const result = ref({ inserted: 0, skipped: 0, failed: false })

// 顶栏滚动态：静止时透明，滚动后转毛玻璃（与设置页一致）
const navSolid = ref(false)
const navStyle = computed(() =>
  'transition: background-color 0.18s ease;' +
  (navSolid.value
    ? 'background-color: var(--glass-nav-bg);-webkit-backdrop-filter: blur(18px) saturate(1.6);backdrop-filter: blur(18px) saturate(1.6);'
    : 'background-color: transparent;')
)

// 支出分类的可选叶子（一级 + 子类），全部分映射行共用
const leafColumns = buildLeafColumns()
const previews = computed(() => {
  const map = {}
  for (const m of mappings.value) map[m.key] = m.target
  return rows.value.slice(0, 8).map(r => {
    const income = r.type === '收入'
    const t = String(r.time || '').slice(5, 10).replace('-', '/')
    return {
      day: t,
      income,
      amount: fmtAmount(r.amount),
      cat: income ? '收入' : (map[r.type + '|' + r.cat + '|' + r.sub] || r.cat || '其它')
    }
  })
})
const doneText = computed(() => {
  const r = result.value
  let s = '新增 ' + Number(r.inserted || 0) + ' 条账单'
  if (Number(r.skipped || 0) > 0) s += '，跳过重复 ' + Number(r.skipped) + ' 条'
  return s
})

let pollTimer = null
let pickStartAt = 0
const PICK_TIMEOUT_MS = 90 * 1000

function buildLeafColumns() {
  const options = []
  for (const top of listByType('expense')) {
    options.push({ label: top.name, value: top.name })
    for (const s of top.subs || []) options.push({ label: top.name + ' / ' + s.name, value: s.name })
  }
  return options
}

function fmtAmount(v) {
  const n = Number(v) || 0
  return String(Math.round(n * 100) / 100)
}

/* ---------- 选文件 ---------- */

function onPick() {
  if (picking.value || importing.value) return
  let resp = {}
  try { resp = JSON.parse(openImportPicker() || '{}') } catch (e) { resp = {} }
  if (!resp.success) {
    const err = String(resp.error || '')
    if (err === 'h5 unsupported') {
      uni.showToast({ title: '请在 App 内使用导入功能', icon: 'none' })
    } else if (!err || err.indexOf('start failed') === 0) {
      // 旧自定义调试基座没有本次新增的原生插件，调用会被兜底成失败
      uni.showToast({ title: '无法打开文件选择器：请重新制作自定义调试基座后再试', icon: 'none' })
    } else {
      uni.showToast({ title: '无法打开文件选择器：' + err, icon: 'none' })
    }
    return
  }
  picking.value = true
  pickStartAt = Date.now()
  pollTick()
}

function pollTick() {
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
    if (Date.now() - pickStartAt > PICK_TIMEOUT_MS) {
      picking.value = false
      uni.showToast({ title: '等待超时，请重新选择', icon: 'none' })
      return
    }
    pollTick()
  }, 500)
}

/* ---------- 解析 ---------- */

function handlePicked(path, name) {
  if (!path) {
    uni.showToast({ title: '文件路径无效，请重试', icon: 'none' })
    return
  }
  let sheet = {}
  try { sheet = JSON.parse(readImportSheet(path) || '{}') } catch (e) { sheet = {} }
  if (!sheet.ok) {
    uni.showToast({ title: sheet.error || '读取文件失败', icon: 'none' })
    return
  }
  const fetched = fetchAllSheetRows()
  if (!fetched.ok) {
    uni.showToast({ title: '读取表格失败，请重选文件', icon: 'none' })
    return
  }
  const parsed = parseSheet(sheet.header || [], fetched.rows)
  if (!parsed.ok) {
    uni.showToast({ title: parsed.error || '解析失败', icon: 'none' })
    return
  }
  if (!parsed.rows.length) {
    uni.showToast({ title: '文件里没有可导入的账单', icon: 'none' })
    return
  }
  let dupInDb = 0
  const orderNos = orderNosOf(parsed.rows)
  if (orderNos.length) {
    let dup = {}
    try { dup = JSON.parse(countExistingOrders(JSON.stringify(orderNos)) || '{}') } catch (e) { dup = {} }
    dupInDb = Number(dup.count) || 0
  }
  summary.value = {
    total: parsed.total,
    expense: parsed.expense,
    income: parsed.income,
    skipped: parsed.skipped,
    minDay: parsed.minDay,
    maxDay: parsed.maxDay,
    dupInDb
  }
  fileName.value = name || '钱迹账单.xlsx'
  mappings.value = buildMappings(parsed.cats || [], leafColumns)
  rows.value = parsed.rows
  step.value = 'review'
}

/** 分块取回原生缓存里的原始数据行（纯数组，体积比对象小） */
function fetchAllSheetRows() {
  const all = []
  const CHUNK = 500
  let off = 0
  let nativeTotal = 0
  while (true) {
    let resp = {}
    try { resp = JSON.parse(getImportSheetRows(String(off), String(CHUNK)) || '{}') } catch (e) { resp = {} }
    if (!Array.isArray(resp.rows)) return { ok: false, rows: [] }
    nativeTotal = Number(resp.total) || 0
    for (const r of resp.rows) all.push(r)
    off += CHUNK
    if (off >= nativeTotal || resp.rows.length === 0) break
  }
  return { ok: all.length === nativeTotal, rows: all }
}

/* ---------- 导入 ---------- */

async function startImport() {
  if (importing.value) return
  const bills = mapBills(rows.value, mappings.value)
  if (!bills.length) {
    uni.showToast({ title: '没有可导入的账单', icon: 'none' })
    return
  }
  importing.value = true
  importedCount.value = 0
  let inserted = 0
  let skipped = 0
  let failed = false
  const CHUNK = 200
  for (let i = 0; i < bills.length; i += CHUNK) {
    const part = bills.slice(i, i + CHUNK)
    let resp = {}
    try { resp = JSON.parse(commitImportBills(JSON.stringify(part)) || '{}') } catch (e) { resp = {} }
    if (!resp.success) {
      failed = true
      break
    }
    inserted += resp.inserted || 0
    skipped += resp.skipped || 0
    importedCount.value = Math.min(i + CHUNK, bills.length)
    // 让一帧出来，进度数字才能刷到界面上
    await new Promise(resolve => setTimeout(resolve, 30))
  }
  importing.value = false
  result.value = { inserted, skipped, failed }
  step.value = 'done'
  if (!failed) uni.showToast({ title: '已导入 ' + inserted + ' 条', icon: 'success' })
}

function reset() {
  step.value = 'pick'
  summary.value = {}
  mappings.value = []
  rows.value = []
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
/* 功能图标块：白图标压纯色块（新代码不用渐变） */
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
.intro-card {
  margin: 0 24rpx;
  border-radius: 20rpx;
  padding: 26rpx 28rpx;
  display: flex;
  flex-direction: column;
}
.intro-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 16rpx;
}
.intro-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--c-text);
}
.intro-text {
  font-size: 24rpx;
  color: var(--c-sub);
  line-height: 1.8;
}
.intro-note {
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid var(--c-line-soft);
  font-size: 22rpx;
  color: var(--c-dim);
  line-height: 1.7;
}
.pick-btn {
  margin: 32rpx 24rpx 0;
  height: 96rpx;
  border-radius: 24rpx;
  background-color: var(--c-primary-fill);
  box-shadow: var(--shadow-card-soft);
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
.pick-btn-text {
  margin-left: 10rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--c-ink-on-primary);
  letter-spacing: 1rpx;
}
.pick-btn-disabled {
  opacity: 0.55;
}
.pick-hint {
  display: block;
  margin: 18rpx 40rpx 0;
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
  font-size: 44rpx;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -1rpx;
}
.stat-out {
  color: var(--c-out);
}
.stat-in {
  color: var(--c-in);
}
.stat-label {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-dim);
}
.range-text {
  margin-top: 18rpx;
  font-size: 23rpx;
  color: var(--c-sub);
  text-align: center;
}
.dup-text {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--c-out-deep);
  text-align: center;
}
.map-card {
  margin: 0 24rpx;
  border-radius: 20rpx;
  padding: 4rpx 28rpx;
}
.map-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
}
.map-row + .map-row {
  border-top: 1rpx solid var(--c-line-soft);
}
.map-left {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.map-cat {
  font-size: 28rpx;
  color: var(--c-text);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.map-count {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-dim);
}
.map-target {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 12rpx 0 12rpx 24rpx;
  flex-shrink: 0;
}
.map-target-text {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--c-primary-deep);
  margin-right: 8rpx;
}
.map-note {
  padding: 18rpx 0 20rpx;
  border-top: 1rpx solid var(--c-line-soft);
}
.map-note-text {
  font-size: 22rpx;
  color: var(--c-dim);
  line-height: 1.6;
}
.preview-card {
  margin: 0 24rpx;
  border-radius: 20rpx;
  padding: 4rpx 28rpx;
}
.preview-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 20rpx 0;
}
.preview-row + .preview-row {
  border-top: 1rpx solid var(--c-line-soft);
}
.preview-day {
  width: 110rpx;
  font-size: 23rpx;
  color: var(--c-dim);
  flex-shrink: 0;
}
.preview-cat {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: var(--c-text);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.preview-amount {
  margin-left: 16rpx;
  font-size: 27rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.preview-out {
  color: var(--c-out);
}
.preview-in {
  color: var(--c-in);
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
.main-btn {
  flex: 1;
  margin-left: 20rpx;
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
