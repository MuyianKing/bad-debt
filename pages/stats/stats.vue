<template>
  <view :class="['page', themeClass]">
    <wd-navbar
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      :bordered="navSolid"
      :custom-style="navStyle"
      custom-class="stats-navbar"
      @click-left="goBack"
    >
      <template #title>
        <view class="nav-period">
          <view class="nav-step" @tap="shiftPeriod(-1)">
            <app-icon name="chevron-left" color="#8a90a0" :size="16"></app-icon>
          </view>
          <picker mode="date" :fields="range === 'month' ? 'month' : 'year'" :value="pickerValue" @change="onPeriodChange">
            <view class="nav-date">
              <text class="nav-date-text">{{ periodDisplay }}</text>
            </view>
          </picker>
          <view class="nav-step" @tap="shiftPeriod(1)">
            <app-icon name="chevron-right" color="#8a90a0" :size="16"></app-icon>
          </view>
        </view>
      </template>
      <template #right>
        <wd-segmented
          custom-class="range-seg"
          size="small"
          :value="rangeLabel"
          :options="['月', '年']"
          @change="onRangeChange"
        ></wd-segmented>
      </template>
    </wd-navbar>

    <!-- 收支总览 -->
    <view class="card total-card">
      <view class="total-head">
        <text class="total-label">{{ range === 'month' ? '本月支出' : '本年支出' }}</text>
        <view class="total-count">{{ bills.length }} 笔</view>
      </view>
      <view class="total-amount">
        <text class="total-currency">¥</text>
        <text class="total-value">{{ fmtMoney(expenseTotal) }}</text>
      </view>

      <view class="total-metrics">
        <view class="metric">
          <text class="metric-label">收入</text>
          <text class="metric-value">{{ fmtMoney(incomeTotal) }}</text>
        </view>
        <view class="metric-div"></view>
        <view class="metric">
          <text class="metric-label">结余</text>
          <text class="metric-value">{{ fmtMoney(balance) }}</text>
        </view>
        <view class="metric-div"></view>
        <view class="metric">
          <text class="metric-label">{{ range === 'month' ? '日均支出' : '月均支出' }}</text>
          <text class="metric-value">{{ fmtMoney(avgExpense) }}</text>
        </view>
      </view>
    </view>

    <!-- 每日/每月统计 -->
    <view class="card glass">
      <view class="card-head">
        <view class="card-head-title">
          <view class="card-icon">
            <app-icon name="bar-chart" :color="theme.primaryDeep" :size="18" :solid="true"></app-icon>
          </view>
          <text class="card-title">{{ range === 'month' ? '每日统计' : '每月统计' }}</text>
        </view>
        <view class="card-head-icons">
          <view class="mini-toggle">
            <text
              :class="['mini-toggle-item', chartType === 'bar' ? 'mini-toggle-active' : '']"
              @tap="switchChart('bar')"
            >柱状</text>
            <text
              :class="['mini-toggle-item', chartType === 'line' ? 'mini-toggle-active' : '']"
              @tap="switchChart('line')"
            >折线</text>
          </view>
        </view>
      </view>
      <view class="bar-chart" v-if="maxBarValue > 0">
        <view class="bar-y">
          <text v-for="(t, i) in yTicks" :key="i" class="bar-y-label">{{ fmtTick(t) }}</text>
        </view>
        <view id="line-wrap" class="bar-body">
          <view class="chart-grid">
            <view v-for="n in 4" :key="n" class="grid-line" :style="{ top: ((n - 1) * 25) + '%' }"></view>
          </view>
          <canvas
            v-if="chartType === 'line'"
            canvas-id="dailyLine"
            id="dailyLine"
            class="line-canvas"
            :style="{ width: lineW + 'px', height: lineH + 'px' }"
          ></canvas>
          <view v-else :class="['bar-cols', range === 'year' ? 'is-year' : '']">
            <view
              v-for="(b, i) in bars"
              :key="i"
              class="bar-col"
              @tap="onBarTap(b)"
            >
              <view class="bar-bar bar-out" :style="{ height: b.height + 'rpx' }"></view>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <view class="empty-copy">
          <text class="empty-text">该期间暂无支出</text>
          <text class="empty-hint">记一笔后显示每日趋势</text>
        </view>
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
        <view class="card-head-title">
          <view class="card-icon">
            <app-icon name="bar-list" :color="theme.primaryDeep" :size="18" :solid="true"></app-icon>
          </view>
          <text class="card-title">分类报表</text>
        </view>
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
            <template v-if="selSeg">
              <text class="donut-center-name">{{ selSeg.name }}</text>
              <text class="donut-center-amount">¥{{ fmtMoney(selSeg.value) }}</text>
              <text class="donut-center-pct">{{ selSeg.pct }}</text>
            </template>
            <template v-else>
              <text class="donut-center-name">{{ donutKind === 'expense' ? '总支出' : '总收入' }}</text>
              <text class="donut-center-amount">¥{{ fmtMoney(donutTotal) }}</text>
              <text class="donut-center-pct">点击圆环查看</text>
            </template>
          </view>
          <!-- 透明命中层：部分端 canvas 收不到 tap，改由视图接管点击 -->
          <view id="donut-hit" class="donut-hit" @tap="onDonutTap"></view>
        </view>
        <view class="cat-list">
          <view
            v-for="s in donutSegments"
            :key="s.name"
            class="cat-row"
            :style="selName === s.name ? { backgroundColor: tintOf(s.color) } : ''"
            @tap="openCat(s)"
          >
            <view class="cat-ic" :style="{ background: catBg(s.color) }">
              <app-icon :name="iconOf(s.name, donutKind)" color="#ffffff" :size="18" :solid="true"></app-icon>
            </view>
            <view class="cat-main">
              <view class="cat-line">
                <text class="cat-name">{{ s.name }}</text>
                <text class="cat-pct">{{ s.pct }}</text>
              </view>
              <view class="cat-bar">
                <view class="cat-bar-fill" :style="{ width: barWidth(s), backgroundColor: s.color }"></view>
              </view>
            </view>
            <text class="cat-amt">{{ fmtMoney(s.value) }}</text>
            <app-icon name="chevron-right" color="#c6cad4" :size="14"></app-icon>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <view class="empty-copy">
          <text class="empty-text">该期间暂无{{ donutKind === 'expense' ? '支出' : '收入' }}</text>
          <text class="empty-hint">记一笔后显示分类占比</text>
        </view>
      </view>
    </view>

    <!-- 分类明细：底部弹层，列出该类全部账单 -->
    <wd-popup
      v-model="catShow"
      position="bottom"
      :z-index="300"
      :safe-area-inset-bottom="true"
      custom-class="cat-sheet"
    >
      <view class="cat-grab"></view>
      <view class="cat-sheet-head">
        <view class="cat-sheet-left">
          <view class="cat-ic" :style="{ background: catBg(catColor) }">
            <app-icon :name="iconOf(catName, donutKind)" color="#ffffff" :size="18" :solid="true"></app-icon>
          </view>
          <view class="cat-sheet-title">
            <text class="cat-sheet-name">{{ catName }}</text>
            <text class="cat-sheet-meta">{{ catCount }} 笔 · 占比 {{ catPct }}</text>
          </view>
        </view>
        <text class="cat-sheet-total">¥{{ fmtMoney(catTotal) }}</text>
      </view>
      <view class="cat-sheet-list">
        <view v-for="b in catBills" :key="b.id" class="cat-bill" @tap="onCatBillTap(b)">
          <view class="cat-bill-main">
            <text class="cat-bill-name">{{ b.costType || catName }}</text>
            <text class="cat-bill-meta">{{ billMeta(b) }}</text>
          </view>
          <text :class="['cat-bill-amt', Number(b.money) < 0 ? 'is-out' : 'is-in']">{{ Number(b.money) < 0 ? '-' : '+' }}{{ fmtMoney(Math.abs(Number(b.money))) }}</text>
        </view>
      </view>
    </wd-popup>

    <!-- 日报表 / 月报表：月视图按天、年视图按月 -->
    <view class="card">
      <view class="card-head">
        <view class="card-head-title">
          <view class="card-icon">
            <app-icon name="file-text" :color="theme.primaryDeep" :size="18" :solid="true"></app-icon>
          </view>
          <text class="card-title">{{ range === 'month' ? '日报表' : '月报表' }}</text>
        </view>
        <text class="report-hint">{{ reportRows.length }}{{ range === 'month' ? ' 天有记录' : ' 个月有记录' }}</text>
      </view>
      <view class="report-table" v-if="reportRows.length">
        <view class="report-tr report-th">
          <text class="report-td report-date">{{ range === 'month' ? '日期' : '月份' }}</text>
          <text class="report-td report-num">支出</text>
          <text class="report-td report-num">收入</text>
        </view>
        <view v-for="r in reportRows" :key="r.key" class="report-tr">
          <text class="report-td report-date">{{ r.label }}</text>
          <text class="report-td report-num report-out">{{ r.expense > 0 ? fmtMoney(r.expense) : '-' }}</text>
          <text class="report-td report-num report-in">{{ r.income > 0 ? fmtMoney(r.income) : '-' }}</text>
        </view>
      </view>
      <view v-else class="empty">
        <view class="empty-copy">
          <text class="empty-text">该期间暂无账单</text>
          <text class="empty-hint">记一笔后显示{{ range === 'month' ? '每日' : '每月' }}明细</text>
        </view>
      </view>
    </view>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref, computed, watch, nextTick, getCurrentInstance } from 'vue'
import { onReady, onShow, onPageScroll } from '@dcloudio/uni-app'
import { fmtMoney, fmtTick, pad2, daysInMonth } from '@/common/fmt.js'
import { topNameOf, colorOf, iconOf, tintOf } from '@/common/cats.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
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
// 趋势图形态：柱状 / 折线（两者都只画支出，收入看下方分类报表）
const chartType = ref('bar')
const donutKind = ref('expense')
const donutSize = Math.round(uni.upx2px(300))
// 折线画布尺寸：宽度由布局测量（y 轴刻度列宽度不固定），高度与柱状图区等高
const lineH = Math.round(uni.upx2px(330))
const lineW = ref(Math.round(uni.upx2px(548)))
// 画布组件实例：事件回调/定时器里拿不到 getCurrentInstance()，setup 阶段先存下来
const pageInst = getCurrentInstance()

const rangeLabel = computed(() => (range.value === 'month' ? '月' : '年'))
const pickerValue = computed(() => (range.value === 'month' ? year.value + '-' + pad2(month.value) : String(year.value)))
const periodDisplay = computed(() => (range.value === 'month' ? year.value + '年' + month.value + '月' : year.value + '年'))
// 顶栏滚动态：静止时透明让状态栏与页面同色，滚动后转毛玻璃避免内容从标题下穿过
const navSolid = ref(false)
// wd-navbar 的 custom-class 进不去组件内层样式域，底色只能走 custom-style
const navStyle = computed(() =>
  'transition: background-color 0.18s ease;' +
  (navSolid.value
    ? 'background-color: var(--glass-nav-bg);-webkit-backdrop-filter: blur(18px) saturate(1.6);backdrop-filter: blur(18px) saturate(1.6);'
    : 'background-color: transparent;')
)

const expenseBills = computed(() => bills.value.filter(b => Number(b.money) < 0))
const incomeBills = computed(() => bills.value.filter(b => Number(b.money) > 0))
const expenseTotal = computed(() => expenseBills.value.reduce((s, b) => s + Math.abs(Number(b.money)), 0))
const incomeTotal = computed(() => incomeBills.value.reduce((s, b) => s + Number(b.money), 0))
const balance = computed(() => incomeTotal.value - expenseTotal.value)

// 日报表/月报表：月视图按天、年视图按月，只列有账单的行，倒序
const reportRows = computed(() => {
  const byMonth = range.value === 'year'
  const map = new Map()
  for (const b of bills.value) {
    const key = byMonth ? Number(b.month) : Number(b.day)
    const row = map.get(key) || { key, expense: 0, income: 0 }
    if (Number(b.money) < 0) row.expense += Math.abs(Number(b.money))
    else row.income += Number(b.money)
    map.set(key, row)
  }
  return Array.from(map.values())
    .sort((a, b) => b.key - a.key)
    .map(r => ({
      key: r.key,
      label: byMonth ? r.key + '月' : pad2(month.value) + '-' + pad2(r.key),
      expense: r.expense,
      income: r.income
    }))
})

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

/* ---------- 趋势图（柱状 / 折线，数据同为支出） ---------- */
// 每日/每月支出汇总；柱高统一按 axisMax 换算，柱顶才能和左侧刻度对齐
const barSums = computed(() => {
  const out = []
  const slots = range.value === 'month' ? daysInMonth(year.value, month.value) : 12
  const sums = new Array(slots).fill(0)
  for (const b of expenseBills.value) {
    const idx = range.value === 'month' ? Number(b.day) - 1 : Number(b.month) - 1
    if (idx >= 0 && idx < slots) sums[idx] += Math.abs(Number(b.money))
  }
  for (let i = 0; i < slots; i++) {
    out.push({
      label: range.value === 'month' ? i + 1 : i + 1 + '月',
      value: sums[i]
    })
  }
  return out
})

const maxBarValue = computed(() => barSums.value.reduce((m, b) => Math.max(m, b.value), 0))

// 轴上限向上取整到 4 等分的"整数刻度"（2,000 / 4,000 …），略高于实际最大值
const axisMax = computed(() => {
  const max = maxBarValue.value
  if (max <= 0) return 0
  const unit = Math.pow(10, Math.max(0, Math.floor(Math.log10(max / 4))))
  return Math.ceil(max / 4 / unit) * 4 * unit
})

// 柱区可用高度（rpx），与 Y 轴刻度同一量纲：axisMax 对应满格高度
const BAR_PLOT_RPX = 328

const bars = computed(() =>
  barSums.value.map(b => ({
    ...b,
    height: b.value > 0 && axisMax.value > 0
      ? Math.max(10, Math.round(b.value / axisMax.value * BAR_PLOT_RPX))
      : 0
  }))
)

const yTicks = computed(() => {
  const max = axisMax.value
  if (max <= 0) return [0]
  return [max, max * 0.75, max * 0.5, max * 0.25, 0]
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
    title: b.label + ' 支出 ' + fmtMoney(b.value),
    icon: 'none'
  })
}

/* ---------- 折线图 ---------- */
function measureLine() {
  const query = uni.createSelectorQuery()
  const q = pageInst && pageInst.proxy ? query.in(pageInst.proxy) : query
  q.select('#line-wrap').boundingClientRect(rect => {
    if (rect && rect.width) lineW.value = Math.round(rect.width)
    nextTick(() => setTimeout(drawLineChart, 30))
  }).exec()
}

function switchChart(type) {
  chartType.value = type
}

function drawLineChart() {
  if (chartType.value !== 'line') return
  const W = lineW.value
  const H = lineH
  const ctx = uni.createCanvasContext('dailyLine', pageInst ? pageInst.proxy : undefined)
  ctx.clearRect(0, 0, W, H)
  const list = bars.value
  const axis = axisMax.value
  if (!W || axis <= 0) {
    ctx.draw()
    return
  }
  const top = Math.max(4, Math.round(H * 0.02))
  const plotH = H - top
  const stepX = list.length > 1 ? W / (list.length - 1) : 0
  const pts = list.map((b, i) => ({
    x: i * stepX,
    y: top + plotH * (1 - b.value / axis)
  }))
  // 面积：整条折线垂到基线的淡色填充
  ctx.beginPath()
  ctx.moveTo(pts[0].x, pts[0].y)
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i].x, pts[i].y)
  ctx.lineTo(W, H)
  ctx.lineTo(0, H)
  ctx.closePath()
  ctx.setFillStyle(theme.value.primaryFill)
  ctx.fill()
  // 折线
  ctx.beginPath()
  ctx.moveTo(pts[0].x, pts[0].y)
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i].x, pts[i].y)
  ctx.setStrokeStyle(theme.value.primary)
  ctx.setLineWidth(2)
  ctx.setLineCap('round')
  ctx.setLineJoin('round')
  ctx.stroke()
  ctx.draw()
}

watch(chartType, (v) => {
  if (v === 'line') nextTick(() => setTimeout(measureLine, 30))
})
watch(bars, () => {
  if (chartType.value === 'line') nextTick(() => setTimeout(drawLineChart, 30))
})

/* ---------- 环形图（同时喂给下方明细列表与底部弹层） ---------- */
const donutSegments = computed(() => {
  const list = donutKind.value === 'expense' ? expenseBills.value : incomeBills.value
  const type = donutKind.value
  const map = new Map()
  for (const b of list) {
    const name = topNameOf(b.costType, type)
    const g = map.get(name) || { name, value: 0, bills: [] }
    g.value += Math.abs(Number(b.money))
    g.bills.push(b)
    map.set(name, g)
  }
  const groups = Array.from(map.values())
  const total = groups.reduce((s, g) => s + g.value, 0)
  if (total <= 0) return []
  groups.sort((a, b) => b.value - a.value)
  const top = groups.slice(0, 8)
  const rest = groups.slice(8)
  const restValue = rest.reduce((s, g) => s + g.value, 0)
  const segs = top.map(g => ({
    name: g.name,
    value: g.value,
    bills: g.bills,
    pct: (g.value / total * 100).toFixed(1) + '%',
    color: colorOf(g.name, type)
  }))
  if (restValue > 0) {
    const restBills = rest.reduce((arr, g) => arr.concat(g.bills), [])
    segs.push({
      name: '其他',
      value: restValue,
      bills: restBills,
      pct: (restValue / total * 100).toFixed(1) + '%',
      color: '#c8ccd4'
    })
  }
  return segs
})

/** 环图中心未选中任何分类时展示的合计 */
const donutTotal = computed(() => donutSegments.value.reduce((s, x) => s + x.value, 0))

/* ---------- 分类明细弹层 ---------- */
const catShow = ref(false)
const catName = ref('')
// 圆环上被点选的高亮分类（同时联动中间文字与下方列表行）
const selName = ref('')
const selSeg = computed(() => donutSegments.value.find(s => s.name === selName.value) || null)
const activeCat = computed(() => donutSegments.value.find(s => s.name === catName.value) || null)
const catColor = computed(() => (activeCat.value ? activeCat.value.color : '#c8ccd4'))
const catTotal = computed(() => (activeCat.value ? activeCat.value.value : 0))
const catPct = computed(() => (activeCat.value ? activeCat.value.pct : '0%'))
const catCount = computed(() => (activeCat.value ? activeCat.value.bills.length : 0))
// 弹层列表：按时间倒序的扁平列表（不走按日分组的账单卡片）
const catBills = computed(() => {
  const seg = activeCat.value
  if (!seg) return []
  return seg.bills.slice().sort((a, b) => {
    const ka = Number(a.year) * 10000 + Number(a.month) * 100 + Number(a.day)
    const kb = Number(b.year) * 10000 + Number(b.month) * 100 + Number(b.day)
    if (ka !== kb) return kb - ka
    return String(b.clock || '').localeCompare(String(a.clock || ''))
  })
})

/** 列表行右侧进度条：相对最大分类，而不是占总额，长短更好辨认 */
const maxCatValue = computed(() => {
  const list = donutSegments.value
  let max = 0
  for (const s of list) if (s.value > max) max = s.value
  return max
})

function barWidth(seg) {
  if (!maxCatValue.value) return '0%'
  return Math.max(6, Math.round(seg.value / maxCatValue.value * 100)) + '%'
}

/** 分类色 -> 纯色图标块（扁平化：不用渐变，阴影也去掉） */
function catBg(hex) {
  return hex
}

/** 弹层每行的副标题：日期 + 备注（备注里的图片 token 不展示） */
function billMeta(b) {
  const date = b.year + '-' + pad2(b.month) + '-' + pad2(b.day)
  const remark = String(b.orderRemark || '').replace(/\{\{img:[^}]*\}\}/g, '').trim()
  return remark ? date + ' ' + remark : date
}

function openCat(seg) {
  catName.value = seg.name
  catShow.value = true
}

function onCatBillTap(bill) {
  catShow.value = false
  goEdit(bill)
}

function drawDonut() {
  const segs = donutSegments.value
  const ctx = uni.createCanvasContext('donut', pageInst ? pageInst.proxy : undefined)
  const c = donutSize / 2
  // 选中扇形会沿半径外移 SEL_OFFSET，外径必须留出这段余量 + 一圈空隙，
  // 否则抽出的部分会超出画布被裁成一条直边（点下方分类行时最明显）
  const SEL_OFFSET = 4
  const rOuter = c - SEL_OFFSET - 3
  const rInner = c * 0.62
  if (!segs.length) {
    ctx.draw()
    return
  }
  const total = segs.reduce((s, x) => s + x.value, 0)
  const selIdx = segs.findIndex(s => s.name === selName.value)
  let start = -Math.PI / 2
  segs.forEach((seg, i) => {
    const angle = seg.value / total * Math.PI * 2
    const end = start + angle
    const isSel = i === selIdx
    // 选中的扇形沿角平分线外移一截，其余压暗，形成"抽出"效果
    const off = isSel ? SEL_OFFSET : 0
    const mid = (start + end) / 2
    const ox = Math.cos(mid) * off
    const oy = Math.sin(mid) * off
    ctx.beginPath()
    ctx.arc(c + ox, c + oy, rOuter, start + 0.012, end - 0.012, false)
    ctx.arc(c + ox, c + oy, rInner, end - 0.012, start + 0.012, true)
    ctx.closePath()
    ctx.setFillStyle(selIdx >= 0 && !isSel ? hexToRgba(seg.color, 0.45) : seg.color)
    ctx.fill()
    start = end
  })
  ctx.draw()
}

/** 画布点击 -> 命中哪个扇形（再次点同一块取消选中） */
function pickDonut(x, y) {
  const segs = donutSegments.value
  const c = donutSize / 2
  const dist = Math.sqrt((x - c) * (x - c) + (y - c) * (y - c))
  if (dist < c * 0.62 || dist > c + 8) {
    selName.value = ''
    return
  }
  const total = segs.reduce((s, g) => s + g.value, 0)
  if (total <= 0) return
  // 画布从 12 点开始顺时针排布，这里换算成同一起点的极角
  let ang = Math.atan2(y - c, x - c) + Math.PI / 2
  if (ang < 0) ang += Math.PI * 2
  let acc = 0
  for (const seg of segs) {
    const span = seg.value / total * Math.PI * 2
    if (ang >= acc && ang < acc + span) {
      selName.value = selName.value === seg.name ? '' : seg.name
      return
    }
    acc += span
  }
}

function onDonutTap(e) {
  // 各端给的坐标口径不一致（H5 是视口坐标，detail.x/y 同 clientX/clientY），
  // 统一按 clientX/clientY 处理，再减掉画布偏移换算成画布内坐标
  const t = (e && e.touches && e.touches[0]) || (e && e.changedTouches && e.changedTouches[0])
  const d = e && e.detail
  const px = t && typeof t.clientX === 'number' ? t.clientX : (d && typeof d.x === 'number' ? d.x : null)
  const py = t && typeof t.clientY === 'number' ? t.clientY : (d && typeof d.y === 'number' ? d.y : null)
  if (px === null || py === null) return
  const query = uni.createSelectorQuery()
  const q = pageInst && pageInst.proxy ? query.in(pageInst.proxy) : query
  q.select('#donut-hit').boundingClientRect(rect => {
    if (rect) pickDonut(px - rect.left, py - rect.top)
  }).exec()
}

function hexToRgba(hex, alpha) {
  const h = String(hex || '#c8ccd4').replace('#', '')
  const r = parseInt(h.slice(0, 2), 16) || 0
  const g = parseInt(h.slice(2, 4), 16) || 0
  const b = parseInt(h.slice(4, 6), 16) || 0
  return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')'
}

watch(donutSegments, () => {
  if (selName.value && !donutSegments.value.some(s => s.name === selName.value)) selName.value = ''
  nextTick(() => setTimeout(drawDonut, 60))
})
watch(donutKind, () => {
  selName.value = ''
  nextTick(() => setTimeout(drawDonut, 60))
})
watch(selName, () => {
  nextTick(() => setTimeout(drawDonut, 30))
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

function goBack() { uni.navigateBack() }

/** 明细里点某笔账单：关掉弹层去编辑（参数与首页一致） */
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

onPageScroll((e) => {
  const top = Number(e.scrollTop) || 0
  if (top > 8 && !navSolid.value) navSolid.value = true
  else if (top <= 8 && navSolid.value) navSolid.value = false
})

onReady(() => {
  setTimeout(drawDonut, 200)
})

onShow(() => {
  refreshTheme()
  load()
  nextTick(() => {
    setTimeout(drawDonut, 200)
    if (chartType.value === 'line') setTimeout(measureLine, 200)
  })
})
</script>

<style lang="scss">
.page {
  position: relative;
  z-index: 0;
  min-height: 100vh;
  background-color: var(--c-bg);
  padding-bottom: 40rpx;
}
/* 顶栏：返回 + 期间步进 + 月/年切换 */
.nav-period {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  height: 100%;
  line-height: normal;
}
.nav-step {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    background-color: var(--c-fill);
  }
}
.nav-date {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 12rpx;
  border-radius: 12rpx;
  &:active {
    background-color: var(--c-fill);
  }
}
.nav-date-text {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.2rpx;
}
/* 月/年切换：中性滑块（灰轨 + 白块 + 主题色文字），不再用整块主题色压顶。
   内边距必须等于组件默认的 4px（8rpx）——滑块高度是它用 calc(100% - 2*padding) 算的。
   组件内部节点要 :deep() 穿透：页面样式会被自动加上 data-v 作用域，
   直接写后代选择器匹配不到组件内部，滑块会退回默认的 4px 方角。 */
.range-seg {
  width: auto !important;
  min-width: 128rpx;
  padding: 4rpx !important;
  border-radius: 999rpx !important;
  background-color: var(--c-fill) !important;
  border: none;
  box-sizing: border-box;
  :deep(.wd-segmented__item.is-small) {
    flex: 0 0 auto !important;
    min-height: 40rpx !important;
    line-height: 40rpx !important;
    padding: 0 18rpx !important;
    font-size: 21rpx !important;
    border-radius: 999rpx !important;
    color: var(--c-sub) !important;
  }
  :deep(.wd-segmented__item.is-active) {
    color: var(--c-primary-deep) !important;
    font-weight: 600 !important;
  }
  :deep(.wd-segmented__item--active) {
    top: 4rpx !important;
    height: 40rpx !important;
    border-radius: 999rpx !important;
    background: var(--c-raised) !important;
    box-shadow: 0 2rpx 8rpx rgba(29, 33, 41, 0.1) !important;
  }
}
/* 收支总览：纯色主题面 + 白字。扁平化——不用渐变、不用柔光装饰，
   只用一层中性软阴影把它从底图上托起来。 */
.card.total-card {
  position: relative;
  overflow: hidden;
  margin-top: 8rpx;
  padding: 34rpx 32rpx 30rpx;
  background-color: var(--c-primary-fill);
  box-shadow: var(--shadow-card-soft);
}
.total-head {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.total-label {
  font-size: 24rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.82);
  letter-spacing: 1rpx;
}
.total-count {
  font-size: 20rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.92);
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background-color: rgba(255, 255, 255, 0.18);
}
.total-amount {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-top: 16rpx;
}
.total-currency {
  font-size: 30rpx;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.72);
  margin-right: 10rpx;
}
.total-value {
  font-size: 76rpx;
  font-weight: 600;
  color: #ffffff;
  line-height: 1.06;
  font-variant-numeric: tabular-nums;
  letter-spacing: -2rpx;
}
/* 三项明细：不加面板底色，只靠细分隔线分组，留白比色块更透气 */
.total-metrics {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 30rpx;
}
.metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.metric-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.7);
}
.metric-value {
  margin-top: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.4rpx;
  /* 七位数以上的极端金额不许撑破卡片 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.metric-div {
  width: 1rpx;
  height: 44rpx;
  margin: 0 20rpx;
  background-color: rgba(255, 255, 255, 0.2);
}
.card {
  background-color: var(--c-card);
  border-radius: 32rpx;
  margin: 20rpx 32rpx 0;
  padding: 32rpx;
  box-shadow: var(--shadow-card-soft);
}
.card-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.card-head-title {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.card-icon {
  display: flex;
  align-items: center;
  margin-right: 12rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--c-text);
  letter-spacing: -0.2rpx;
}
.card-head-icons {
  display: flex;
  flex-direction: row;
  align-items: center;
}
/* 小型支出/收入、柱状/折线切换：同为中性滑块，选中项靠白块与主题色文字区分 */
.mini-toggle {
  display: flex;
  flex-direction: row;
  background-color: var(--c-fill);
  border-radius: 14rpx;
  padding: 4rpx;
}
.mini-toggle-item {
  font-size: 21rpx;
  color: var(--c-sub);
  padding: 6rpx 20rpx;
  border-radius: 11rpx;
  line-height: 1.5;
}
.mini-toggle-active {
  background-color: var(--c-raised);
  color: var(--c-primary-deep);
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(29, 33, 41, 0.1);
}
/* 柱状图 */
.bar-chart {
  display: flex;
  flex-direction: row;
}
.bar-y {
  width: 78rpx;
  height: 330rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  padding-right: 14rpx;
}
.bar-y-label {
  font-size: 19rpx;
  color: var(--c-dim);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.bar-body {
  position: relative;
  flex: 1;
  height: 330rpx;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  border-bottom: 1rpx solid var(--c-line);
}
/* 网格线：和左侧刻度同一条基线，只在 25% 的整数倍处出线 */
.chart-grid {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  pointer-events: none;
}
.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1rpx;
  background-color: var(--c-line-soft);
}
.bar-cols {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
}
.line-canvas {
  position: relative;
  z-index: 1;
  display: block;
}
.bar-col {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  &:active .bar-bar {
    opacity: 0.72;
  }
}
.bar-bar {
  width: 10rpx;
  border-radius: 5rpx;
}
/* 年视图只有 12 根柱，间距变大，柱体同步加宽才不会细成一根线 */
.bar-cols.is-year .bar-bar {
  width: 18rpx;
  border-radius: 9rpx;
}
.bar-out {
  background-color: var(--c-primary);
}
.bar-x {
  display: flex;
  flex-direction: row;
  margin-top: 14rpx;
}
.bar-x-label {
  flex: 1;
  font-size: 20rpx;
  color: var(--c-dim);
  font-variant-numeric: tabular-nums;
}
.bar-x-mid {
  text-align: center;
}
.bar-x .bar-x-label:last-child {
  text-align: right;
}
/* 空状态：只留两行文案，不放图标（小尺寸实心图标在灰底上会糊成一团） */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 34rpx 0 30rpx;
}
.empty-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-text {
  color: var(--c-sub);
  font-size: 25rpx;
  font-weight: 500;
}
.empty-hint {
  margin-top: 6rpx;
  color: var(--c-dim);
  font-size: 20rpx;
}
/* 环形图 + 分类明细 */
.donut-wrap {
  display: flex;
  flex-direction: column;
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
.donut-hit {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
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
  /* 中间文字只是覆盖层，不能挡住画布上的点选 */
  pointer-events: none;
}
.donut-center-name {
  font-size: 21rpx;
  color: var(--c-sub);
  max-width: 96%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.donut-center-amount {
  margin-top: 6rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.8rpx;
}
.donut-center-pct {
  margin-top: 4rpx;
  font-size: 19rpx;
  color: var(--c-dim);
  font-variant-numeric: tabular-nums;
}
.cat-list {
  width: 100%;
  margin-top: 16rpx;
}
.cat-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 18rpx 0;
  border-top: 1rpx solid var(--c-line-soft);
  &:active {
    background-color: var(--c-fill);
  }
}
.cat-ic {
  width: 60rpx;
  height: 60rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.cat-main {
  flex: 1;
  min-width: 0;
}
.cat-line {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.cat-name {
  font-size: 27rpx;
  font-weight: 500;
  color: var(--c-text);
  max-width: 260rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.cat-pct {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: var(--c-dim);
  font-variant-numeric: tabular-nums;
}
.cat-bar {
  margin-top: 10rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background-color: var(--c-fill);
  overflow: hidden;
}
.cat-bar-fill {
  height: 100%;
  border-radius: 3rpx;
}
.cat-amt {
  margin-left: 16rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.4rpx;
}
/* 分类明细底部弹层 */
.cat-sheet {
  border-radius: 28rpx 28rpx 0 0 !important;
  overflow: hidden !important;
  background-color: var(--c-raised) !important;
}
.cat-grab {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background-color: var(--c-line);
  margin: 16rpx auto 4rpx;
}
.cat-sheet-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 32rpx 22rpx;
}
.cat-sheet-left {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 0;
}
.cat-sheet-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.cat-sheet-name {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
}
.cat-sheet-meta {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-sub);
}
.cat-sheet-total {
  margin-left: 20rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.cat-sheet-list {
  padding: 0 32rpx 8rpx;
  max-height: 52vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  border-top: 1rpx solid var(--c-line-soft);
}
.cat-bill {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid var(--c-line-soft);
  &:last-child {
    border-bottom: none;
  }
  &:active {
    background-color: var(--c-line-soft);
  }
}
.cat-bill-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.cat-bill-name {
  font-size: 28rpx;
  color: var(--c-text);
}
.cat-bill-meta {
  margin-top: 5rpx;
  font-size: 21rpx;
  color: var(--c-sub);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.cat-bill-amt {
  margin-left: 20rpx;
  font-size: 29rpx;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.cat-bill-amt.is-out {
  color: var(--c-out);
}
.cat-bill-amt.is-in {
  color: var(--c-in);
}
/* 日报表 / 月报表：日期左对齐、金额右对齐，表头只做弱化说明 */
.report-hint {
  font-size: 20rpx;
  color: var(--c-dim);
}
.report-table {
  margin-top: 2rpx;
}
.report-tr {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 20rpx 0;
  border-top: 1rpx solid var(--c-line-soft);
}
.report-th {
  border-top: none;
  padding: 0 0 14rpx;
}
.report-th .report-td {
  font-size: 20rpx;
  color: var(--c-dim);
  font-weight: 500;
  letter-spacing: 0.6rpx;
}
.report-td {
  min-width: 0;
  font-size: 26rpx;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.report-date {
  flex: 1.2;
  text-align: left;
  color: var(--c-sub);
}
.report-num {
  flex: 1;
  text-align: right;
  font-weight: 500;
}
.report-out {
  color: var(--c-out);
}
.report-in {
  color: var(--c-in);
}
.bottom-safe {
  height: 60rpx;
}
</style>
