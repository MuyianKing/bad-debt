package muyian.bill.listener

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * 原生核心门面：对页面侧暴露同步 JSON API；采集/解析/去重委托给 Capture* 文件，
 * 数据库委托给 BillDb，权限委托给 SystemPerms，导入/备份仍由本文件承载。
 *
 * 自动记账链路：
 * 无障碍/通知观察 → CaptureEngine（页面分类 → 字段解析 → 门禁 → 账本）→ CapturePresenter（弹层队列）。
 * 解析成功永不直接写库，必须由 BillSheetActivity 的用户保存触发。
 */
class MuyianBillCore private constructor() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val PREFS_NAME = "muyian_bill"

        /* ---------- 系统权限/自愈：实现见 SystemPerms ---------- */

        @JvmStatic
        fun isKeepAliveEnabled(context: Context): Boolean = SystemPerms.isKeepAliveEnabled(context)

        @JvmStatic
        fun setKeepAliveEnabled(context: Context, enabled: Boolean): Boolean =
            SystemPerms.setKeepAliveEnabled(context, enabled)

        @JvmStatic
        fun hasOverlayPermission(context: Context): Boolean = SystemPerms.hasOverlayPermission(context)

        @JvmStatic
        fun requestOverlayPermission(context: Context): Boolean = SystemPerms.requestOverlayPermission(context)

        @JvmStatic
        fun getPopupPermissionState(context: Context): String = SystemPerms.getPopupPermissionState(context)

        @JvmStatic
        fun openPopupPermissionSettings(context: Context): Boolean =
            SystemPerms.openPopupPermissionSettings(context)

        @JvmStatic
        fun clearPopupBlocked(context: Context) = SystemPerms.clearPopupBlocked(context)

        @JvmStatic
        fun setCardThemeColor(context: Context, color: String): Boolean =
            SystemPerms.setCardThemeColor(context, color)

        @JvmStatic
        fun getCardThemeColor(context: Context): Int = SystemPerms.getCardThemeColor(context)

        @JvmStatic
        fun isIgnoringBatteryOptimizations(context: Context): Boolean =
            SystemPerms.isIgnoringBatteryOptimizations(context)

        @JvmStatic
        fun requestIgnoreBatteryOptimizations(context: Context): String =
            SystemPerms.requestIgnoreBatteryOptimizations(context)

        @JvmStatic
        fun openAppSettings(context: Context): Boolean = SystemPerms.openAppSettings(context)

        @JvmStatic
        fun isAccessibilityEnabled(context: Context): Boolean = SystemPerms.isAccessibilityEnabled(context)

        @JvmStatic
        fun isNotificationListenerEnabled(context: Context): Boolean =
            SystemPerms.isNotificationListenerEnabled(context)

        @JvmStatic
        fun areNotificationsEnabled(context: Context): Boolean = SystemPerms.areNotificationsEnabled(context)

        @JvmStatic
        fun getSelfRepairState(context: Context): String = SystemPerms.getSelfRepairState(context)

        @JvmStatic
        fun repairAccessibilityService(context: Context): String = SystemPerms.repairAccessibilityService(context)

        /** App 启动时调用：清理旧版本状态、恢复未处理的待确认队列、拉起保活服务。 */
        @JvmStatic
        fun init(context: Context) {
            try {
                prefs(context).edit()
                    // P5 之前的旧去重/草稿状态，账本接管后不再使用
                    .remove("pending_drafts")
                    .remove("pending_open_token")
                    .remove("overlay_enabled")
                    .remove("capture_dedup")
                    .remove("order_dedup")
                    .remove("host_page_dedup")
                    .remove("capture_flows")
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "clean legacy prefs failed", e)
            }
            PendingNotices.ensureChannel(context)
            try {
                Log.i(TAG, "popup permission state " + getPopupPermissionState(context))
            } catch (e: Exception) {
                Log.w(TAG, "probe popup permission state failed", e)
            }
            if (isKeepAliveEnabled(context)) {
                MuyianKeepAliveService.start(context)
            }
            try {
                CaptureEngine.restore(context)
            } catch (e: Exception) {
                Log.w(TAG, "restore capture queue failed", e)
            }
        }

        /** 候选 App 判定：内置注册表 + 用户覆盖 + 应用名关键词（带缓存）。 */
        @JvmStatic
        fun isCandidateApp(context: Context, pkg: String): Boolean =
            CaptureRegistry.isCandidate(context, pkg)

        /** 采集层签名（保留给旧调用方/测试）：同 App 同页面文本只解析一次。 */
        @JvmStatic
        fun screenSignature(pkg: String, lines: List<String>): String {
            return pkg + "#" + CaptureText.hash(lines.joinToString("|"))
        }

        /** 窗口切换：维护支付路线状态机（宿主 → 支付 → 宿主）。 */
        @JvmStatic
        fun noteForeground(context: Context, pkg: String) {
            try {
                CaptureRoute.noteForeground(context, pkg)
            } catch (e: Exception) {
                Log.w(TAG, "note foreground failed", e)
            }
        }

        // ---------- 采集入口 ----------

        /**
         * 无障碍页面 → 引擎。snapshot 结构见 CaptureEngine.screenJson。
         * @return {"accepted":bool,"reason":"...","bill":{...}?}
         */
        @JvmStatic
        fun handleScreenSnapshot(
            context: Context, pkg: String, snapshotJson: String, force: Boolean
        ): String = CaptureEngine.screenJson(context, pkg, snapshotJson, force)

        /**
         * 通知 → 引擎。snapshot 结构见 BillNotificationListener。
         */
        @JvmStatic
        fun handleNotificationSnapshot(context: Context, snapshotJson: String, force: Boolean): String =
            CaptureEngine.notificationJson(context, snapshotJson, force)

        /** 旧签名（纯文本）保留给测试注入；没有字号/位置，金额只走锚点与货币单位。 */
        @JvmStatic
        fun handleScreen(context: Context, pkg: String, lines: List<String>, force: Boolean): String {
            val items = ArrayList<UiText>()
            for (i in lines.indices) {
                items.add(UiText(lines[i], top = i * 60, bottom = i * 60 + 48, sizePx = 30))
            }
            return CaptureEngine.handleScreen(
                context, pkg, items, "", 0, System.currentTimeMillis(), force
            )
        }

        /** 旧签名（通知字段拆分）保留给测试注入。 */
        @JvmStatic
        fun handleNotification(
            context: Context, pkg: String, title: String, text: String,
            subText: String, bigText: String, lines: List<String>, postTime: Long, force: Boolean
        ): String {
            val obs = NotificationObservation(
                pkg = pkg,
                title = title,
                text = text,
                subText = subText,
                bigText = bigText,
                lines = lines,
                postTime = postTime
            )
            return CaptureEngine.handleNotification(context, obs, force)
        }

        // ---------- 弹层队列 ----------

        /** 弹层已经开着时，把通知点击带来的新账单放回队列。 */
        @JvmStatic
        fun enqueueSheetBill(context: Context, billJson: String): Boolean =
            CapturePresenter.enqueueFromNotice(context.applicationContext, billJson)

        /** 弹层关闭后继续处理队列（由 BillSheetActivity.onDestroy 调用）。 */
        @JvmStatic
        fun onSheetClosed(context: Context) =
            CapturePresenter.onSheetClosed(context.applicationContext)

        /**
         * 弹层保存：用户确认后才写 bills；同时更新账本状态并记录分类学习。
         */
        @JvmStatic
        fun saveSheetBill(
            context: Context, billJson: String, moneyText: String, isIncome: Boolean,
            costType: String, remark: String
        ): Boolean {
            val bill = try {
                JSONObject(billJson)
            } catch (e: Exception) {
                return false
            }
            val amount = try {
                moneyText.trim().toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
            if (amount <= 0) return false
            val suggested = bill.optString("costType")
            val finalType = when {
                isIncome && (suggested == "退款" || suggested == "转账") -> suggested
                isIncome -> "收入"
                costType.isEmpty() -> "消费"
                else -> costType
            }
            val cv = ContentValues()
            cv.put("year", bill.optInt("year"))
            cv.put("month", bill.optInt("month"))
            cv.put("day", bill.optInt("day"))
            cv.put("clock", bill.optString("clock"))
            cv.put("money", if (isIncome) amount else -amount)
            cv.put("bankName", bill.optString("bankName"))
            cv.put("costType", finalType)
            cv.put("orderRemark", remark)
            cv.put("source", bill.optString("capture", "notify"))
            cv.put("sourcePkg", bill.optString("sourcePkg"))
            cv.put("createdAt", bill.optLong("createdAt", System.currentTimeMillis()))
            cv.put("orderNo", bill.optString("orderNo"))
            val db = BillDb.openDb(context)
            val ok = try {
                db.insert("bills", null, cv) > 0
            } catch (e: Exception) {
                Log.e(TAG, "insert bill from sheet failed", e)
                false
            } finally {
                db.close()
            }
            if (ok) {
                CapturePresenter.onSaved(context.applicationContext, bill, finalType, amount)
            }
            return ok
        }

        /** 弹层取消/超时/熄屏：丢弃这一笔并更新账本。 */
        @JvmStatic
        fun discardSheetBill(context: Context, billJson: String) {
            try {
                CapturePresenter.onDiscarded(context.applicationContext, JSONObject(billJson))
            } catch (e: Exception) {
                Log.w(TAG, "discard sheet bill failed", e)
            }
        }

        @JvmStatic
        fun cancelPendingNotice(context: Context, noticeId: Int) = PendingNotices.cancel(context, noticeId)

        // ---------- 诊断与 App 配置 ----------

        @JvmStatic
        fun getCaptureDiagnostics(context: Context, limit: Int): String =
            CaptureEngine.diagnosticsJson(context, limit)

        @JvmStatic
        fun getCaptureApps(context: Context): String = CaptureEngine.appsJson(context, false)

        @JvmStatic
        fun getCaptureApps(context: Context, includeAll: Boolean): String =
            CaptureEngine.appsJson(context, includeAll)

        @JvmStatic
        fun setCaptureApp(context: Context, pkg: String, role: String, enabled: Boolean): Boolean =
            CaptureEngine.setApp(context, pkg, role, enabled)

        @JvmStatic
        fun getCaptureSettings(context: Context): String = CaptureEngine.settingsJson(context)

        @JvmStatic
        fun reparseCapture(context: Context, pkg: String, source: String, text: String): String =
            CaptureEngine.reparse(context, pkg, source, text)

        @JvmStatic
        fun setCaptureSettings(context: Context, json: String): Boolean =
            CaptureEngine.setSettings(context, json)

        @JvmStatic
        fun clearCaptureDiagnostics(context: Context) = CaptureEngine.clearDiagnostics(context)

        @JvmStatic
        fun restoreCaptureQueue(context: Context) = CaptureEngine.restore(context)

        // ---------- 备份导出 ----------

        private const val BACKUP_MAX_BYTES = 50L * 1024 * 1024
        private val backupSaveLock = Any()
        private var backupSaveState = "idle" // idle | saving | saved | cancelled | error
        private var backupSaveError = ""
        private var backupSaveName = ""
        private var backupExportContent = ""

        /**
         * 导出：页面拼好的备份 JSON 先暂存在进程内，再拉起系统"另存为"Activity 写入用户选的位置。
         * 内容不走 Intent extra，避免大备份超过 Binder 大小限制。
         */
        fun openBackupExport(context: Context, content: String, name: String): String {
            var busy = false
            synchronized(backupSaveLock) {
                if (backupSaveState == "saving" && BackupSaveActivity.isAlive()) {
                    busy = true
                } else {
                    backupExportContent = content
                    backupSaveState = "saving"
                    backupSaveError = ""
                    backupSaveName = name
                }
            }
            if (busy) {
                val busyObj = JSONObject()
                busyObj.put("success", false).put("error", "busy")
                return busyObj.toString()
            }
            return try {
                val intent = Intent(context, BackupSaveActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(BackupSaveActivity.EXTRA_NAME, name)
                context.startActivity(intent)
                val ok = JSONObject()
                ok.put("success", true)
                ok.toString()
            } catch (e: Exception) {
                Log.w(TAG, "open backup export failed", e)
                val reason = "start failed: " + e.javaClass.name
                synchronized(backupSaveLock) {
                    backupSaveState = "error"
                    backupSaveError = reason
                    backupExportContent = ""
                }
                val fail = JSONObject()
                fail.put("success", false).put("error", reason)
                fail.toString()
            }
        }

        /** 页面侧轮询导出状态 {"state","name","error"} */
        fun getBackupSaveState(context: Context): String {
            val o = JSONObject()
            o.put("state", backupSaveState)
            o.put("name", backupSaveName)
            o.put("error", backupSaveError)
            return o.toString()
        }

        /** BackupSaveActivity 读取待写内容 */
        fun backupContent(): String = backupExportContent

        fun onBackupSaved(name: String) {
            synchronized(backupSaveLock) {
                backupSaveState = "saved"
                if (name.isNotEmpty()) backupSaveName = name
                backupExportContent = ""
            }
        }

        fun onBackupSaveCancelled() {
            synchronized(backupSaveLock) {
                backupSaveState = "cancelled"
                backupExportContent = ""
            }
        }

        fun onBackupSaveError(message: String) {
            synchronized(backupSaveLock) {
                backupSaveState = "error"
                backupSaveError = message
                backupExportContent = ""
            }
        }

        /** 读取备份文件文本（UTF-8），超过 50MB 拒绝，返回 {"ok","text","error"} */
        fun readBackupText(context: Context, path: String): String {
            val result = JSONObject()
            try {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) {
                    result.put("ok", false).put("error", "文件不存在或为空")
                    return result.toString()
                }
                if (file.length() > BACKUP_MAX_BYTES) {
                    result.put("ok", false).put("error", "备份文件过大（超过 50MB）")
                    return result.toString()
                }
                val ins = FileInputStream(file)
                val bos = ByteArrayOutputStream()
                try {
                    val buf = ByteArray(64 * 1024)
                    var n = ins.read(buf)
                    while (n >= 0) {
                        bos.write(buf, 0, n)
                        n = ins.read(buf)
                    }
                } finally {
                    ins.close()
                }
                result.put("ok", true)
                result.put("text", String(bos.toByteArray(), Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "read backup text failed", e)
                result.put("ok", false).put("error", "读取文件失败")
            }
            return result.toString()
        }

        // ---------- 账单写入/查询 ----------

        @JvmStatic
        fun saveBillEx(
            context: Context, date: String, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String,
            reimb: Boolean, starred: Boolean
        ): String = BillDb.saveBillEx(
            context, date, money, isIncome, bankName, costType, orderRemark, reimb, starred
        )

        @JvmStatic
        fun updateBill(
            context: Context, id: String, date: String, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String,
            reimb: Boolean, starred: Boolean
        ): String = BillDb.updateBill(
            context, id, date, money, isIncome, bankName, costType, orderRemark, reimb, starred
        )

        @JvmStatic
        fun getYearBills(context: Context, year: String): String = BillDb.getYearBills(context, year)

        @JvmStatic
        fun getMonthBills(context: Context, year: String, month: String): String =
            BillDb.getMonthBills(context, year, month)

        @JvmStatic
        fun deleteBill(context: Context, id: String): Boolean = BillDb.deleteBill(context, id)

        @JvmStatic
        fun countExistingOrders(context: Context, orderNosJson: String): String =
            BillDb.countExistingOrders(context, orderNosJson)

        @JvmStatic
        fun commitImportBills(context: Context, billsJson: String): String =
            BillDb.commitImportBills(context, billsJson)

        @JvmStatic
        fun commitBackupBills(context: Context, billsJson: String): String =
            BillDb.commitBackupBills(context, billsJson)

        @JvmStatic
        fun getAllBills(context: Context, offset: String, limit: String): String =
            BillDb.getAllBills(context, offset, limit)

        @JvmStatic
        fun saveManualBill(
            context: Context, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String
        ): String {
            return saveBillEx(
                context, "", money, isIncome, bankName, costType, orderRemark, false, false
            )
        }

        // ---------- 钱迹 Excel 导入 ----------
        // 选文件由 ImportPickActivity 中转（SAF，无需存储权限）：选中的文件拷到应用私有目录
        // qianji_import.xlsx，路径与状态放在静态字段，页面侧用同步 API 轮询读取。
        // 原生只做「xlsx 解压成表头 + 原始单元格行」和「批量写库」；时间/类型/金额校验、
        // 统计、分类计数与映射、入库字段拼装在 com.muyian.debt.data 的 Qianji.kt。

        private const val IMPORT_CACHE_NAME = "qianji_import.xlsx"
        private const val IMPORT_MAX_ROWS = 20000
        private const val IMPORT_MAX_COLUMNS = 64
        private val importLock = Any()
        private var importPickState = "idle" // idle | picking | picked | cancelled | error
        private var importPickError = ""
        private var importPickPath = ""
        private var importPickName = ""
        private val importSheetRows = ArrayList<ArrayList<String>>()

        @JvmStatic
        fun openImportPicker(context: Context): String = startFilePicker(context, "xlsx")

        @JvmStatic
        fun openBackupPicker(context: Context): String = startFilePicker(context, "json")

        private fun startFilePicker(context: Context, kind: String): String {
            var busy = false
            synchronized(importLock) {
                if (importPickState == "picking" && ImportPickActivity.isAlive()) {
                    busy = true
                } else {
                    importPickState = "picking"
                    importPickError = ""
                    importPickPath = ""
                    importPickName = ""
                }
            }
            if (busy) {
                val busyObj = JSONObject()
                busyObj.put("success", false).put("error", "picking")
                return busyObj.toString()
            }
            return try {
                val intent = Intent(context, ImportPickActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(ImportPickActivity.EXTRA_KIND, kind)
                context.startActivity(intent)
                val ok = JSONObject()
                ok.put("success", true)
                ok.toString()
            } catch (e: Exception) {
                Log.w(TAG, "open file picker failed", e)
                val reason = "start failed: " + e.javaClass.name
                synchronized(importLock) {
                    importPickState = "error"
                    importPickError = reason
                }
                val fail = JSONObject()
                fail.put("success", false).put("error", reason)
                fail.toString()
            }
        }

        /** 页面侧轮询：读取选文件状态 {"state","path","name","error"} */
        @JvmStatic
        fun getImportPickState(context: Context): String {
            val o = JSONObject()
            o.put("state", importPickState)
            o.put("path", importPickPath)
            o.put("name", importPickName)
            o.put("error", importPickError)
            return o.toString()
        }

        @JvmStatic
        fun onImportPicked(path: String, name: String) {
            synchronized(importLock) {
                importPickState = "picked"
                importPickPath = path
                importPickName = name
            }
        }

        @JvmStatic
        fun onImportPickCancelled() {
            synchronized(importLock) {
                importPickState = "cancelled"
            }
        }

        @JvmStatic
        fun onImportPickError(message: String) {
            synchronized(importLock) {
                importPickState = "error"
                importPickError = message
            }
        }

        /**
         * 读取钱迹导出的 xlsx，只解压成「表头 + 原始单元格行」，不做账单语义解析。
         */
        @JvmStatic
        fun readImportSheet(path: String): String {
            val result = JSONObject()
            try {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) {
                    result.put("ok", false).put("error", "文件不存在或为空")
                    return result.toString()
                }
                val sheet = XlsxReader.read(file)
                if (sheet.isEmpty()) {
                    result.put("ok", false).put("error", "表格里没有内容")
                    return result.toString()
                }
                var width = 0
                for (row in sheet) {
                    for (key in row.keys) {
                        if (key + 1 > width) width = key + 1
                    }
                }
                if (width > IMPORT_MAX_COLUMNS) width = IMPORT_MAX_COLUMNS
                val headerRow = sheet.get(0)
                val header = ArrayList<String>()
                var c = 0
                while (c < width) {
                    header.add(XlsxReader.cell(headerRow, c))
                    c = c + 1
                }
                val data = ArrayList<ArrayList<String>>()
                var capped = false
                var i = 1
                while (i < sheet.size) {
                    val row = sheet.get(i)
                    i = i + 1
                    if (row.isEmpty()) continue
                    val values = ArrayList<String>()
                    c = 0
                    while (c < width) {
                        values.add(XlsxReader.cell(row, c))
                        c = c + 1
                    }
                    data.add(values)
                    if (data.size >= IMPORT_MAX_ROWS) {
                        capped = i < sheet.size
                        if (capped) Log.w(TAG, "import sheet capped at " + IMPORT_MAX_ROWS)
                        break
                    }
                }
                synchronized(importLock) {
                    importSheetRows.clear()
                    importSheetRows.addAll(data)
                }
                val headerJson = JSONArray()
                for (value in header) headerJson.put(value)
                result.put("ok", true)
                result.put("header", headerJson)
                result.put("width", width)
                result.put("total", data.size)
                if (capped) result.put("capped", true)
                return result.toString()
            } catch (e: Exception) {
                Log.e(TAG, "read import sheet failed", e)
                result.put("ok", false).put("error", "解析失败，请确认是钱迹导出的 Excel 文件")
                return result.toString()
            }
        }

        /** 分块读取原始数据行：{"total":n,"rows":[[…],…]}。 */
        @JvmStatic
        fun getImportSheetRows(offset: String, limit: String): String {
            val start = offset.trim().toIntOrNull() ?: 0
            val count = limit.trim().toIntOrNull() ?: 200
            val out = JSONObject()
            val arr = JSONArray()
            synchronized(importLock) {
                var i = start
                if (i < 0) i = 0
                val size = importSheetRows.size
                var end = i + count
                if (end > size) end = size
                while (i < end) {
                    val row = importSheetRows.get(i)
                    val rowJson = JSONArray()
                    for (value in row) rowJson.put(value)
                    arr.put(rowJson)
                    i = i + 1
                }
                out.put("total", size)
            }
            out.put("rows", arr)
            return out.toString()
        }

        /** 导入文件缓存名：ImportPickActivity 写到这里 */
        fun importCacheName(): String = IMPORT_CACHE_NAME

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
