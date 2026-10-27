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
        <view class="cal-title" @tap="openMonthPicker">
          <text class="cal-title-text">{{ year }}.{{ pad2(month) }}</text>
          <app-icon name="chevron-down" :color="theme.ink" :size="14"></app-icon>
        </view>
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
    <!-- 选中日摘要与账单合成一张卡：摘要和列表之间用细线分隔，列表行沿用 bill-list 的分隔线 -->
    <view class="day-card glass">
      <view :class="['day-summary', dayBills.length ? 'day-summary-rich' : '']">
        <view class="day-card-main">
          <view class="day-card-head">
            <text class="day-card-title">{{ dayTitle }}</text>
            <text v-if="dayBills.length" class="day-card-count">{{ dayBills.length }} 笔</text>
          </view>
          <view v-if="dayBills.length" class="day-card-totals">
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
          <view class="day-card-say">
            <text class="day-card-say-name">{{ activeMascot.name }}</text>
            <text class="day-card-say-text">：{{ dayLine }}</text>
          </view>
        </view>
        <view v-if="dayBills.length" class="day-card-mascot">
          <mascot-face :mood="dayMood" :size="158"></mascot-face>
        </view>
      </view>

      <view class="day-divider"></view>

      <view class="day-list">
        <bill-list :groups="groups" :hide-head="true" :flush="true" @row="onRow" @rowlong="onRowLong"></bill-list>
        <view v-if="!groups.length" class="empty">
          <view class="empty-ic">
            <mascot-face mood="empty" head :size="128"></mascot-face>
          </view>
          <text class="empty-text">{{ emptyText }}</text>
          <text class="empty-sub">{{ activeMascot.name }}帮你盯着</text>
        </view>
      </view>
    </view>

    <view class="bottom-safe"></view>

    <!-- 账单操作菜单 -->
    <wd-action-sheet
      v-model="actionShow"
      :actions="actionItems"
      cancel-text="取消"
      :z-index="300"
      :safe-area-inset-bottom="true"
      @select="onActionSelect"
    ></wd-action-sheet>

    <!-- 月份选择：wot 日期面板（原生 picker 在深色主题下是白底）。
         弹层放在 navbar 之外，避免导航栏的 fixed/毛玻璃样式影响弹层定位 -->
    <wd-datetime-picker ref="monthPickerRef" v-model="monthStamp" type="year-month" :z-index="300">
      <view></view>
    </wd-datetime-picker>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onHide, onPageScroll } from '@dcloudio/uni-app'
import { fmtMoney, fmtNum, pad2, groupByDay, dayGroupTitle, daysInMonth } from '@/common/fmt.js'
import { lunarText } from '@/common/lunar.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
import { activeMascot, refreshMascots } from '@/common/mascots.js'
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

// wot 日期面板吃时间戳；确认时才回写年月，滚动过程中不改日历数据
const monthPickerRef = ref(null)
const monthStamp = computed({
  get: () => new Date(year.value, month.value - 1, 1).getTime(),
  set: (stamp) => {
    const d = new Date(Number(stamp))
    if (isNaN(d.getTime())) return
    const y = d.getFullYear()
    const m = d.getMonth() + 1
    if (y === year.value && m === month.value) return
    year.value = y
    month.value = m
    resetSelectedDay()
    load()
  }
})
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
// 角色表情跟随当前筛选结果：有进账且没倒挂时大笑，其余有账单的场景用基础表情
const dayMood = computed(() => {
  let income = 0
  let expense = 0
  for (const b of dayBills.value) {
    const n = Number(b.money) || 0
    if (n > 0) income += n
    else expense += Math.abs(n)
  }
  return income > 0 && income >= expense ? 'success' : 'idle'
})
const dayLine = computed(() => {
  if (!dayBills.value.length) {
    if (mode.value === 'income') return '这天没有进账'
    if (mode.value === 'expense') return '这天没花钱'
    return '这天还没开账'
  }
  return dayMood.value === 'success' ? '有进账，这天不错' : '钱都花这儿了'
})
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

function openMonthPicker() {
  if (monthPickerRef.value) monthPickerRef.value.open()
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

function goBack() { uni.navigateBack() }

onPageScroll((e) => {
  const top = Number(e.scrollTop) || 0
  if (top > 8 && !navSolid.value) navSolid.value = true
  else if (top <= 8 && navSolid.value) navSolid.value = false
})

/** 深色主题的黑底需要白色状态栏图标（与设置页一致） */
function applyStatusBarStyle() {
  // #ifdef APP-PLUS
  try {
    plus.navigator.setStatusBarStyle(theme.value.dark ? 'light' : 'dark')
  } catch (e) {}
  // #endif
}

onShow(() => {
  refreshTheme()
  refreshMascots()
  applyStatusBarStyle()
  load()
})

onHide(() => {
  // 下一层页面沿用清单默认的深色图标
  // #ifdef APP-PLUS
  try { plus.navigator.setStatusBarStyle('dark') } catch (e) {}
  // #endif
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
/* ---- 选中日摘要 + 账单：同一张卡，中间细线分隔 ---- */
.day-card {
  position: relative;
  margin: 24rpx 24rpx 0;
  padding: 0;
  border-radius: 28rpx;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.day-summary {
  position: relative;
  padding: 22rpx 28rpx;
  /* 角色的投影只留在摘要区内，不压到下方列表的分隔线 */
  overflow: hidden;
}
/* 有账单：右侧给整身角色让位，摘要区撑到角色完整露出 */
.day-summary-rich {
  display: flex;
  align-items: center;
  min-height: 210rpx;
  padding-right: 188rpx;
  box-sizing: border-box;
}
.day-card-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.day-card-head {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.day-card-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--c-text);
}
.day-card-count {
  margin-left: 12rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background-color: var(--c-primary-weak);
  color: var(--c-primary-deep);
  font-size: 21rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.day-card-totals {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 10rpx;
}
.day-card-say {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-top: 12rpx;
}
.day-card-say-name {
  font-size: 23rpx;
  font-weight: 600;
  color: var(--c-primary-deep);
}
.day-card-say-text {
  font-size: 23rpx;
  color: var(--c-sub);
}
/* 角色压在摘要区右下角，不参与文本排版 */
.day-card-mascot {
  position: absolute;
  right: 10rpx;
  bottom: 0;
  z-index: 0;
  width: 158rpx;
  pointer-events: none;
  filter: drop-shadow(0 6rpx 14rpx rgba(0, 0, 0, 0.16));
}
.day-divider {
  height: 1rpx;
  margin: 0 28rpx;
  background-color: var(--c-line);
}
.day-list {
  padding-bottom: 4rpx;
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
.empty {
  padding: 56rpx 0 72rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic {
  width: 144rpx;
  height: 144rpx;
  padding: 8rpx;
  box-sizing: border-box;
  border-radius: 50%;
  background-color: var(--c-primary-weak);
  border: 1rpx solid var(--c-primary-tint);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  margin-bottom: 24rpx;
  overflow: hidden;
}
.empty-text {
  color: var(--c-sub);
  font-size: 28rpx;
  font-weight: 600;
}
.empty-sub {
  margin-top: 10rpx;
  color: var(--c-dim);
  font-size: 24rpx;
}
.bottom-safe {
  height: calc(60rpx + env(safe-area-inset-bottom));
}
</style>
