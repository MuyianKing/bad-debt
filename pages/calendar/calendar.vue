<template>
  <view :class="['page', themeClass]">
    <wd-navbar
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      :bordered="navSolid"
      :custom-style="navStyle"
      :custom-class="'cal-navbar' + (navSolid ? ' is-scrolled' : '')"
      @click-left="goBack"
    >
      <template #title>
        <picker mode="date" fields="month" :value="pickerValue" @change="onMonthChange">
          <view class="cal-title">
            <text class="cal-title-text">{{ year }}.{{ pad2(month) }}</text>
            <app-icon name="chevron-down" :color="theme.ink" :size="14"></app-icon>
          </view>
        </picker>
      </template>
    </wd-navbar>

    <!-- 月历 -->
    <view class="card glass">
      <view class="week-head">
        <text v-for="(w, wi) in weekHeads" :key="w" :class="['week-head-cell', wi >= 5 ? 'week-head-weekend' : '']">{{ w }}</text>
      </view>
      <view class="cal-grid">
        <view
          v-for="(cell, i) in cells"
          :key="i"
          class="cal-cell"
          @tap="selectDay(cell)"
        >
          <view v-if="cell.day > 0" :class="['cal-cell-inner', isSelected(cell) ? 'cal-cell-selected' : '']">
            <view :class="['cal-day-pill', cell.isToday && !isSelected(cell) ? 'cal-day-pill-today' : '']">
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
    <view class="day-head">
      <text class="day-head-title">{{ dayTitle }}</text>
      <view class="day-head-totals">
        <template v-if="mode === 'income'">
          <text class="ms-item">收 <text class="ms-in">{{ fmtMoney(dayIncome) }}</text></text>
        </template>
        <template v-else-if="mode === 'expense'">
          <text class="ms-item">支 <text class="ms-out">{{ fmtMoney(dayExpense) }}</text></text>
        </template>
        <template v-else>
          <text class="ms-item">支 <text class="ms-out">{{ fmtMoney(dayExpense) }}</text></text>
          <text class="ms-dot">·</text>
          <text class="ms-item">收 <text class="ms-in">{{ fmtMoney(dayIncome) }}</text></text>
          <text class="ms-dot">·</text>
          <text class="ms-item">结 <text :class="dayBalance < 0 ? 'ms-out' : 'ms-in'">{{ fmtMoney(dayBalance) }}</text></text>
        </template>
      </view>
    </view>

    <!-- 账单列表：只展示当前选中日期 -->
    <view class="list-area">
      <bill-list :groups="groups" :hide-head="true" @row="onRow" @rowlong="onRowLong"></bill-list>
    <view v-if="!groups.length" class="empty">
      <view class="empty-ic">
        <app-icon name="calendar" :color="theme.primary" :size="44" :stroke-width="1.6"></app-icon>
      </view>
      <text class="empty-text">{{ emptyText }}</text>
    </view>
    </view>

    <view class="bottom-safe"></view>
    <app-fab position="bottom-center" @click="goAdd"></app-fab>

    <!-- 账单操作菜单 -->
    <wd-action-sheet
      v-model="actionShow"
      :actions="actionItems"
      cancel-text="取消"
      :z-index="300"
      :safe-area-inset-bottom="true"
      @select="onActionSelect"
    ></wd-action-sheet>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPageScroll } from '@dcloudio/uni-app'
import { fmtMoney, fmtNum, pad2, groupByDay, dayGroupTitle, daysInMonth } from '@/common/fmt.js'
import { lunarText } from '@/common/lunar.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
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
// 账单操作菜单（wot action sheet）
const actionShow = ref(false)
const actionBill = ref(null)
const actionItems = ref([])
// 顶栏滚动态：默认透明让光斑透到状态栏，滚动后转毛玻璃避免列表从标题下穿过
const navSolid = ref(false)
// wd-navbar 的 custom-class 进不去组件内层样式域，底色只能走 custom-style
const navStyle = computed(() =>
  'transition: background-color 0.18s ease;' +
  (navSolid.value
    ? 'background-color: var(--glass-nav-bg);-webkit-backdrop-filter: blur(18px) saturate(1.6);backdrop-filter: blur(18px) saturate(1.6);'
    : 'background-color: transparent;')
)
const weekHeads = ['一', '二', '三', '四', '五', '六', '日']
const modes = [
  { value: 'both', label: '收&支' },
  { value: 'balance', label: '结余' },
  { value: 'income', label: '收入' },
  { value: 'expense', label: '支出' }
]

const pickerValue = computed(() => year.value + '-' + pad2(month.value))
/** 选中日的全部账单 */
const dayBillsAll = computed(() => bills.value.filter(b => Number(b.day) === Number(selectedDay.value)))
/** 列表随「收&支 / 结余 / 收入 / 支出」过滤；结余同收&支，展示全部 */
const dayBills = computed(() => {
  if (mode.value === 'income') return dayBillsAll.value.filter(b => Number(b.money) > 0)
  if (mode.value === 'expense') return dayBillsAll.value.filter(b => Number(b.money) < 0)
  return dayBillsAll.value
})
const dayExpense = computed(() => dayBills.value.filter(b => Number(b.money) < 0).reduce((s, b) => s + Math.abs(Number(b.money)), 0))
const dayIncome = computed(() => dayBills.value.filter(b => Number(b.money) > 0).reduce((s, b) => s + Number(b.money), 0))
const dayBalance = computed(() => dayIncome.value - dayExpense.value)
const dayTitle = computed(() => dayGroupTitle(year.value, month.value, selectedDay.value))
const emptyText = computed(() => {
  if (mode.value === 'income') return '这一天还没有收入'
  if (mode.value === 'expense') return '这一天还没有支出'
  return '这一天还没有账单'
})
const groups = computed(() => groupByDay(dayBills.value, year.value, month.value))

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
  const t = new Date()
  const isToday =
    t.getFullYear() === Number(year.value) &&
    t.getMonth() + 1 === Number(month.value) &&
    t.getDate() === d
  return { day: d, text, kind, isToday }
}

function isSelected(cell) {
  return cell.day > 0 && Number(cell.day) === Number(selectedDay.value)
}

function load() {
  try {
    bills.value = JSON.parse(getMonthBills(String(year.value), String(month.value)) || '[]')
  } catch (e) { bills.value = [] }
}

/** 切月后重新定位选中日：本月回到今天，其他月份回到 1 号 */
function resetSelectedDay() {
  const t = new Date()
  const isThisMonth = t.getFullYear() === Number(year.value) && t.getMonth() + 1 === Number(month.value)
  const day = isThisMonth ? t.getDate() : 1
  selectedDay.value = Math.min(day, daysInMonth(year.value, month.value))
}

function onMonthChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  const parts = v.split('-')
  if (parts.length >= 2) {
    year.value = Number(parts[0])
    month.value = Number(parts[1])
    resetSelectedDay()
    load()
  }
}

function selectDay(cell) {
  if (cell.day > 0) selectedDay.value = cell.day
}

function onRow(bill) {
  actionBill.value = bill
  actionItems.value = [
    { name: '编辑' },
    { name: '删除', color: 'var(--c-out)' }
  ]
  actionShow.value = true
}

function onActionSelect({ index }) {
  const bill = actionBill.value
  if (!bill) return
  if (index === 0) goEdit(bill)
  else if (index === 1) askDelete(bill)
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

onPageScroll((e) => {
  const top = Number(e.scrollTop) || 0
  if (top > 8 && !navSolid.value) navSolid.value = true
  else if (top <= 8 && navSolid.value) navSolid.value = false
})

onShow(() => {
  refreshTheme()
  load()
})
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background-color: var(--c-bg);
}
.cal-title {
  display: flex;
  flex-direction: row;
  align-items: center;
  /* wd-navbar 的 title 槽是 60% 宽的盒子，flex 行必须显式居中，否则文字贴左 */
  justify-content: center;
}
.cal-title-text {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--c-text);
  margin-right: 8rpx;
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
.week-head-weekend {
  color: var(--c-primary-deep);
  font-weight: 600;
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
.cal-cell-inner.cal-cell-selected {
  background-color: var(--c-primary-weak);
  border-radius: 22rpx;
}
.cal-cell-selected .cal-day-pill {
  background: var(--grad-primary);
  box-shadow: var(--shadow-primary-sm);
}
.cal-cell-selected .cal-day {
  color: #ffffff;
  font-weight: 700;
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
  background-color: var(--c-primary-tint);
}
.cal-day-pill-today .cal-day {
  color: var(--c-primary-deep);
  font-weight: 700;
}
.cal-day {
  font-size: 29rpx;
  color: var(--c-text);
  font-weight: 500;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
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
/* 选中格的农历文字也跟着高亮换色：浅灰在浅黄底上会显得没被选中 */
.cal-cell-selected .cal-mark.lunar {
  color: var(--c-primary-deep);
  font-weight: 500;
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
  background: var(--grad-primary);
  color: #ffffff;
  font-weight: 700;
  box-shadow: var(--shadow-primary-sm);
}
.day-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin: 26rpx 30rpx 6rpx;
}
.day-head-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--c-text);
}
.day-head-totals {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.ms-item {
  font-size: 24rpx;
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
  margin: 0 12rpx;
  color: var(--c-dim);
}
.list-area {
  margin-top: 10rpx;
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
  border-radius: 42rpx;
  background-color: var(--c-primary-weak);
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
