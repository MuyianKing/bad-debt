<template>
  <view class="page">
    <!-- 顶部：关闭 / 支出收入 / 添加分类 -->
    <view class="status-bar"></view>
    <view class="nav">
      <view class="nav-btn" @tap="goBack">
        <app-icon name="x" color="#1d2129" :size="22"></app-icon>
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
        <app-icon name="plus" color="#1d2129" :size="22"></app-icon>
      </view>
    </view>

    <!-- 草稿提示 -->
    <view v-if="mode === 'draft'" class="draft-tip">
      <app-icon name="bell" color="#ff9f43" :size="16"></app-icon>
      <text class="draft-tip-text">自动记账草稿 · 确认后入账</text>
      <text class="draft-tip-link" @tap="discardDraftNow">不记这笔</text>
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
            <app-icon :name="c.icon || 'grid'" :color="selectedTop === c.name ? '#e8930c' : '#7a8194'" :size="25" :solid="true"></app-icon>
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
          <view class="sub-chip-inner"><app-icon :name="s.icon || 'grid'" color="#7a8194" :size="15" :solid="true"></app-icon><text class="sub-chip-text">{{ s.name }}</text></view>
        </view>
      </view>
      <view class="grid-bottom-space"></view>
    </scroll-view>

    <!-- 底部：备注 + 键盘 -->
    <view class="keypad-panel">
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
          <view class="chip">
            <app-icon name="calendar" color="#5b6170" :size="15"></app-icon>
            <text class="chip-text">{{ dateChipText }}</text>
          </view>
        </picker>
        <view :class="['chip', reimb ? 'chip-active' : '']" @tap="reimb = !reimb">
          <text class="chip-text">报销</text>
        </view>
        <view :class="['chip', imgPath ? 'chip-active' : '']" @tap="onImageChip">
          <app-icon name="image" color="#5b6170" :size="15"></app-icon>
          <text class="chip-text">图片</text>
        </view>
        <view :class="['chip', starred ? 'chip-star-active' : '']" @tap="starred = !starred">
          <app-icon name="star" :color="starred ? '#f7a600' : '#5b6170'" :size="15" :stroke-width="2.2"></app-icon>
        </view>
      </view>

      <view class="keys">
        <view class="key" hover-class="key-hover" @tap="onKey('1')"><text class="key-text">1</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('2')"><text class="key-text">2</text></view>
        <view class="key" hover-class="key-hover" @tap="onKey('3')"><text class="key-text">3</text></view>
        <view class="key key-fn" hover-class="key-fn-hover" @tap="onKey('del')">
          <app-icon name="delete" color="#4a5060" :size="22"></app-icon>
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
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { pad2, todayStr, evalExpr } from '@/common/fmt.js'
import { listByType } from '@/common/cats.js'
import { suggestCategory, learnCategory } from '@/common/merchants.js'
// #ifdef APP-PLUS
import { getDraft, resolveDraft, discardDraft, saveBillEx, updateBill } from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import { getDraft, resolveDraft, discardDraft, saveBillEx, updateBill } from '@/common/web-bill-api.js'
// #endif

const mode = ref('new') // new | edit | draft
const editId = ref('')
const token = ref('')
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
const launchQuery = ref({})
const draftMerchant = ref('')

const selectedTop = computed(() => (cats.value[selectedTopIndex.value] || {}).name || '')
const subList = computed(() => (cats.value[selectedTopIndex.value] || {}).subs || [])
const displayAmount = computed(() => (expr.value ? expr.value : '0.0'))
const dateChipText = computed(() => (dateValue.value === todayStr() ? '今天' : dateValue.value.slice(5).replace('-', '/')))

onLoad((query) => {
  const q = query || {}
  launchQuery.value = q
  if (q.token) {
    mode.value = 'draft'
    token.value = decodeURIComponent(q.token)
  } else if (q.id) {
    mode.value = 'edit'
    editId.value = String(q.id)
  }
  if (q.isIncome === '1') isIncome.value = true
  if (q.date) dateValue.value = String(q.date)
  if (q.reimb === '1') reimb.value = true
  if (q.starred === '1') starred.value = true
})

onShow(() => {
  // 分类每次进入都刷新，便于刚在分类管理里改完回来生效
  cats.value = listByType(isIncome.value ? 'income' : 'expense')
  if (selectedTopIndex.value >= cats.value.length) selectedTopIndex.value = 0

  if (mode.value === 'draft' && token.value && !expr.value) {
    try {
      const d = JSON.parse(getDraft(token.value) || 'null')
      if (d) {
        expr.value = String(Math.abs(Number(d.money)))
        isIncome.value = Number(d.money) >= 0
        remark.value = d.orderRemark || ''
        dateValue.value = d.year + '-' + pad2(d.month) + '-' + pad2(d.day)
        draftMerchant.value = d.merchant || ''
        // 商户名预填备注（草稿没有备注时），确认入账时随备注落库
        if (draftMerchant.value && !remark.value) remark.value = draftMerchant.value
        // 草稿可能改写收支方向，按最终方向刷新分类列表
        cats.value = listByType(isIncome.value ? 'income' : 'expense')
        if (selectedTopIndex.value >= cats.value.length) selectedTopIndex.value = 0
        // 分类建议：用户历史修正 > 内置词典；都没命中时保持默认选中第一项
        const sug = suggestCategory(draftMerchant.value, isIncome.value)
        if (sug) {
          selectLeaf(sug)
        } else if (d.costType && d.costType !== '消费' && d.costType !== '收入') {
          const idx = cats.value.findIndex(c => c.name === d.costType)
          if (idx >= 0) selectedTopIndex.value = idx
        }
      }
    } catch (e) {
      // 草稿读取失败按新记录处理
    }
  }
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
  if (imgPath.value) {
    uni.showActionSheet({
      itemList: ['预览图片', '移除图片', '更换图片'],
      success: (res) => {
        if (res.tapIndex === 0) uni.previewImage({ urls: [imgPath.value] })
        else if (res.tapIndex === 1) imgPath.value = ''
        else chooseImage()
      }
    })
  } else {
    chooseImage()
  }
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
  if (mode.value === 'draft') {
    raw = resolveDraft(token.value, String(amount), isIncome.value, bankNameOf(), leafName.value, buildRemark())
  } else if (mode.value === 'edit') {
    raw = updateBill(editId.value, dateValue.value, String(amount), isIncome.value, bankNameOf(), leafName.value, buildRemark(), reimb.value, starred.value)
  } else {
    raw = saveBillEx(dateValue.value, String(amount), isIncome.value, bankNameOf(), leafName.value, buildRemark(), reimb.value, starred.value)
  }
  let resp = {}
  try { resp = JSON.parse(raw || '{}') } catch (e) { resp = { success: false } }
  if (!resp.success) {
    uni.showToast({ title: resp.error === 'draft not found' ? '草稿已失效' : '保存失败', icon: 'none' })
    return false
  }
  uni.showToast({ title: keepOpen ? '已记一笔' : '已入账', icon: 'success' })
  // 用户修正回流：把"商户 → 实际选择分类"记下来，下次同商户自动建议
  if (mode.value === 'draft' && draftMerchant.value) {
    learnCategory(draftMerchant.value, leafName.value)
  }
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

function discardDraftNow() {
  uni.showModal({
    title: '不记这笔',
    content: '确认丢弃这条待确认账单？',
    success: (res) => {
      if (res.confirm) {
        discardDraft(token.value)
        uni.navigateBack()
      }
    }
  })
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
  background: transparent;
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
    background-color: var(--c-bg);
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
  color: var(--c-text);
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
  background-color: var(--c-text);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.2s ease;
}
.nav-line-in {
  background-color: var(--c-in);
}
.draft-tip {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 12rpx 28rpx 0;
  background-color: rgba(255, 248, 236, 0.72);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-radius: 16rpx;
  padding: 14rpx 20rpx;
}
.draft-tip-text {
  flex: 1;
  margin-left: 10rpx;
  font-size: 24rpx;
  color: #b26a2c;
}
.draft-tip-link {
  font-size: 24rpx;
  color: var(--c-primary-deep);
  font-weight: 600;
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
  transition: box-shadow 0.15s ease, background-color 0.15s ease;
}
.cat-circle-active {
  /* 参考图式选中态：淡色圆底承载实体图标 */
  background-color: rgba(247, 166, 0, 0.15);
}
.cat-circle .app-icon {
  opacity: 0.95;
}
.cat-name {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  max-width: 130rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s ease;
}
.cat-name-active {
  color: #c87f00;
  font-weight: 600;
}
.sub-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  padding: 8rpx 32rpx;
}
.sub-chip {
  background-color: #f4f6fa;
  border-radius: 32rpx;
  padding: 12rpx 26rpx;
  margin: 8rpx 16rpx 8rpx 0;
  font-size: 25rpx;
  color: #5b6170;
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
  background-color: var(--c-primary-weak);
  color: var(--c-primary-deep);
  font-weight: 600;
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
  background-color: var(--c-primary-weak);
}
.chip-active .chip-text {
  color: var(--c-primary-deep);
  font-weight: 600;
}
.chip-star-active {
  background-color: var(--c-primary-weak);
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
  background-color: #ffffff;
  margin-bottom: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3rpx 0 #e5e8ef;
  transition: transform 0.06s ease, box-shadow 0.06s ease;
}
.key-hover {
  transform: translateY(2rpx);
  box-shadow: 0 1rpx 0 #e5e8ef;
  background-color: #fafbfd;
}
.key-fn {
  background-color: #e9ebf2;
  box-shadow: 0 3rpx 0 #dcdfe8;
}
.key-fn-hover {
  transform: translateY(2rpx);
  box-shadow: 0 1rpx 0 #dcdfe8;
  background-color: #e2e5ee;
}
.key-text {
  font-size: 38rpx;
  color: #2a2f3a;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.key-fn .key-text {
  color: #4a5060;
}
.key-again {
  background-color: #e9ebf2;
  box-shadow: 0 3rpx 0 #dcdfe8;
}
.key-text-again {
  font-size: 29rpx;
  color: #6b7280;
  font-weight: 600;
}
.key-save {
  background: linear-gradient(180deg, #ff7a72 0%, #f0655e 100%);
  box-shadow: 0 3rpx 0 #d84a44, 0 8rpx 18rpx rgba(240, 101, 94, 0.3);
}
.key-save-hover {
  transform: translateY(2rpx);
  background: linear-gradient(180deg, #f4695f 0%, #e8574f 100%);
  box-shadow: 0 1rpx 0 #d84a44, 0 4rpx 10rpx rgba(240, 101, 94, 0.28);
}
.key-text-save {
  color: #ffffff;
  font-size: 31rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
</style>
