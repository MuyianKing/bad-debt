/**
 * 最近任务可见性
 *
 * 清单里的 android:excludeFromRecents 在部分 ROM（实测 HyperOS）不生效，
 * 所以启动后 / 切换开关时再通过 AppTask.setExcludeFromRecents 显式设置一次。
 * 设置页「后台运行 → 从多任务页面隐藏」读写这个开关，默认开启。
 */

const KEY = 'muyian_hide_recents_v1'

export function isHideRecentsEnabled() {
  try {
    const raw = uni.getStorageSync(KEY)
    // 未设置过默认开启（与产品要求一致）
    return raw === '' || raw === null || raw === undefined ? true : !!raw
  } catch (e) {
    return true
  }
}

export function setHideRecentsEnabled(hidden) {
  try {
    uni.setStorageSync(KEY, !!hidden)
  } catch (e) {
    // ignore
  }
  applyRecentsVisibility()
}

/** 按当前开关值设置本应用所有任务的“排除最近任务”标记 */
export function applyRecentsVisibility() {
  // #ifdef APP-PLUS
  try {
    const main = plus.android.runtimeMainActivity()
    const app = plus.android.invoke(main, 'getApplicationContext')
    const am = plus.android.invoke(app, 'getSystemService', 'activity')
    const tasks = plus.android.invoke(am, 'getAppTasks')
    const size = Number(plus.android.invoke(tasks, 'size')) || 0
    const hidden = isHideRecentsEnabled()
    for (let i = 0; i < size; i++) {
      const task = plus.android.invoke(tasks, 'get', i)
      plus.android.invoke(task, 'setExcludeFromRecents', hidden)
    }
  } catch (e) {
    console.warn('[recents] apply failed', e)
  }
  // #endif
}
