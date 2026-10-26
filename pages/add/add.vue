<template>
  <view :class="['page', themeClass]">
    <!-- 顶部：关闭 / 支出收入 / 添加分类 -->
    <view class="status-bar"></view>
    <view class="nav">
      <view class="nav-btn" @tap="goBack">
        <app-icon name="x" :color="theme.ink" :size="22"></app-icon>
      </view>
      <view class="nav-tabs">
        <view :class="['nav-tab', !isIncome ? 'nav-tab-active' : '']" @tap="switchType(false)">
          <text>支出</text>
        </view>
        <view :class="['nav-tab', isIncome ? 'nav-tab-active nav-tab-in' : '']" @tap="switchType(true)">
          <text>收入</text>
        </view>
        <view :class="['nav-line', isIncome ? 'nav-line-in' : '']" :style="{ transform: isIncome ? 'translateX(100%)' : 'translateX(0)' }"></view>
      </view>
      <view class="nav-btn" @tap="goCategories">
        <app-icon name="plus" :color="theme.ink" :size="22"></app-icon>
      </view>
    </view>

    <!-- 分类宫格 -->
    <scroll-view scroll-y class="cat-scroll">
      <view :key="isIncome ? 'in' : 'out'" class="cat-grid">
        <view
          v-for="(c, i) in cats"
          :key="c.name"
          class="cat-item"
          @tap="selectCat(i)"
        >
          <view
            :class="['cat-circle', selectedTop === c.name ? 'cat-circle-active' : '']"
          >
            <app-icon :name="c.icon || 'grid'" :color="selectedTop === c.name ? theme.primaryDeep : '#7a8194'" :size="25" :solid="true"></app-icon>
          </view>
          <text :class="['cat-name', selectedTop === c.name ? 'cat-name-active' : '']">{{ c.name }}</text>
        </view>
      </view>
      <!-- 子类 -->
      <view v-if="subList.length" class="sub-row">
        <view
          :class="['sub-chip', !selectedSub ? 'sub-chip-active' : '']"
          @tap="selectSub('')"
        >
          <text>全部</text>
        </view>
        <view
          v-for="s in subList"
          :key="s.name"
          :class="['sub-chip', selectedSub === s.name ? 'sub-chip-active' : '']"
          @tap="selectSub(s.name)"
        >
          <view class="sub-chip-inner"><app-icon :name="s.icon || 'grid'" :color="selectedSub === s.name ? '#ffffff' : '#8a90a0'" :size="15" :solid="true"></app-icon><text class="sub-chip-text">{{ s.name }}</text></view>
        </view>
      </view>
      <view class="grid-bottom-space"></view>
    </scroll-view>

    <!-- 底部：备注 + 键盘 -->
    <view class="keypad-panel glass">
      <view class="input-row">
        <input
          class="remark-input"
          v-model="remark"
          placeholder="点此输入备注..."
          placeholder-class="remark-placeholder"
          confirm-type="done"
        />
        <view :class="['amount-box', isIncome ? 'amount-in' : 'amount-out']">
          <text class="amount-cny">¥</text>
          <text class="amount-view">{{ displayAmount }}</text>
        </view>
      </view>

      <view class="chips-row">
        <picker mode="date" :value="dateValue" @change="onDateChange">
          <view class="chip glass">
            <app-icon name="calendar" :color="theme.inkSub" :size="15"></app-icon>
            <text class="chip-text">{{ dateChipText }}</text>
          </view>
        </picker>
        <view :class="['chip', reimb ? 'chip-active' : '']" @tap="reimb = !reimb">
          <text class="chip-text">报销</text>
        </view>
        <view :class="['chip', imgPath ? 'chip-active' : '']" @tap="onImageChip">
          <app-icon name="image" :color="imgPath ? '#ffffff' : theme.inkSub" :size="15"></app-icon>
          <text class="chip-text">图片</text>
        </view>
        <view :class="['chip', starred ? 'chip-active' : '']" @tap="starred = !starred">
          <app-icon name="star" :color="starred ? '#ffffff' : theme.inkSub" :size="15" :stroke-width="2.2"></app-icon>
        </view>
      </view>

      <view class="keys">
        <view class="key" hover-class="key-hover" @tap="onKey('1')"><text class="key-text">1</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('2')"><text class="key-text">2</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('3')"><text class="key-text">3</text></view>
        <view class="key key-fn" hover-class="key-fn-hover" @tap="onKey('del')">
          <app-icon name="delete" :color="theme.inkSub" :size="22"></app-icon>
        </view>

        <view class="key" hover-class="key-hover" @tap="onKey('4')"><text class="key-text">4</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('5')"><text class="key-text">5</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('6')"><text class="key-text">6</text></view>
        <view class="key key-fn" hover-class="key-fn-hover" @tap="onKey('-')"><text class="key-text">−</text></view>

        <view class="key" hover-class="key-hover" @tap="onKey('7')"><text class="key-text">7</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('8')"><text class="key-text">8</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('9')"><text class="key-text">9</text></view>
        <view class="key key-fn" hover-class="key-fn-hover" @tap="onKey('+')"><text class="key-text">+</text></view>

        <view class="key key-again" hover-class="key-fn-hover" @tap="onAgain">
          <text class="key-text key-text-again">再记</text>
        </view>
        <view class="key" hover-class="key-hover" @tap="onKey('0')"><text class="key-text">0</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('.')"><text class="key-text">.</text></view>
        <view class="key key-save" hover-class="key-save-hover" @tap="onSave">
          <text class="key-text key-text-save">保存</text>
        </view>
      </view>
    </view>

    <!-- 备注图片操作菜单 -->
    <wd-action-sheet
      v-model="imgMenuShow"
      :actions="imgMenuItems"
      cancel-text="取消"
      :z-index="300"
      :safe-area-inset-bottom="true"
      @select="onImageMenuSelect"
    ></wd-action-sheet>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { todayStr, evalExpr } from '@/common/fmt.js'
import { listByType } from '@/common/cats.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
// #ifdef APP-PLUS
import { saveBillEx, updateBill } from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import { saveBillEx, updateBill } from '@/common/web-bill-api.js'
// #endif

const mode = ref('new') // new | edit
const editId = ref('')
const isIncome = ref(false)
const cats = ref([])
const selectedTopIndex = ref(0)
const selectedSub = ref('')
const remark = ref('')
const expr = ref('')
const reimb = ref(false)
const starred = ref(false)
const dateValue = ref(todayStr())
const imgPath = ref('')
// 备注图片操作菜单（wot action sheet）
const imgMenuShow = ref(false)
const imgMenuItems = [
  { name: '预览图片' },
  { name: '移除图片', color: 'var(--c-out)' },
  { name: '更换图片' }
]
const launchQuery = ref({})

const selectedTop = computed(() => (cats.value[selectedTopIndex.value] || {}).name || '')
const subList = computed(() => (cats.value[selectedTopIndex.value] || {}).subs || [])
const displayAmount = computed(() => (expr.value ? expr.value : '0.0'))
const dateChipText = computed(() => (dateValue.value === todayStr() ? '今天' : dateValue.value.slice(5).replace('-', '/')))

onLoad((query) => {
  const q = query || {}
  launchQuery.value = q
  if (q.id) {
    mode.value = 'edit'
    editId.value = String(q.id)
  }
  if (q.isIncome === '1') isIncome.value = true
  if (q.date) dateValue.value = String(q.date)
  if (q.reimb === '1') reimb.value = true
  if (q.starred === '1') starred.value = true
})

onShow(() => {
  refreshTheme()
  // 分类每次进入都刷新，便于刚在分类管理里改完回来生效
  cats.value = listByType(isIncome.value ? 'income' : 'expense')
  if (selectedTopIndex.value >= cats.value.length) selectedTopIndex.value = 0

  if (mode.value === 'edit' && !expr.value) {
    // 编辑模式：参数带入
    const params = launchQuery.value || {}
    expr.value = params.money || ''
    remark.value = params.remark || ''
    if (params.costType) {
      const list = cats.value
      let idx = list.findIndex(c => c.name === params.costType)
      if (idx < 0) {
        idx = list.findIndex(c => (c.subs || []).some(s => s.name === params.costType))
      }
      if (idx >= 0) {
        selectedTopIndex.value = idx
        const top = list[idx]
        const sub = (top.subs || []).find(s => s.name === params.costType)
        selectedSub.value = sub ? sub.name : ''
      }
    }
    const rm = String(params.remark || '')
    const m = rm.match(/\{\{img:([^}]+)\}\}/)
    if (m) {
      imgPath.value = m[1]
      remark.value = rm.slice(0, rm.indexOf('{{img:')).trim()
    }
  }
})

function switchType(income) {
  if (isIncome.value === income) return
  isIncome.value = income
  selectedTopIndex.value = 0
  selectedSub.value = ''
  cats.value = listByType(income ? 'income' : 'expense')
}

function selectCat(i) {
  selectedTopIndex.value = i
  selectedSub.value = ''
}

function selectSub(name) {
  selectedSub.value = name
}

/** 按分类叶子名选中（一级或子类），找不到不动 */
function selectLeaf(name) {
  const list = cats.value
  let idx = list.findIndex(c => c.name === name)
  if (idx >= 0) {
    selectedTopIndex.value = idx
    selectedSub.value = ''
    return
  }
  idx = list.findIndex(c => (c.subs || []).some(s => s.name === name))
  if (idx >= 0) {
    selectedTopIndex.value = idx
    const sub = (list[idx].subs || []).find(s => s.name === name)
    selectedSub.value = sub ? sub.name : ''
  }
}

const leafName = computed(() => selectedSub.value || selectedTop.value || '其它')

function onDateChange(e) {
  const v = e.detail && e.detail.value ? String(e.detail.value) : ''
  if (v) dateValue.value = v
}

function onImageChip() {
  if (imgPath.value) imgMenuShow.value = true
  else chooseImage()
}

function onImageMenuSelect({ index }) {
  if (!imgPath.value) return
  if (index === 0) uni.previewImage({ urls: [imgPath.value] })
  else if (index === 1) imgPath.value = ''
  else if (index === 2) chooseImage()
}

function chooseImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (res) => {
      const temp = res.tempFilePaths && res.tempFilePaths[0]
      if (!temp) return
      uni.saveFile({
        tempFilePath: temp,
        success: (s) => {
          imgPath.value = s.savedFilePath
          uni.showToast({ title: '已添加图片', icon: 'none' })
        },
        fail: () => {
          imgPath.value = temp
          uni.showToast({ title: '已添加图片', icon: 'none' })
        }
      })
    }
  })
}

const OPS = ['+', '-']

function onKey(k) {
  if (k === 'del') {
    expr.value = expr.value.slice(0, -1)
    return
  }
  if (k === '.' ) {
    const lastToken = expr.value.split(/[+-]/).pop()
    if (lastToken.indexOf('.') >= 0) return
    if (expr.value === '' || OPS.indexOf(expr.value.slice(-1)) >= 0) {
      expr.value += '0.'
    } else {
      expr.value += '.'
    }
    return
  }
  if (k === '+' || k === '-') {
    if (expr.value === '') {
      if (k === '-') expr.value = '-'
      return
    }
    if (OPS.indexOf(expr.value.slice(-1)) >= 0) {
      expr.value = expr.value.slice(0, -1) + k
    } else {
      expr.value += k
    }
    return
  }
  // 数字
  if (expr.value.length > 15) return
  const lastToken = expr.value.split(/[+-]/).pop()
  if (lastToken === '0') {
    expr.value = expr.value.slice(0, -1) + k
    return
  }
  expr.value += k
}

function currentAmount() {
  const v = evalExpr(expr.value)
  return isFinite(v) ? v : NaN
}

function buildRemark() {
  const text = (remark.value || '').trim()
  return imgPath.value ? (text ? text + ' ' : '') + '{{img:' + imgPath.value + '}}' : text
}

function persist(amount, keepOpen) {
  if (!isFinite(amount) || amount <= 0) {
    uni.showToast({ title: '请输入大于0的有效金额', icon: 'none' })
    return false
  }
  let raw = ''
  if (mode.value === 'edit') {
    raw = updateBill(editId.value, dateValue.value, String(amount), isIncome.value, bankNameOf(), leafName.value, buildRemark(), reimb.value, starred.value)
  } else {
    raw = saveBillEx(dateValue.value, String(amount), isIncome.value, bankNameOf(), leafName.value, buildRemark(), reimb.value, starred.value)
  }
  let resp = {}
  try { resp = JSON.parse(raw || '{}') } catch (e) { resp = { success: false } }
  if (!resp.success) {
    uni.showToast({ title: '保存失败', icon: 'none' })
    return false
  }
  uni.showToast({ title: keepOpen ? '已记一笔' : '已入账', icon: 'success' })
  return true
}

function bankNameOf() {
  return '手动'
}

function onSave() {
  const amount = currentAmount()
  if (!persist(amount, false)) return
  setTimeout(() => { uni.navigateBack() }, 400)
}

function onAgain() {
  const amount = currentAmount()
  if (!persist(amount, true)) return
  expr.value = ''
  remark.value = ''
}

function goBack() {
  uni.navigateBack()
}

function goCategories() {
  uni.navigateTo({ url: '/pages/categories/categories' })
}
</script>

<style lang="scss">
.page {
  /* 固定视口高度：键盘区常驻可视区域，分类宫格在剩余空间内自行滚动 */
  height: 100vh;
  overflow: hidden;
  /* 与首页、抽屉统一：极淡主题色底 */
  background-color: var(--c-page-wash);
  display: flex;
  flex-direction: column;
}
.status-bar {
  height: var(--status-bar-height);
}
.nav {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 20rpx 0;
  border-bottom: 1rpx solid var(--c-line);
}
.nav-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active {
    background-color: var(--c-primary-tint);
  }
}
/* 页签：单线滑动切换 */
.nav-tabs {
  position: relative;
  display: flex;
  flex-direction: row;
}
.nav-tab {
  width: 140rpx;
  text-align: center;
  padding-bottom: 14rpx;
  font-size: 33rpx;
  color: var(--c-dim);
  font-weight: 500;
  transition: color 0.2s ease, font-weight 0.2s ease;
}
.nav-tab-active {
  color: var(--c-primary-deep);
  font-weight: 700;
}
.nav-tab-in.nav-tab-active {
  color: var(--c-in);
}
.nav-line {
  position: absolute;
  bottom: 0;
  left: 5rpx;
  width: 130rpx;
  height: 6rpx;
  border-radius: 4rpx;
  background: var(--grad-primary);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.2s ease;
}
.nav-line-in {
  background-color: var(--c-in);
}
.cat-scroll {
  flex: 1;
  min-height: 0;
}
.cat-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding: 18rpx 8rpx 0;
  animation: gridIn 0.22s ease both;
}
@keyframes gridIn {
  from {
    opacity: 0;
    transform: translateY(8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.cat-item {
  width: 20%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 0;
}
.cat-circle {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.15s ease;
}
.cat-circle-active {
  /* 参考图式选中态：淡色圆底承载实体图标 */
  background-color: var(--c-primary-tint);
}
.cat-circle .app-icon {
  opacity: 0.95;
}
.cat-name {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--c-sub);
  max-width: 130rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s ease;
}
.cat-name-active {
  color: var(--c-primary-deep);
  font-weight: 600;
}
.sub-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding: 8rpx 32rpx;
}
.sub-chip {
  background-color: var(--c-fill);
  border-radius: 32rpx;
  padding: 12rpx 26rpx;
  margin: 8rpx 16rpx 8rpx 0;
  font-size: 25rpx;
  color: var(--c-sub);
}
.sub-chip-inner {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.sub-chip-text {
  margin-left: 8rpx;
}
.sub-chip-active {
  background: var(--grad-primary);
  color: #ffffff;
  font-weight: 600;
  box-shadow: var(--shadow-primary-sm);
}
.grid-bottom-space {
  height: 20rpx;
}
.keypad-panel {
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(20px) saturate(1.5);
  border-radius: 28rpx 28rpx 0 0;
  padding: 20rpx 24rpx calc(14rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -6rpx 30rpx rgba(35, 46, 111, 0.05);
  flex-shrink: 0;
}
.input-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 12rpx 16rpx;
  border-bottom: 1rpx solid #e9ebf1;
  margin-bottom: 16rpx;
}
.remark-input {
  flex: 1;
  font-size: 27rpx;
  color: var(--c-text);
}
.remark-placeholder {
  color: var(--c-dim);
}
.amount-box {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-left: 24rpx;
}
.amount-cny {
  font-size: 30rpx;
  font-weight: 700;
  opacity: 0.55;
  margin-right: 4rpx;
}
.amount-view {
  font-size: 54rpx;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5rpx;
  line-height: 1.2;
}
.amount-out {
  color: var(--c-out);
}
.amount-in {
  color: var(--c-in);
}
.chips-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 16rpx;
}
.chip {
  background-color: var(--glass-bg);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  border-radius: 14rpx;
  padding: 11rpx 24rpx;
  margin-right: 16rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  box-shadow: 0 1rpx 4rpx rgba(35, 46, 111, 0.05);
}
.chip-text {
  font-size: 26rpx;
  color: var(--c-text);
  margin-left: 6rpx;
}
.chip-text:first-child {
  margin-left: 0;
}
.chip-active {
  background: var(--grad-primary);
  box-shadow: var(--shadow-primary-sm);
}
.chip-active .chip-text {
  color: #ffffff;
  font-weight: 600;
}
/* 键盘：白色键帽 + 底边立体感 */
.keys {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-between;
}
.key {
  width: 162rpx;
  height: 86rpx;
  border-radius: 18rpx;
  background-color: var(--c-key-bg);
  margin-bottom: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3rpx 0 var(--c-key-edge);
  transition: transform 0.06s ease, box-shadow 0.06s ease;
}
.key-hover {
  transform: translateY(2rpx);
  box-shadow: 0 1rpx 0 var(--c-key-edge);
  background-color: var(--c-key-press);
}
.key-fn {
  background-color: var(--c-key-fn-bg);
  box-shadow: 0 3rpx 0 var(--c-key-fn-edge);
}
.key-fn-hover {
  transform: translateY(2rpx);
  box-shadow: 0 1rpx 0 var(--c-key-fn-edge);
  background-color: var(--c-key-fn-edge);
}
.key-text {
  font-size: 38rpx;
  color: var(--c-key-fg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.key-fn .key-text {
  color: var(--c-key-fn-fg);
}
.key-again {
  background-color: var(--c-primary-weak);
  box-shadow: 0 3rpx 0 var(--c-primary-tint);
}
.key-text-again {
  font-size: 29rpx;
  color: var(--c-primary-deep);
  font-weight: 600;
}
.key-save {
  background: var(--grad-primary);
  box-shadow: 0 3rpx 0 var(--c-primary-press), var(--shadow-primary);
}
.key-save-hover {
  transform: translateY(2rpx);
  background: var(--c-primary-press);
  box-shadow: 0 1rpx 0 var(--c-primary-press);
}
.key-text-save {
  color: var(--c-ink-on-primary);
  font-size: 31rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
</style>
