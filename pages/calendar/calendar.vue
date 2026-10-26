<template>
  <view class="page">
    <wd-navbar
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      custom-class="cal-navbar glass-nav"
      @click-left="goBack"
    >
      <template #title>
        <picker mode="date" fields="month" :value="pickerValue" @change="onMonthChange">
          <view class="cal-title">
            <text class="cal-title-text">{{ year }}.{{ pad2(month) }}</text>
            <app-icon name="chevron-down" color="#1d2129" :size="14"></app-icon>
          </view>
        </picker>
      </template>
      <template #right>
        <view class="nav-right-btn" @tap="goToday">
          <app-icon name="clock" color="#1d2129" :size="19"></app-icon>
        </view>
      </template>
    </wd-navbar>

    <!-- 月历 -->
    <view class="card glass">
      <view class="week-head">
        <text v-for="w in weekHeads" :key="w" class="week-head-cell">{{ w }}</text>
      </view>
      <view class="cal-grid">
        <view
          v-for="(cell, i) in cells"
          :key="i"
          class="cal-cell"
          @tap="selectDay(cell)"
        >
          <view v-if="cell.day > 0" :class="['cal-cell-inner', isToday(cell) ? 'cal-cell-today' : '']">
            <view :class="['cal-day-pill', isToday(cell) ? 'cal-day-pill-today' : '']">
              <text class="cal-day">{{ cell.day }}</text>
            </view>
            <text v-if="cell.text" :class="['cal-mark', cell.kind]">{{ cell.text }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 模式切换 -->
    <view class="mode-bar glass">
      <view
        v-for="m in modes"
        :key="m.value"
        :class="['mode-pill', mode === m.value ? 'mode-pill-active' : '']"
        @tap="mode = m.value"
      >
        <text>{{ m.label }}</text>
      </view>
    </view>
    <view class="month-summary">
      <text class="ms-item">收入 <text class="ms-in">{{ fmtMoney(incomeTotal) }}</text></text>
      <text class="ms-dot">·</text>
      <text class="ms-item">支出 <text class="ms-out">{{ fmtMoney(expenseTotal) }}</text></text>
      <text class="ms-dot">·</text>
      <text class="ms-item">结余 <text :class="incomeTotal - expenseTotal < 0 ? 'ms-out' : 'ms-in'">{{ fmtMoney(incomeTotal - expenseTotal) }}</text></text>
    </view>

    <!-- 账单列表 -->
    <view class="list-area">
      <bill-list :groups="groups" @row="onRow" @rowlong="onRowLong"></bill-list>
      <view v-if="!groups.length" class="empty">
        <view class="empty-ic">
          <app-icon name="calendar" color="#dfe2ec" :size="46" :stroke-width="1.5"></app-icon>
        </view>
        <text class="empty-text">本月还没有账单</text>
      </view>
    </view>

    <view class="bottom-safe"></view>
    <app-fab position="bottom-center" @click="goAdd"></app-fab>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fmtMoney, fmtNum, pad2, groupByDay } from '@/common/fmt.js'
import { lunarText } from '@/common/lunar.js'
// #ifdef APP-PLUS
import { getMonthBills, deleteBill } from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import { getMonthBills, deleteBill } from '@/common/web-bill-api.js'
// #endif

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const bills = ref([])
const mode = ref('both')
const selectedDay = ref(now.getDate())
const weekHeads = ['一', '二', '三', '四', '五', '六', '日']
const modes = [
  { value: 'both', label: '收&支' },
  { value: 'balance', label: '结余' },
  { value: 'income', label: '收入' },
  { value: 'expense', label: '支出' }
]

const pickerValue = computed(() => year.value + '-' + pad2(month.value))
const expenseTotal = computed(() => bills.value.filter(b => Number(b.money) < 0).reduce((s, b) => s + Math.abs(Number(b.money)), 0))
const incomeTotal = computed(() => bills.value.filter(b => Number(b.money) > 0).reduce((s, b) => s + Number(b.money), 0))
const groups = computed(() => groupByDay(bills.value, year.value, month.value))

/** 每日金额表 */
const dayMap = computed(() => {
  const map = {}
  for (const b of bills.value) {
    const d = Number(b.day)
    if (!map[d]) map[d] = { expense: 0, income: 0 }
    if (Number(b.money) < 0) map[d].expense += Math.abs(Number(b.money))
    else map[d].income += Number(b.money)
  }
  return map
})

/** 6 行 x 7 列的月历格子 */
const cells = computed(() => {
  const out = []
  const firstDay = new Date(Number(year.value), Number(month.value) - 1, 1)
  let lead = firstDay.getDay() - 1 // 周一为一周开始
  if (lead < 0) lead = 6
  for (let i = 0; i < lead; i++) out.push({ day: 0 })
  const dim = new Date(Number(year.value), Number(month.value), 0).getDate()
  for (let d = 1; d <= dim; d++) {
    out.push(makeCell(d))
  }
  while (out.length % 7 !== 0) out.push({ day: 0 })
  return out
})

function makeCell(d) {
  const stat = dayMap.value[d]
  let text = ''
  let kind = ''
  if (stat) {
    if (mode.value === 'both') {
      if (stat.expense > 0) { text = '-' + fmtNum(stat.expense); kind = 'out' }
      else if (stat.income > 0) { text = '+' + fmtNum(stat.income); kind = 'in' }
    } else if (mode.value === 'balance') {
      const net = stat.income - stat.expense
      if (net !== 0) { text = fmtNum(net); kind = net < 0 ? 'out' : 'in' }
    } else if (mode.value === 'income') {
      if (stat.income > 0) { text = '+' + fmtNum(stat.income); kind = 'in' }
    } else if (mode.value === 'expense') {
      if (stat.expense > 0) { text = '-' + fmtNum(stat.expense); kind = 'out' }
    }
  }
  if (!text) {
    text = lunarText(year.value, month.value, d)
    kind = 'lunar'
  }
  return { day: d, text, kind }
}

function isToday(cell) {
  const t = new Date()
  return cell.day > 0 && t.getFullYear() === Number(year.value) && t.getMonth() + 1 === Number(month.value) && t.getDate() === cell.day
}

function load() {
  try {
    bills.value = JSON.parse(getMonthBills(String(year.value), String(month.value)) || '[]')
  } catch (e) { bills.value = [] }
}

function onMonthChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  const parts = v.split('-')
  if (parts.length >= 2) {
    year.value = Number(parts[0])
    month.value = Number(parts[1])
    load()
  }
}

function goToday() {
  const t = new Date()
  year.value = t.getFullYear()
  month.value = t.getMonth() + 1
  selectedDay.value = t.getDate()
  load()
}

function selectDay(cell) {
  if (cell.day > 0) selectedDay.value = cell.day
}

function onRow(bill) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) goEdit(bill)
      else if (res.tapIndex === 1) askDelete(bill)
    }
  })
}

function onRowLong(bill) {
  askDelete(bill)
}

function askDelete(bill) {
  uni.showModal({
    title: '删除账单',
    content: '删除这笔「' + bill.costType + ' ' + fmtMoney(Math.abs(Number(bill.money))) + '元」的记录？',
    success: (res) => {
      if (res.confirm) {
        deleteBill(String(bill.id))
        load()
      }
    }
  })
}

function goEdit(bill) {
  const q = [
    'id=' + bill.id,
    'money=' + Math.abs(Number(bill.money)).toFixed(2),
    'isIncome=' + (Number(bill.money) >= 0 ? 1 : 0),
    'date=' + bill.year + '-' + pad2(bill.month) + '-' + pad2(bill.day),
    'costType=' + encodeURIComponent(bill.costType || ''),
    'remark=' + encodeURIComponent(bill.orderRemark || ''),
    'reimb=' + (Number(bill.reimb) === 1 ? 1 : 0),
    'starred=' + (Number(bill.starred) === 1 ? 1 : 0)
  ].join('&')
  uni.navigateTo({ url: '/pages/add/add?' + q })
}

function goAdd() { uni.navigateTo({ url: '/pages/add/add' }) }
function goBack() { uni.navigateBack() }

onShow(() => {
  load()
})
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background: transparent;
}
.cal-title {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.cal-title-text {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--c-text);
  margin-right: 8rpx;
}
.nav-right-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card {
  background-color: var(--c-card);
  border-radius: 28rpx;
  margin: 20rpx 24rpx 0;
  padding: 26rpx 14rpx;
  box-shadow: var(--shadow-card);
}
.week-head {
  display: flex;
  flex-direction: row;
  margin-bottom: 10rpx;
}
.week-head-cell {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: var(--c-sub);
}
.cal-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.cal-cell {
  width: 14.28%;
  height: 112rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cal-cell-inner {
  width: 100%;
  height: 100rpx;
  border-radius: 18rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.cal-cell-today {
  background-color: var(--c-primary-weak);
}
.cal-day-pill {
  min-width: 44rpx;
  height: 44rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6rpx;
}
.cal-day-pill-today {
  background-color: var(--c-primary);
}
.cal-day {
  font-size: 29rpx;
  color: var(--c-text);
  font-weight: 500;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.cal-day-pill-today .cal-day {
  color: var(--c-ink-on-primary);
  font-weight: 700;
}
.cal-mark {
  margin-top: 5rpx;
  font-size: 21rpx;
  line-height: 1.1;
  max-width: 94rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-variant-numeric: tabular-nums;
}
.cal-mark.out {
  color: var(--c-out);
  font-weight: 500;
}
.cal-mark.in {
  color: var(--c-in);
  font-weight: 500;
}
.cal-mark.lunar {
  color: var(--c-dim);
}
.mode-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  background-color: var(--glass-bg);
  border-radius: 24rpx;
  margin: 24rpx 24rpx 0;
  padding: 6rpx;
}
.mode-pill {
  flex: 1;
  text-align: center;
  padding: 15rpx 0;
  border-radius: 20rpx;
  font-size: 27rpx;
  color: var(--c-sub);
}
.mode-pill-active {
  background-color: #ffffff;
  color: var(--c-text);
  font-weight: 700;
  box-shadow: 0 2rpx 10rpx rgba(35, 46, 111, 0.08);
}
.month-summary {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin: 24rpx 24rpx 0;
}
.ms-item {
  font-size: 26rpx;
  color: var(--c-sub);
}
.ms-in {
  color: var(--c-in);
  font-weight: 600;
}
.ms-out {
  color: var(--c-out);
  font-weight: 600;
}
.ms-dot {
  margin: 0 18rpx;
  color: var(--c-dim);
}
.list-area {
  margin-top: 24rpx;
}
.empty {
  padding: 100rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic {
  width: 130rpx;
  height: 130rpx;
  border-radius: 50%;
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
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
  height: calc(150rpx + env(safe-area-inset-bottom));
}
</style>
