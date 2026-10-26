<template>
  <view class="page">
    <view class="bg-blobs"><view class="blob b1"></view><view class="blob b2"></view></view>
    <wd-navbar custom-class="glass-nav" :left-arrow="true" :fixed="true" :placeholder="true" :safe-area-inset-top="true" @click-left="goBack">
      <template #title>
        <wd-segmented :value="rangeLabel" :options="['月', '年']" @change="onRangeChange"></wd-segmented>
      </template>
      <template #right>
        <view class="nav-right-btn" @tap="goRank">
          <app-icon name="filter" color="#1d2129" :size="19"></app-icon>
        </view>
      </template>
    </wd-navbar>

    <!-- 期间切换 -->
    <view class="period-row">
      <view class="period-btn" @tap="shiftPeriod(-1)">
        <app-icon name="chevron-left" color="#5b6170" :size="18"></app-icon>
      </view>
      <picker mode="date" :fields="range === 'month' ? 'month' : 'year'" :value="pickerValue" @change="onPeriodChange">
        <view class="period-value glass">
          <text class="period-text">{{ periodText }}</text>
          <app-icon name="chevron-down" color="#5b6170" :size="14"></app-icon>
        </view>
      </picker>
      <view class="period-btn" @tap="shiftPeriod(1)">
        <app-icon name="chevron-right" color="#5b6170" :size="18"></app-icon>
      </view>
    </view>

    <!-- 收支总览 -->
    <view class="card glass">
      <text class="card-big-title">收支总览</text>
      <view class="overview">
        <view class="ov-item">
          <text class="ov-label">支出</text>
          <text class="ov-num ov-out">{{ fmtMoney(expenseTotal) }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">收入</text>
          <text class="ov-num ov-in">{{ fmtMoney(incomeTotal) }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">结余</text>
          <text class="ov-num">{{ fmtMoney(incomeTotal - expenseTotal) }}</text>
        </view>
        <view class="ov-item">
          <text class="ov-label">{{ range === 'month' ? '日均支出' : '月均支出' }}</text>
          <text class="ov-num">{{ fmtMoney(avgExpense) }}</text>
        </view>
      </view>
    </view>

    <!-- 每日/每月统计 -->
    <view class="card glass">
      <view class="card-head">
        <text class="card-title">{{ range === 'month' ? '每日统计' : '每月统计' }}</text>
        <view class="card-head-icons">
          <view class="mini-toggle">
            <text
              :class="['mini-toggle-item', series === 'expense' ? 'mini-toggle-active' : '']"
              @tap="series = 'expense'"
            >支出</text>
            <text
              :class="['mini-toggle-item', series === 'income' ? 'mini-toggle-active' : '']"
              @tap="series = 'income'"
            >收入</text>
          </view>
          <view v-if="range === 'month'" class="head-btn" @tap="goReport">
            <app-icon name="maximize" color="#8a90a0" :size="18"></app-icon>
          </view>
        </view>
      </view>
      <view class="bar-chart" v-if="maxBarValue > 0">
        <view class="bar-y">
          <text v-for="(t, i) in yTicks" :key="i" class="bar-y-label">{{ fmtTick(t) }}</text>
        </view>
        <view class="bar-body">
          <view
            v-for="(b, i) in bars"
            :key="i"
            class="bar-col"
            @tap="onBarTap(b)"
          >
            <view
              class="bar-bar"
              :class="series === 'expense' ? 'bar-out' : 'bar-in'"
              :style="{ height: b.height + 'rpx' }"
            ></view>
          </view>
        </view>
      </view>
      <view v-else class="chart-empty">
        <view class="empty-ic-sm">
          <app-icon name="bar-chart" color="#dfe2ec" :size="40" :stroke-width="1.5"></app-icon>
        </view>
        <text class="chart-empty-text">该期间暂无{{ series === 'expense' ? '支出' : '收入' }}</text>
      </view>
      <view class="bar-x" v-if="maxBarValue > 0">
        <text class="bar-x-label">{{ xLabels[0] }}</text>
        <text class="bar-x-label bar-x-mid">{{ xLabels[1] }}</text>
        <text class="bar-x-label">{{ xLabels[2] }}</text>
      </view>
    </view>

    <!-- 分类报表 -->
    <view class="card glass">
      <view class="card-head">
        <text class="card-title">分类报表</text>
        <view class="mini-toggle">
          <text
            :class="['mini-toggle-item', donutKind === 'expense' ? 'mini-toggle-active' : '']"
            @tap="donutKind = 'expense'"
          >支出</text>
          <text
            :class="['mini-toggle-item', donutKind === 'income' ? 'mini-toggle-active' : '']"
            @tap="donutKind = 'income'"
          >收入</text>
        </view>
      </view>
      <view class="donut-wrap" v-if="donutSegments.length">
        <view class="donut-box" :style="{ width: donutSize + 'px', height: donutSize + 'px' }">
          <canvas
            canvas-id="donut"
            id="donut"
            class="donut-canvas"
            :style="{ width: donutSize + 'px', height: donutSize + 'px' }"
          ></canvas>
          <view class="donut-center">
            <text class="donut-center-text">{{ donutKind === 'expense' ? '支出' : '收入' }}</text>
            <text class="donut-center-sub">占比分布</text>
          </view>
        </view>
        <view class="legend">
          <view v-for="s in donutSegments" :key="s.name" class="legend-item">
            <view class="legend-dot" :style="{ backgroundColor: s.color }"></view>
            <text class="legend-name">{{ s.name }}</text>
            <text class="legend-pct">{{ s.pct }}</text>
          </view>
        </view>
      </view>
      <view v-else class="chart-empty">
        <view class="empty-ic-sm">
          <app-icon name="pie-chart" color="#dfe2ec" :size="40" :stroke-width="1.5"></app-icon>
        </view>
        <text class="chart-empty-text">该期间暂无{{ donutKind === 'expense' ? '支出' : '收入' }}</text>
      </view>
      <view class="card-foot" @tap="goRank">
        <text class="card-foot-text">查看分类排行</text>
        <app-icon name="chevron-right" color="#8a90a0" :size="14"></app-icon>
      </view>
    </view>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref, computed, watch, nextTick, getCurrentInstance } from 'vue'
import { onReady, onShow } from '@dcloudio/uni-app'
import { fmtMoney, fmtTick, pad2, daysInMonth } from '@/common/fmt.js'
import { topNameOf, colorOf } from '@/common/cats.js'
// #ifdef APP-PLUS
import { getMonthBills, getYearBills } from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import { getMonthBills, getYearBills } from '@/common/web-bill-api.js'
// #endif

const now = new Date()
const range = ref('month')
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const bills = ref([])
const series = ref('expense')
const donutKind = ref('expense')
const donutSize = Math.round(uni.upx2px(300))

const rangeLabel = computed(() => (range.value === 'month' ? '月' : '年'))
const pickerValue = computed(() => (range.value === 'month' ? year.value + '-' + pad2(month.value) : String(year.value)))
const periodText = computed(() => (range.value === 'month' ? year.value + '-' + pad2(month.value) : String(year.value)))

const expenseBills = computed(() => bills.value.filter(b => Number(b.money) < 0))
const incomeBills = computed(() => bills.value.filter(b => Number(b.money) > 0))
const expenseTotal = computed(() => expenseBills.value.reduce((s, b) => s + Math.abs(Number(b.money)), 0))
const incomeTotal = computed(() => incomeBills.value.reduce((s, b) => s + Number(b.money), 0))

const avgExpense = computed(() => {
  if (range.value === 'month') {
    const t = new Date()
    const isCurrent = t.getFullYear() === year.value && t.getMonth() + 1 === month.value
    const elapsed = isCurrent ? t.getDate() : daysInMonth(year.value, month.value)
    return expenseTotal.value / Math.max(1, elapsed)
  }
  const t = new Date()
  const elapsed = t.getFullYear() === year.value ? t.getMonth() + 1 : 12
  return expenseTotal.value / Math.max(1, elapsed)
})

/* ---------- 柱状图 ---------- */
const bars = computed(() => {
  const out = []
  const list = series.value === 'expense' ? expenseBills.value : incomeBills.value
  let slots = 0
  if (range.value === 'month') {
    slots = daysInMonth(year.value, month.value)
  } else {
    slots = 12
  }
  const sums = new Array(slots).fill(0)
  for (const b of list) {
    const idx = range.value === 'month' ? Number(b.day) - 1 : Number(b.month) - 1
    if (idx >= 0 && idx < slots) sums[idx] += Math.abs(Number(b.money))
  }
  let max = 0
  for (const v of sums) if (v > max) max = v
  for (let i = 0; i < slots; i++) {
    out.push({
      label: range.value === 'month' ? i + 1 : i + 1 + '月',
      value: sums[i],
      height: sums[i] > 0 ? Math.max(12, Math.round(sums[i] / max * 290)) : 0
    })
  }
  return out
})

const maxBarValue = computed(() => bars.value.reduce((m, b) => Math.max(m, b.value), 0))

const yTicks = computed(() => {
  const max = maxBarValue.value
  if (max <= 0) return [0]
  const unit = Math.pow(10, Math.max(0, Math.floor(Math.log10(max / 4))))
  const niceMax = Math.ceil(max / 4 / unit) * 4 * unit
  return [niceMax, niceMax * 0.75, niceMax * 0.5, niceMax * 0.25, 0]
})

const xLabels = computed(() => {
  if (range.value === 'month') {
    const dim = daysInMonth(year.value, month.value)
    return [month.value + '-01', month.value + '-' + pad2(Math.ceil(dim / 2)), month.value + '-' + pad2(dim)]
  }
  return ['1月', '6月', '12月']
})

function onBarTap(b) {
  if (!b.value) return
  uni.showToast({
    title: b.label + ' ' + (series.value === 'expense' ? '支出' : '收入') + ' ' + fmtMoney(b.value),
    icon: 'none'
  })
}

/* ---------- 环形图 ---------- */
const donutSegments = computed(() => {
  const list = donutKind.value === 'expense' ? expenseBills.value : incomeBills.value
  const type = donutKind.value
  const map = new Map()
  for (const b of list) {
    const name = topNameOf(b.costType, type)
    const v = Math.abs(Number(b.money))
    map.set(name, (map.get(name) || 0) + v)
  }
  const total = Array.from(map.values()).reduce((s, v) => s + v, 0)
  if (total <= 0) return []
  const entries = Array.from(map.entries()).sort((a, b) => b[1] - a[1])
  const top = entries.slice(0, 8)
  const restTotal = entries.slice(8).reduce((s, e) => s + e[1], 0)
  const segs = top.map(e => ({
    name: e[0],
    value: e[1],
    pct: (e[1] / total * 100).toFixed(1) + '%',
    color: colorOf(e[0], type)
  }))
  if (restTotal > 0) {
    segs.push({
      name: '其他',
      value: restTotal,
      pct: (restTotal / total * 100).toFixed(1) + '%',
      color: '#c8ccd4'
    })
  }
  return segs
})

function drawDonut() {
  const segs = donutSegments.value
  const inst = getCurrentInstance()
  const ctx = uni.createCanvasContext('donut', inst ? inst.proxy : undefined)
  const c = donutSize / 2
  const rOuter = c - 2
  const rInner = c * 0.62
  if (!segs.length) {
    ctx.draw()
    return
  }
  const total = segs.reduce((s, x) => s + x.value, 0)
  let start = -Math.PI / 2
  segs.forEach(seg => {
    const angle = seg.value / total * Math.PI * 2
    const end = start + angle
    ctx.beginPath()
    ctx.arc(c, c, rOuter, start + 0.012, end - 0.012, false)
    ctx.arc(c, c, rInner, end - 0.012, start + 0.012, true)
    ctx.closePath()
    ctx.setFillStyle(seg.color)
    ctx.fill()
    start = end
  })
  ctx.draw()
}

watch(donutSegments, () => {
  nextTick(() => setTimeout(drawDonut, 60))
})
watch(donutKind, () => {
  nextTick(() => setTimeout(drawDonut, 60))
})

/* ---------- 数据加载 ---------- */
function load() {
  try {
    const raw = range.value === 'month'
      ? getMonthBills(String(year.value), String(month.value))
      : getYearBills(String(year.value))
    bills.value = JSON.parse(raw || '[]')
  } catch (e) { bills.value = [] }
}

function onRangeChange(e) {
  const v = e && e.value !== undefined ? String(e.value) : ''
  if ((v === '月' && range.value !== 'month') || (v === '年' && range.value !== 'year')) {
    range.value = v === '月' ? 'month' : 'year'
    load()
  }
}

function onPeriodChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  if (!v) return
  if (range.value === 'month') {
    const parts = v.split('-')
    if (parts.length >= 2) {
      year.value = Number(parts[0])
      month.value = Number(parts[1])
    }
  } else {
    year.value = Number(v)
  }
  load()
}

function shiftPeriod(delta) {
  if (range.value === 'month') {
    let m = month.value + delta
    let y = year.value
    if (m < 1) { m = 12; y-- }
    if (m > 12) { m = 1; y++ }
    month.value = m
    year.value = y
  } else {
    year.value += delta
  }
  load()
}

function goRank() { uni.navigateTo({ url: '/pages/rank/rank' }) }
function goReport() { uni.navigateTo({ url: '/pages/report/report' }) }
function goBack() { uni.navigateBack() }

onReady(() => {
  setTimeout(drawDonut, 200)
})

onShow(() => {
  load()
  nextTick(() => setTimeout(drawDonut, 200))
})
</script>

<style lang="scss">
.bg-blobs {
  position: fixed;
  top: -140rpx;
  left: -100rpx;
  right: -100rpx;
  height: 640rpx;
  z-index: -1;
  pointer-events: none;
}
.bg-blobs .blob {
  position: absolute;
  border-radius: 50%;
}
.bg-blobs .b1 {
  left: -80rpx;
  top: -40rpx;
  width: 620rpx;
  height: 620rpx;
  background: rgba(255, 163, 26, 0.42);
  filter: blur(110px);
}
.bg-blobs .b2 {
  right: -120rpx;
  top: 120rpx;
  width: 560rpx;
  height: 560rpx;
  background: rgba(57, 181, 117, 0.26);
  filter: blur(120px);
}
.page {
  min-height: 100vh;
  background: transparent;
  padding-bottom: 40rpx;
}
.nav-right-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.period-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0 4rpx;
}
.period-btn {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    background-color: #eceef5;
  }
}
.period-value {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 0 24rpx;
  padding: 8rpx 28rpx;
  border-radius: 32rpx;
  background-color: #ffffff;
  box-shadow: var(--shadow-card);
}
.period-text {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
  margin-right: 8rpx;
  font-variant-numeric: tabular-nums;
}
.card {
  background-color: var(--c-card);
  border-radius: 28rpx;
  margin: 24rpx 24rpx 0;
  padding: 30rpx 28rpx;
  box-shadow: var(--shadow-card);
}
.card-big-title {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
  margin-bottom: 28rpx;
}
.card-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26rpx;
}
.card-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
}
.card-head-icons {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.head-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8rpx;
}
/* 小型支出/收入切换 */
.mini-toggle {
  display: flex;
  flex-direction: row;
  background-color: #f1f2f8;
  border-radius: 18rpx;
  padding: 4rpx;
}
.mini-toggle-item {
  font-size: 23rpx;
  color: var(--c-sub);
  padding: 8rpx 22rpx;
  border-radius: 14rpx;
}
.mini-toggle-active {
  background-color: #ffffff;
  color: var(--c-text);
  font-weight: 700;
  box-shadow: 0 2rpx 8rpx rgba(35, 46, 111, 0.08);
}
/* 总览 */
.overview {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.ov-item {
  width: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 18rpx 0;
}
.ov-label {
  font-size: 24rpx;
  color: var(--c-sub);
}
.ov-num {
  margin-top: 10rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.ov-out { color: var(--c-out); }
.ov-in { color: var(--c-in); }
/* 柱状图 */
.bar-chart {
  display: flex;
  flex-direction: row;
}
.bar-y {
  width: 84rpx;
  height: 330rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  padding-right: 14rpx;
}
.bar-y-label {
  font-size: 20rpx;
  color: var(--c-dim);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.bar-body {
  flex: 1;
  height: 330rpx;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  border-bottom: 1rpx solid var(--c-line);
}
.bar-col {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}
.bar-bar {
  width: 10rpx;
  border-radius: 8rpx;
}
.bar-out {
  background: linear-gradient(180deg, #ff8a80, #f0655e);
}
.bar-in {
  background: linear-gradient(180deg, #3fd392, #22b877);
}
.bar-x {
  display: flex;
  flex-direction: row;
  margin-top: 12rpx;
}
.bar-x-label {
  flex: 1;
  font-size: 21rpx;
  color: var(--c-dim);
}
.bar-x-mid {
  text-align: center;
}
.bar-x .bar-x-label:last-child {
  text-align: right;
}
.chart-empty {
  padding: 30rpx 0 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic-sm {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background-color: var(--glass-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18rpx;
}
.chart-empty-text {
  color: var(--c-dim);
  font-size: 25rpx;
}
/* 环形图 */
.donut-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.donut-box {
  position: relative;
  flex-shrink: 0;
}
.donut-canvas {
  width: 100%;
  height: 100%;
}
.donut-center {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.donut-center-text {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--c-text);
}
.donut-center-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: var(--c-dim);
}
.legend {
  flex: 1;
  margin-left: 30rpx;
  display: flex;
  flex-direction: column;
}
.legend-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 7rpx 0;
}
.legend-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 6rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
}
.legend-name {
  font-size: 24rpx;
  color: var(--c-text);
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.legend-pct {
  font-size: 24rpx;
  color: var(--c-sub);
  font-variant-numeric: tabular-nums;
}
.card-foot {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin-top: 26rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid var(--c-line);
}
.card-foot-text {
  font-size: 26rpx;
  color: var(--c-sub);
  margin-right: 6rpx;
}
.bottom-safe {
  height: 60rpx;
}
</style>
