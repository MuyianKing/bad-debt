<template>
  <view class="page">
    <wd-navbar :left-arrow="true" :fixed="true" :placeholder="true" :safe-area-inset-top="true" @click-left="goBack">
      <template #title>
        <view class="cat-tabs">
          <view :class="['cat-tab', type === 'expense' ? 'cat-tab-active' : '']" @tap="switchType('expense')">
            <text>支出</text>
            <view v-if="type === 'expense'" class="cat-tab-line"></view>
          </view>
          <view :class="['cat-tab', type === 'income' ? 'cat-tab-active' : '']" @tap="switchType('income')">
            <text>收入</text>
            <view v-if="type === 'income'" class="cat-tab-line"></view>
          </view>
        </view>
      </template>
      <template #right>
        <view class="nav-right-btn" @tap="showHelp">
          <app-icon name="help-circle" color="#1d2129" :size="19"></app-icon>
        </view>
      </template>
    </wd-navbar>

    <view class="hint-bar">
      <app-icon name="edit-3" color="#9aa0ab" :size="14"></app-icon>
      <text class="hint-text">长按分类拖动排序，单击可修改图标与名称</text>
    </view>

    <view
      class="cat-card glass"
      :class="{ 'drag-active': drag }"
      @touchstart="onCardTouch"
      @touchmove="onDragMove"
      @touchend="onDragEnd"
      @touchcancel="onDragEnd"
    >
      <view
        v-for="(c, i) in list"
        :key="c.name"
        class="cat-block"
        :class="{ 'cat-block-border': i > 0 }"
      >
        <view
          :class="['cat-row', isDragTarget('top', i) ? 'drag-target' : '']"
          :style="dragStyle('top', i)"
          @longpress="startDrag('top', i)"
          @tap="openEditTop(i)"
        >
          <view class="cat-icon-circle" :style="iconCircleStyle(c)">
            <app-icon :name="c.icon || 'grid'" :color="iconStrokeColor(c)" :size="20" :solid="true"></app-icon>
          </view>
          <text class="cat-name">{{ c.name }}</text>
          <text v-if="(c.subs || []).length" class="cat-sub-count">{{ c.subs.length }} 个子类</text>
          <view class="cat-ops">
            <view class="op-btn" @tap.stop="onRowMenu(i)">
              <app-icon name="more-horizontal" color="#c2c6d1" :size="18"></app-icon>
            </view>
            <view class="op-btn" @tap.stop="toggleExpand(i)">
              <app-icon
                :name="expanded === i ? 'chevron-down' : 'chevron-right'"
                color="#c2c6d1" :size="16"
              ></app-icon>
            </view>
          </view>
        </view>

        <!-- 子类面板：跟随在父类下方 -->
        <view v-if="expanded === i" class="sub-panel">
          <view
            v-for="(s, si) in (c.subs || [])"
            :key="s.name"
            :class="['sub-item', isDragTarget('sub', si) ? 'drag-target' : '']"
            :style="dragStyle('sub', si)"
            @longpress="startDrag('sub', si, i)"
            @tap="openEditSub(i, si)"
          >
            <view class="sub-icon-circle">
              <app-icon :name="s.icon || 'grid'" color="#7a8194" :size="19" :solid="true"></app-icon>
            </view>
            <text class="sub-name">{{ s.name }}</text>
          </view>
          <view class="sub-item sub-item-add" @tap="openAddSub(i)">
            <view class="sub-icon-circle sub-icon-add">
              <app-icon name="plus" color="#8a90a0" :size="22"></app-icon>
            </view>
            <text class="sub-name">添加子类</text>
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-safe"></view>
    <app-fab position="right-bottom" @click="openAddTop"></app-fab>

    <!-- 编辑弹窗 -->
    <wd-popup v-model="dlgShow" position="center" :close-on-click-modal="true" custom-class="dlg">
      <view class="dlg-body">
        <text class="dlg-title">{{ dlgTitle }}</text>
        <view class="icon-grid">
          <view
            v-for="ic in ICON_CHOICES"
            :key="ic"
            :class="['icon-cell', dlgIcon === ic ? 'icon-cell-active' : '']"
            @tap="dlgIcon = ic"
          >
            <app-icon :name="ic" color="#5b6170" :size="19" :solid="true"></app-icon>
          </view>
        </view>
        <wd-input
          v-model="dlgName"
          :maxlength="8"
          placeholder="分类名称"
          custom-class="dlg-input"
        ></wd-input>
        <view class="dlg-btns">
          <wd-button plain custom-class="dlg-btn" @click="dlgShow = false">取消</wd-button>
          <wd-button
            v-if="dlgMode === 'editSub'"
            custom-class="dlg-btn dlg-btn-danger"
            @click="confirmDeleteSub"
          >删除</wd-button>
          <wd-button custom-class="dlg-btn dlg-btn-primary" @click="confirmDialog">保存</wd-button>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<script setup>
import { ref, computed, getCurrentInstance } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCategories, saveCategories, listByType, ICON_CHOICES, DEFAULT_ICON, PALETTE } from '@/common/cats.js'

const type = ref('expense')
const expanded = ref(-1)
const data = ref({ expense: [], income: [] })
const instance = getCurrentInstance()

const list = computed(() => (type.value === 'income' ? data.value.income : data.value.expense))

const dlgShow = ref(false)
const dlgMode = ref('addTop') // addTop | addSub | editTop | editSub
const dlgIndex = ref(-1)
const dlgSubIndex = ref(-1)
const dlgName = ref('')
const dlgIcon = ref(DEFAULT_ICON)

const dlgTitle = computed(() => {
  if (dlgMode.value === 'addTop') return '添加一级分类'
  if (dlgMode.value === 'addSub') return '添加子类'
  if (dlgMode.value === 'editTop') return '编辑分类'
  return '编辑子类'
})

/* ---------- 分类图标配色 ---------- */
function catColor(i) {
  const c = list.value[i]
  if (!c) return PALETTE[0]
  const idx = list.value.findIndex(x => x.name === c.name)
  return PALETTE[(idx < 0 ? 0 : idx) % PALETTE.length]
}
function iconCircleStyle(c) {
  const idx = list.value.findIndex(x => x.name === c.name)
  const color = PALETTE[(idx < 0 ? 0 : idx) % PALETTE.length]
  return { backgroundColor: color + '14' }
}
function iconStrokeColor(c) {
  const idx = list.value.findIndex(x => x.name === c.name)
  return PALETTE[(idx < 0 ? 0 : idx) % PALETTE.length]
}

function refresh() {
  data.value = getCategories()
}

function persist() {
  saveCategories(data.value)
  refresh()
}

function switchType(t) {
  if (type.value === t) return
  type.value = t
  expanded.value = -1
}

function showHelp() {
  uni.showToast({ title: '长按拖动排序；单击修改图标与名称', icon: 'none' })
}

function toggleExpand(i) {
  expanded.value = expanded.value === i ? -1 : i
}

/* ---------- 长按拖拽排序 ---------- */
const drag = ref(null) // { type:'top'|'sub', parentIdx, from, to, startY, dy, rects }
let lastTouch = { x: 0, y: 0 }
let justDraggedAt = 0

function onCardTouch(e) {
  const t = e.touches && e.touches[0]
  if (t) lastTouch = { x: t.clientX, y: t.clientY }
}

function startDrag(kind, from, parentIdx = -1) {
  if (drag.value) return
  const sel = kind === 'top' ? '.cat-row' : '.sub-item'
  const query = uni.createSelectorQuery().in(instance.proxy)
  query.selectAll(sel).boundingClientRect()
  query.exec(res => {
    const rects = (res && res[0]) || []
    if (!rects.length) return
    drag.value = {
      kind,
      parentIdx,
      from,
      to: from,
      startY: lastTouch.y,
      dy: 0,
      rects
    }
    if (uni.vibrateShort) {
      try { uni.vibrateShort() } catch (e) { /* ignore */ }
    }
  })
}

function isDragTarget(kind, i) {
  const d = drag.value
  return !!d && d.kind === kind && i === d.to && i !== d.from
}

function dragStyle(kind, i) {
  const d = drag.value
  if (!d || d.kind !== kind || i !== d.from) return {}
  return {
    transform: 'translateY(' + d.dy + 'px) scale(1.04)',
    zIndex: 10,
    position: 'relative',
    boxShadow: '0 10rpx 30rpx rgba(60,50,20,0.18)',
    backgroundColor: '#ffffff'
  }
}

function onDragMove(e) {
  const d = drag.value
  if (!d) return
  const t = e.touches && e.touches[0]
  if (!t) return
  if (e.preventDefault) e.preventDefault()
  if (e.stopPropagation) e.stopPropagation()
  d.dy = t.clientY - d.startY
  // 命中检测：指针落在哪个条目上，就是目标位置
  let hit = d.from
  for (let i = 0; i < d.rects.length; i++) {
    const r = d.rects[i]
    if (t.clientY >= r.top && t.clientY <= r.bottom) {
      hit = i
      break
    }
  }
  d.to = hit
}

function onDragEnd() {
  const d = drag.value
  if (!d) return
  drag.value = null
  justDraggedAt = Date.now()
  if (d.to === d.from) return
  if (d.kind === 'top') {
    const arr = type.value === 'income' ? data.value.income : data.value.expense
    arr.splice(d.to, 0, arr.splice(d.from, 1)[0])
  } else {
    const top = list.value[d.parentIdx]
    if (top && top.subs) {
      top.subs.splice(d.to, 0, top.subs.splice(d.from, 1)[0])
    }
  }
  persist()
}

/* ---------- 弹窗 ---------- */
function openAddTop() {
  dlgMode.value = 'addTop'
  dlgName.value = ''
  dlgIcon.value = DEFAULT_ICON
  dlgShow.value = true
}

function openAddSub(topIdx) {
  dlgMode.value = 'addSub'
  dlgIndex.value = topIdx
  dlgName.value = ''
  dlgIcon.value = DEFAULT_ICON
  dlgShow.value = true
}

function openEditTop(i) {
  if (Date.now() - justDraggedAt < 600) return
  dlgMode.value = 'editTop'
  dlgIndex.value = i
  dlgName.value = list.value[i].name
  dlgIcon.value = list.value[i].icon || DEFAULT_ICON
  dlgShow.value = true
}

function openEditSub(topIdx, subIdx) {
  if (Date.now() - justDraggedAt < 600) return
  dlgMode.value = 'editSub'
  dlgIndex.value = topIdx
  dlgSubIndex.value = subIdx
  const sub = list.value[topIdx].subs[subIdx]
  dlgName.value = sub.name
  dlgIcon.value = sub.icon || DEFAULT_ICON
  dlgShow.value = true
}

function confirmDialog() {
  const name = (dlgName.value || '').trim()
  if (!name) {
    uni.showToast({ title: '请输入分类名称', icon: 'none' })
    return
  }
  const icon = dlgIcon.value || DEFAULT_ICON
  const arr = type.value === 'income' ? data.value.income : data.value.expense

  if (dlgMode.value === 'addTop') {
    if (arr.some(c => c.name === name)) {
      uni.showToast({ title: '分类已存在', icon: 'none' })
      return
    }
    arr.push({ name, icon })
  } else if (dlgMode.value === 'addSub') {
    const top = arr[dlgIndex.value]
    top.subs = top.subs || []
    if (top.subs.some(s => s.name === name)) {
      uni.showToast({ title: '子类已存在', icon: 'none' })
      return
    }
    top.subs.push({ name, icon })
    expanded.value = dlgIndex.value
  } else if (dlgMode.value === 'editTop') {
    const i = dlgIndex.value
    if (arr.some((c, idx) => idx !== i && c.name === name)) {
      uni.showToast({ title: '分类已存在', icon: 'none' })
      return
    }
    arr[i].name = name
    arr[i].icon = icon
  } else if (dlgMode.value === 'editSub') {
    const top = arr[dlgIndex.value]
    const si = dlgSubIndex.value
    if (top.subs.some((s, idx) => idx !== si && s.name === name)) {
      uni.showToast({ title: '子类已存在', icon: 'none' })
      return
    }
    top.subs[si].name = name
    top.subs[si].icon = icon
  }
  dlgShow.value = false
  persist()
  uni.showToast({ title: '已保存', icon: 'success' })
}

function confirmDeleteSub() {
  dlgShow.value = false
  removeSub(dlgIndex.value, dlgSubIndex.value)
}

/* ---------- 删除 ---------- */
function removeTop(i) {
  const c = list.value[i]
  uni.showModal({
    title: '删除分类「' + c.name + '」',
    content: '历史账单仍会保留该分类名称，仅从分类列表中移除。确定删除？',
    success: (res) => {
      if (res.confirm) {
        const arr = type.value === 'income' ? data.value.income : data.value.expense
        arr.splice(i, 1)
        if (expanded.value === i) expanded.value = -1
        persist()
      }
    }
  })
}

function removeSub(topIdx, subIdx) {
  const top = list.value[topIdx]
  const sub = top.subs[subIdx]
  uni.showModal({
    title: '删除子类「' + sub.name + '」',
    content: '历史账单仍会保留该子类名称。确定删除？',
    success: (res) => {
      if (res.confirm) {
        top.subs.splice(subIdx, 1)
        if (!top.subs.length) delete top.subs
        persist()
      }
    }
  })
}

/* 一级分类的"更多"操作：点行内右侧按钮（长按已用于拖拽） */
function onRowMenu(i) {
  uni.showActionSheet({
    itemList: ['展开/收起子类', '上移', '下移', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) expanded.value = expanded.value === i ? -1 : i
      else if (res.tapIndex === 1) move(i, -1)
      else if (res.tapIndex === 2) move(i, 1)
      else if (res.tapIndex === 3) removeTop(i)
    }
  })
}

function move(i, delta) {
  const arr = type.value === 'income' ? data.value.income : data.value.expense
  const j = i + delta
  if (j < 0 || j >= arr.length) {
    uni.showToast({ title: '已经到头了', icon: 'none' })
    return
  }
  const tmp = arr[i]
  arr[i] = arr[j]
  arr[j] = tmp
  persist()
}

function goBack() { uni.navigateBack() }

onShow(() => {
  refresh()
})
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background: transparent;
}
.nav-right-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cat-tabs {
  display: flex;
  flex-direction: row;
}
.cat-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24rpx 12rpx;
  font-size: 32rpx;
  color: var(--c-dim);
  font-weight: 500;
}
.cat-tab-active {
  color: var(--c-text);
  font-weight: 700;
}
.cat-tab-line {
  margin-top: 6rpx;
  width: 48rpx;
  height: 6rpx;
  border-radius: 4rpx;
  background-color: var(--c-text);
}
.hint-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  background-color: #f4f2ec;
  padding: 16rpx 0;
}
.hint-text {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #9aa0ab;
}
.cat-card {
  background-color: var(--c-card);
  margin: 24rpx 24rpx 0;
  border-radius: 28rpx;
  box-shadow: var(--shadow-card);
  padding: 6rpx 28rpx 28rpx;
}
.drag-active {
  user-select: none;
}
.cat-block {
  border-radius: 16rpx;
}
.cat-block-border {
  border-top: 1rpx solid var(--c-line);
}
.cat-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 24rpx 0;
}
.drag-target {
  background-color: var(--c-primary-weak);
  border-radius: 16rpx;
}
.cat-icon-circle {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.cat-name {
  font-size: 30rpx;
  color: var(--c-text);
  font-weight: 500;
}
.cat-sub-count {
  margin-left: 14rpx;
  font-size: 22rpx;
  color: var(--c-dim);
}
.cat-ops {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: auto;
}
.op-btn {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 6rpx;
}
.sub-panel {
  background-color: var(--glass-bg);
  border-radius: 22rpx;
  padding: 24rpx 18rpx;
  margin: 0 0 20rpx;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}
.sub-item {
  width: 150rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12rpx 0;
  border-radius: 16rpx;
}
.sub-icon-circle {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sub-name {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #5b6170;
  max-width: 140rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bottom-safe {
  height: 160rpx;
}
/* 弹窗 */
.dlg {
  border-radius: 32rpx !important;
  width: 640rpx;
  background-color: var(--glass-bg-strong) !important;
  -webkit-backdrop-filter: blur(18px) saturate(1.6);
  backdrop-filter: blur(20px) saturate(1.4);
}
.dlg-body {
  padding: 40rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
}
.dlg-title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--c-text);
  margin-bottom: 26rpx;
}
.icon-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  max-height: 330rpx;
  overflow-y: auto;
}
.icon-cell {
  width: 76rpx;
  height: 76rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 4rpx;
  background-color: var(--glass-bg);
}
.icon-cell-active {
  background-color: var(--c-primary-weak);
  box-shadow: 0 0 0 3rpx var(--c-primary) inset;
}
.dlg-input {
  margin-top: 22rpx;
  background-color: #f4f6fa;
  border-radius: 16rpx;
}
.dlg-btns {
  display: flex;
  flex-direction: row;
  margin-top: 32rpx;
}
.dlg-btn {
  flex: 1;
  margin: 0 8rpx;
}
.dlg-btn.plain {
  color: var(--c-sub) !important;
  border-color: var(--c-dim) !important;
  background-color: #ffffff !important;
}
.dlg-btn-primary {
  background-color: var(--c-primary) !important;
  color: #ffffff !important;
}
.dlg-btn-danger {
  background-color: var(--c-out) !important;
  color: #ffffff !important;
}
</style>
