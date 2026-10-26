<template>
  <view class="page">
    <view class="bg-blobs"><view class="blob b1"></view><view class="blob b2"></view><view class="blob b3"></view></view>
    <!-- 顶部渐变头图 -->
    <view class="hero" :class="{ 'hero-on-img': heroImg }">
      <image v-if="heroImg" class="hero-img" :src="heroImg" mode="aspectFill" @error="onHeroImgError"></image>
      <view class="status-bar"></view>
      <view class="hero-nav">
        <view class="nav-side">
          <view class="nav-btn" @tap="drawerShow = true">
            <app-icon name="menu" :color="heroImg ? '#ffffff' : '#1d2129'" :size="22"></app-icon>
          </view>
        </view>
        <picker mode="date" fields="month" :value="pickerValue" @change="onMonthChange">
          <view class="hero-month">
            <text class="hero-month-text">{{ monthLabel }}</text>
            <app-icon name="chevron-down" :color="heroImg ? 'rgba(255,255,255,0.9)' : 'rgba(29,33,41,0.65)'" :size="16"></app-icon>
          </view>
        </picker>
        <view class="nav-side nav-side-right">
          <view class="nav-btn" @tap="goCalendar">
            <app-icon name="calendar" :color="heroImg ? '#ffffff' : '#1d2129'" :size="20"></app-icon>
          </view>
          <view class="nav-btn" @tap="goStats">
            <app-icon name="bar-chart" :color="heroImg ? '#ffffff' : '#1d2129'" :size="20"></app-icon>
          </view>
        </view>
      </view>

      <view class="hero-body">
        <text class="hero-label">本月支出</text>
        <text class="hero-amount">¥ {{ fmtMoney(monthExpense) }}</text>
      </view>

      <!-- 悬浮概览卡：压住头图底部 -->
      <view class="overview-card">
        <view class="ov-col">
          <text class="ov-label">月收入</text>
          <text class="ov-num">{{ fmtMoney(monthIncome) }}</text>
        </view>
        <view class="ov-divider"></view>
        <view class="ov-col">
          <text class="ov-label">本月结余</text>
          <text :class="['ov-num', monthBalance < 0 ? 'ov-out' : 'ov-in']">{{ fmtMoney(monthBalance) }}</text>
        </view>
        <view class="ov-divider"></view>
        <view class="ov-col">
          <text class="ov-label">日均支出</text>
          <text class="ov-num">{{ fmtMoney(monthAvg) }}</text>
        </view>
      </view>
    </view>

    <!-- 无障碍未开启提示 -->
    <view v-if="!listenerEnabled" class="notice notice-warn glass" @tap="goSettings">
      <view class="notice-ic">
        <app-icon name="bell" color="#ff9f43" :size="18"></app-icon>
      </view>
      <text class="notice-text">未开启"无障碍服务"，点此去开启自动记账</text>
      <app-icon name="chevron-right" color="#c2c6d1" :size="15"></app-icon>
    </view>

    <!-- 待确认草稿横幅 -->
    <view v-if="drafts.length" class="notice notice-draft glass" @tap="openDraft">
      <view class="notice-ic notice-ic-red">
        <app-icon name="bell" color="#f0655e" :size="18"></app-icon>
      </view>
      <view class="notice-main">
        <text class="notice-title">{{ drafts.length }} 笔自动记账待确认</text>
        <text class="notice-sub">来自支付页自动解析，点击逐笔确认</text>
      </view>
      <app-icon name="chevron-right" color="#f0655e" :size="16"></app-icon>
    </view>

    <!-- 最近7日支出 -->
    <view class="card glass">
      <view class="card-head">
        <view class="card-head-left">
          <text class="card-title">最近7日支出</text>
          <text class="card-sub">总计 ¥{{ fmtMoney(weekTotal) }}</text>
        </view>
        <view class="head-btn" @tap="goReport">
          <app-icon name="more-horizontal" color="#c2c6d1" :size="20"></app-icon>
        </view>
      </view>
      <view class="week-chart">
        <view v-for="(d, i) in weekData" :key="i" class="week-col">
          <text class="week-value" :class="{ 'week-value-empty': !d.value }">{{ d.value > 0 ? fmtNum(d.value) : '' }}</text>
          <view class="week-bar-wrap">
            <view
              class="week-bar"
              :class="{ 'week-bar-today': d.isToday }"
              :style="{ height: d.height + 'rpx' }"
            ></view>
          </view>
          <text :class="['week-label', d.isToday ? 'week-label-today' : '']">{{ d.label }}</text>
        </view>
      </view>
    </view>

    <!-- 账单列表 -->
    <bill-list :groups="groups" @row="onRow" @rowlong="onRowLong"></bill-list>
    <view v-if="!groups.length" class="empty">
      <view class="empty-ic">
        <app-icon name="wallet" color="#dfe2ec" :size="52" :stroke-width="1.5"></app-icon>
      </view>
      <text class="empty-text">本月还没有账单</text>
      <text class="empty-sub">点击下方 + 记第一笔</text>
    </view>

    <view class="bottom-safe"></view>

    <!-- 浮动记账按钮 -->
    <app-fab position="bottom-center" @click="goAdd"></app-fab>

    <!-- 侧滑抽屉：z-index 须高于 app-fab(99),展开时遮罩连同加号一起压暗 -->
    <wd-popup v-model="drawerShow" position="left" :z-index="200" :close-on-click-modal="true" custom-class="drawer">
      <view class="drawer-body">
        <view class="drawer-head">
          <view class="drawer-logo">
            <app-icon name="wallet" color="#3a2c05" :size="24"></app-icon>
          </view>
          <text class="drawer-name">孽账</text>
          <text class="drawer-slogan">记在本地 · 安心省心</text>
        </view>
        <view class="drawer-menu">
          <view
            v-for="m in drawerMenus"
            :key="m.name"
            class="drawer-item"
            @tap="onDrawerTap(m)"
          >
            <view class="drawer-item-ic">
              <app-icon :name="m.icon" :color="m.color || '#5b6170'" :size="19"></app-icon>
            </view>
            <text class="drawer-item-text">{{ m.name }}</text>
          </view>
        </view>
        <view class="drawer-foot">
          <text class="drawer-version">孽账 v{{ version }}</text>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { fmtMoney, fmtNum, pad2, groupByDay, weekdayCN, daysInMonth } from '@/common/fmt.js'
// #ifdef APP-PLUS
import {
  isAccessibilityEnabled, getPendingDrafts, getMonthBills,
  getSelfRepairState, repairAccessibilityService,
  deleteBill
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  isAccessibilityEnabled, getPendingDrafts, getMonthBills,
  getSelfRepairState, repairAccessibilityService,
  deleteBill
} from '@/common/web-bill-api.js'
// #endif

const now = new Date()
const selYear = ref(now.getFullYear())
const selMonth = ref(now.getMonth() + 1)
const bills = ref([])
const drafts = ref([])
const listenerEnabled = ref(true)
const drawerShow = ref(false)
const version = ref('1.0.0')
// 首页头图：设置页选择后存 storage（key：muyian_hero_img_v1），空表示默认渐变
const heroImg = ref('')

const drawerMenus = [
  { name: '日历', icon: 'calendar', url: '/pages/calendar/calendar' },
  { name: '统计', icon: 'pie-chart', url: '/pages/stats/stats' },
  { name: '分类排行', icon: 'bar-chart', url: '/pages/rank/rank' },
  { name: '日报表', icon: 'file-text', url: '/pages/report/report' },
  { name: '分类管理', icon: 'list', url: '/pages/categories/categories' },
  { name: '设置', icon: 'sliders', url: '/pages/settings/settings' }
]

const monthLabel = computed(() => selYear.value + '-' + pad2(selMonth.value))
const pickerValue = computed(() => selYear.value + '-' + pad2(selMonth.value))

const monthExpense = computed(() => sumWhere(bills.value, b => Number(b.money) < 0))
const monthIncome = computed(() => sumWhere(bills.value, b => Number(b.money) > 0))
const monthBalance = computed(() => monthIncome.value - monthExpense.value)
const monthAvg = computed(() => {
  const t = new Date()
  const isCurrent = t.getFullYear() === selYear.value && t.getMonth() + 1 === selMonth.value
  const elapsed = isCurrent ? t.getDate() : daysInMonth(selYear.value, selMonth.value)
  return monthExpense.value / Math.max(1, elapsed)
})

const groups = computed(() => groupByDay(bills.value, selYear.value, selMonth.value))

/** 近 7 天账单（跨月合并），用于周柱状图 */
const weekBills = ref([])

/** 最近 7 天（含今天）的支出 */
const weekData = computed(() => {
  const out = []
  const today = new Date()
  const todayZero = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  let max = 0
  for (let i = 6; i >= 0; i--) {
    const t = todayZero - i * 86400000
    const d = new Date(t)
    const y = d.getFullYear()
    const m = d.getMonth() + 1
    const day = d.getDate()
    let value = 0
    for (const b of weekBills.value) {
      if (Number(b.year) === y && Number(b.month) === m && Number(b.day) === day && Number(b.money) < 0) {
        value += Math.abs(Number(b.money))
      }
    }
    if (value > max) max = value
    out.push({ y, m, day, value, label: weekdayCN(y, m, day), isToday: i === 0, height: 0 })
  }
  for (const d of out) {
    d.height = d.value > 0 ? Math.max(16, Math.round(d.value / max * 190)) : 8
  }
  return out
})

const weekTotal = computed(() => weekData.value.reduce((s, d) => s + d.value, 0))

function sumWhere(list, pred) {
  let s = 0
  for (const b of list) {
    if (pred(b)) s += Math.abs(Number(b.money))
  }
  return s
}

function load() {
  try { listenerEnabled.value = isAccessibilityEnabled() } catch (e) { listenerEnabled.value = false }
  try { drafts.value = JSON.parse(getPendingDrafts() || '[]') } catch (e) { drafts.value = [] }
  try {
    bills.value = JSON.parse(getMonthBills(String(selYear.value), String(selMonth.value)) || '[]')
  } catch (e) { bills.value = [] }
  try { heroImg.value = uni.getStorageSync('muyian_hero_img_v1') || '' } catch (e) { heroImg.value = '' }
  loadWeekBills()
}

function onHeroImgError() {
  // 头图文件可能已被系统清理，回退默认渐变
  heroImg.value = ''
  try { uni.setStorageSync('muyian_hero_img_v1', '') } catch (e) {}
}

/** 近 7 天可能跨两个月，合并所需月份的账单 */
function loadWeekBills() {
  const months = new Set()
  const today = new Date()
  const todayZero = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  for (let i = 0; i < 7; i++) {
    const d = new Date(todayZero - i * 86400000)
    months.add(d.getFullYear() + '-' + (d.getMonth() + 1))
  }
  const merged = []
  for (const key of months) {
    const parts = key.split('-')
    try {
      const list = JSON.parse(getMonthBills(parts[0], parts[1]) || '[]')
      for (const b of list) merged.push(b)
    } catch (e) {
      // ignore
    }
  }
  weekBills.value = merged
}
function onMonthChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  const parts = v.split('-')
  if (parts.length >= 2) {
    selYear.value = Number(parts[0])
    selMonth.value = Number(parts[1])
    load()
  }
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

function openDraft() {
  if (!drafts.value.length) return
  goDraft(drafts.value[0])
}

function goDraft(d) {
  uni.navigateTo({ url: '/pages/add/add?token=' + encodeURIComponent(d.token) })
}

function goAdd() { uni.navigateTo({ url: '/pages/add/add' }) }
function goCalendar() { uni.navigateTo({ url: '/pages/calendar/calendar' }) }
function goStats() { uni.navigateTo({ url: '/pages/stats/stats' }) }
function goReport() { uni.navigateTo({ url: '/pages/report/report' }) }

function goSettings() {
  uni.navigateTo({ url: '/pages/settings/settings' })
}

function onDrawerTap(m) {
  drawerShow.value = false
  if (m.url) uni.navigateTo({ url: m.url })
}

let pollTimer = null

/** 头图模式下状态栏文字转白；离开本页恢复深色，避免其他浅色页面看不清 */
function applyStatusBarStyle() {
  // #ifdef APP-PLUS
  try {
    plus.navigator.setStatusBarStyle(heroImg.value ? 'light' : 'dark')
  } catch (e) {}
  // #endif
}

/** 无障碍自愈：已授权 WRITE_SECURE_SETTINGS 且服务掉开关时，静默补回（未授权或 H5 恒为无操作） */
function selfRepairQuietly() {
  try {
    const st = JSON.parse(getSelfRepairState() || '{}')
    if (st.hasPermission && !st.serviceEnabled) {
      repairAccessibilityService()
    }
  } catch (e) {}
}

onShow(() => {
  load()
  applyStatusBarStyle()
  selfRepairQuietly()
  // 草稿由原生层生成，前端定时轮询保证 App 常驻前台时也能看到新草稿
  if (pollTimer == null) {
    pollTimer = setInterval(load, 4000)
  }
})

onHide(() => {
  if (pollTimer != null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  // #ifdef APP-PLUS
  try { plus.navigator.setStatusBarStyle('dark') } catch (e) {}
  // #endif
})
</script>

<style lang="scss">
.bg-blobs {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: -1;
  pointer-events: none;
}
.bg-blobs .blob {
  position: absolute;
  border-radius: 50%;
}
.bg-blobs .b1 {
  right: -160rpx;
  top: 360rpx;
  width: 640rpx;
  height: 640rpx;
  background: rgba(255, 163, 26, 0.42);
  filter: blur(110px);
}
.bg-blobs .b2 {
  left: -180rpx;
  top: 1040rpx;
  width: 600rpx;
  height: 600rpx;
  background: rgba(57, 181, 117, 0.26);
  filter: blur(120px);
}
.bg-blobs .b3 {
  right: -140rpx;
  bottom: -120rpx;
  width: 600rpx;
  height: 600rpx;
  background: rgba(255, 187, 74, 0.4);
  filter: blur(110px);
}
.page {
  min-height: 100vh;
  background: transparent;
  padding-bottom: 40rpx;
}
.status-bar {
  height: var(--status-bar-height);
}
/* ---- 头图 ---- */
.hero {
  position: relative;
  background: linear-gradient(140deg, #ffd21e 0%, #ffb400 46%, #ff9500 100%);
  border-radius: 0 0 36rpx 36rpx;
  padding-bottom: 110rpx;
  /* 给压在底边的悬浮概览卡留出空间 */
  margin-bottom: 96rpx;
}
/* 自定义头图：铺满整个 hero，圆角与容器一致，原样显示不加蒙版 */
.hero-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  border-radius: 0 0 36rpx 36rpx;
  z-index: 0;
}
.hero-nav {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 12rpx 24rpx;
}
/* 左右两侧 flex:1 等宽，中间的月份切换器才能真正居中 */
.nav-side {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.nav-side-right {
  justify-content: flex-end;
}
.nav-btn {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    background-color: rgba(29, 33, 41, 0.1);
  }
}
.hero-month {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 10rpx 24rpx;
  border-radius: 32rpx;
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(10px) saturate(1.4);
}
.hero-month-text {
  color: #1d2129;
  font-size: 30rpx;
  font-weight: 700;
  margin-right: 8rpx;
}
.hero-body {
  position: relative;
  z-index: 1;
  padding: 40rpx 48rpx 0;
  display: flex;
  flex-direction: column;
}
.hero-label {
  color: rgba(74, 58, 0, 0.66);
  font-size: 26rpx;
  letter-spacing: 2rpx;
}
.hero-amount {
  color: #1d2129;
  font-size: 72rpx;
  font-weight: 700;
  line-height: 1.3;
  margin-top: 10rpx;
  letter-spacing: 1rpx;
}
/* 自定义头图模式：文字与月份胶囊转白色系 */
.hero-on-img .hero-label {
  color: rgba(255, 255, 255, 0.85);
}
.hero-on-img .hero-amount {
  color: #ffffff;
}
.hero-on-img .hero-month {
  background-color: rgba(0, 0, 0, 0.28);
}
.hero-on-img .hero-month-text {
  color: #ffffff;
}
.hero-on-img .nav-btn:active {
  background-color: rgba(255, 255, 255, 0.22);
}
/* ---- 悬浮概览卡 ---- */
.overview-card {
  position: absolute;
  left: 32rpx;
  right: 32rpx;
  bottom: -70rpx;
  z-index: 1;
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  backdrop-filter: blur(16px) saturate(1.6);
  border-radius: 28rpx;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 30rpx 0;
}
.ov-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ov-label {
  font-size: 24rpx;
  color: var(--c-sub);
}
.ov-num {
  margin-top: 10rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.ov-out { color: var(--c-out); }
.ov-in { color: var(--c-in); }
.ov-divider {
  width: 1rpx;
  height: 52rpx;
  background-color: var(--c-line);
}
/* ---- 提示条 ---- */
.notice {
  margin: 24rpx 24rpx 0;
  border-radius: 22rpx;
  padding: 22rpx 24rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.notice-draft {
  background: linear-gradient(120deg, rgba(255, 242, 241, 0.78), rgba(255, 236, 234, 0.78));
}
.notice-warn {
  background: rgba(255, 248, 236, 0.72);
  border: 1rpx solid #ffe4b8;
}
.notice-ic {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 18rpx;
  flex-shrink: 0;
}
.notice-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.notice-text {
  flex: 1;
  color: #b26a2c;
  font-size: 26rpx;
}
.notice-title {
  color: var(--c-out-deep);
  font-size: 28rpx;
  font-weight: 600;
}
.notice-sub {
  margin-top: 4rpx;
  color: #d88a85;
  font-size: 22rpx;
}
/* ---- 卡片 ---- */
.card {
  background-color: var(--c-card);
  border-radius: 28rpx;
  margin: 24rpx 24rpx 24rpx;
  padding: 30rpx 30rpx 24rpx;
  box-shadow: var(--shadow-card);
}
.card-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}
.card-head-left {
  display: flex;
  flex-direction: column;
}
.card-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
}
.card-sub {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--c-sub);
}
.head-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* ---- 7 日柱状图 ---- */
.week-chart {
  display: flex;
  flex-direction: row;
  align-items: flex-end;
}
.week-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.week-value {
  height: 34rpx;
  font-size: 22rpx;
  color: var(--c-out-deep);
  font-variant-numeric: tabular-nums;
}
.week-value-empty {
  color: transparent;
}
.week-bar-wrap {
  height: 200rpx;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.week-bar {
  width: 20rpx;
  min-height: 10rpx;
  border-radius: 12rpx;
  background: linear-gradient(180deg, #ff8a80, #f0655e);
}
.week-bar-today {
  background: linear-gradient(180deg, #ffd21e, #ffae00);
}
.week-label {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: var(--c-sub);
}
.week-label-today {
  color: var(--c-primary-deep);
  font-weight: 600;
}
/* ---- 空态 ---- */
.empty {
  padding: 90rpx 0 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-ic {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  margin-bottom: 28rpx;
}
.empty-text {
  color: var(--c-sub);
  font-size: 30rpx;
  font-weight: 600;
}
.empty-sub {
  margin-top: 10rpx;
  color: var(--c-dim);
  font-size: 24rpx;
}
.bottom-safe {
  height: calc(150rpx + env(safe-area-inset-bottom));
}
/* ---- 抽屉 ---- */
.drawer {
  width: 82vw !important;
  max-width: 640rpx;
  height: 100vh;
  background-color: var(--glass-bg-strong) !important;
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(22px) saturate(1.5);
  border-radius: 0 !important;
  box-shadow: 12rpx 0 48rpx rgba(29, 33, 41, 0.12) !important;
}
.drawer-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-top: var(--status-bar-height);
}
.drawer-head {
  padding: 70rpx 40rpx 46rpx;
  border-bottom: 1rpx solid var(--c-line);
  display: flex;
  flex-direction: column;
}
.drawer-logo {
  width: 96rpx;
  height: 96rpx;
  border-radius: 30rpx;
  background-color: var(--c-primary-weak);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.drawer-name {
  color: #1d2129;
  font-size: 38rpx;
  font-weight: 700;
}
.drawer-slogan {
  margin-top: 8rpx;
  color: var(--c-sub);
  font-size: 24rpx;
}
.drawer-menu {
  flex: 1;
  padding: 24rpx 20rpx;
  overflow-y: auto;
}
.drawer-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 26rpx 20rpx;
  border-radius: 20rpx;
  &:active {
    background-color: var(--c-primary-weak);
  }
}
.drawer-item-ic {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background-color: var(--glass-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 22rpx;
}
.drawer-item-text {
  flex: 1;
  font-size: 30rpx;
  color: var(--c-text);
  font-weight: 500;
}
.drawer-foot {
  padding: 30rpx 0 calc(30rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--c-line);
  display: flex;
  justify-content: center;
}
.drawer-version {
  font-size: 22rpx;
  color: var(--c-dim);
}
</style>
