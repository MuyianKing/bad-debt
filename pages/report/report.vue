<template>
  <view class="page">
    <wd-navbar custom-class="glass-nav" :left-arrow="true" title="日报表" :fixed="true" :placeholder="true" :safe-area-inset-top="true" @click-left="goBack">
      <template #right>
        <view class="nav-right-btn" @tap="goStats">
          <app-icon name="pie-chart" color="#1d2129" :size="19"></app-icon>
        </view>
      </template>
    </wd-navbar>

    <view class="period-row">
      <view class="period-btn" @tap="shiftPeriod(-1)">
        <app-icon name="chevron-left" color="#5b6170" :size="18"></app-icon>
      </view>
      <picker mode="date" fields="month" :value="pickerValue" @change="onPeriodChange">
        <view class="period-value glass">
          <text class="period-text">{{ year + '-' + pad2(month) }}</text>
          <app-icon name="chevron-down" color="#5b6170" :size="14"></app-icon>
        </view>
      </picker>
      <view class="period-btn" @tap="shiftPeriod(1)">
        <app-icon name="chevron-right" color="#5b6170" :size="18"></app-icon>
      </view>
    </view>

    <view class="card glass">
      <view class="report-head">
        <text class="report-title">日报表</text>
        <text class="report-sub">日均支出 ¥{{ avgExpense }}</text>
      </view>

      <view class="table">
        <view class="tr th">
          <text class="td td-date">日期</text>
          <text class="td td-num">收入</text>
          <text class="td td-num">支出</text>
          <text class="td td-num">结余</text>
        </view>
        <view
          v-for="r in rows"
          :key="r.day"
          class="tr"
          hover-class="tr-hover"
          @tap="goCalendar"
        >
          <text class="td td-date">{{ r.label }}</text>
          <text class="td td-num td-in">{{ r.income > 0 ? fmtMoney(r.income) : '-' }}</text>
          <text class="td td-num td-out">{{ r.expense > 0 ? fmtMoney(r.expense) : '-' }}</text>
          <text :class="['td', 'td-num', r.balance < 0 ? 'td-bad' : '']">{{ fmtMoney(r.balance) }}</text>
        </view>
        <view v-if="!rows.length" class="empty">
          <view class="empty-ic">
            <app-icon name="file-text" color="#dfe2ec" :size="42" :stroke-width="1.5"></app-icon>
          </view>
          <text class="empty-text">本月暂无账单</text>
        </view>
      </view>
    </view>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fmtMoney, pad2, daysInMonth } from '@/common/fmt.js'
// #ifdef APP-PLUS
import { getMonthBills } from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import { getMonthBills } from '@/common/web-bill-api.js'
// #endif

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const bills = ref([])

const pickerValue = computed(() => year.value + '-' + pad2(month.value))

const rows = computed(() => {
  const map = new Map()
  for (const b of bills.value) {
    const d = Number(b.day)
    if (!map.has(d)) map.set(d, { income: 0, expense: 0 })
    const row = map.get(d)
    if (Number(b.money) < 0) row.expense += Math.abs(Number(b.money))
    else row.income += Number(b.money)
  }
  const out = []
  const days = Array.from(map.keys()).sort((a, b) => b - a)
  for (const d of days) {
    const r = map.get(d)
    out.push({
      day: d,
      label: pad2(month.value) + '-' + pad2(d),
      income: r.income,
      expense: r.expense,
      balance: r.income - r.expense
    })
  }
  return out
})

const avgExpense = computed(() => {
  const expense = rows.value.reduce((s, r) => s + r.expense, 0)
  const t = new Date()
  const isCurrent = t.getFullYear() === year.value && t.getMonth() + 1 === month.value
  const elapsed = isCurrent ? t.getDate() : daysInMonth(year.value, month.value)
  return (expense / Math.max(1, elapsed)).toFixed(2)
})

function load() {
  try {
    bills.value = JSON.parse(getMonthBills(String(year.value), String(month.value)) || '[]')
  } catch (e) { bills.value = [] }
}

function onPeriodChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  const parts = v.split('-')
  if (parts.length >= 2) {
    year.value = Number(parts[0])
    month.value = Number(parts[1])
    load()
  }
}

function shiftPeriod(delta) {
  let m = month.value + delta
  let y = year.value
  if (m < 1) { m = 12; y-- }
  if (m > 12) { m = 1; y++ }
  month.value = m
  year.value = y
  load()
}

function goCalendar() { uni.navigateTo({ url: '/pages/calendar/calendar' }) }
function goStats() { uni.navigateTo({ url: '/pages/stats/stats' }) }
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
.card {
  background-color: var(--c-card);
  border-radius: 28rpx;
  margin: 24rpx 24rpx 0;
  padding: 32rpx 26rpx;
  box-shadow: var(--shadow-card);
}
.report-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 26rpx;
}
.report-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
}
.report-sub {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--c-sub);
}
.table {
  display: flex;
  flex-direction: column;
}
.tr {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 26rpx 12rpx;
}
.tr + .tr {
  border-top: 1rpx solid var(--c-line);
}
.tr-hover {
  background-color: #fafbfd;
}
.th {
  background-color: var(--glass-bg);
  border-radius: 14rpx;
  padding: 18rpx 12rpx;
  margin-bottom: 6rpx;
}
.td {
  font-size: 27rpx;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.th .td {
  color: var(--c-sub);
  font-size: 25rpx;
  font-weight: 600;
}
.td-date {
  flex: 1;
}
.td-num {
  width: 185rpx;
  text-align: right;
}
.td-in {
  color: var(--c-in);
}
.td-out {
  color: var(--c-out);
}
.td-bad {
  color: var(--c-out);
  font-weight: 600;
}
.empty {
  padding: 70rpx 0 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: var(--glass-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
}
.empty-text {
  color: var(--c-dim);
  font-size: 26rpx;
}
.bottom-safe {
  height: 60rpx;
}
</style>
