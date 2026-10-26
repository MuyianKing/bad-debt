<template>
  <view class="redirect">
    <text class="redirect-text">正在打开记账…</text>
  </view>
</template>

<script>
// 旧的草稿确认页已并入"记一笔"页：本页只做兼容跳转（通知点击入口保留）
export default {
  onLoad(query) {
    const q = query || {}
    let url = '/pages/add/add'
    if (q.token) url += '?token=' + encodeURIComponent(q.token)
    uni.redirectTo({ url })
  }
}
</script>

<style>
.redirect {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.redirect-text {
  color: #b0b3ba;
  font-size: 28rpx;
}
</style>
