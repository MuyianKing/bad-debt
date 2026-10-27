<template>
  <view :class="['page', themeClass]">
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
    </wd-navbar>

    <view class="hint-bar">
      <mascot-face class="hint-mascot" :mood="hintMood" :head="true" :size="52"></mascot-face>
      <text class="hint-name">{{ activeMascot.name }}</text>
      <text class="hint-text">：{{ hintLine }}</text>
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
        :style="blockStyle(i)"
      >
        <view
          class="cat-row"
          @longpress="startDrag('top', i)"
          @tap="openEditTop(i)"
        >
          <view class="cat-icon-circle" :style="iconCircleStyle(c)">
            <app-icon :name="c.icon || 'grid'" color="#ffffff" :size="20" :solid="true"></app-icon>
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
            class="sub-item"
            :style="subItemStyle(si)"
            @longpress="startDrag('sub', si, i)"
            @tap="openEditSub(i, si)"
          >
            <view class="sub-icon-circle" :style="{ backgroundColor: subTint(i) }">
              <app-icon :name="s.icon || 'grid'" :color="subColor(i)" :size="19" :solid="true"></app-icon>
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
            v-for="(ic, ii) in ICON_CHOICES"
            :key="ic"
            :class="['icon-cell', dlgIcon === ic ? 'icon-cell-active' : '']"
            :style="{ backgroundColor: iconTint(ii) }"
            @tap="dlgIcon = ic"
          >
            <app-icon :name="ic" :color="iconColor(ii)" :size="19" :solid="true"></app-icon>
          </view>
        </view>
        <view class="dlg-field-label">
          <text class="dlg-field-name">分类名称</text>
          <text :class="['dlg-count', dlgName.length >= 8 ? 'dlg-count-max' : '']">{{ dlgName.length }}/8</text>
        </view>
        <view :class="['dlg-field', inputFocused ? 'dlg-field-focus' : '']">
          <wd-input
            v-model="dlgName"
            :maxlength="8"
            :no-border="true"
            placeholder="给分类起个名字"
            placeholder-class="dlg-placeholder"
            custom-class="dlg-input"
            custom-input-class="dlg-input-inner"
            @focus="inputFocused = true"
            @blur="inputFocused = false"
          >
            <template #prefix>
              <view class="dlg-input-ic">
                <app-icon name="edit-3" color="#ffffff" :size="15" :stroke-width="2.2"></app-icon>
              </view>
            </template>
          </wd-input>
        </view>
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

    <!-- 一级分类更多操作 -->
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
import { ref, computed, getCurrentInstance, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getCategories, saveCategories, listByType, ICON_CHOICES, DEFAULT_ICON, PALETTE, tintOf } from '@/common/cats.js'
import { theme, themeClass, refreshTheme } from '@/common/theme.js'
import { activeMascot, refreshMascots } from '@/common/mascots.js'

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
// 输入框聚焦态：驱动主题色的边框/光晕，关闭弹窗或失焦时复原
const inputFocused = ref(false)

// 一级分类更多操作菜单（wot action sheet）
const actionShow = ref(false)
const actionIndex = ref(-1)
const actionItems = ref([])

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
  return { backgroundColor: color }
}
function subColor(i) {
  return PALETTE[i % PALETTE.length]
}
function subTint(i) {
  return tintOf(subColor(i), 0.86)
}
function iconColor(i) {
  return PALETTE[i % PALETTE.length]
}
function iconTint(i) {
  return tintOf(iconColor(i), 0.88)
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

function toggleExpand(i) {
  expanded.value = expanded.value === i ? -1 : i
}

/* ---------- 长按拖拽排序 ---------- */
// drag 状态：{ kind, parentIdx, from, to, startX, startY, dx, dy, rects, settling }
const drag = ref(null)
let lastTouch = { x: 0, y: 0 }
let justDraggedAt = 0
const SETTLE_MS = 200

/* ---------- 提示条角色：常态报操作，拖拽/保存后换表情与台词 ---------- */
const savedTip = ref('')
let savedTimer = null

const hintMood = computed(() => {
  if (savedTip.value) return 'success'
  return drag.value ? 'warn' : 'idle'
})

const hintLine = computed(() => {
  if (savedTip.value) return savedTip.value
  const d = drag.value
  if (d) {
    if (d.settling) return '放好了'
    return d.to === d.from ? '按住，上下挪' : '松手，放这儿'
  }
  return type.value === 'income' ? '点一下改名，长按能拖动' : '长按排序，点一下改图标'
})

function flashSaved(text) {
  savedTip.value = text
  clearTimeout(savedTimer)
  savedTimer = setTimeout(() => { savedTip.value = '' }, 1200)
}

function onCardTouch(e) {
  const t = e.touches && e.touches[0]
  if (t) lastTouch = { x: t.clientX, y: t.clientY }
}

/* ---------- 拖动时锁住页面滚动 ---------- */
// App 端页面逻辑跑在逻辑层（app-service.js），这里没有 document；
// 滚动发生在视图层，锁滚动由文件末尾 renderjs 的 scrollGuard 负责。

function startDrag(kind, from, parentIdx = -1) {
  if (drag.value) return
  // 父类量整块（行 + 展开的子类面板），子类量每个格子
  const sel = kind === 'top' ? '.cat-block' : '.sub-item'
  const query = uni.createSelectorQuery().in(instance.proxy)
  query.selectAll(sel).boundingClientRect()
  query.exec(res => {
    let rects = (res && res[0]) || []
    if (kind === 'sub') {
      // 末尾的「添加子类」不是排序目标，按真实子类数量截掉
      const subCount = ((list.value[parentIdx] || {}).subs || []).length
      rects = rects.slice(0, subCount)
    }
    if (!rects.length) return
    drag.value = {
      kind,
      parentIdx,
      from,
      to: from,
      startX: lastTouch.x,
      startY: lastTouch.y,
      dx: 0,
      dy: 0,
      rects,
      settling: false
    }
    if (uni.vibrateShort) {
      try { uni.vibrateShort() } catch (e) { /* ignore */ }
    }
  })
}

/** 父类块：被拖的整块（含子类面板）跟手，其余块按目标槽位实时让位 */
function blockStyle(i) {
  const d = drag.value
  if (!d || d.kind !== 'top') return {}
  if (i === d.from) {
    return {
      transform: 'translate3d(0,' + d.dy + 'px,0)',
      transition: d.settling ? 'transform ' + SETTLE_MS + 'ms ease' : 'none',
      position: 'relative',
      zIndex: 20,
      // 用主题淡底托住整块，别用纯白块把玻璃卡盖住
      backgroundColor: 'var(--c-primary-weak)',
      borderRadius: '16rpx',
      boxShadow: '0 12rpx 30rpx rgba(60, 50, 20, 0.2)'
    }
  }
  const shift = topShift(d, i)
  return {
    transform: shift ? 'translate3d(0,' + shift + 'px,0)' : '',
    transition: 'transform ' + SETTLE_MS + 'ms ease'
  }
}

/** 子类格子：被拖的跟手，其余格子补到前/后一个格位 */
function subItemStyle(i) {
  const d = drag.value
  if (!d || d.kind !== 'sub') return {}
  if (i === d.from) {
    return {
      transform: 'translate3d(' + d.dx + 'px,' + d.dy + 'px,0)',
      transition: d.settling ? 'transform ' + SETTLE_MS + 'ms ease' : 'none',
      position: 'relative',
      zIndex: 20,
      backgroundColor: 'var(--c-primary-weak)',
      borderRadius: '16rpx',
      boxShadow: '0 12rpx 30rpx rgba(60, 50, 20, 0.2)'
    }
  }
  const off = subShift(d, i)
  return {
    transform: off ? 'translate3d(' + off.dx + 'px,' + off.dy + 'px,0)' : '',
    transition: 'transform ' + SETTLE_MS + 'ms ease'
  }
}

/** 父类让位：向下拖时上面的块整体上移一格，向上拖反之 */
function topShift(d, i) {
  const h = d.rects[d.from].height
  if (i > d.from && i <= d.to) return -h
  if (i < d.from && i >= d.to) return h
  return 0
}

/** 子类让位：把受影响的格子平移到它前/后一个格子原来的位置（可跨行） */
function subShift(d, i) {
  if (i > d.from && i <= d.to) {
    return {
      dx: d.rects[i - 1].left - d.rects[i].left,
      dy: d.rects[i - 1].top - d.rects[i].top
    }
  }
  if (i < d.from && i >= d.to) {
    return {
      dx: d.rects[i + 1].left - d.rects[i].left,
      dy: d.rects[i + 1].top - d.rects[i].top
    }
  }
  return null
}

function onDragMove(e) {
  const d = drag.value
  if (!d || d.settling) return
  const t = e.touches && e.touches[0]
  if (!t) return
  if (e.preventDefault) e.preventDefault()
  if (e.stopPropagation) e.stopPropagation()
  d.dx = t.clientX - d.startX
  d.dy = t.clientY - d.startY
  const hit = hitTest(d, t.clientX, t.clientY)
  if (hit !== d.to) d.to = hit
}

/** 命中检测：父类按块、子类按格子横纵坐标，落在缝隙里保持上一个目标 */
function hitTest(d, x, y) {
  let hit = d.to
  for (let i = 0; i < d.rects.length; i++) {
    const r = d.rects[i]
    const inY = y >= r.top && y <= r.bottom
    if (!inY) continue
    if (d.kind === 'sub' && (x < r.left || x > r.right)) continue
    hit = i
    break
  }
  return hit
}

let settleTimer = null

function clearSettle() {
  if (settleTimer) {
    clearTimeout(settleTimer)
    settleTimer = null
  }
}

function onDragEnd() {
  const d = drag.value
  if (!d || d.settling) return
  justDraggedAt = Date.now()
  d.settling = true
  if (d.to === d.from) {
    // 原位放回：滑回原槽位
    d.dx = 0
    d.dy = 0
  } else if (d.kind === 'top') {
    const from = d.rects[d.from]
    const to = d.rects[d.to]
    // 向下拖时，让位后空出来的槽在目标块旧位置的下沿
    d.dy = (d.to > d.from ? to.top + to.height - from.height : to.top) - from.top
  } else {
    d.dx = d.rects[d.to].left - d.rects[d.from].left
    d.dy = d.rects[d.to].top - d.rects[d.from].top
  }
  clearSettle()
  // 吸附动画由视图层执行，样式下发有一两帧的桥接延迟，
  // 落库时间要比动画时长再多留一点，避免动画没播完就被改数据截断
  settleTimer = setTimeout(commitDrag, SETTLE_MS + 100)
}

/** 松手后先播完吸附动画，再改数据落库 */
function commitDrag() {
  settleTimer = null
  const d = drag.value
  if (!d) return
  drag.value = null
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
  flashSaved('排好了')
}

onUnmounted(() => {
  // 页面被直接销毁时别让吸附定时器继续跑
  if (settleTimer) {
    clearSettle()
    commitDrag()
  }
  clearTimeout(savedTimer)
})

/* ---------- 弹窗 ---------- */
function openAddTop() {
  dlgMode.value = 'addTop'
  dlgName.value = ''
  dlgIcon.value = DEFAULT_ICON
  inputFocused.value = false
  dlgShow.value = true
}

function openAddSub(topIdx) {
  dlgMode.value = 'addSub'
  dlgIndex.value = topIdx
  dlgName.value = ''
  dlgIcon.value = DEFAULT_ICON
  inputFocused.value = false
  dlgShow.value = true
}

function openEditTop(i) {
  if (Date.now() - justDraggedAt < 600) return
  dlgMode.value = 'editTop'
  dlgIndex.value = i
  dlgName.value = list.value[i].name
  dlgIcon.value = list.value[i].icon || DEFAULT_ICON
  inputFocused.value = false
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
  inputFocused.value = false
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
  flashSaved('记下了')
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

/* 一级分类的"更多"操作：只留删除；展开用行尾箭头，排序用长按拖拽 */
function onRowMenu(i) {
  actionIndex.value = i
  actionItems.value = [{ name: '删除', color: 'var(--c-out)' }]
  actionShow.value = true
}

function onActionSelect({ index }) {
  const i = actionIndex.value
  if (i < 0) return
  if (index === 0) removeTop(i)
}

function goBack() { uni.navigateBack() }

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
  refresh()
})

onHide(() => {
  // 拖拽中途切后台时 touchend 可能收不到：吸附中的直接落位，其余复位
  if (settleTimer) {
    clearSettle()
    commitDrag()
  }
  drag.value = null
  clearTimeout(savedTimer)
  savedTip.value = ''
  // 下一层页面沿用清单默认的深色图标
  // #ifdef APP-PLUS
  try { plus.navigator.setStatusBarStyle('dark') } catch (e) {}
  // #endif
})
</script>

<!-- 拖拽排序时锁住页面滚动。App 端页面逻辑跑在逻辑层（没有 document），
     滚动发生在视图层，所以监听必须写在 renderjs 里；H5 会把这段混入页面组件，同样生效 -->
<script module="scrollGuard" lang="renderjs">
export default {
  data() {
    return { locked: false }
  },
  mounted() {
    // 非 passive 的 touchmove 守卫在页面渲染时就挂上：
    // 浏览器从手势开始就允许 preventDefault，拖到一半再挂就来不及了
    this.onTouchStart = (e) => {
      // 拖到一半切后台等异常路径不会有 touchend，靠下一次单指触摸自愈
      if (e.touches.length === 1) this.locked = false
    }
    // 视图层在长按 350ms（位移不超过 10px）时派发 longpress DOM 事件，
    // 和页面 @longpress 是同一个事件，用它作为"开始拖拽"的信号
    this.onLongPress = (e) => {
      const row = e.target && e.target.closest && e.target.closest('.cat-row, .sub-item')
      if (row && !row.classList.contains('sub-item-add')) this.locked = true
    }
    this.onTouchMove = (e) => {
      if (!this.locked) return
      if (e.cancelable) e.preventDefault()
    }
    this.onTouchEnd = () => { this.locked = false }

    document.addEventListener('touchstart', this.onTouchStart, true)
    document.addEventListener('longpress', this.onLongPress, true)
    // 捕获阶段 + 非 passive：既不会被行上的事件转发 stopPropagation 截断，
    // 又能真正取消 WebView 的原生滚动
    document.addEventListener('touchmove', this.onTouchMove, { passive: false, capture: true })
    document.addEventListener('touchend', this.onTouchEnd, true)
    document.addEventListener('touchcancel', this.onTouchEnd, true)
  },
  beforeDestroy() {
    document.removeEventListener('touchstart', this.onTouchStart, true)
    document.removeEventListener('longpress', this.onLongPress, true)
    document.removeEventListener('touchmove', this.onTouchMove, { capture: true })
    document.removeEventListener('touchend', this.onTouchEnd, true)
    document.removeEventListener('touchcancel', this.onTouchEnd, true)
  }
}
</script>

<style lang="scss">
.page {
  min-height: 100vh;
  background-color: var(--c-bg);
}
.cat-tabs {
  display: flex;
  flex-direction: row;
  justify-content: center;
  width: 100%;
  /* wd-navbar 的 title 槽是 60% 宽、行高为导航栏高度，这里显式垂直居中 */
  height: 100%;
  align-items: center;
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
  background-color: var(--c-primary);
}
.hint-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-start;
  background-color: var(--c-primary-weak);
  padding: 12rpx 28rpx;
}
.hint-mascot {
  flex-shrink: 0;
  margin-right: 10rpx;
}
.hint-name {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--c-primary-deep);
  flex-shrink: 0;
}
.hint-text {
  flex: 1;
  min-width: 0;
  margin-left: 0;
  font-size: 24rpx;
  color: var(--c-sub);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.cat-card {
  background-color: var(--c-card);
  margin: 24rpx 24rpx 0;
  border-radius: 28rpx;
  box-shadow: var(--shadow-card);
  /* 容器不加左右内边距：内边距放到每一行，拖动时行底色才能铺到卡片边缘 */
  padding: 6rpx 0 28rpx;
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
  padding: 24rpx 28rpx;
}
.cat-icon-circle {
  width: 66rpx;
  height: 66rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
  box-shadow: 0 5rpx 12rpx rgba(60, 50, 20, 0.16);
}
.cat-name {
  font-size: 30rpx;
  color: var(--c-text);
  font-weight: 500;
}
.cat-sub-count {
  margin-left: 14rpx;
  font-size: 21rpx;
  color: var(--c-primary-deep);
  background-color: var(--c-primary-weak);
  border-radius: 9rpx;
  padding: 3rpx 10rpx;
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
  margin: 0 28rpx 20rpx;
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
  color: var(--c-sub);
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
  -webkit-overflow-scrolling: touch;
}
/* 图标区滚动条在弹窗里很碍眼，直接隐藏（仍可滚动） */
.icon-grid::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
.icon-cell {
  /* 弹窗内容宽 576rpx：74 + 8 边距 = 82rpx/列，正好排 7 列，右侧几乎不留白 */
  width: 74rpx;
  height: 74rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 4rpx;
}
.icon-cell-active {
  box-shadow: 0 0 0 3rpx var(--c-primary) inset, var(--shadow-primary-sm);
  transform: scale(1.04);
}
/* 名称输入：主题化表单字段（图标 + 计数 + 聚焦光晕） */
.dlg-field-label {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin: 24rpx 8rpx 0;
}
.dlg-field-name {
  font-size: 23rpx;
  color: var(--c-sub);
  letter-spacing: 1rpx;
}
.dlg-count {
  font-size: 22rpx;
  color: var(--c-dim);
  font-variant-numeric: tabular-nums;
}
.dlg-count-max {
  color: var(--c-primary-deep);
  font-weight: 600;
}
.dlg-field {
  margin-top: 12rpx;
  padding: 0 18rpx;
  border-radius: 24rpx;
  background-color: var(--c-primary-weak);
  border: 2rpx solid transparent;
  transition: background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}
.dlg-field-focus {
  background-color: var(--c-card);
  border-color: var(--c-primary);
  box-shadow: 0 0 0 6rpx var(--c-primary-tint), var(--shadow-primary-sm);
}
.dlg-input {
  background-color: transparent !important;
  padding: 0 !important;
}
.dlg-input .wd-input__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 96rpx;
}
.dlg-input .wd-input__value {
  align-items: center;
}
.dlg-input .wd-input__prefix {
  margin-right: 16rpx;
}
.dlg-input .wd-input__inner {
  height: 96rpx;
  padding-left: 0;
  font-size: 30rpx;
  font-weight: 500;
  color: var(--c-text);
}
.dlg-placeholder {
  color: var(--c-dim);
  font-weight: 400;
}
.dlg-input-ic {
  width: 46rpx;
  height: 46rpx;
  border-radius: 14rpx;
  background-color: var(--c-primary-fill);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-primary-sm);
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
.dlg-btn.is-plain {
  color: var(--c-sub) !important;
  border-color: var(--c-line) !important;
  background-color: var(--c-card) !important;
}
.dlg-btn-primary {
  background-color: var(--c-primary-fill) !important;
  color: #ffffff !important;
  box-shadow: var(--shadow-primary-sm);
}
.dlg-btn-danger {
  background-color: var(--c-out) !important;
  color: #ffffff !important;
}
</style>
