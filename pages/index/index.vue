<template>
  <view
    :class="['page', themeClass]"
    @touchstart="onSwipeTouchStart"
    @touchmove="onSwipeTouchMove"
    @touchend="onSwipeTouchEnd"
    @touchcancel="onSwipeTouchEnd"
  >
    <!-- 玻璃折射层在 App.vue 的 .page::before（全站统一），这里不要再加一份 -->
    <!-- 抽屉打开时整页右移：抽屉从左侧露出，首页像卡片被推开 -->
    <view class="page-main" :style="pageStyle">
      <!-- 顶部渐变头图 -->
      <view class="hero" :class="{ 'hero-on-img': heroImg }">
        <image v-if="heroImg" class="hero-img" :src="heroImg" mode="aspectFill" @error="onHeroImgError"></image>
        <view v-if="!heroImg" class="hero-deco">
          <view class="deco-sheen"></view>
          <image
            class="hero-nezha"
            :src="nezhaStyle.src"
            :style="{ width: nezhaStyle.widthRpx + 'rpx', bottom: nezhaStyle.bottomRpx + 'rpx' }"
            mode="widthFix"
          ></image>
          <view class="deco-scrim"></view>
        </view>
        <view class="status-bar"></view>
        <view class="hero-nav">
          <view class="nav-side">
            <view class="nav-btn" @tap="openDrawer">
              <app-icon name="menu" color="#ffffff" :size="22"></app-icon>
            </view>
          </view>
          <picker mode="date" fields="month" :value="pickerValue" @change="onMonthChange">
            <view class="hero-month">
              <text class="hero-month-text">{{ monthLabel }}</text>
              <app-icon name="chevron-down" color="rgba(255,255,255,0.9)" :size="15"></app-icon>
            </view>
          </picker>
          <view class="nav-side nav-side-right">
            <view class="nav-btn" @tap="goCalendar">
              <app-icon name="calendar" color="#ffffff" :size="20"></app-icon>
            </view>
            <view class="nav-btn" @tap="goStats">
              <app-icon name="bar-chart" color="#ffffff" :size="20"></app-icon>
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
            <view class="ov-label-row">
              <view class="ov-ic">
                <app-icon name="trending-up" :color="COLOR_IN" :size="13" :stroke-width="2.4"></app-icon>
              </view>
              <text class="ov-label">月收入</text>
            </view>
            <text class="ov-num">{{ fmtMoney(monthIncome) }}</text>
          </view>
          <view class="ov-divider"></view>
          <view class="ov-col">
            <view class="ov-label-row">
              <view class="ov-ic">
                <app-icon name="wallet" :color="monthBalance < 0 ? COLOR_OUT : COLOR_IN" :size="13" :stroke-width="2.4"></app-icon>
              </view>
              <text class="ov-label">本月结余</text>
            </view>
            <text :class="['ov-num', monthBalance < 0 ? 'ov-out' : '']">{{ fmtMoney(monthBalance) }}</text>
          </view>
          <view class="ov-divider"></view>
          <view class="ov-col">
            <view class="ov-label-row">
              <view class="ov-ic">
                <app-icon name="clock" :color="COLOR_OUT" :size="13" :stroke-width="2.4"></app-icon>
              </view>
              <text class="ov-label">日均支出</text>
            </view>
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

      <!-- 电池优化提示：受限制时系统可能在锁屏后回收进程 -->
      <view v-if="!batteryOk" class="notice notice-warn glass" @tap="goBattery">
        <view class="notice-ic">
          <app-icon name="battery-charging" color="#ff9f43" :size="18"></app-icon>
        </view>
        <text class="notice-text">电池策略受限制，点此设为"不限制"以免后台被回收</text>
        <app-icon name="chevron-right" color="#c2c6d1" :size="15"></app-icon>
      </view>

      <!-- 最近7日支出 -->
      <view class="card glass">
        <view class="card-head">
          <view class="card-head-left">
            <view class="card-title-row">
              <view class="card-chip">
                <app-icon name="trending-up" color="#ffffff" :size="14" :stroke-width="2.4"></app-icon>
              </view>
              <text class="card-title">最近7日支出</text>
            </view>
            <text class="card-sub">总计 ¥{{ fmtMoney(weekTotal) }}</text>
          </view>
        </view>
        <view class="week-chart">
          <view v-for="(d, i) in weekData" :key="i" class="week-col">
            <view class="week-bar-wrap">
              <text class="week-value" :class="{ 'week-value-empty': !d.value }">{{ d.value > 0 ? fmtNum(d.value) : '' }}</text>
              <view
                class="week-bar"
                :class="{ 'week-bar-today': d.isToday, 'week-bar-empty': !d.value }"
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
          <app-icon name="wallet" :color="theme.primary" :size="50" :stroke-width="1.6"></app-icon>
        </view>
        <text class="empty-text">本月还没有账单</text>
        <text class="empty-sub">点击下方 + 记第一笔</text>
      </view>

      <view class="bottom-safe"></view>
    </view>

    <!-- 浮动记账按钮：自身保持视口定位，抽屉展开时淡出（避免跟着整页位移跳到文档底部） -->
    <app-fab :style="fabStyle" position="bottom-center" @click="goAdd"></app-fab>

    <!-- 侧滑抽屉：面板与首页共用 drawerProgress，拖多少露多少 -->
    <view v-if="drawerShow" class="drawer-mask" :style="drawerMaskStyle" @tap="closeDrawer"></view>
    <view v-if="drawerShow" class="drawer" :style="drawerPanelStyle">
      <view class="drawer-body">
        <view class="drawer-head">
          <view class="drawer-ring drawer-ring-lg"></view>
          <view class="drawer-ring drawer-ring-sm"></view>
          <view class="drawer-brand">
            <view class="drawer-logo">
              <app-icon name="wallet" color="#ffffff" :size="26" :stroke-width="2.2"></app-icon>
            </view>
            <view class="drawer-brand-text">
              <text class="drawer-name">孽账</text>
              <text class="drawer-slogan">记在本地 · 安心省心</text>
            </view>
          </view>
        </view>

        <view class="drawer-menu">
          <text class="drawer-section">功能</text>
          <view
            v-for="m in drawerMenus"
            :key="m.name"
            class="drawer-item"
            @tap="onDrawerTap(m)"
          >
            <view class="drawer-item-ic" :style="{ background: m.grad }">
              <app-icon :name="m.icon" color="#ffffff" :size="20"></app-icon>
            </view>
            <text class="drawer-item-text">{{ m.name }}</text>
            <app-icon name="chevron-right" color="#c9cdd6" :size="16"></app-icon>
          </view>

          <view class="drawer-status" @tap="goSettings">
            <view :class="['drawer-status-dot', listenerEnabled ? 'is-on' : 'is-off']"></view>
            <view class="drawer-status-main">
              <text class="drawer-status-title">{{ listenerEnabled ? '自动记账运行中' : '自动记账未开启' }}</text>
              <text class="drawer-status-sub">{{ listenerEnabled ? '无障碍服务已授权' : '点此开启无障碍服务' }}</text>
            </view>
            <app-icon name="chevron-right" color="#c9cdd6" :size="15"></app-icon>
          </view>
        </view>

        <view class="drawer-foot">
          <view class="drawer-privacy">
            <app-icon name="lock" color="#a9aeb9" :size="13"></app-icon>
            <text class="drawer-privacy-text">数据仅保存在本机</text>
          </view>
          <text class="drawer-version">孽账 v{{ version }}</text>
        </view>
      </view>
    </view>

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
import { onShow, onHide, onReady, onBackPress } from '@dcloudio/uni-app'
import { fmtMoney, fmtNum, pad2, groupByDay, weekdayCN, daysInMonth } from '@/common/fmt.js'
import { theme, themeClass, refreshTheme, COLOR_IN, COLOR_OUT } from '@/common/theme.js'
import { nezhaStyle, refreshNezhaStyle } from '@/common/hero-nezha.js'
// #ifdef APP-PLUS
import {
  isAccessibilityEnabled, getMonthBills,
  getSelfRepairState, repairAccessibilityService,
  isIgnoringBatteryOptimizations, requestIgnoreBatteryOptimizations,
  deleteBill
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  isAccessibilityEnabled, getMonthBills,
  getSelfRepairState, repairAccessibilityService,
  isIgnoringBatteryOptimizations, requestIgnoreBatteryOptimizations,
  deleteBill
} from '@/common/web-bill-api.js'
// #endif

const now = new Date()
const selYear = ref(now.getFullYear())
const selMonth = ref(now.getMonth() + 1)
const bills = ref([])
const listenerEnabled = ref(true)
// 系统电池优化是否已对本应用豁免（true = 不限制）
const batteryOk = ref(true)
const drawerShow = ref(false)
const version = ref('1.0.0')
// 账单操作菜单（wot action sheet）
const actionShow = ref(false)
const actionBill = ref(null)
const actionItems = ref([])
// 首页头图：设置页选择后存 storage（key：muyian_hero_img_v1），空表示默认渐变
const heroImg = ref('')

const drawerMenus = [
  { name: '分类管理', icon: 'list', url: '/pages/categories/categories', grad: 'var(--grad-blue)' },
  { name: '账单日历', icon: 'calendar', url: '/pages/calendar/calendar', grad: 'var(--grad-mint)' },
  { name: '统计分析', icon: 'bar-chart', url: '/pages/stats/stats', grad: 'var(--grad-sky)' },
  { name: '设置', icon: 'sliders', url: '/pages/settings/settings', grad: 'var(--grad-violet)' }
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
    d.height = d.value > 0 ? Math.max(14, Math.round(d.value / max * 132)) : 8
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
  // 先静默补回掉开关的服务，再读状态：否则刚补回时读到的仍是 false，横幅会一直挂着
  selfRepairQuietly()
  try { listenerEnabled.value = isAccessibilityEnabled() } catch (e) { listenerEnabled.value = false }
  try { batteryOk.value = isIgnoringBatteryOptimizations() } catch (e) { batteryOk.value = true }
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
function goCalendar() { uni.navigateTo({ url: '/pages/calendar/calendar' }) }
function goStats() { uni.navigateTo({ url: '/pages/stats/stats' }) }

function goSettings() {
  uni.navigateTo({ url: '/pages/settings/settings' })
}

/** 电池优化引导：确认后弹系统"不限制"确认框 */
function goBattery() {
  // #ifdef APP-PLUS
  uni.showModal({
    title: '关闭电池优化',
    content: '在接下来的系统弹窗中选择"不限制"，锁屏或切到后台后自动记账才不会被系统回收。部分机型还需在"应用详情"里允许自启动。',
    confirmText: '去设置',
    cancelText: '以后再说',
    success: (r) => {
      if (!r.confirm) return
      try { requestIgnoreBatteryOptimizations() } catch (e) {}
    }
  })
  // #endif
}

/**
 * 首页返回键：不退出应用，把任务移到后台回到桌面，进程继续保活。
 * 抽屉展开时先收起抽屉。
 */
onBackPress(() => {
  if (drawerShow.value) {
    closeDrawer()
    return true
  }
  // #ifdef APP-PLUS
  try {
    const activity = plus.android.runtimeMainActivity()
    plus.android.importClass(activity)
    activity.moveTaskToBack(true)
  } catch (e) {}
  return true
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
})

/** 跳转前立即收起（不做收合动画，避免和新页面动画叠在一起） */
function closeDrawerNow() {
  clearTimeout(drawerTimer)
  clearTimeout(drawerOpenTimer)
  drawerPressing.value = false
  drawerProgress.value = 0
  drawerShow.value = false
}

function onDrawerTap(m) {
  closeDrawerNow()
  if (m.url) uni.navigateTo({ url: m.url })
}

/** 触摸点坐标（app-vue 与 H5 都提供 clientX，pageX 兜底） */
function touchPoint(e) {
  const t = e.touches && e.touches[0]
  if (!t) return null
  return {
    x: t.clientX != null ? t.clientX : t.pageX,
    y: t.clientY != null ? t.clientY : t.pageY
  }
}

/* ---- 抽屉跟手拖动：抽屉滑出宽度与首页右移宽度始终相等 ---- */
const DRAWER_MS = 280
const DRAWER_MAX_W = 250
const DRAWER_EASE = 'cubic-bezier(0.22, 0.61, 0.36, 1)'
const drawerProgress = ref(0) // 0 = 完全收起，1 = 完全展开
const drawerPressing = ref(false) // 手指拖动中：关闭过渡，严格跟手
const drawerWidth = ref(DRAWER_MAX_W)
let drawerTimer = null
let drawerOpenTimer = null
let swipeStartX = 0
let swipeStartY = 0
let swipeBase = 0
let swipeDragging = false

/** 抽屉宽度与样式里的 250px / max-width: 78vw 保持一致 */
function computeDrawerWidth() {
  try {
    const w = Number(uni.getSystemInfoSync().windowWidth) || 390
    drawerWidth.value = Math.min(DRAWER_MAX_W, w * 0.78)
  } catch (e) {}
}
computeDrawerWidth()

const drawerPanelStyle = computed(() => {
  const x = -drawerWidth.value * (1 - drawerProgress.value)
  const t = drawerPressing.value ? 'none' : `transform ${DRAWER_MS}ms ${DRAWER_EASE}`
  return `transform: translate3d(${x.toFixed(1)}px, 0, 0); transition: ${t};`
})

const drawerMaskStyle = computed(() => {
  const t = drawerPressing.value ? 'none' : `opacity ${DRAWER_MS}ms ease`
  return `opacity: ${drawerProgress.value.toFixed(3)}; transition: ${t};`
})

/** 首页位移量 = 抽屉展开宽度 × 进度，与抽屉完全同步 */
const pageStyle = computed(() => {
  const x = drawerWidth.value * drawerProgress.value
  const t = drawerPressing.value ? 'none' : `transform ${DRAWER_MS}ms ${DRAWER_EASE}`
  return `transform: translate3d(${x.toFixed(1)}px, 0, 0); transition: ${t};`
})

const fabStyle = computed(() => ({
  opacity: 1 - drawerProgress.value,
  transition: drawerPressing.value ? 'none' : `opacity ${DRAWER_MS}ms ease`
}))

function openDrawer() {
  if (drawerShow.value) return
  computeDrawerWidth()
  drawerPressing.value = false
  drawerProgress.value = 0
  drawerShow.value = true
  // 先以收起态挂载一帧再设目标值，CSS 过渡才有起点
  clearTimeout(drawerOpenTimer)
  drawerOpenTimer = setTimeout(() => {
    if (drawerShow.value && !drawerPressing.value) drawerProgress.value = 1
  }, 30)
}

function closeDrawer() {
  if (!drawerShow.value) return
  clearTimeout(drawerOpenTimer)
  drawerPressing.value = false
  drawerProgress.value = 0
  clearTimeout(drawerTimer)
  drawerTimer = setTimeout(() => {
    if (drawerProgress.value === 0) drawerShow.value = false
  }, DRAWER_MS + 40)
}

/** 整屏手势：右滑拉出、左滑收起，进度按手指位移实时计算 */
const SWIPE_MIN_PX = 4

function onSwipeTouchStart(e) {
  if (actionShow.value) return
  const p = touchPoint(e)
  if (!p) return
  clearTimeout(drawerTimer)
  clearTimeout(drawerOpenTimer)
  swipeStartX = p.x
  swipeStartY = p.y
  swipeBase = drawerProgress.value
  swipeDragging = false
}

function onSwipeTouchMove(e) {
  if (actionShow.value) return
  const p = touchPoint(e)
  if (!p) return
  const dx = p.x - swipeStartX
  const dy = p.y - swipeStartY
  if (!swipeDragging) {
    if (Math.abs(dx) < SWIPE_MIN_PX) return
    if (Math.abs(dx) < Math.abs(dy) * 1.2) return // 判定为竖向滚动，不抢手势
    if (!drawerShow.value && dx < 0) return // 已收起时左滑无意义
    if (!drawerShow.value) {
      drawerProgress.value = 0
      drawerShow.value = true
    }
    swipeDragging = true
    drawerPressing.value = true
  }
  const next = swipeBase + dx / drawerWidth.value
  drawerProgress.value = Math.max(0, Math.min(1, next))
}

function onSwipeTouchEnd() {
  if (!swipeDragging) return
  swipeDragging = false
  drawerPressing.value = false
  // 松手看拖过的距离：过半即展开，否则收回
  if (drawerProgress.value >= 0.5) drawerProgress.value = 1
  else closeDrawer()
}

/** 首页头图始终是主题渐变或用户图片，导航文字/图标都是白色，状态栏统一用白色图标 */
function applyStatusBarStyle() {
  // #ifdef APP-PLUS
  try {
    plus.navigator.setStatusBarStyle('light')
  } catch (e) {}
  // #endif
}

/** 无障碍自愈：已授权 WRITE_SECURE_SETTINGS 且服务掉开关时，静默补回（未授权或 H5 恒为无操作） */
function selfRepairQuietly() {
  try {
    const st = JSON.parse(getSelfRepairState() || '{}')
    if (st.hasPermission && !st.serviceEnabled) {
      const resp = JSON.parse(repairAccessibilityService() || '{}')
      if (resp.serviceEnabled) listenerEnabled.value = true
    }
  } catch (e) {}
}

onReady(() => {
  // 冷启动时首帧之前设置的状态栏样式可能被启动图/系统重置，渲染完成后再补一次
  applyStatusBarStyle()
  setTimeout(() => applyStatusBarStyle(), 300)
})

onShow(() => {
  refreshTheme()
  refreshNezhaStyle()
  computeDrawerWidth()
  load()
  applyStatusBarStyle()
})

onHide(() => {
  // #ifdef APP-PLUS
  try { plus.navigator.setStatusBarStyle('dark') } catch (e) {}
  // #endif
})
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  /* 首页背景：极淡的主题色纯色底 */
  background-color: var(--c-page-wash);
  padding-bottom: 40rpx;
}
/* 折射层在 App.vue 的 .page::before，全站统一，这里不再单独铺光雾 */
/* 抽屉展开时整页右移：位移与抽屉宽度由 drawerProgress 同步驱动（见 pageStyle） */
.page-main {
  transform-origin: left center;
}
.status-bar {
  height: var(--status-bar-height);
}
/* ---- 头图 ---- */
.hero {
  position: relative;
  background: var(--grad-primary-strong);
  border-radius: 0 0 36rpx 36rpx;
  padding-bottom: 110rpx;
  /* 给压在底边的悬浮概览卡留出空间 */
  margin-bottom: 96rpx;
  box-shadow: var(--shadow-primary-lg);
}
/* 渐变头的装饰：柔光 + 右侧哪吒，纯装饰不占交互 */
.hero-deco {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  border-radius: 0 0 36rpx 36rpx;
}
.deco-sheen {
  position: absolute;
  left: -15%;
  top: -45%;
  width: 80%;
  height: 130%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.32) 0%, rgba(255, 255, 255, 0) 70%);
}
/* 首页哪吒：透明 WebP，靠右但留出间距；宽度与上提量由 common/hero-nezha.js 决定 */
.hero-nezha {
  position: absolute;
  right: 20rpx;
  display: block;
  filter: drop-shadow(0 5rpx 12rpx rgba(90, 40, 0, 0.22));
}
/* 底部轻压暗：白字更稳，也让压在上面的玻璃概览卡有层次 */
.deco-scrim {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 46%;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.16) 100%);
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
  background-color: transparent;
  border: none;
  &:active {
    background-color: rgba(255, 255, 255, 0.24);
  }
}
.hero-month {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  background-color: transparent;
  border: none;
  &:active {
    background-color: rgba(255, 255, 255, 0.18);
  }
}
.hero-month-text {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
  margin-right: 6rpx;
  letter-spacing: 0.5rpx;
  text-shadow: 0 2rpx 10rpx rgba(120, 70, 20, 0.28);
}
.hero-body {
  position: relative;
  z-index: 1;
  padding: 40rpx 48rpx 0;
  display: flex;
  flex-direction: column;
}
.hero-label {
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  letter-spacing: 2rpx;
}
.hero-amount {
  color: #ffffff;
  font-size: 72rpx;
  font-weight: 700;
  line-height: 1.3;
  margin-top: 10rpx;
  letter-spacing: 1rpx;
  text-shadow: 0 2rpx 12rpx rgba(150, 80, 0, 0.24);
}
/* 自定义头图模式：文字转白色系 */
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
  border: 1rpx solid var(--glass-border);
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
.ov-label-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.ov-ic {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 8rpx;
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
.notice-warn {
  background-color: var(--c-primary-weak);
  border: 1rpx solid var(--c-primary-tint);
}
.notice-ic {
  width: 60rpx;
  height: 60rpx;
  border-radius: 18rpx;
  background-color: var(--c-primary-weak);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 18rpx;
  flex-shrink: 0;
}
.notice-text {
  flex: 1;
  color: var(--c-primary-deep);
  font-size: 26rpx;
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
  margin-bottom: 20rpx;
}
.card-head-left {
  display: flex;
  flex-direction: column;
}
.card-title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.card-chip {
  width: 40rpx;
  height: 40rpx;
  border-radius: 12rpx;
  background: var(--grad-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  box-shadow: var(--shadow-primary-sm);
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
  height: 26rpx;
  line-height: 26rpx;
  margin-bottom: 8rpx;
  font-size: 21rpx;
  color: var(--c-primary-deep);
  font-variant-numeric: tabular-nums;
}
.week-value-empty {
  color: transparent;
}
/* 数值与柱子同列底对齐：数字始终贴在柱顶，短柱也不会悬在半空 */
.week-bar-wrap {
  height: 176rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.week-bar {
  width: 26rpx;
  min-height: 10rpx;
  border-radius: 13rpx;
  background: var(--c-bar);
}
/* 当天柱用实底强调色：深色主题的 --grad-primary 是深灰，压在深色卡上会直接看不见 */
.week-bar-today {
  background: var(--c-primary);
  box-shadow: var(--shadow-primary-sm);
}
/* 当天没支出：用中性底色占位（写在 today 之后，空柱不会抢强调色） */
.week-bar-empty {
  background: var(--c-fill);
  box-shadow: none;
}
.week-label {
  margin-top: 10rpx;
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
  border-radius: 44rpx;
  background-color: var(--c-primary-weak);
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
/* 与首页、记一笔页保持同一底色：极淡主题色实底；位移由 drawerPanelStyle 驱动 */
.drawer {
  position: fixed;
  left: 0;
  top: 0;
  z-index: 200;
  width: 250px;
  max-width: 78vw;
  height: 100vh;
  background-color: var(--c-page-wash);
  box-shadow: 12rpx 0 48rpx rgba(29, 33, 41, 0.16);
  will-change: transform;
}
/* 遮罩透明度跟着拖动进度走 */
.drawer-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 199;
  background-color: var(--c-mask);
  will-change: opacity;
}
.drawer-body {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.drawer-head {
  position: relative;
  z-index: 1;
  padding: calc(var(--status-bar-height) + 48rpx) 40rpx 46rpx;
  background: var(--grad-primary-strong);
  overflow: hidden;
}
.drawer-ring {
  position: absolute;
  border-radius: 50%;
  border: 2rpx solid rgba(255, 255, 255, 0.18);
  pointer-events: none;
}
.drawer-ring-lg {
  width: 380rpx;
  height: 380rpx;
  right: -130rpx;
  top: -150rpx;
}
.drawer-ring-sm {
  width: 190rpx;
  height: 190rpx;
  right: 60rpx;
  bottom: -70rpx;
  background: rgba(255, 255, 255, 0.08);
}
.drawer-brand {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.drawer-logo {
  width: 92rpx;
  height: 92rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.22);
  border: 1rpx solid rgba(255, 255, 255, 0.4);
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
  flex-shrink: 0;
}
.drawer-brand-text {
  display: flex;
  flex-direction: column;
}
.drawer-name {
  color: #ffffff;
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.drawer-slogan {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.85);
  font-size: 23rpx;
  letter-spacing: 0.5rpx;
}
.drawer-menu {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 34rpx 20rpx 20rpx;
  overflow-y: auto;
}
.drawer-section {
  display: block;
  padding: 0 24rpx 14rpx;
  font-size: 22rpx;
  color: var(--c-sub);
  letter-spacing: 2rpx;
  opacity: 0.85;
}
.drawer-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 20rpx;
  border-radius: 22rpx;
  margin-bottom: 4rpx;
  &:active {
    background-color: var(--c-primary-weak);
  }
}
.drawer-item-ic {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 22rpx;
  box-shadow: 0 6rpx 14rpx rgba(60, 50, 20, 0.12);
  flex-shrink: 0;
}
.drawer-item-text {
  flex: 1;
  font-size: 29rpx;
  color: var(--c-text);
  font-weight: 500;
}
/* 自动记账状态：贴在菜单底部，兼作设置入口 */
.drawer-status {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: auto 8rpx 4rpx;
  padding: 22rpx 20rpx;
  border-radius: 22rpx;
  background-color: var(--glass-bg);
  border: 1rpx solid var(--glass-border);
  &:active {
    background-color: var(--glass-bg-strong);
  }
}
.drawer-status-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 18rpx;
  flex-shrink: 0;
}
.drawer-status-dot.is-on {
  background-color: var(--c-in);
  box-shadow: 0 0 0 6rpx rgba(34, 184, 119, 0.14);
}
.drawer-status-dot.is-off {
  background-color: var(--c-out);
  box-shadow: 0 0 0 6rpx rgba(240, 101, 94, 0.14);
}
.drawer-status-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.drawer-status-title {
  font-size: 25rpx;
  font-weight: 600;
  color: var(--c-text);
}
.drawer-status-sub {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-sub);
}
.drawer-foot {
  position: relative;
  z-index: 1;
  padding: 24rpx 32rpx calc(28rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  align-items: center;
}
.drawer-privacy {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background-color: var(--glass-bg);
  border: 1rpx solid var(--c-line);
}
.drawer-privacy-text {
  margin-left: 8rpx;
  font-size: 21rpx;
  color: var(--c-sub);
}
.drawer-version {
  margin-top: 16rpx;
  font-size: 21rpx;
  color: var(--c-dim);
}
</style>
