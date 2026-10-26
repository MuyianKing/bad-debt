<template>
  <view class="page">
    <wd-navbar custom-class="glass-nav" :left-arrow="true" :fixed="true" :placeholder="true" :safe-area-inset-top="true" @click-left="goBack">
      <template #title>
        <wd-segmented :value="rangeLabel" :options="['月', '年']" @change="onRangeChange"></wd-segmented>
      </template>
      <template #right>
        <view class="nav-right-btn" @tap="goReport">
          <app-icon name="file-text" color="#1d2129" :size="19"></app-icon>
        </view>
      </template>
    </wd-navbar>

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

    <!-- 支出 / 收入 -->
    <view class="kind-bar glass">
      <view
        v-for="k in kinds"
        :key="k.value"
        :class="['kind-pill', kind === k.value ? 'kind-pill-active' : '']"
        @tap="kind = k.value"
      >
        <text>{{ k.label }}</text>
      </view>
    </view>

    <!-- 排行列表 -->
    <view class="rank-card glass" v-if="ranks.length">
      <view v-for="(r, i) in ranks" :key="r.name" :class="['rank-row', i > 0 ? 'rank-row-border' : '']">
        <view class="rank-line">
          <view v-if="i < 3" :class="['rank-no', 'rank-no-' + (i + 1)]">{{ i + 1 }}</view>
          <view class="rank-no rank-no-plain" v-else>{{ i + 1 }}</view>
          <view class="rank-icon" :style="{ backgroundColor: r.tint }">
            <app-icon :name="r.icon" :color="r.color" :size="19" :solid="true"></app-icon>
          </view>
          <view class="rank-name-col">
            <view class="rank-name-row">
              <text class="rank-name">{{ r.name }}</text>
              <text class="rank-pct">{{ r.pctText }}</text>
            </view>
          </view>
          <text :class="['rank-delta', r.deltaClass]">{{ r.deltaText }}</text>
          <text class="rank-total">{{ fmtMoney(r.value) }}</text>
        </view>
        <view class="rank-bar-wrap">
          <wd-progress :percentage="r.pct" :hide-text="true" :color="r.color" :duration="10" custom-class="rank-progress"></wd-progress>
        </view>
      </view>
    </view>
    <view v-else class="empty">
      <view class="empty-ic">
        <app-icon name="bar-chart" color="#dfe2ec" :size="46" :stroke-width="1.5"></app-icon>
      </view>
      <text class="empty-text">该期间暂无{{ kind === 'expense' ? '支出' : '收入' }}</text>
    </view>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fmtMoney, pad2 } from '@/common/fmt.js'
import { topNameOf, iconOf, colorOf } from '@/common/cats.js'
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
const kind = ref('expense')
const bills = ref([])
const prevBills = ref([])
const kinds = [
  { value: 'expense', label: '支出' },
  { value: 'income', label: '收入' }
]

const rangeLabel = computed(() => (range.value === 'month' ? '月' : '年'))
const pickerValue = computed(() => (range.value === 'month' ? year.value + '-' + pad2(month.value) : String(year.value)))
const periodText = computed(() => (range.value === 'month' ? year.value + '-' + pad2(month.value) : String(year.value)))

function hexToTint(hex) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const mix = (c) => Math.round(c + (255 - c) * 0.85)
  return 'rgb(' + mix(r) + ',' + mix(g) + ',' + mix(b) + ')'
}

function aggregate(list) {
  const type = kind.value
  const map = new Map()
  for (const b of list) {
    if (type === 'expense' && Number(b.money) >= 0) continue
    if (type === 'income' && Number(b.money) < 0) continue
    const name = topNameOf(b.costType, type)
    map.set(name, (map.get(name) || 0) + Math.abs(Number(b.money)))
  }
  return map
}

const ranks = computed(() => {
  const cur = aggregate(bills.value)
  const prev = aggregate(prevBills.value)
  const total = Array.from(cur.values()).reduce((s, v) => s + v, 0)
  if (total <= 0) return []
  const max = Math.max(...Array.from(cur.values()))
  const entries = Array.from(cur.entries()).sort((a, b) => b[1] - a[1])
  return entries.map(e => {
    const name = e[0]
    const value = e[1]
    const pct = Math.max(2, Math.round(value / max * 100))
    const pctText = value / total < 0.02 ? '<2.00%' : (value / total * 100).toFixed(2) + '%'
    let deltaText = '新增'
    let deltaClass = 'rank-delta-flat'
    if (prev.has(name)) {
      const diff = value - prev.get(name)
      if (Math.abs(diff) < 0.005) {
        deltaText = '持平'
      } else {
        const up = diff > 0
        deltaText = (up ? '▲' : '▼') + fmtMoney(Math.abs(diff))
        // 支出涨=红 跌=绿；收入涨=绿 跌=红
        const good = kind.value === 'expense' ? !up : up
        deltaClass = good ? 'rank-delta-good' : 'rank-delta-bad'
      }
    }
    const color = colorOf(name, kind.value)
    return {
      name,
      value,
      pct,
      pctText,
      deltaText,
      deltaClass,
      icon: iconOf(name, kind.value),
      color,
      tint: hexToTint(color)
    }
  })
})

function load() {
  try {
    const raw = range.value === 'month'
      ? getMonthBills(String(year.value), String(month.value))
      : getYearBills(String(year.value))
    bills.value = JSON.parse(raw || '[]')
  } catch (e) { bills.value = [] }
  loadPrev()
}

function loadPrev() {
  let py = year.value
  let pm = month.value - 1
  if (range.value === 'month') {
    if (pm < 1) { pm = 12; py-- }
    try {
      prevBills.value = JSON.parse(getMonthBills(String(py), String(pm)) || '[]')
    } catch (e) { prevBills.value = [] }
  } else {
    py = year.value - 1
    try {
      prevBills.value = JSON.parse(getYearBills(String(py)) || '[]')
    } catch (e) { prevBills.value = [] }
  }
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

function goReport() { uni.navigateTo({ url: '/pages/report/report' }) }
function goBack() { uni.navigateBack() }

onShow(() => {
  load()
})
</script>

<style lang="scss">
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
.kind-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  background-color: var(--glass-bg);
  border-radius: 22rpx;
  margin: 20rpx auto 0;
  padding: 5rpx;
  width: 320rpx;
}
.kind-pill {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  border-radius: 18rpx;
  font-size: 28rpx;
  color: var(--c-sub);
}
.kind-pill-active {
  background-color: #ffffff;
  color: var(--c-text);
  font-weight: 700;
  box-shadow: 0 2rpx 10rpx rgba(35, 46, 111, 0.08);
}
.rank-card {
  background-color: var(--c-card);
  border-radius: 28rpx;
  margin: 24rpx 24rpx 0;
  padding: 10rpx 28rpx;
  box-shadow: var(--shadow-card);
}
.rank-row {
  padding: 26rpx 0;
}
.rank-row-border {
  border-top: 1rpx solid var(--c-line);
}
.rank-line {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.rank-no {
  width: 44rpx;
  height: 44rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
  margin-right: 16rpx;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.rank-no-1 {
  background-color: #fff1d6;
  color: #f5a623;
}
.rank-no-2 {
  background-color: #eef1f7;
  color: #98a2b8;
}
.rank-no-3 {
  background-color: #fdeee2;
  color: #e8975a;
}
.rank-no-plain {
  background-color: transparent;
  color: var(--c-dim);
}
.rank-icon {
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rank-icon .app-icon {
  opacity: 0.95;
}
.rank-name-col {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}
.rank-name-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.rank-name {
  font-size: 29rpx;
  color: var(--c-text);
  font-weight: 600;
  max-width: 240rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.rank-pct {
  margin-left: 12rpx;
  font-size: 23rpx;
  color: var(--c-dim);
  font-variant-numeric: tabular-nums;
}
.rank-delta {
  font-size: 22rpx;
  border-radius: 10rpx;
  padding: 6rpx 12rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.rank-delta-bad {
  color: var(--c-out);
  background-color: var(--c-out-weak);
}
.rank-delta-good {
  color: var(--c-in);
  background-color: var(--c-in-weak);
}
.rank-delta-flat {
  color: var(--c-sub);
  background-color: #f1f2f8;
}
.rank-total {
  font-size: 29rpx;
  color: var(--c-text);
  font-weight: 700;
  flex-shrink: 0;
  min-width: 150rpx;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.rank-bar-wrap {
  margin-top: 14rpx;
  padding-left: 56rpx;
}
.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic {
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
  background-color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  margin-bottom: 24rpx;
}
.empty-text {
  color: var(--c-sub);
  font-size: 28rpx;
  font-weight: 600;
}
.bottom-safe {
  height: 60rpx;
}
</style>
