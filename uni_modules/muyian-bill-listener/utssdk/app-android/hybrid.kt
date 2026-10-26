package muyian.bill.listener

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * 系统通知监听服务（辅助渠道）：收到候选App的通知后交给 MuyianBillCore 处理。
 * 覆盖无障碍方案看不到的场景：收款到账提醒只出现在通知栏、熄屏碰卡支付的交易通知等。
 * 在本插件的 AndroidManifest.xml 中注册，用户授予"通知使用权"后被系统 bind。
 */
class BillNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val pkg = sbn.packageName ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val lines = ArrayList<String>()
        val textLines = extras.getCharSequenceArray("android.textLines")
        if (textLines != null) {
            for (line in textLines) {
                if (line != null) lines.add(line.toString())
            }
        }
        try {
            MuyianBillCore.handleNotification(
                applicationContext, pkg, title, text, subText, bigText, lines, sbn.postTime, false
            )
        } catch (e: Exception) {
            Log.e("MuyianBill", "handle notification failed", e)
        }
    }
}

/**
 * 无障碍服务：用户在系统"无障碍"中开启后由系统 bind。
 * 监听候选支付 App 的窗口切换与内容变化，采集当前窗口全部文本
 * 交给 MuyianBillCore.handleScreen 解析（识别"支付成功"结果页）。
 * 在本插件的 AndroidManifest.xml 中注册，配置见 res/xml/muyian_accessibility_config.xml。
 */
class MuyianBillAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val THROTTLE_MS = 600L
        private const val MAX_NODES = 400
        private const val MAX_DEPTH = 30
        private val executor = Executors.newSingleThreadExecutor()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lastEventAt = HashMap<String, Long>()
    private val lastSignature = HashMap<String, String>()
    private val pendingPages = HashSet<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // 白名单三层匹配（应用名关键词查询带缓存），非候选 App 直接放行
        if (!MuyianBillCore.isCandidateApp(applicationContext, pkg)) return

        val now = System.currentTimeMillis()
        val last = lastEventAt[pkg] ?: 0L
        if (now - last >= THROTTLE_MS) {
            lastEventAt[pkg] = now
            processWindow(pkg)
        } else if (pendingPages.add(pkg)) {
            // 节流窗口内的事件不能直接丢：页面可能恰好在窗口内渲染完成，
            // 补一次延迟采集，保证每个事件突发都以一次最终解析收尾
            handler.postDelayed(Runnable {
                pendingPages.remove(pkg)
                lastEventAt[pkg] = System.currentTimeMillis()
                processWindow(pkg)
            }, THROTTLE_MS)
        }
    }

    private fun processWindow(pkg: String) {
        val root = rootInActiveWindow ?: return
        val lines = ArrayList<String>()
        collectTexts(root, lines, 0, intArrayOf(MAX_NODES))
        if (Build.VERSION.SDK_INT < 33) {
            try { root.recycle() } catch (e: Exception) { }
        }
        if (lines.isEmpty()) return
        val signature = MuyianBillCore.screenSignature(pkg, lines)
        if (signature == lastSignature[pkg]) return
        lastSignature[pkg] = signature
        val snapshot = ArrayList(lines)
        executor.execute(Runnable {
            try {
                MuyianBillCore.handleScreen(applicationContext, pkg, snapshot, false)
            } catch (e: Exception) {
                Log.e(TAG, "handle screen failed", e)
            }
        })
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: ArrayList<String>, depth: Int, budget: IntArray) {
        if (node == null || depth > MAX_DEPTH || budget[0] <= 0) return
        if (node.isVisibleToUser) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) out.add(text.trim())
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrBlank()) out.add(desc.trim())
            budget[0]--
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1, budget)
            if (budget[0] <= 0) return
        }
    }

    override fun onInterrupt() {
    }
}

/**
 * 自动记账核心引擎：
 * 候选App过滤 -> 页面黑名单 -> "支付成功"标记解析 -> 双层去重 -> 待确认草稿 + 系统通知。
 * 数据全部保存在本机（SharedPreferences 存草稿，SQLite 存账单），不经任何服务端。
 */
class MuyianBillCore private constructor() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val PREFS_NAME = "muyian_bill"
        private const val KEY_DRAFTS = "pending_drafts"
        private const val KEY_DEDUP = "capture_dedup"
        private const val KEY_NOTIFY_SEQ = "draft_notification_seq"
        private const val DB_NAME = "muyian.db"
        private const val CHANNEL_ID = "bill_pending"
        private const val CAPTURE_DEDUP_WINDOW_MS = 5 * 60 * 1000L
        private const val BILL_DEDUP_WINDOW_MS = 2 * 60 * 1000L
        private const val DRAFT_TTL_MS = 7 * 24 * 3600 * 1000L

        private val WHITESPACE = Pattern.compile("\\s+")
        private val IGNORE_PATTERN = Pattern.compile(
            "待支付|未支付|还未支付|支付失败|付款失败|扣款失败|交易关闭|自动取消|验证码|登录提醒|配置更新"
        )
        private val SCREEN_IGNORE_PATTERN = Pattern.compile(
            "取消支付|订单已关闭|订单已取消|付款超时|待付款|处理中"
        )
        /** 账单列表/统计页：行内也有"交易成功"字样，整页跳过防误报 */
        private val SCREEN_LIST_PATTERN = Pattern.compile(
            "账单明细|全部账单|收支统计|交易记录|账单列表|本月支出|本月收入"
        )
        private val SCREEN_EXPENSE_MARK = Pattern.compile(
            "支付成功|付款成功|转账成功|转出成功|扣款成功|还款成功|付款完成|交易成功"
        )
        private val SCREEN_INCOME_MARK = Pattern.compile(
            "收款成功|收款到账|已到账|退款成功|退回成功|转入成功|存入零钱|存入余额|已收款"
        )
        // 通知文本的关键词（通知里没有"支付成功"这类页面级完成标记）
        private val EXPENSE_PATTERN = Pattern.compile("支出|付款|支付|消费|扣款|转出|提现|买单|代扣")
        private val INCOME_PATTERN = Pattern.compile("退款|退回|返还|收入|收款|到账|入账|转入")
        private val REFUND_PATTERN = Pattern.compile("退款|退回|返还")
        private val CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:￥|¥|人民币|RMB|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)|([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)\\s*(?:元|块|圆))"
        )
        private val SEMANTIC_AMOUNT_PATTERN = Pattern.compile(
            "(?:金额|实付|消费|支付|支出|收入|转入|转出|扣款|付款|收款|入账|提现|退款)[^0-9￥¥]{0,8}(?:￥|¥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)"
        )
        private val SYMBOL_AMOUNT_LINE = Pattern.compile("^[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)$")
        private val BARE_DECIMAL_LINE = Pattern.compile("^([0-9]+(?:,[0-9]{3})*\\.\\d{2})$")
        private val DATE_LINE = Pattern.compile("^\\d{4}[.\\-/年]\\d{1,2}")

        private val BILL_PACKAGES = arrayOf(
            "com.eg.android.AlipayGphone", "com.tencent.mm", "com.unionpay",
            "com.unionpay.tsmservice", "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew",
            "com.dianping.v1", "com.jingdong.app.mall", "com.jd.jrapp", "com.icbc",
            "com.chinamworld.main", "com.cmbchina.ccd.pluto.cmbActivity", "com.ccb.longjiLife",
            "com.bankcomm.Bankcomm", "cn.com.cmbc.newmbank", "com.cib.cibmb",
            "com.cebbank.mobile.cemb", "com.spdbccc.app", "com.pingan.paces.ccms",
            "com.citicbank.mobile", "com.hxb.mobile.client", "com.psbc.mobilebank",
            "com.boc.bocsoft.mobile"
        )
        private val BILL_PREFIXES = arrayOf(
            "com.alipay.", "com.tencent.mm", "com.unionpay", "com.jingdong.", "com.jd.",
            "com.sankuai.", "com.dianping.", "com.icbc", "com.cmbchina.", "com.ccb.",
            "com.bankcomm.", "cn.com.cmbc.", "com.cib.", "com.cebbank.", "com.spdb",
            "com.pingan.", "com.citicbank.", "com.hxb.", "com.psbc.", "com.boc."
        )
        private val LABEL_KEYWORDS = arrayOf(
            "银行", "信用卡", "支付宝", "微信", "云闪付", "美团", "大众点评", "京东", "京东金融", "翼支付", "数字人民币"
        )

        /** 应用名关键词白名单的判定缓存：无障碍事件高频，避免每次都查 PackageManager */
        private val labelCache = ConcurrentHashMap<String, Boolean>()

        /** 创建通知渠道并清理过期草稿，App 启动时调用 */
        @JvmStatic
        fun init(context: Context) {
            createChannel(context)
            cleanupDrafts(context)
        }

        @JvmStatic
        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val expected = ComponentName(context, MuyianBillAccessibilityService::class.java)
                val raw = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val flat = expected.flattenToString()
                val shortFlat = expected.flattenToShortString()
                raw.split(":").any {
                    it == flat || it == shortFlat || ComponentName.unflattenFromString(it) == expected
                }
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun isNotificationListenerEnabled(context: Context): Boolean {
            return try {
                val raw = Settings.Secure.getString(
                    context.contentResolver, "enabled_notification_listeners"
                )
                if (raw == null) false
                else raw.split(":").any { it.substringBefore('/') == context.packageName }
            } catch (e: Exception) {
                false
            }
        }

        /** WRITE_SECURE_SETTINGS 是否已通过 adb 授予：授权后 App 可自行开关无障碍服务（掉开关自愈） */
        @JvmStatic
        fun hasWriteSecureSettings(context: Context): Boolean {
            return try {
                context.checkPermission(
                    "android.permission.WRITE_SECURE_SETTINGS",
                    Process.myPid(), Process.myUid()
                ) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 自恢复状态查询，返回 {"hasPermission":bool,"serviceEnabled":bool,"packageName":"..."}。
         * packageName 供设置页拼接 adb 授权命令展示。
         */
        @JvmStatic
        fun getSelfRepairState(context: Context): String {
            val o = JSONObject()
            o.put("hasPermission", hasWriteSecureSettings(context))
            o.put("serviceEnabled", isAccessibilityEnabled(context))
            o.put("packageName", context.packageName)
            return o.toString()
        }

        /**
         * 无障碍服务自恢复：已授予 WRITE_SECURE_SETTINGS 时，把本应用无障碍服务
         * 追加回系统"已启用无障碍服务"列表并打开总开关，免用户去系统设置手动重开。
         * 只追加本应用的条目，不改动其他应用已注册的服务。
         * 返回 {"success":bool,"serviceEnabled":bool,"error"?:string}
         */
        @JvmStatic
        fun repairAccessibilityService(context: Context): String {
            val result = JSONObject()
            if (!hasWriteSecureSettings(context)) {
                result.put("success", false).put("error", "no permission")
                return result.toString()
            }
            var ok = false
            try {
                val cn = ComponentName(context, MuyianBillAccessibilityService::class.java)
                ok = appendEnabledService(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cn)
                if (ok) {
                    Settings.Secure.putInt(
                        context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "repair accessibility service failed", e)
                ok = false
            }
            result.put("success", ok)
            result.put("serviceEnabled", isAccessibilityEnabled(context))
            if (!ok) result.put("error", "write failed")
            return result.toString()
        }

        /** 把一个 ComponentName 追加进 secure settings 的冒号分隔服务列表（已在列表则不动，视为成功） */
        private fun appendEnabledService(context: Context, key: String, cn: ComponentName): Boolean {
            return try {
                val flat = cn.flattenToString()
                val shortFlat = cn.flattenToShortString()
                val raw = Settings.Secure.getString(context.contentResolver, key) ?: ""
                val parts = ArrayList<String>()
                var exists = false
                for (p in raw.split(":")) {
                    if (p.isBlank()) continue
                    if (p == flat || p == shortFlat || ComponentName.unflattenFromString(p) == cn) {
                        exists = true
                    }
                    parts.add(p)
                }
                if (exists) return true
                parts.add(flat)
                Settings.Secure.putString(context.contentResolver, key, parts.joinToString(":"))
                true
            } catch (e: Exception) {
                Log.e(TAG, "append enabled service failed", e)
                false
            }
        }

        /** 候选 App 判定：包名 / 前缀 / 应用名关键词三层匹配（关键词结果带缓存） */
        @JvmStatic
        fun isCandidateApp(context: Context, pkg: String): Boolean {
            if (BILL_PACKAGES.contains(pkg)) return true
            for (prefix in BILL_PREFIXES) {
                if (pkg.startsWith(prefix)) return true
            }
            val cached = labelCache[pkg]
            if (cached != null) return cached
            val hit = try {
                val label = context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)
                )
                val name = label?.toString() ?: ""
                LABEL_KEYWORDS.any { name.contains(it) }
            } catch (e: Exception) {
                false
            }
            labelCache[pkg] = hit
            return hit
        }

        /** 采集层签名：同 App 同页面文本只解析一次，无障碍层去抖用 */
        @JvmStatic
        fun screenSignature(pkg: String, lines: List<String>): String {
            return pkg + "#" + WHITESPACE.matcher(lines.joinToString("|")).replaceAll("")
        }

        /**
         * 支付结果页处理流水线（后台线程执行）。
         * @param force true 时跳过白名单与去重（测试注入用）
         * @return 结果 JSON：{"accepted":bool,"reason":"...","draft":{...}?}
         */
        @JvmStatic
        fun handleScreen(context: Context, pkg: String, lines: List<String>, force: Boolean): String {
            val result = JSONObject()
            if (pkg == context.packageName) {
                result.put("accepted", false).put("reason", "self package")
                return result.toString()
            }
            if (!force && !isCandidateApp(context, pkg)) {
                result.put("accepted", false).put("reason", "not candidate app")
                return result.toString()
            }
            val parsed = parseScreenBill(pkg, lines) ?: run {
                result.put("accepted", false).put("reason", "not a payment page")
                return result.toString()
            }

            val money = parsed.getDouble("money")
            val bankName = parsed.getString("bankName")
            val now = System.currentTimeMillis()

            if (!force) {
                // 捕获级去重：同 App+金额+页面文本头 5 分钟内只处理一次
                val readable = WHITESPACE.matcher(lines.joinToString("\n")).replaceAll("")
                val head = if (readable.length > 40) readable.substring(0, 40) else readable
                val dedupKey = pkg + "|" + String.format(Locale.US, "%.2f", Math.abs(money)) + "|" + head
                if (isDuplicateCapture(context, dedupKey)) {
                    result.put("accepted", false).put("reason", "duplicate capture")
                    return result.toString()
                }
                // 账单级去重：同渠道+金额 2 分钟内已有入账记录或待确认草稿则丢弃
                if (isDuplicateBill(context, bankName, money, now) ||
                            isDuplicateDraft(context, bankName, money, now)) {
                    result.put("accepted", false).put("reason", "duplicate bill")
                    return result.toString()
                }
            }

            val merchant = extractScreenMerchant(lines)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            val draft = JSONObject()
            draft.put("token", UUID.randomUUID().toString())
            draft.put("money", money)
            draft.put("bankName", bankName)
            draft.put("costType", parsed.getString("costType"))
            draft.put("orderRemark", "")
            draft.put("merchant", merchant)
            draft.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(now)))
            draft.put("year", calendar.get(Calendar.YEAR))
            draft.put("month", calendar.get(Calendar.MONTH) + 1)
            draft.put("day", calendar.get(Calendar.DAY_OF_MONTH))
            draft.put("sourcePkg", pkg)
            draft.put("capture", "screen")
            draft.put("createdAt", now)
            draft.put("notificationId", nextNotificationId(context))

            val drafts = loadDrafts(context)
            drafts.put(draft.getString("token"), draft)
            saveDrafts(context, drafts)
            notifyDraft(context, draft)

            result.put("accepted", true).put("draft", draft)
            return result.toString()
        }

        /**
         * 通知处理流水线（辅助渠道，同步执行，回调线程非主线程）。
         * @param force true 时跳过白名单与去重（测试注入用）
         * @return 结果 JSON：{"accepted":bool,"reason":"...","draft":{...}?}
         */
        @JvmStatic
        fun handleNotification(
            context: Context, pkg: String, title: String, text: String,
            subText: String, bigText: String, lines: List<String>, postTime: Long, force: Boolean
        ): String {
            val result = JSONObject()
            if (pkg == context.packageName) {
                result.put("accepted", false).put("reason", "self package")
                return result.toString()
            }
            if (!force && !isCandidateApp(context, pkg)) {
                result.put("accepted", false).put("reason", "not candidate app")
                return result.toString()
            }
            val readable = title + "\n" + text + "\n" + subText + "\n" + bigText + "\n" + lines.toString()
            val parsed = parseBill(pkg, title, readable) ?: run {
                result.put("accepted", false).put("reason", "not a bill")
                return result.toString()
            }

            val money = parsed.getDouble("money")
            val bankName = parsed.getString("bankName")
            val now = System.currentTimeMillis()

            if (!force) {
                // 捕获级去重：同 内容+首金额 5 分钟内只处理一次（微信服务通知会反复 update）
                val firstAmount = String.format(Locale.US, "%.2f", Math.abs(money))
                val dedupKey = WHITESPACE.matcher(pkg + "|" + title + "|" + text + "|" + firstAmount).replaceAll("")
                if (isDuplicateCapture(context, dedupKey)) {
                    result.put("accepted", false).put("reason", "duplicate capture")
                    return result.toString()
                }
                // 账单级去重：同渠道+金额 2 分钟窗口，screen/notify 两路来源互防重复
                if (isDuplicateBill(context, bankName, money, postTime) ||
                            isDuplicateDraft(context, bankName, money, postTime)) {
                    result.put("accepted", false).put("reason", "duplicate bill")
                    return result.toString()
                }
            }

            val merchant = extractNotificationMerchant(text, subText, bigText, lines)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = postTime
            val draft = JSONObject()
            draft.put("token", UUID.randomUUID().toString())
            draft.put("money", money)
            draft.put("bankName", bankName)
            draft.put("costType", parsed.getString("costType"))
            draft.put("orderRemark", "")
            draft.put("merchant", merchant)
            draft.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(postTime)))
            draft.put("year", calendar.get(Calendar.YEAR))
            draft.put("month", calendar.get(Calendar.MONTH) + 1)
            draft.put("day", calendar.get(Calendar.DAY_OF_MONTH))
            draft.put("sourcePkg", pkg)
            draft.put("capture", "notify")
            draft.put("createdAt", now)
            draft.put("notificationId", nextNotificationId(context))

            val drafts = loadDrafts(context)
            drafts.put(draft.getString("token"), draft)
            saveDrafts(context, drafts)
            notifyDraft(context, draft)

            result.put("accepted", true).put("draft", draft)
            return result.toString()
        }

        /**
         * 通知解析：返回 {money(带正负号), bankName, costType}，非账单返回 null。
         * 收支关键词匹配前先剔除应用名（"支付宝""微信支付"里都含"支付"，
         * 不剔除会把所有收款类通知误判成支出）。
         */
        private fun parseBill(pkg: String, title: String, readable: String): JSONObject? {
            val compact = WHITESPACE.matcher(readable).replaceAll("")
            if (compact.isEmpty() || IGNORE_PATTERN.matcher(compact).find()) return null
            val isAlipay = pkg == "com.eg.android.AlipayGphone" || pkg.startsWith("com.alipay.")
            val isWechat = pkg == "com.tencent.mm"
            // 小荷包为多人共享账本，需按昵称区分归属，v0.1 暂不解析（路线图）
            if (compact.contains("小荷包")) return null

            val keywordScope = compact.replace("支付宝", "").replace("微信支付", "").replace("微信", "")
            val hasExpense = EXPENSE_PATTERN.matcher(keywordScope).find()
            val hasIncome = INCOME_PATTERN.matcher(keywordScope).find()
            val isRefund = REFUND_PATTERN.matcher(keywordScope).find()
            val matchesHistoricalTitle = (isWechat && title.contains("微信支付")) ||
                (isAlipay && title.contains("交易提醒"))
            if (!hasExpense && !hasIncome && !matchesHistoricalTitle) return null

            val amounts = extractAmounts(readable)
            if (amounts.isEmpty()) return null
            val amount = Math.abs(amounts[0])
            val income = isRefund || (hasIncome && !hasExpense)

            val parsed = JSONObject()
            parsed.put("money", if (income) amount else -amount)
            parsed.put("bankName", inferPayWay(pkg))
            parsed.put("costType", if (income) "收入" else "消费")
            return parsed
        }

        /**
         * 支付结果页解析：返回 {money(带正负号), bankName, costType}，非结果页返回 null。
         * 依赖"支付成功"类完成标记而非泛化关键词，避免在浏览页面时误报；
         * 金额优先取结果页的大字金额（独立的 ¥xx 行 / 纯数字行），兜底走货币正则。
         */
        private fun parseScreenBill(pkg: String, lines: List<String>): JSONObject? {
            val readable = lines.joinToString("\n")
            val compact = WHITESPACE.matcher(readable).replaceAll("")
            if (compact.isEmpty()) return null
            if (IGNORE_PATTERN.matcher(compact).find() || SCREEN_IGNORE_PATTERN.matcher(compact).find()) return null
            if (SCREEN_LIST_PATTERN.matcher(compact).find()) return null
            // 小荷包为多人共享账本，需按昵称区分归属，v0.1 暂不解析（路线图）
            if (compact.contains("小荷包")) return null

            val hasExpenseMark = SCREEN_EXPENSE_MARK.matcher(compact).find()
            val hasIncomeMark = SCREEN_INCOME_MARK.matcher(compact).find()
            if (!hasExpenseMark && !hasIncomeMark) return null

            val amount = extractScreenAmount(lines, compact)
            if (amount <= 0.0) return null
            val income = hasIncomeMark && !hasExpenseMark

            val parsed = JSONObject()
            parsed.put("money", if (income) amount else -amount)
            parsed.put("bankName", inferPayWay(pkg))
            parsed.put("costType", if (income) "收入" else "消费")
            return parsed
        }

        // ---------- 商户名提取（结果供页面侧词典做分类建议，原生不感知分类体系） ----------

        private val MERCHANT_EXCLUDE = Pattern.compile(
            "^[0-9.,，。:：+\\-¥￥元]+$|\\d{1,2}月\\d{1,2}日|\\d{1,2}:\\d{2}|订单|单号|凭证|账单|余额|零钱|余额宝|零钱通|储蓄卡|信用卡|银行卡|花呗|白条|积分|支付宝|微信支付|^微信$|^支付宝$|退款|红包|转账|自动扣|代扣|手续费"
        )
        private val BANK_SUFFIX = Pattern.compile("(银行|储蓄卡|信用卡)$")
        private val PAREN_MERCHANT = Pattern.compile("（([^（）]{2,30})）|\\(([^()]{2,30})\\)")

        /** 判断一个字符串是否像商户/对方名称 */
        private fun isUsableMerchant(v: String): Boolean {
            if (v.length < 2 || v.length > 30) return false
            if (MERCHANT_EXCLUDE.matcher(v).find()) return false
            if (BANK_SUFFIX.matcher(v).find()) return false
            return true
        }

        /**
         * 从支付结果页文本提取商户名：按"收款方/商户/转账给"等标签行取值，
         * 值可能在本行标签后，也可能单独成一行。取不到返回空串。
         */
        private fun extractScreenMerchant(lines: List<String>): String {
            val labels = arrayOf(
                "收款方", "商户全称", "商户名称", "收款店铺", "对方账户",
                "付款给", "转账给", "收款人", "商户"
            )
            for (i in lines.indices) {
                val line = lines[i].trim()
                for (label in labels) {
                    val idx = line.indexOf(label)
                    if (idx < 0) continue
                    val value = line.substring(idx + label.length).trim().trimStart('：', ':').trim()
                    if (isUsableMerchant(value)) return value
                    if (value.isEmpty() && i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (isUsableMerchant(next)) return next
                    }
                }
            }
            return ""
        }

        /**
         * 从通知文本提取商户名：优先显式标签，其次取正文中最后一个可用的
         * （...）括号内容（通知常把商户放在尾部括号里）。取不到返回空串。
         */
        private fun extractNotificationMerchant(
            text: String, subText: String, bigText: String, lines: List<String>
        ): String {
            val body = StringBuilder()
            body.append(text).append('\n').append(subText).append('\n').append(bigText)
            for (l in lines) body.append('\n').append(l)
            val source = body.toString()
            for (label in arrayOf("商户：", "商户:", "商户名称", "收款方", "付款给", "转账给", "收款店铺", "对方账户")) {
                val idx = source.indexOf(label)
                if (idx < 0) continue
                val rest = source.substring(idx + label.length).trim()
                val endOfLine = rest.indexOf('\n')
                val value = (if (endOfLine >= 0) rest.substring(0, endOfLine) else rest).trim().trimStart('：', ':').trim()
                if (isUsableMerchant(value)) return value
            }
            val m = PAREN_MERCHANT.matcher(source)
            var last = ""
            while (m.find()) {
                val v = (m.group(1) ?: m.group(2) ?: "").trim()
                if (isUsableMerchant(v)) last = v
            }
            return last
        }

        private fun extractScreenAmount(lines: List<String>, compact: String): Double {
            var index = 0
            for (raw in lines) {
                val line = raw.trim()
                val symbol = SYMBOL_AMOUNT_LINE.matcher(line)
                if (symbol.matches()) return toAmount(symbol.group(1))
                // 纯数字行只在前几行里认（结果页大字金额的位置），且排除形如日期的行
                if (index < 12 && !DATE_LINE.matcher(line).find()) {
                    val bare = BARE_DECIMAL_LINE.matcher(line)
                    if (bare.matches()) return toAmount(bare.group(1))
                }
                index++
            }
            val amounts = extractAmounts(compact)
            return if (amounts.isEmpty()) 0.0 else amounts[0]
        }

        private fun extractAmounts(text: String): ArrayList<Double> {
            val amounts = ArrayList<Double>()
            addMatchedAmounts(amounts, text, CURRENCY_AMOUNT_PATTERN)
            addMatchedAmounts(amounts, text, SEMANTIC_AMOUNT_PATTERN)
            return amounts
        }

        private fun addMatchedAmounts(amounts: ArrayList<Double>, text: String, pattern: Pattern) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                var value = ""
                for (i in 1..matcher.groupCount()) {
                    val g = matcher.group(i)
                    if (g != null && g.isNotEmpty()) {
                        value = g
                        break
                    }
                }
                val amount = try {
                    value.replace(",", "").toDouble()
                } catch (e: NumberFormatException) {
                    0.0
                }
                if (amount > 0 && !containsAmount(amounts, amount)) {
                    amounts.add(amount)
                }
            }
        }

        private fun containsAmount(amounts: ArrayList<Double>, target: Double): Boolean {
            for (a in amounts) {
                if (Math.abs(a - target) < 0.01) return true
            }
            return false
        }

        private fun toAmount(value: String): Double {
            return try {
                value.replace(",", "").toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
        }

        private fun inferPayWay(pkg: String): String {
            return when {
                pkg == "com.tencent.mm" -> "微信"
                pkg == "com.eg.android.AlipayGphone" || pkg.startsWith("com.alipay.") -> "支付宝"
                else -> "银行卡"
            }
        }

        // ---------- 捕获级去重（SharedPreferences 持久化，进程重启后依然有效） ----------

        private fun isDuplicateCapture(context: Context, key: String): Boolean {
            val prefs = prefs(context)
            val map = try {
                JSONObject(prefs.getString(KEY_DEDUP, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val now = System.currentTimeMillis()
            val stale = ArrayList<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (now - map.optLong(k, 0L) >= CAPTURE_DEDUP_WINDOW_MS) stale.add(k)
            }
            for (k in stale) map.remove(k)
            val last = map.optLong(key, 0L)
            val duplicate = last > 0 && now - last < CAPTURE_DEDUP_WINDOW_MS
            if (!duplicate) {
                map.put(key, now)
                prefs.edit().putString(KEY_DEDUP, map.toString()).apply()
            }
            return duplicate
        }

        // ---------- 账单级去重 ----------

        private fun isDuplicateBill(context: Context, bankName: String, money: Double, createdAt: Long): Boolean {
            val db = openDb(context)
            var cursor: Cursor? = null
            try {
                cursor = db.query("bills", null, "source in (?,?)", arrayOf("notify", "screen"), null, null, "id desc")
                while (cursor.moveToNext()) {
                    val existingBank = cursor.getString(cursor.getColumnIndex("bankName")) ?: continue
                    if (existingBank != bankName) continue
                    if (Math.abs(cursor.getDouble(cursor.getColumnIndex("money")) - money) >= 0.01) continue
                    val existingAt = cursor.getLong(cursor.getColumnIndex("createdAt"))
                    if (Math.abs(existingAt - createdAt) <= BILL_DEDUP_WINDOW_MS) return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "dedup bill failed", e)
            } finally {
                cursor?.close()
                db.close()
            }
            return false
        }

        private fun isDuplicateDraft(context: Context, bankName: String, money: Double, createdAt: Long): Boolean {
            val drafts = loadDrafts(context)
            val keys = drafts.keys()
            while (keys.hasNext()) {
                val draft = drafts.optJSONObject(keys.next()) ?: continue
                if (draft.optString("bankName") != bankName) continue
                if (Math.abs(draft.optDouble("money", 0.0) - money) >= 0.01) continue
                if (Math.abs(draft.optLong("createdAt", 0L) - createdAt) <= BILL_DEDUP_WINDOW_MS) return true
            }
            return false
        }

        // ---------- 草稿 ----------

        private fun loadDrafts(context: Context): JSONObject {
            return try {
                JSONObject(prefs(context).getString(KEY_DRAFTS, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
        }

        private fun saveDrafts(context: Context, drafts: JSONObject) {
            prefs(context).edit().putString(KEY_DRAFTS, drafts.toString()).apply()
        }

        private fun cleanupDrafts(context: Context) {
            val drafts = loadDrafts(context)
            val now = System.currentTimeMillis()
            val stale = ArrayList<String>()
            val keys = drafts.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val draft = drafts.optJSONObject(k) ?: continue
                if (now - draft.optLong("createdAt", 0L) > DRAFT_TTL_MS) stale.add(k)
            }
            if (stale.isNotEmpty()) {
                for (k in stale) drafts.remove(k)
                saveDrafts(context, drafts)
            }
        }

        private fun nextNotificationId(context: Context): Int {
            val prefs = prefs(context)
            val next = prefs.getInt(KEY_NOTIFY_SEQ, 1000000) + 1
            prefs.edit().putInt(KEY_NOTIFY_SEQ, next).apply()
            return next
        }

        // ---------- 对外 API ----------

        @JvmStatic
        fun getPendingDrafts(context: Context): String {
            val drafts = loadDrafts(context)
            val list = ArrayList<JSONObject>()
            val keys = drafts.keys()
            while (keys.hasNext()) {
                val draft = drafts.optJSONObject(keys.next()) ?: continue
                list.add(draft)
            }
            list.sortByDescending { it.optLong("createdAt", 0L) }
            val array = JSONArray()
            for (draft in list) array.put(draft)
            return array.toString()
        }

        @JvmStatic
        fun getDraft(context: Context, token: String): String {
            val draft = loadDrafts(context).optJSONObject(token)
            return draft?.toString() ?: ""
        }

        @JvmStatic
        fun resolveDraft(
            context: Context, token: String, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String
        ): String {
            val result = JSONObject()
            val amount = try {
                money.trim().toDouble()
            } catch (e: NumberFormatException) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            if (amount <= 0) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            val drafts = loadDrafts(context)
            val draft = drafts.optJSONObject(token)
            if (draft == null) {
                result.put("success", false).put("error", "draft not found")
                return result.toString()
            }
            val signedMoney = if (isIncome) amount else -amount
            val finalCostType = if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType
            val capture = draft.optString("capture")
            val cv = ContentValues()
            cv.put("year", draft.optInt("year"))
            cv.put("month", draft.optInt("month"))
            cv.put("day", draft.optInt("day"))
            cv.put("clock", draft.optString("clock"))
            cv.put("money", signedMoney)
            cv.put("bankName", if (bankName.isEmpty()) draft.optString("bankName") else bankName)
            cv.put("costType", finalCostType)
            cv.put("orderRemark", orderRemark)
            cv.put("source", if (capture.isEmpty()) "notify" else capture)
            cv.put("sourcePkg", draft.optString("sourcePkg"))
            cv.put("createdAt", draft.optLong("createdAt"))

            val db = openDb(context)
            val id = try {
                db.insert("bills", null, cv)
            } finally {
                db.close()
            }
            drafts.remove(token)
            saveDrafts(context, drafts)
            cancelDraftNotification(context, draft)
            result.put("success", id > 0).put("id", id)
            return result.toString()
        }

        @JvmStatic
        fun discardDraft(context: Context, token: String): Boolean {
            val drafts = loadDrafts(context)
            val draft = drafts.optJSONObject(token) ?: return false
            drafts.remove(token)
            saveDrafts(context, drafts)
            cancelDraftNotification(context, draft)
            return true
        }

        @JvmStatic
        fun saveManualBill(
            context: Context, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String
        ): String {
            return saveBillEx(
                context, "", money, isIncome, bankName, costType, orderRemark, false, false
            )
        }

        /**
         * 按指定日期记一笔（date 为空字符串时取今天）。
         * @param date "YYYY-MM-DD"
         * @return {"success":true,"id":n}
         */
        @JvmStatic
        fun saveBillEx(
            context: Context, date: String, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String,
            reimb: Boolean, starred: Boolean
        ): String {
            val result = JSONObject()
            val amount = try {
                money.trim().toDouble()
            } catch (e: NumberFormatException) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            if (amount <= 0) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            val now = System.currentTimeMillis()
            val nowCal = Calendar.getInstance()
            nowCal.timeInMillis = now
            var cal = nowCal
            val dateTrim = date.trim()
            if (dateTrim.isNotEmpty()) {
                val parts = dateSplit(dateTrim)
                if (parts == null) {
                    result.put("success", false).put("error", "invalid date")
                    return result.toString()
                }
                cal = Calendar.getInstance()
                cal.set(parts[0], parts[1] - 1, parts[2], 12, 0, 0)
            }
            val cv = ContentValues()
            cv.put("year", cal.get(Calendar.YEAR))
            cv.put("month", cal.get(Calendar.MONTH) + 1)
            cv.put("day", cal.get(Calendar.DAY_OF_MONTH))
            cv.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(now)))
            cv.put("money", if (isIncome) amount else -amount)
            cv.put("bankName", bankName)
            cv.put("costType", if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType)
            cv.put("orderRemark", orderRemark)
            cv.put("source", "manual")
            cv.put("sourcePkg", "")
            cv.put("createdAt", now)
            cv.put("reimb", if (reimb) 1 else 0)
            cv.put("starred", if (starred) 1 else 0)
            val db = openDb(context)
            val id = try {
                db.insert("bills", null, cv)
            } finally {
                db.close()
            }
            result.put("success", id > 0).put("id", id)
            return result.toString()
        }

        /**
         * 编辑已入账账单（保留 source/sourcePkg/createdAt）。
         * @param date "YYYY-MM-DD"
         * @return {"success":true}
         */
        @JvmStatic
        fun updateBill(
            context: Context, id: String, date: String, money: String, isIncome: Boolean,
            bankName: String, costType: String, orderRemark: String,
            reimb: Boolean, starred: Boolean
        ): String {
            val result = JSONObject()
            val amount = try {
                money.trim().toDouble()
            } catch (e: NumberFormatException) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            if (amount <= 0) {
                result.put("success", false).put("error", "invalid money")
                return result.toString()
            }
            val dateTrim = date.trim()
            val parts = dateSplit(dateTrim)
            if (parts == null) {
                result.put("success", false).put("error", "invalid date")
                return result.toString()
            }
            val cv = ContentValues()
            cv.put("year", parts[0])
            cv.put("month", parts[1])
            cv.put("day", parts[2])
            cv.put("money", if (isIncome) amount else -amount)
            cv.put("bankName", bankName)
            cv.put("costType", if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType)
            cv.put("orderRemark", orderRemark)
            cv.put("reimb", if (reimb) 1 else 0)
            cv.put("starred", if (starred) 1 else 0)
            val db = openDb(context)
            val updated = try {
                db.update("bills", cv, "id=?", arrayOf(id.trim()))
            } finally {
                db.close()
            }
            result.put("success", updated > 0)
            return result.toString()
        }

        private fun dateSplit(date: String): IntArray? {
            val m = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(date)
            if (!m.find()) return null
            val y = m.group(1)!!.toInt()
            val mo = m.group(2)!!.toInt()
            val d = m.group(3)!!.toInt()
            if (mo < 1 || mo > 12 || d < 1 || d > 31) return null
            return intArrayOf(y, mo, d)
        }

        @JvmStatic
        fun getYearBills(context: Context, year: String): String {
            val db = openDb(context)
            var cursor: Cursor? = null
            val array = JSONArray()
            try {
                cursor = db.query(
                    "bills", null,
                    "year=?",
                    arrayOf(year.trim()),
                    null, null, "id desc"
                )
                var count = 0
                while (cursor.moveToNext() && count < 5000) {
                    array.put(billJson(cursor))
                    count++
                }
            } catch (e: Exception) {
                Log.e(TAG, "query year bills failed", e)
            } finally {
                cursor?.close()
                db.close()
            }
            return array.toString()
        }

        @JvmStatic
        fun getMonthBills(context: Context, year: String, month: String): String {
            val db = openDb(context)
            var cursor: Cursor? = null
            val array = JSONArray()
            try {
                cursor = db.query(
                    "bills", null,
                    "year=? and month=?",
                    arrayOf(year.trim(), month.trim()),
                    null, null, "id desc"
                )
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    array.put(billJson(cursor))
                    count++
                }
            } catch (e: Exception) {
                Log.e(TAG, "query month bills failed", e)
            } finally {
                cursor?.close()
                db.close()
            }
            return array.toString()
        }

        @JvmStatic
        fun deleteBill(context: Context, id: String): Boolean {
            val db = openDb(context)
            return try {
                db.delete("bills", "id=?", arrayOf(id.trim())) > 0
            } catch (e: Exception) {
                Log.e(TAG, "delete bill failed", e)
                false
            } finally {
                db.close()
            }
        }

        /** 测试注入：把一段文本当作"支付成功"页面跑一遍解析流水线 */
        @JvmStatic
        fun emitTestBill(context: Context, text: String): String {
            val lines = ArrayList<String>()
            for (line in text.split('\n')) {
                lines.add(line)
            }
            return handleScreen(context, "com.eg.android.AlipayGphone", lines, true)
        }

        // ---------- 通知与存储基建 ----------

        private fun notifyDraft(context: Context, draft: JSONObject) {
            val nm = context.getSystemService("notification") as NotificationManager
            val seq = draft.optInt("notificationId")
            val money = draft.optDouble("money", 0.0)
            val orderType = if (money >= 0) "收入" else "支出"
            val amountText = String.format("%.2f", Math.abs(money))
            val contentTitle = "待确认 · $orderType ${amountText}元"
            val contentText = draft.optString("bankName") + " · " + draft.optString("costType") + " · " + draft.optString("clock")

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            var pi: PendingIntent? = null
            if (launch != null) {
                launch.addFlags(0x10000000) // Intent.FLAG_ACTIVITY_NEW_TASK
                launch.putExtra("draftToken", draft.optString("token"))
                val flags = 0x08000000 or // PendingIntent.FLAG_UPDATE_CURRENT
                    (if (Build.VERSION.SDK_INT >= 31) 0x04000000 else 0) // FLAG_IMMUTABLE
                pi = PendingIntent.getActivity(context, seq, launch, flags)
            }

            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                Notification.Builder(context)
            }
            builder.setContentTitle(contentTitle)
                .setContentText(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setSmallIcon(context.applicationInfo.icon)
                .setAutoCancel(true)
                .setContentIntent(pi)
            if (Build.VERSION.SDK_INT < 26) {
                builder.setPriority(1).setDefaults(-1) // PRIORITY_HIGH, DEFAULT_ALL
            }
            try {
                nm.notify(seq, builder.build())
            } catch (e: Exception) {
                // Android 13+ 未授予 POST_NOTIFICATIONS 时静默失败，草稿仍在首页待确认列表中
                Log.w(TAG, "notify draft failed", e)
            }
        }

        private fun cancelDraftNotification(context: Context, draft: JSONObject) {
            val nm = context.getSystemService("notification") as NotificationManager
            nm.cancel(draft.optInt("notificationId"))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService("notification") as NotificationManager
                val channel = NotificationChannel(CHANNEL_ID, "记账待确认", NotificationManager.IMPORTANCE_HIGH)
                channel.description = "解析到支付后弹出的待确认账单"
                nm.createNotificationChannel(channel)
            }
        }

        private fun openDb(context: Context): SQLiteDatabase {
            val db = SQLiteDatabase.openOrCreateDatabase(
                context.getFilesDir().getAbsolutePath() + "/" + DB_NAME, null
            )
            db.execSQL(
                "create table if not exists bills(" +
                    "id integer primary key autoincrement," +
                    "year integer,month integer,day integer,clock text," +
                    "money real,bankName text,costType text,orderRemark text," +
                    "source text,sourcePkg text,createdAt integer)"
            )
            // 旧库平滑迁移：报销 / 星标标记（不存在则补列，已存在则忽略报错）
            try {
                db.execSQL("alter table bills add column reimb integer default 0")
            } catch (e: Exception) {
                // column already exists
            }
            try {
                db.execSQL("alter table bills add column starred integer default 0")
            } catch (e: Exception) {
                // column already exists
            }
            return db
        }

        private fun billJson(cursor: Cursor): JSONObject {
            val o = JSONObject()
            o.put("id", cursor.getLong(cursor.getColumnIndex("id")))
            o.put("year", cursor.getInt(cursor.getColumnIndex("year")))
            o.put("month", cursor.getInt(cursor.getColumnIndex("month")))
            o.put("day", cursor.getInt(cursor.getColumnIndex("day")))
            o.put("clock", cursor.getString(cursor.getColumnIndex("clock")))
            o.put("money", cursor.getDouble(cursor.getColumnIndex("money")))
            o.put("bankName", cursor.getString(cursor.getColumnIndex("bankName")))
            o.put("costType", cursor.getString(cursor.getColumnIndex("costType")))
            o.put("orderRemark", cursor.getString(cursor.getColumnIndex("orderRemark")))
            o.put("source", cursor.getString(cursor.getColumnIndex("source")))
            o.put("sourcePkg", cursor.getString(cursor.getColumnIndex("sourcePkg")))
            o.put("createdAt", cursor.getLong(cursor.getColumnIndex("createdAt")))
            val reimbIdx = cursor.getColumnIndex("reimb")
            val starredIdx = cursor.getColumnIndex("starred")
            o.put("reimb", if (reimbIdx >= 0) cursor.getInt(reimbIdx) else 0)
            o.put("starred", if (starredIdx >= 0) cursor.getInt(starredIdx) else 0)
            return o
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, 0) // Context.MODE_PRIVATE
    }
}
