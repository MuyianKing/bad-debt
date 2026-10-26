<template>
  <view class="page">
    <wd-navbar custom-class="glass-nav" :left-arrow="true" title="设置" :fixed="true" :placeholder="true" :safe-area-inset-top="true" @click-left="goBack"></wd-navbar>

    <view class="group-title">自动记账</view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell :is-link="true" :clickable="true" @click="goAuth">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <text class="cell-title">无障碍服务</text>
              <text :class="['state', listenerEnabled ? 'state-ok' : 'state-warn']">{{ listenerEnabled ? '已开启' : '未开启' }}</text>
            </view>
            <text class="cell-desc">自动记账的核心开关。开启后，你在支付宝/微信等App里完成支付时，孽账会读取支付结果页的文字，在本地解析出待确认账单。</text>
          </view>
        </template>
        <text class="cell-action">{{ listenerEnabled ? '前往设置' : '去开启' }}</text>
      </wd-cell>
      <wd-cell :is-link="true" :clickable="true" @click="goNotifyAuth">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <text class="cell-title">通知使用权（辅助）</text>
              <text :class="['state', notifyEnabled ? 'state-ok' : 'state-warn']">{{ notifyEnabled ? '已开启' : '未开启' }}</text>
            </view>
            <text class="cell-desc">补充无障碍覆盖不到的场景：收款到账提醒只在通知栏、熄屏碰卡支付的交易通知。两路来源自动去重，不会重复入账。</text>
          </view>
        </template>
        <text class="cell-action">{{ notifyEnabled ? '前往授权' : '去开启' }}</text>
      </wd-cell>
      <wd-cell :clickable="true" @click="onSelfRepair">
        <template #title>
          <view class="cell-title-wrap">
            <view class="cell-title-row">
              <text class="cell-title">无障碍自恢复</text>
              <text :class="['state', repairOk ? 'state-ok' : 'state-warn']">{{ repairOk ? '已授权' : '未授权' }}</text>
            </view>
            <text class="cell-desc">系统可能杀掉无障碍服务导致自动记账失效。用电脑 adb 执行一次授权命令后，打开孽账会自动把服务重新开启，无需再去系统设置手动开。</text>
          </view>
        </template>
        <text class="cell-action">{{ repairOk ? (listenerEnabled ? '已就绪' : '立即恢复') : '授权教程' }}</text>
      </wd-cell>
      <wd-cell :clickable="true" @click="requestNotifPermission">
        <template #title>
          <view class="cell-title-wrap">
            <text class="cell-title">App 弹通知权限</text>
            <text class="cell-desc">Android 13+ 需要，用于弹出"待确认账单"通知</text>
          </view>
        </template>
        <text class="cell-action">请求权限</text>
      </wd-cell>
    </wd-cell-group>

    <view class="group-title">测试账单解析</view>
    <view class="test-card glass">
      <text class="test-desc">把下面的文本当作"支付成功"页面注入解析流水线，无需真实支付即可验证效果。文本需包含完成标记（如"支付成功"）和金额。</text>
      <textarea class="test-input" v-model="testText" placeholder="例如：支付成功&#10;¥ 25.80&#10;收款方 瑞幸咖啡" :maxlength="-1" auto-height />
      <wd-button block custom-class="test-btn" @click="emitTest">生成测试草稿</wd-button>
    </view>

    <view class="group-title">数据</view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell title="分类管理" :is-link="true" @click="goCategories"></wd-cell>
      <wd-cell title="统计报表" :is-link="true" @click="goStats"></wd-cell>
    </wd-cell-group>

    <view class="group-title">外观</view>
    <wd-cell-group border custom-class="cell-group glass">
      <wd-cell :is-link="true" :clickable="true" @click="onHeroImgTap">
        <template #title>
          <view class="cell-title-wrap">
            <text class="cell-title">首页头图</text>
            <text class="cell-desc">自定义首页顶部背景图，仅保存在本机</text>
          </view>
        </template>
        <image v-if="heroImg" class="hero-thumb" :src="heroImg" mode="aspectFill"></image>
        <text v-else class="cell-action">设置图片</text>
      </wd-cell>
    </wd-cell-group>

    <view class="group-title">隐私说明</view>
    <view class="privacy-card glass">
      <text class="privacy-text">· 只读取候选支付App的窗口文字，且只在手机本地解析，不上传任何服务器</text>
      <text class="privacy-text">· 账单数据保存在本机 SQLite，可随时删除</text>
      <text class="privacy-text">· 白名单之外的App内容不会被读取或存储</text>
    </view>

    <view class="bottom-safe"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
// #ifdef APP-PLUS
import {
  isAccessibilityEnabled, openAccessibilitySettings,
  isNotificationListenerEnabled, openNotificationListenerSettings,
  getSelfRepairState, repairAccessibilityService,
  emitTestBill
} from '@/uni_modules/muyian-bill-listener'
// #endif
// #ifndef APP-PLUS
import {
  isAccessibilityEnabled, openAccessibilitySettings,
  isNotificationListenerEnabled, openNotificationListenerSettings,
  getSelfRepairState, repairAccessibilityService,
  emitTestBill
} from '@/common/web-bill-api.js'
// #endif

const listenerEnabled = ref(false)
const notifyEnabled = ref(false)
// 自恢复：WRITE_SECURE_SETTINGS 是否已授权、目标应用包名（拼 adb 命令展示用）
const repairOk = ref(false)
const repairPkg = ref('')
const testText = ref('支付成功\n¥ 25.80\n付款方式 余额\n收款方 瑞幸咖啡')
// 首页头图路径（APP 为 saveFile 持久路径，H5 为 dataURL），空表示默认渐变
const heroImg = ref('')
const HERO_IMG_KEY = 'muyian_hero_img_v1'

const REJECT_REASONS = {
  'self package': '内部异常',
  'not candidate app': '非候选支付App',
  'not a payment page': '缺少"支付成功"等完成标记或金额',
  'duplicate capture': '5分钟内已生成过相同草稿',
  'duplicate bill': '2分钟内已有相同账单'
}

function refresh() {
  try { listenerEnabled.value = isAccessibilityEnabled() } catch (e) { listenerEnabled.value = false }
  try { notifyEnabled.value = isNotificationListenerEnabled() } catch (e) { notifyEnabled.value = false }
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
  uni.showActionSheet({
    itemList: ['选择图片', '恢复默认'],
    success: (res) => {
      if (res.tapIndex === 0) chooseHeroImg()
      else if (res.tapIndex === 1) resetHeroImg()
    }
  })
}

function chooseHeroImg() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
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
  try {
    plus.android.requestPermissions(
      ['android.permission.POST_NOTIFICATIONS'],
      () => {
        uni.showToast({ title: '已处理，如未弹出请在系统设置中允许', icon: 'none' })
      },
      () => {
        uni.showToast({ title: '请在系统设置中手动允许通知', icon: 'none' })
      }
    )
  } catch (err) {
    uni.showToast({ title: '请在系统设置中手动允许通知', icon: 'none' })
  }
  // #endif
}

function emitTest() {
  const text = (testText.value || '').trim()
  if (!text) {
    uni.showToast({ title: '请先输入测试文本', icon: 'none' })
    return
  }
  let resp = {}
  try {
    resp = JSON.parse(emitTestBill(text) || '{}')
  } catch (e) {
    resp = { accepted: false, reason: 'error' }
  }
  if (resp.accepted) {
    uni.showToast({ title: '已生成草稿，去首页确认', icon: 'success' })
  } else {
    uni.showToast({ title: '未识别：' + (REJECT_REASONS[resp.reason] || resp.reason || '未知原因'), icon: 'none' })
  }
}

function goCategories() { uni.navigateTo({ url: '/pages/categories/categories' }) }
function goStats() { uni.navigateTo({ url: '/pages/stats/stats' }) }
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
.group-title {
  font-size: 26rpx;
  color: #9aa0ab;
  margin: 32rpx 32rpx 12rpx;
}
.cell-group {
  border-radius: 20rpx;
  overflow: hidden;
}
.cell-title-wrap {
  display: flex;
  flex-direction: column;
  padding-right: 12rpx;
}
.cell-title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.cell-title {
  font-size: 30rpx;
  color: #333;
  margin-right: 16rpx;
}
.cell-desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #b0b3ba;
  line-height: 1.5;
}
.cell-action {
  font-size: 26rpx;
  color: var(--c-primary-deep);
  white-space: nowrap;
}
.hero-thumb {
  width: 96rpx;
  height: 60rpx;
  border-radius: 12rpx;
  display: block;
}
.state {
  font-size: 24rpx;
}
.state-ok { color: #3eb575; }
.state-warn { color: #e8605c; }
.cell-group.glass .wd-cell {
  background-color: transparent !important;
}
/* wot cell 原生左右各半，长描述会被压窄：让标题区占 2.2 份 */
.cell-group .wd-cell__left {
  flex: 2.2;
}
.cell-group .wd-cell__right {
  flex: 1;
  justify-content: flex-end;
}
.test-card {
  background-color: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin: 0 24rpx;
  display: flex;
  flex-direction: column;
}
.test-desc {
  font-size: 24rpx;
  color: #868994;
  line-height: 1.6;
  margin-bottom: 16rpx;
}
.test-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 120rpx;
  background-color: var(--glass-bg);
  border-radius: 14rpx;
  padding: 20rpx;
  font-size: 26rpx;
  margin-bottom: 20rpx;
}
.test-btn {
  background-color: var(--c-primary) !important;
  border-color: var(--c-primary) !important;
  border-radius: 40rpx;
}
.test-btn .wd-button__text {
  color: #ffffff !important;
}
.privacy-card {
  background-color: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  margin: 0 24rpx;
  display: flex;
  flex-direction: column;
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
