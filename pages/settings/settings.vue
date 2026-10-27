<template>
  <view :class="['page', themeClass]">
    <wd-navbar
      title="设置·关于"
      :left-arrow="true"
      :fixed="true"
      :placeholder="true"
      :safe-area-inset-top="true"
      :bordered="navSolid"
      :custom-style="navStyle"
      @click-left="goBack"
    ></wd-navbar>

    <view class="group-title">外观</view>
    <view class="theme-card glass">
      <view class="theme-head">
        <view class="cell-ic ic-primary">
          <app-icon name="droplet" color="#ffffff" :size="16"></app-icon>
        </view>
        <view class="theme-head-text">
          <text class="theme-head-title">主题</text>
          <text class="theme-head-desc">当前：{{ theme.name }}，切换后立即生效</text>
        </view>
      </view>
      <view class="theme-list">
        <view
          v-for="t in THEMES"
          :key="t.id"
          :class="['theme-tile', theme.id === t.id ? 'theme-tile-active' : '']"
          @tap="applyTheme(t.id)"
        >
          <view class="theme-preview" :style="{ background: t.grad }">
            <view class="theme-preview-card">
              <view class="theme-preview-line"></view>
              <view class="theme-preview-line theme-preview-line-sm"></view>
            </view>
            <view v-if="theme.id === t.id" class="theme-check">
              <app-icon name="check" :color="theme.primary" :size="12" :stroke-width="3.4"></app-icon>
            </view>
          </view>
          <text class="theme-name">{{ t.name }}</text>
          <text class="theme-tag">{{ t.tagline }}</text>
        </view>
      </view>
    </view>
    <view v-if="activeMascot && activeMascotBaseStyle" class="theme-card glass mascot-card">
      <view class="theme-head">
        <view class="cell-ic ic-rose">
          <app-icon name="smile" color="#ffffff" :size="16"></app-icon>
        </view>
        <view class="theme-head-text">
          <text class="theme-head-title">{{ activeMascot.name }}表情</text>
          <text class="theme-head-desc">当前：{{ activeMascotBaseStyle.name }}{{ autoMood ? '，跟随场景' : '，固定表情' }}</text>
        </view>
      </view>
      <view class="mood-row">
        <view class="mood-row-text">
          <text class="mood-row-title">跟随场景</text>
          <text class="mood-row-desc">{{ moodHint }}</text>
        </view>
        <wd-switch :model-value="autoMood" active-color="var(--c-primary)" @change="onAutoMoodChange" />
      </view>
      <view class="theme-list mascot-list">
        <view
          v-for="n in activeMascot.styles"
          :key="n.id"
          :class="['theme-tile', 'mascot-tile', activeMascotBaseStyle.id === n.id ? 'theme-tile-active' : '']"
          @tap="applyMascotStyle(n.id)"
        >
          <view class="mascot-preview">
            <image class="mascot-thumb" :src="n.headSrc || n.src" mode="aspectFit"></image>
            <view v-if="activeMascotBaseStyle.id === n.id" class="theme-check">
              <app-icon name="check" :color="theme.primary" :size="12" :stroke-width="3.4"></app-icon>
            </view>
          </view>
          <text class="theme-name">{{ n.name }}</text>
          <text class="theme-tag">{{ n.desc }}</text>
        </view>
      </view>
    </view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell :is-link="true" :clickable="true" @click="onHeroImgTap">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-sky">
                <app-icon name="image" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">首页头图</text>
            </view>
            <text class="cell-desc">自定义首页顶部背景图，仅保存在本机</text>
          </view>
        </template>
        <image v-if="heroImg" class="hero-thumb" :src="heroImg" mode="aspectFill"></image>
        <text v-else class="cell-action">设置图片</text>
      </wd-cell>
    </wd-cell-group>

    <view class="group-title">自动记账</view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell :is-link="!listenerEnabled" :custom-class="listenerEnabled ? 'cell-flush' : ''" :clickable="true" @click="goAuth">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-primary">
                <app-icon name="shield" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">无障碍服务</text>
              <text :class="['state', listenerEnabled ? 'state-ok' : 'state-warn', listenerEnabled ? 'state-right' : '']">{{ listenerEnabled ? '已开启' : '未开启' }}</text>
            </view>
            <text class="cell-desc">自动记账的核心开关。开启后，你在支付宝/微信等App里完成支付时，孽账会读取支付结果页的文字，在本地解析出待确认账单。</text>
          </view>
        </template>
      </wd-cell>
      <wd-cell :is-link="!notifyEnabled" :custom-class="notifyEnabled ? 'cell-flush' : ''" :clickable="true" @click="goNotifyAuth">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-sky">
                <app-icon name="bell" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">通知使用权（辅助）</text>
              <text :class="['state', notifyEnabled ? 'state-ok' : 'state-warn', notifyEnabled ? 'state-right' : '']">{{ notifyEnabled ? '已开启' : '未开启' }}</text>
            </view>
            <text class="cell-desc">补充无障碍覆盖不到的场景：收款到账提醒只在通知栏、熄屏碰卡支付的交易通知。两路来源自动去重，不会重复入账。</text>
          </view>
        </template>
      </wd-cell>
      <wd-cell :is-link="!(repairOk && listenerEnabled)" :custom-class="repairOk && listenerEnabled ? 'cell-flush' : ''" :clickable="true" @click="onSelfRepair">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-violet">
                <app-icon name="rotate-ccw" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">无障碍自恢复</text>
              <text :class="['state', repairOk ? 'state-ok' : 'state-warn', repairOk && listenerEnabled ? 'state-right' : '']">{{ repairOk ? '已授权' : '未授权' }}</text>
              <text v-if="!repairOk || !listenerEnabled" class="cell-action">{{ repairOk ? '立即恢复' : '授权教程' }}</text>
            </view>
            <text class="cell-desc">系统可能杀掉无障碍服务导致自动记账失效。用电脑 adb 执行一次授权命令后，打开孽账会自动把服务重新开启，无需再去系统设置手动开。</text>
          </view>
        </template>
      </wd-cell>
      <!-- #ifdef APP-PLUS -->
      <wd-cell :is-link="!overlayPerm" :custom-class="overlayPerm ? 'cell-flush' : ''" :clickable="true" @click="onOverlayPermission">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-blue">
                <app-icon name="maximize" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">显示在其他应用上层</text>
              <text :class="['state', overlayPerm ? 'state-ok' : 'state-warn', overlayPerm ? 'state-right' : '']">{{ overlayPerm ? '已允许' : '未允许' }}</text>
              <text v-if="!overlayPerm" class="cell-action">去允许</text>
            </view>
            <text class="cell-desc">系统级悬浮窗开关，弹层要盖在支付页上方必须先允许。这里只开系统开关，不绘制悬浮窗。</text>
          </view>
        </template>
      </wd-cell>
      <wd-cell v-if="popupState.miui" :is-link="!miuiPopupState.ok" :custom-class="miuiPopupState.ok ? 'cell-flush' : ''" :clickable="true" @click="onMiuiPopupPermission">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-violet">
                <app-icon name="shield" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">后台弹出界面（MIUI）</text>
              <text :class="['state', miuiPopupState.ok ? 'state-ok' : 'state-warn', miuiPopupState.ok ? 'state-right' : '']">{{ miuiPopupState.text }}</text>
              <text v-if="!miuiPopupState.ok" class="cell-action">去开启</text>
            </view>
            <text class="cell-desc">MIUI/HyperOS 单独的后台启动 Activity 开关，和上面的悬浮窗开关不是一个。点「去开启」后进入权限页，再点「其他权限」→「后台弹出界面」；未开启时弹层会被系统拦掉，只能收到待确认通知。</text>
          </view>
        </template>
      </wd-cell>
      <!-- #endif -->
      <wd-cell :is-link="!notifAllowed" :custom-class="notifAllowed ? 'cell-flush' : ''" :clickable="true" @click="requestNotifPermission">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-rose">
                <app-icon name="bell" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">App 弹通知权限</text>
              <text :class="['state', notifAllowed ? 'state-ok' : 'state-warn', notifAllowed ? 'state-right' : '']">{{ notifAllowed ? '已开启' : '未开启' }}</text>
              <text v-if="!notifAllowed" class="cell-action">请求权限</text>
            </view>
            <text class="cell-desc">Android 13+ 需要。用于后台保活常驻通知，以及确认弹层被系统拦住时的待确认提醒；关闭后这两类通知都收不到</text>
          </view>
        </template>
      </wd-cell>
    </wd-cell-group>

    <!-- #ifdef APP-PLUS -->
    <view class="group-title">后台运行</view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell :is-link="!batteryOk" :custom-class="batteryOk ? 'cell-flush' : ''" :clickable="true" @click="onBattery">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-primary">
                <app-icon name="battery-charging" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">电池优化</text>
              <text :class="['state', batteryOk ? 'state-ok' : 'state-warn', batteryOk ? 'state-right' : '']">{{ batteryOk ? '不限制' : '受限制' }}</text>
            </view>
            <text class="cell-desc">系统省电策略可能在锁屏后回收后台进程，导致自动记账失效。请把电池策略设为"不限制"。</text>
          </view>
        </template>
      </wd-cell>
      <wd-cell>
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-mint">
                <app-icon name="zap" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">后台保活</text>
              <text :class="['state', keepAlive ? 'state-ok' : 'state-warn']">{{ keepAlive ? '已开启' : '已关闭' }}</text>
            </view>
            <text class="cell-desc">用一条低优先级常驻通知维持进程，降低被系统回收的概率。关闭后仍可记账，但后台更容易被厂商省电策略杀掉。</text>
          </view>
        </template>
        <wd-switch :model-value="keepAlive" active-color="var(--c-primary)" @change="onKeepAliveChange" />
      </wd-cell>
      <wd-cell>
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-blue">
                <app-icon name="layers" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">从多任务页面隐藏</text>
              <text :class="['state', hideRecents ? 'state-ok' : 'state-warn']">{{ hideRecents ? '已隐藏' : '显示中' }}</text>
            </view>
            <text class="cell-desc">隐藏后系统「一键清理」不会把本应用任务一起清掉，自动记账更不容易被连带杀死。关闭后可在最近任务里看到本应用。</text>
          </view>
        </template>
        <wd-switch :model-value="hideRecents" active-color="var(--c-primary)" @change="onHideRecentsChange" />
      </wd-cell>
      <wd-cell :is-link="true" :clickable="true" @click="openAppDetail">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <view class="cell-ic ic-violet">
                <app-icon name="power" color="#ffffff" :size="16"></app-icon>
              </view>
              <text class="cell-title">自启动 / 后台管理</text>
              <text class="cell-action">去设置</text>
            </view>
            <text class="cell-desc">小米、OPPO、vivo 等机型还需在应用详情里允许"自启动""后台运行"，否则锁屏后仍可能被清理。</text>
          </view>
        </template>
      </wd-cell>
    </wd-cell-group>
    <!-- #endif -->

    <view class="group-title">隐私说明</view>
    <view class="privacy-card glass">
      <view class="privacy-head">
        <view class="cell-ic ic-primary">
          <app-icon name="lock" color="#ffffff" :size="16"></app-icon>
        </view>
        <text class="privacy-title">数据只在本机</text>
      </view>
      <text class="privacy-text">· 只读取候选支付App的窗口文字，且只在手机本地解析，不上传任何服务器</text>
      <text class="privacy-text">· 账单数据保存在本机 SQLite，可随时删除</text>
      <text class="privacy-text">· 白名单之外的App内容不会被读取或存储</text>
    </view>

    <view class="bottom-safe"></view>

    <!-- 首页头图操作菜单 -->
    <wd-action-sheet
      v-model="heroMenuShow"
      :actions="heroMenuItems"
      cancel-text="取消"
      :z-index="300"
      :safe-area-inset-bottom="true"
      @select="onHeroMenuSelect"
    ></wd-action-sheet>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onHide, onPageScroll } from '@dcloudio/uni-app'
import { THEMES, theme, themeClass, refreshTheme, setTheme } from '@/common/theme.js'
import { activeMascot, activeMascotBaseStyle, autoMood, refreshMascots, setMascotStyle, setAutoMood } from '@/common/mascots.js'
import { isHideRecentsEnabled, setHideRecentsEnabled } from '@/common/recents.js'
// #ifdef APP-PLUS
import {
  isAccessibilityEnabled, openAccessibilitySettings,
  isNotificationListenerEnabled, openNotificationListenerSettings,
  areNotificationsEnabled,
  getSelfRepairState, repairAccessibilityService,
  isKeepAliveEnabled, setKeepAliveEnabled,
  isIgnoringBatteryOptimizations, requestIgnoreBatteryOptimizations,
  openAppSettings,
  hasOverlayPermission, requestOverlayPermission,
  getPopupPermissionState, openPopupPermissionSettings,
  setCardThemeColor
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  isAccessibilityEnabled, openAccessibilitySettings,
  isNotificationListenerEnabled, openNotificationListenerSettings,
  areNotificationsEnabled,
  getSelfRepairState, repairAccessibilityService,
  isKeepAliveEnabled, setKeepAliveEnabled,
  isIgnoringBatteryOptimizations, requestIgnoreBatteryOptimizations,
  openAppSettings,
  hasOverlayPermission, requestOverlayPermission,
  getPopupPermissionState, openPopupPermissionSettings,
  setCardThemeColor
} from '@/common/web-bill-api.js'
// #endif

const listenerEnabled = ref(false)
const notifyEnabled = ref(false)
const notifAllowed = ref(true)
// 系统"显示在其他应用上层"（SYSTEM_ALERT_WINDOW）：弹层要盖在支付页上方的前置条件
const overlayPerm = ref(false)
// MIUI「后台弹出界面」是小米/Redmi/POCO 独有的独立 AppOp，非 MIUI 机型不显示这一项
const popupState = ref({ miui: false, overlay: false, miuiPopup: -1, miuiPopupKnown: false, blocked: false })
const miuiPopupState = computed(() => {
  const s = popupState.value
  if (s.miuiPopupKnown) {
    if (s.miuiPopup === 0) return { ok: true, text: '已允许' }
    if (s.miuiPopup === 1) return { ok: false, text: '未允许' }
    return { ok: false, text: '未检测' }
  }
  if (s.blocked) return { ok: false, text: '被系统拦截' }
  return { ok: false, text: '未检测' }
})
// 后台运行：电池优化是否已豁免、后台保活开关
const batteryOk = ref(true)
const keepAlive = ref(true)
// 从最近任务里隐藏（运行时 AppTask.setExcludeFromRecents，部分 ROM 不认清单标记）
const hideRecents = ref(true)
// 自恢复：WRITE_SECURE_SETTINGS 是否已授权、目标应用包名（拼 adb 命令展示用）
const repairOk = ref(false)
const repairPkg = ref('')
// 顶栏滚动态：静止时透明让页面底色直通状态栏，滚动后转毛玻璃避免内容从标题下穿过
const navSolid = ref(false)
// wd-navbar 的 custom-class 进不去组件内层样式域，底色只能走 custom-style
const navStyle = computed(() =>
  'transition: background-color 0.18s ease;' +
  (navSolid.value
    ? 'background-color: var(--glass-nav-bg);-webkit-backdrop-filter: blur(18px) saturate(1.6);backdrop-filter: blur(18px) saturate(1.6);'
    : 'background-color: transparent;')
)
// 首页头图路径（APP 为 saveFile 持久路径，H5 为 dataURL），空表示默认渐变
const heroImg = ref('')
const HERO_IMG_KEY = 'muyian_hero_img_v1'
// 首页头图操作菜单（wot action sheet）
const heroMenuShow = ref(false)
const heroMenuItems = [
  { name: '选择图片' },
  { name: '恢复默认' }
]

/** 空态/成功态用哪张表情：没有对应 mood 的角色给一句兜底 */
const moodHint = computed(() => {
  const parts = []
  const empty = activeMascot.value.styles.find(s => s.mood === 'empty')
  const success = activeMascot.value.styles.find(s => s.mood === 'success')
  if (empty) parts.push('空态用「' + empty.name + '」')
  if (success) parts.push('成功用「' + success.name + '」')
  return parts.length ? parts.join('，') : '按场景自动切换表情'
})

/** 切换主题：页面根节点的主题类立即变化，原生底部弹层用字面色同步一次 */
function applyTheme(id) {
  if (theme.value.id === id) return
  const t = setTheme(id)
  // #ifdef APP-PLUS
  try { setCardThemeColor(t.cardColor || t.primary) } catch (e) {}
  // #endif
  uni.showToast({ title: '已切换为' + t.name, icon: 'none' })
}

/** 切换当前角色的基础表情：全局生效，其他页面 onShow 读取 */
function applyMascotStyle(id) {
  if (activeMascotBaseStyle.value && activeMascotBaseStyle.value.id === id) return
  const n = setMascotStyle(id)
  if (n) uni.showToast({ title: '已切换为「' + n.name + '」', icon: 'none' })
}

/** 跟随场景：开启后空态/成功态自动换 mood 对应的表情 */
function onAutoMoodChange(e) {
  const val = !!(e && e.value)
  setAutoMood(val)
  uni.showToast({ title: val ? '已开启跟随场景' : '已固定当前表情', icon: 'none' })
}

function refresh() {
  try { listenerEnabled.value = isAccessibilityEnabled() } catch (e) { listenerEnabled.value = false }
  try { notifyEnabled.value = isNotificationListenerEnabled() } catch (e) { notifyEnabled.value = false }
  try { notifAllowed.value = areNotificationsEnabled() } catch (e) { notifAllowed.value = true }
  try { overlayPerm.value = hasOverlayPermission() } catch (e) { overlayPerm.value = false }
  try {
    const ps = JSON.parse(getPopupPermissionState() || '{}')
    popupState.value = {
      miui: !!ps.miui,
      overlay: !!ps.overlay,
      miuiPopup: typeof ps.miuiPopup === 'number' ? ps.miuiPopup : -1,
      miuiPopupKnown: !!ps.miuiPopupKnown,
      blocked: !!ps.blocked
    }
    overlayPerm.value = popupState.value.overlay
  } catch (e) {
    popupState.value = { miui: false, overlay: false, miuiPopup: -1, miuiPopupKnown: false, blocked: false }
  }
  try { batteryOk.value = isIgnoringBatteryOptimizations() } catch (e) { batteryOk.value = true }
  try { keepAlive.value = isKeepAliveEnabled() } catch (e) { keepAlive.value = false }
  hideRecents.value = isHideRecentsEnabled()
  try { heroImg.value = uni.getStorageSync(HERO_IMG_KEY) || '' } catch (e) { heroImg.value = '' }
  try {
    const st = JSON.parse(getSelfRepairState() || '{}')
    repairOk.value = !!st.hasPermission
    repairPkg.value = st.packageName || ''
  } catch (e) {
    repairOk.value = false
    repairPkg.value = ''
  }
}

/** 电池优化：已不限制则提示，否则弹系统"不限制"确认框 */
function onBattery() {
  if (batteryOk.value) {
    uni.showToast({ title: '已不受电池优化限制', icon: 'none' })
    return
  }
  uni.showModal({
    title: '关闭电池优化',
    content: '在系统弹窗中选择"不限制"。部分机型还需在"应用详情"里把自启动/后台运行设为允许。',
    confirmText: '去设置',
    success: (r) => {
      if (!r.confirm) return
      let resp = {}
      try {
        resp = JSON.parse(requestIgnoreBatteryOptimizations() || '{}')
      } catch (e) {
        resp = {}
      }
      if (!resp.success) uni.showToast({ title: '无法打开系统设置，请手动设置', icon: 'none' })
    }
  })
}

function onKeepAliveChange(e) {
  const val = !!(e && e.value)
  let ok = false
  try { ok = setKeepAliveEnabled(val) } catch (err) { ok = false }
  keepAlive.value = ok ? val : !val
  if (!ok) uni.showToast({ title: '设置失败，请重试', icon: 'none' })
}

function onHideRecentsChange(e) {
  const val = !!(e && e.value)
  setHideRecentsEnabled(val)
  hideRecents.value = val
  uni.showToast({ title: val ? '已从最近任务隐藏' : '已在最近任务显示', icon: 'none' })
}

/** MIUI/HyperOS：允许"显示在其他应用上层"，否则后台启动确认弹层会被系统直接 Abort */
function onOverlayPermission() {
  // #ifdef APP-PLUS
  try { requestOverlayPermission() } catch (e) {}
  // #endif
  uni.showToast({ title: '请允许"显示在其他应用上层"，返回后自动生效', icon: 'none' })
}

/** MIUI/HyperOS：单独的后台弹出界面权限（AppOp 10021），和上面的悬浮窗开关不是一个 */
function onMiuiPopupPermission() {
  // #ifdef APP-PLUS
  try { openPopupPermissionSettings() } catch (e) {}
  // #endif
  uni.showToast({ title: '点「其他权限」→「后台弹出界面」', icon: 'none' })
}

/** 国产 ROM 的自启动/后台管理入口在应用详情页 */
function openAppDetail() {
  try { openAppSettings() } catch (e) {}
  uni.showToast({ title: '请在应用详情中允许自启动/后台运行', icon: 'none' })
}

/** 自恢复入口：未授权给教程（复制 adb 命令）；已授权且服务被关则立即补回 */
function onSelfRepair() {
  if (!repairOk.value) {
    const pkg = repairPkg.value || '<应用包名>'
    const cmd = 'adb shell pm grant ' + pkg + ' android.permission.WRITE_SECURE_SETTINGS'
    uni.showModal({
      title: '开启无障碍自恢复',
      content: '手机连电脑允许 USB 调试后执行一次，永久生效：' + cmd + '\n\n小米/OPPO/vivo 等机型需先在开发者选项开启「USB 调试（安全设置）」，否则命令会报 GRANT_RUNTIME_PERMISSIONS 权限错误。',
      confirmText: '复制命令',
      cancelText: '取消',
      success: (r) => {
        if (r.confirm) {
          uni.setClipboardData({
            data: cmd,
            success: () => uni.showToast({ title: '已复制，执行后回到本页刷新', icon: 'none' })
          })
        }
      }
    })
    return
  }
  if (!listenerEnabled.value) {
    let resp = {}
    try { resp = JSON.parse(repairAccessibilityService() || '{}') } catch (e) { resp = { success: false, error: 'error' } }
    if (resp.success && resp.serviceEnabled) {
      uni.showToast({ title: '已恢复开启无障碍服务', icon: 'success' })
    } else {
      uni.showToast({ title: '恢复失败：' + (resp.error || '未知原因'), icon: 'none' })
    }
    refresh()
    return
  }
  uni.showToast({ title: '无障碍服务运行正常', icon: 'none' })
}

function onHeroImgTap() {
  heroMenuShow.value = true
}

function onHeroMenuSelect({ index }) {
  if (index === 0) chooseHeroImg()
  else if (index === 1) resetHeroImg()
}

function chooseHeroImg() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    // 只从相册选，不弹"拍照"入口
    sourceType: ['album'],
    success: (res) => {
      const temp = res.tempFilePaths && res.tempFilePaths[0]
      if (temp) persistHeroImg(temp)
    }
  })
}

function persistHeroImg(temp) {
  // #ifdef APP-PLUS
  uni.saveFile({
    tempFilePath: temp,
    success: (s) => {
      removeOldHeroFile()
      applyHeroImg(s.savedFilePath)
    },
    fail: () => applyHeroImg(temp)
  })
  // #endif
  // #ifndef APP-PLUS
  h5PersistHeroImg(temp)
  // #endif
}

/** H5：转 dataURL 存 storage，刷新后仍可显示；过大则仅本次会话生效 */
function h5PersistHeroImg(temp) {
  try {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', temp, true)
    xhr.responseType = 'blob'
    xhr.onload = () => {
      const reader = new FileReader()
      reader.onload = () => {
        const dataUrl = String(reader.result || '')
        if (dataUrl.length > 3500000) {
          uni.showToast({ title: '图片过大，仅本次生效', icon: 'none' })
          heroImg.value = temp
          return
        }
        applyHeroImg(dataUrl)
      }
      reader.onerror = () => applyHeroImg(temp)
      reader.readAsDataURL(xhr.response)
    }
    xhr.onerror = () => applyHeroImg(temp)
    xhr.send()
  } catch (e) {
    applyHeroImg(temp)
  }
}

function applyHeroImg(path) {
  try { uni.setStorageSync(HERO_IMG_KEY, path) } catch (e) {}
  heroImg.value = path
  uni.showToast({ title: '已设置首页头图', icon: 'success' })
}

/** 清理被替换的旧持久化头图文件（仅 APP，savedFilePath 以 _doc 开头） */
function removeOldHeroFile() {
  const old = heroImg.value
  if (old && old.indexOf('_doc') === 0) {
    try { uni.removeSavedFile({ filePath: old }) } catch (e) {}
  }
}

function resetHeroImg() {
  // #ifdef APP-PLUS
  removeOldHeroFile()
  // #endif
  try { uni.setStorageSync(HERO_IMG_KEY, '') } catch (e) {}
  heroImg.value = ''
  uni.showToast({ title: '已恢复默认', icon: 'none' })
}

function goAuth() {
  // #ifdef APP-PLUS
  openAccessibilitySettings()
  // #endif
}

function goNotifyAuth() {
  // #ifdef APP-PLUS
  openNotificationListenerSettings()
  // #endif
}

function requestNotifPermission() {
  // #ifdef APP-PLUS
  if (notifAllowed.value) {
    uni.showToast({ title: '通知权限已开启', icon: 'none' })
    return
  }
  try {
    plus.android.requestPermissions(
      ['android.permission.POST_NOTIFICATIONS'],
      () => {
        refresh()
        uni.showToast({ title: '已处理，如未弹出请在系统设置中允许', icon: 'none' })
      },
      () => {
        refresh()
        uni.showToast({ title: '请在系统设置中手动允许通知', icon: 'none' })
      }
    )
  } catch (err) {
    uni.showToast({ title: '请在系统设置中手动允许通知', icon: 'none' })
  }
  // #endif
}

function goBack() { uni.navigateBack() }

/** 深色主题的页面是黑底，状态栏必须换白色图标，否则时钟/信号在顶部直接看不见 */
function applyStatusBarStyle() {
  // #ifdef APP-PLUS
  try {
    plus.navigator.setStatusBarStyle(theme.value.dark ? 'light' : 'dark')
  } catch (e) {}
  // #endif
}

onPageScroll((e) => {
  const top = Number(e.scrollTop) || 0
  if (top > 8 && !navSolid.value) navSolid.value = true
  else if (top <= 8 && navSolid.value) navSolid.value = false
})

onShow(() => {
  refreshTheme()
  refreshMascots()
  refresh()
  applyStatusBarStyle()
})

onHide(() => {
  // 下一层页面沿用清单默认的深色图标（首页自己会在 onShow 里改回白色）
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
.group-title {
  display: flex;
  flex-direction: row;
  align-items: center;
  font-size: 25rpx;
  color: var(--c-primary-deep);
  font-weight: 600;
  margin: 32rpx 32rpx 12rpx;
}
.group-title::before {
  content: '';
  width: 6rpx;
  height: 22rpx;
  border-radius: 3rpx;
  margin-right: 12rpx;
  background: var(--grad-primary);
}
/* ---- 主题选择 ---- */
.theme-card {
  margin: 0 24rpx 16rpx;
  padding: 26rpx 26rpx 22rpx;
  border-radius: 20rpx;
  display: flex;
  flex-direction: column;
}
.theme-head {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.theme-head-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.theme-head-title {
  font-size: 30rpx;
  color: var(--c-text);
}
.theme-head-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--c-sub);
}
.theme-list {
  display: flex;
  flex-direction: row;
  margin-top: 20rpx;
}
.theme-tile {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12rpx;
  border-radius: 18rpx;
  background-color: var(--glass-bg);
  border: 2rpx solid transparent;
}
.theme-tile + .theme-tile {
  margin-left: 16rpx;
}
.theme-tile-active {
  box-shadow: 0 0 0 3rpx var(--c-primary);
}
.theme-preview {
  position: relative;
  height: 88rpx;
  border-radius: 14rpx;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  padding: 12rpx;
  box-shadow: var(--shadow-primary-sm);
}
/* 缩略图里的白色小卡：用令牌底色，不在页面硬编码 rgba 白 */
.theme-preview-card {
  width: 100%;
  background-color: var(--glass-bg-strong);
  border-radius: 10rpx;
  padding: 9rpx 10rpx;
  display: flex;
  flex-direction: column;
}
.theme-preview-line {
  width: 70%;
  height: 6rpx;
  border-radius: 3rpx;
  background-color: var(--c-dim);
}
.theme-preview-line-sm {
  width: 44%;
  height: 6rpx;
  margin-top: 6rpx;
  border-radius: 3rpx;
  background-color: var(--c-dim);
  opacity: 0.7;
}
.theme-check {
  position: absolute;
  right: 10rpx;
  top: 10rpx;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background-color: var(--c-card);
  box-shadow: var(--shadow-card-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.theme-name {
  margin-top: 12rpx;
  font-size: 27rpx;
  font-weight: 600;
  color: var(--c-text);
}
.theme-tag {
  margin-top: 2rpx;
  font-size: 21rpx;
  color: var(--c-sub);
}
/* ---- 角色表情选择 ---- */
.mood-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 20rpx;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background-color: var(--c-fill);
}
.mood-row-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.mood-row-title {
  font-size: 27rpx;
  font-weight: 600;
  color: var(--c-text);
}
.mood-row-desc {
  margin-top: 4rpx;
  font-size: 21rpx;
  color: var(--c-sub);
  line-height: 1.5;
}
.mascot-preview {
  position: relative;
  height: 170rpx;
  border-radius: 14rpx;
  overflow: hidden;
  background-color: var(--c-primary-weak);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mascot-thumb {
  width: 100%;
  height: 170rpx;
  display: block;
}
.cell-group {
  margin: 0 20rpx;
  border-radius: 20rpx;
  overflow: hidden;
}
/* wd-cell-group 的内层 body 自带不透明白底，会整块盖掉外层 .glass 的半透明底色 */
.cell-group.glass .wd-cell-group__body {
  background: transparent !important;
}
.cell-title-wrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.cell-title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 100%;
}
/* 功能图标块：白描边图标压在同色系渐变上 */
.cell-ic {
  width: 48rpx;
  height: 48rpx;
  border-radius: 15rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14rpx;
  flex-shrink: 0;
  box-shadow: 0 4rpx 10rpx rgba(60, 50, 20, 0.14);
}
.ic-primary { background: var(--grad-primary); }
.ic-blue { background: var(--grad-blue); }
.ic-sky { background: var(--grad-sky); }
.ic-violet { background: var(--grad-violet); }
.ic-mint { background: var(--grad-mint); }
.ic-rose { background: var(--grad-rose); }
.cell-title {
  font-size: 30rpx;
  color: var(--c-text);
  margin-right: 16rpx;
}
.cell-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--c-sub);
  line-height: 1.5;
}
.cell-action {
  margin-left: auto;
  padding-left: 16rpx;
  font-size: 26rpx;
  color: var(--c-primary-deep);
  white-space: nowrap;
  flex-shrink: 0;
}
.hero-thumb {
  width: 96rpx;
  height: 60rpx;
  border-radius: 12rpx;
  display: block;
}
.state {
  font-size: 21rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 9rpx;
  flex-shrink: 0;
}
.state-ok {
  color: var(--c-in);
  background-color: var(--c-in-weak);
}
.state-warn {
  color: var(--c-out-deep);
  background-color: var(--c-out-weak);
}
/* 已完成/已授权：状态贴到行尾，和可操作的未完成状态区分开 */
.state-right {
  margin-left: auto;
}
.cell-group.glass .wd-cell {
  background-color: transparent !important;
}
/* wot cell 的左右列是组件内部元素，页面 scoped 样式要用 :deep 才能穿透；
   左侧占满，右侧按内容收缩（动作文字/箭头/开关），描述就能吃满整行 */
.cell-group :deep(.wd-cell__left) {
  flex: 1;
  min-width: 0;
}
/* 标题容器默认按内容撑宽，margin-left:auto 会停在文字末尾；占满左列才能让状态贴齐行尾 */
.cell-group :deep(.wd-cell__title) {
  flex: 1;
  min-width: 0;
}
.cell-group :deep(.wd-cell__right) {
  flex: 0 0 auto;
  justify-content: flex-end;
}
/* 右侧没有箭头/开关时（已开启状态），左列自带的 15px 右间距会把状态徽标顶离卡片边缘，
   导致左侧 15px 内边距、右侧 30px 的不对称；去掉它让徽标与左侧图标同宽对齐 */
.cell-group :deep(.cell-flush .wd-cell__left) {
  margin-right: 0;
}
.privacy-card {
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  margin: 0 20rpx;
  display: flex;
  flex-direction: column;
}
.privacy-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 12rpx;
}
.privacy-title {
  font-size: 27rpx;
  font-weight: 700;
  color: var(--c-text);
}
.privacy-text {
  font-size: 24rpx;
  color: #868994;
  line-height: 1.9;
}
.bottom-safe {
  height: 60rpx;
}
</style>
