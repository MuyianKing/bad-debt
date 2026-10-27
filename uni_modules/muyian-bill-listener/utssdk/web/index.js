/**
 * muyian-bill-listener 的 Web/H5 桩实现
 *
 * UTS 原生插件只能在 App 端运行。提供本文件后，H5 编译的依赖扫描
 * （vite:dep-scan，无法识别 #ifdef 条件编译）可以正常解析
 * `@/uni_modules/muyian-bill-listener` 导入；页面侧的调用已被
 * `#ifdef APP-PLUS` 剥离，运行期不会走到这里，接口仅作兜底。
 * 返回值协议与 interface.uts 保持一致（JSON 字符串进出）。
 */

export function initBillListener() {}

export function isAccessibilityEnabled() {
  return false
}

export function openAccessibilitySettings() {}

export function getSelfRepairState() {
  return '{"hasPermission":false,"serviceEnabled":false,"packageName":""}'
}

export function repairAccessibilityService() {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function isNotificationListenerEnabled() {
  return false
}

export function openNotificationListenerSettings() {}

export function areNotificationsEnabled() {
  return true
}

export function isKeepAliveEnabled() {
  return false
}

export function setKeepAliveEnabled(enabled) {
  return Boolean(enabled)
}

export function hasOverlayPermission() {
  return false
}

export function requestOverlayPermission() {
  return false
}

export function getPopupPermissionState() {
  return '{"miui":false,"overlay":false,"miuiPopup":-1,"miuiPopupKnown":false,"blocked":false}'
}

export function openPopupPermissionSettings() {
  return false
}

export function setCardThemeColor(color) {
  return true
}

export function isIgnoringBatteryOptimizations() {
  return true
}

export function requestIgnoreBatteryOptimizations() {
  return '{"success":false,"granted":false,"error":"h5 unsupported"}'
}

export function openAppSettings() {}

export function saveManualBill(money, isIncome, bankName, costType, orderRemark) {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function saveBillEx(date, money, isIncome, bankName, costType, orderRemark, reimb, starred) {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function updateBill(id, date, money, isIncome, bankName, costType, orderRemark, reimb, starred) {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function getMonthBills(year, month) {
  return '[]'
}

export function getYearBills(year) {
  return '[]'
}

export function deleteBill(id) {
  return false
}

export function emitTestBill(text) {
  return '{"accepted":false,"reason":"h5 unsupported"}'
}

/* ---------- 钱迹 Excel 导入（依赖原生 SAF 文件选择与本地解析，H5 不支持） ---------- */

export function openImportPicker() {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function getImportPickState() {
  return '{"state":"idle","path":"","name":"","error":""}'
}

export function readImportSheet(path) {
  return '{"ok":false,"error":"h5 unsupported"}'
}

export function getImportSheetRows(offset, limit) {
  return '{"total":0,"rows":[]}'
}

export function countExistingOrders(orderNosJson) {
  return '{"count":0}'
}

export function commitImportBills(billsJson) {
  return '{"success":false,"error":"h5 unsupported"}'
}

/* ---------- 本应用备份（依赖原生文件读写，H5 不支持） ---------- */

export function openBackupExport(content, name) {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function getBackupSaveState() {
  return '{"state":"idle","name":"","error":""}'
}

export function openBackupPicker() {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function readBackupText(path) {
  return '{"ok":false,"error":"h5 unsupported"}'
}

export function commitBackupBills(billsJson) {
  return '{"success":false,"error":"h5 unsupported"}'
}

export function getAllBills(offset, limit) {
  return '{"total":0,"rows":[]}'
}
