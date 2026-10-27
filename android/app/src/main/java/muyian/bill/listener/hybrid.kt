package muyian.bill.listener

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.InputType
import android.util.Log
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 自动记账核心引擎：
 * 候选App过滤 -> 页面黑名单 -> "支付成功"标记解析 -> 单号优先的去重 -> 底部确认弹层
 * （弹层拉不起来时发一条「待确认」通知兜底，点开仍回到同一个弹层）。
 * 金额按"锚点标签 > 独立大字金额 > 上下文扫描"三级择取，余额/额度/优惠类数字不进候选。
 * 数据全部保存在本机（SQLite 存账单），不经任何服务端。
 */
class MuyianBillCore private constructor() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val PREFS_NAME = "muyian_bill"
        private const val KEY_DEDUP = "capture_dedup"
        private const val KEY_ORDER_DEDUP = "order_dedup"
        /** 弹层拉不起来时的通知兜底渠道：点开通知仍进 BillSheetActivity，不在通知里入账 */

        /** 弹层队列与去重用的内存状态：不落盘，进程结束即丢弃 */
        private val sheetLock = Any()
        private val sheetQueue = ArrayList<JSONObject>()
        private val recentBills = ArrayList<JSONObject>()
        private val sheetHandler = Handler(Looper.getMainLooper())
        private var sheetShowing = false

        // ---------- 支付路线：宿主 App → 支付 App → 宿主 App ----------

        /** 只记录 App 切换（不记录页面文本），用于判断一笔支付是从哪个宿主 App 发起的 */
        private class RouteEntry(val pkg: String, val at: Long, val from: String)

        /** 一笔已捕获的支付在路线上的归属 */
        private class CaptureFlow(
            val amount: Double,
            val income: Boolean,
            val createdAt: Long,
            val sourcePkg: String,
            val hostPkg: String,
            val payPkg: String,
            var orderNo: String,
            var merchant: String
        ) {
            /** 仍在弹层队列/当前弹层里的那笔账单：跨 App 重复捕获时用来补全字段 */
            var bill: JSONObject? = null
        }

        private class CaptureRoute(val hostPkg: String, val payPkg: String)

        private val routeLock = Any()
        private val routeEntries = ArrayList<RouteEntry>()
        /** 宿主 App 里出现过"去支付/提交订单"的时间 */
        private val pendingPayAt = HashMap<String, Long>()
        /** 从支付 App 回到宿主 App 的时间（且该次访问确实有支付动作） */
        private val returnedFromPayAt = HashMap<String, Long>()
        /** 支付 App 里出现过结果页/收银台的时间，用来确认"这次进支付 App 是真支付" */
        private val paymentEvidenceAt = HashMap<String, Long>()
        /** 已捕获支付流水的路线记录，跨宿主/支付 App 去重 */
        private val flows = ArrayList<CaptureFlow>()
        private var flowsLoaded = false
        /** 通知 ID 自增序号：同一时刻可能有多笔兜底通知，不能互相覆盖 */
        /** 建表/补列只在本进程首次开库时执行一次，避免每次查询都抛 3 次 ALTER 异常 */
        private const val CAPTURE_DEDUP_WINDOW_MS = 5 * 60 * 1000L
        /** 单号是交易唯一凭证：同单号 24 小时内不再弹层 */
        private const val ORDER_DEDUP_WINDOW_MS = 24 * 3600 * 1000L
        private const val BILL_DEDUP_WINDOW_MS = 2 * 60 * 1000L
        /** 支付路线只保留最近 15 分钟：宿主 App → 支付 App → 宿主 App 的切换记录 */
        private const val ROUTE_TTL_MS = 15 * 60 * 1000L
        private const val ROUTE_MAX = 24
        /** 从支付 App 回到宿主 App 后，只在这段时间内接受宿主的结果页 */
        private const val HOST_RETURN_WINDOW_MS = 3 * 60 * 1000L
        /** 宿主 App 内点过"去支付/提交订单"后，结果页在这段时间内可被接受（App 内支付） */
        private const val HOST_PAY_TRIGGER_WINDOW_MS = 10 * 60 * 1000L
        /** 同一笔支付在宿主 App 与支付 App 两处被捕获时，这段时间内合并成一笔 */
        private const val FLOW_MATCH_WINDOW_MS = 15 * 60 * 1000L
        /** 宿主 App 结果页指纹：同一页面 24 小时内不再弹（离开后再进来也不会重复） */
        private const val HOST_PAGE_DEDUP_WINDOW_MS = 24 * 3600 * 1000L
        private const val KEY_FLOWS = "capture_flows"
        private const val KEY_HOST_PAGE_DEDUP = "host_page_dedup"
        /** 弹层同时最多排队的笔数（超出丢弃最旧的，避免误捕获堆积） */
        private const val MAX_SHEETS = 5
        /** 弹层拉起后等待 Activity onCreate 的兜底时长：超时仍没起来就放行队列 */
        private const val SHEET_START_WATCHDOG_MS = 5000L

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
        /** 宿主 App 的订单管理页：有"支付成功"字样也不认，避免翻历史订单时误弹 */
        private val SCREEN_ORDER_MGMT_PATTERN = Pattern.compile(
            "申请退款|查看物流|确认收货|待评价|去评价|再来一单|订单已完成|已送达|下单时间|我的订单|全部订单|订单列表|订单管理|退款/售后"
        )
        private val SCREEN_EXPENSE_MARK = Pattern.compile(
            "支付成功|付款成功|转账成功|转出成功|扣款成功|还款成功|付款完成|交易成功"
        )
        private val SCREEN_INCOME_MARK = Pattern.compile(
            "收款成功|收款到账|已到账|退款成功|退回成功|转入成功|存入零钱|存入余额|已收款"
        )
        /** 收银台/确认支付页：出现后短时间内才允许宿主 App 的结果页入账，防平时浏览订单页误弹 */
        private val SCREEN_PAY_TRIGGER = Pattern.compile(
            "确认支付|立即支付|去支付|确认付款|立即付款|去付款|提交订单|收银台|选择支付方式|输入支付密码|指纹支付|面容支付|免密支付|同意协议并支付"
        )
        // 通知文本的关键词（通知里没有"支付成功"这类页面级完成标记）
        private val EXPENSE_PATTERN = Pattern.compile("支出|付款|支付|消费|扣款|转出|提现|买单|代扣")
        private val INCOME_PATTERN = Pattern.compile("退款|退回|返还|收入|收款|到账|入账|转入")
        private val REFUND_PATTERN = Pattern.compile("退款|退回|返还")
        private val CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:￥|¥|人民币|RMB|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)|([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)\\s*(?:元|块|圆))"
        )
        private val SYMBOL_AMOUNT_LINE = Pattern.compile("^[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)$")
        private val BARE_DECIMAL_LINE = Pattern.compile("^([0-9]+(?:,[0-9]{3})*\\.\\d{2})$")
        /** 标签后紧跟的数字（允许冒号、货币符号等 0-6 个非数字前缀） */
        private val LEADING_AMOUNT = Pattern.compile("^[^0-9]{0,6}([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)")
        /** 只认 19xx/20xx 开头的日期，避免把 1000.00 这类四位数金额当日期跳过 */
        private val DATE_LINE = Pattern.compile("^(?:19|20)[0-9]{2}[.\\-/年][0-9]{1,2}")
        /** "满100元减10元"里的 100 不是交易金额 */
        private val FULL_REDUCTION = Pattern.compile("满\\s*[0-9]+\\s*元?\\s*减")
        /** "优惠后/折后"指的是优惠后的成交价，排除前先剔除，避免误伤实付金额 */
        private val POST_DISCOUNT = Pattern.compile("(?:优惠|满减|立减|折扣|折)后")
        /** 订单/交易流水号：去重的最可靠键，同号永不重复入账（允许中间有空格分组） */
        private val ORDER_NO_PATTERN = Pattern.compile(
            "(?:订单号|订单编号|交易单号|商户单号|交易流水号|流水号|凭证号|交易号|单号)[：:\\s]*([A-Za-z0-9][A-Za-z0-9 \\t]{7,49})"
        )

        // ---------- 金额选择规则：按 App 维护锚点与排除词，未命中走通用规则 ----------

        private class AmountRule(val anchors: Array<String>, val excludes: Array<String>)

        /** 余额/额度/优惠类数字一律不进候选 */
        private val BASE_AMOUNT_EXCLUDES = arrayOf(
            "余额", "额度", "可用", "优惠", "立减", "折扣", "原价", "积分",
            "红包", "返现", "已省", "节省", "优惠券", "抵扣", "手续费"
        )
        /** 强锚点：标签本身就是交易金额，允许整数（银行短信"消费38元"） */
        private val STRONG_AMOUNT_ANCHORS = arrayOf(
            "实付金额", "实付", "实际支付", "支付金额", "付款金额", "交易金额",
            "消费金额", "扣款金额", "支出金额", "收款金额", "到账金额", "退款金额",
            "转入金额", "转出金额", "转账金额", "取现金额", "存入金额",
            "本次消费", "本次支出", "本次支付", "应付金额", "待付金额", "账单金额", "交易额"
        )
        /** 弱锚点：动词/泛称，必须带小数点或货币单位才认，避免把"支付时间2026-10-26"当金额 */
        private val WEAK_AMOUNT_ANCHORS = arrayOf(
            "金额", "合计", "总计", "应付", "待付", "需付",
            "消费", "支付", "支出", "收入", "转入", "转出", "扣款", "付款", "收款", "入账", "提现", "退款"
        )

        private val DEFAULT_AMOUNT_RULE = AmountRule(arrayOf<String>(), BASE_AMOUNT_EXCLUDES)
        private val ALIPAY_AMOUNT_RULE = AmountRule(
            arrayOf("付款金额", "支付金额", "交易金额", "订单金额", "实付金额"),
            BASE_AMOUNT_EXCLUDES + arrayOf("余额宝", "花呗", "信用额度")
        )
        private val WECHAT_AMOUNT_RULE = AmountRule(
            arrayOf("支付金额", "付款金额", "收款金额", "转账金额", "退款金额"),
            BASE_AMOUNT_EXCLUDES + arrayOf("零钱", "零钱通")
        )
        private val MEITUAN_AMOUNT_RULE = AmountRule(
            arrayOf("实付金额", "实付", "订单金额", "支付金额"),
            BASE_AMOUNT_EXCLUDES + arrayOf("配送费", "打包费")
        )
        private val JD_AMOUNT_RULE = AmountRule(
            arrayOf("应付金额", "实付金额", "支付金额", "订单金额"),
            BASE_AMOUNT_EXCLUDES + arrayOf("京豆", "白条")
        )
        private val UNIONPAY_AMOUNT_RULE = AmountRule(
            arrayOf("交易金额", "支付金额", "订单金额"),
            BASE_AMOUNT_EXCLUDES
        )
        private val BANK_AMOUNT_RULE = AmountRule(
            arrayOf("交易金额", "支出金额", "消费金额", "转账金额", "取现金额", "存入金额"),
            BASE_AMOUNT_EXCLUDES + arrayOf("账户余额", "信用额度", "可用额度")
        )
        private val AMOUNT_RULES: Map<String, AmountRule> = mapOf(
            "com.eg.android.AlipayGphone" to ALIPAY_AMOUNT_RULE,
            "com.tencent.mm" to WECHAT_AMOUNT_RULE,
            "com.sankuai.meituan" to MEITUAN_AMOUNT_RULE,
            "com.sankuai.meituan.takeoutnew" to MEITUAN_AMOUNT_RULE,
            "com.dianping.v1" to MEITUAN_AMOUNT_RULE,
            "com.jingdong.app.mall" to JD_AMOUNT_RULE,
            "com.jd.jrapp" to JD_AMOUNT_RULE,
            "com.unionpay" to UNIONPAY_AMOUNT_RULE,
            "com.unionpay.tsmservice" to UNIONPAY_AMOUNT_RULE
        )
        private val AMOUNT_RULE_PREFIXES = arrayOf(
            "com.alipay." to ALIPAY_AMOUNT_RULE,
            "com.sankuai." to MEITUAN_AMOUNT_RULE,
            "com.dianping." to MEITUAN_AMOUNT_RULE,
            "com.jingdong." to JD_AMOUNT_RULE,
            "com.jd." to JD_AMOUNT_RULE,
            "com.unionpay" to UNIONPAY_AMOUNT_RULE
        )
        private val BANK_AMOUNT_PREFIXES = arrayOf(
            "com.icbc", "com.chinamworld.", "com.cmbchina.", "com.ccb.", "com.bankcomm.",
            "cn.com.cmbc.", "com.cib.", "com.cebbank.", "com.spdb", "com.pingan.",
            "com.citicbank.", "com.hxb.", "com.psbc.", "com.boc."
        )

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

        /** App 启动时调用：建好待确认通知渠道、清理旧版本遗留状态，再拉起保活服务 */
        @JvmStatic
        fun init(context: Context) {
            // 旧版本的草稿落盘与悬浮卡开关已整体移除；弹层拉不起来时只发通知，点开仍进弹层
            try {
                prefs(context).edit()
                    .remove("pending_drafts")
                    .remove("pending_open_token")
                    .remove("overlay_enabled")
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "clean legacy prefs failed", e)
            }
            PendingNotices.ensureChannel(context)
            // 启动时记录一次弹层权限状态，便于排查"权限显示已开但弹层仍被拦"
            try {
                Log.i(TAG, "popup permission state " + getPopupPermissionState(context))
            } catch (e: Exception) {
                Log.w(TAG, "probe popup permission state failed", e)
            }
            // 默认开启后台保活；用户明确关闭过则不再拉起
            if (isKeepAliveEnabled(context)) {
                MuyianKeepAliveService.start(context)
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

        // ---------- 支付路线（宿主 App / 支付 App） ----------

        /** 支付 App：微信、支付宝、云闪付与银行 App；其余候选 App 视为宿主 App */
        @JvmStatic
        fun isPaymentApp(pkg: String): Boolean {
            if (pkg == "com.tencent.mm") return true
            if (pkg == "com.eg.android.AlipayGphone" || pkg.startsWith("com.alipay.")) return true
            if (pkg.startsWith("com.unionpay")) return true
            for (prefix in BANK_AMOUNT_PREFIXES) {
                if (pkg.startsWith(prefix)) return true
            }
            return false
        }

        /** 系统弹窗/权限页不参与支付路线，避免把 from 记成 systemui 导致宿主判断丢失 */
        private fun isSystemUi(pkg: String): Boolean {
            if (pkg == "android") return true
            if (pkg == "com.android.systemui") return true
            if (pkg.contains("systemui")) return true
            if (pkg == "com.android.permissioncontroller") return true
            if (pkg == "com.google.android.permissioncontroller") return true
            if (pkg == "com.android.packageinstaller") return true
            if (pkg == "com.google.android.packageinstaller") return true
            if (pkg == "com.miui.securitycenter") return true
            if (pkg == "com.lbe.security.miui") return true
            return false
        }

        /**
         * 无障碍窗口切换时调用：维护最近 15 分钟的 App 路线。
         * 只在"支付 App → 宿主 App"且那次支付 App 访问确实有支付动作时，
         * 才给宿主打上"刚支付完"标记，避免只是去支付宝看一眼也当成支付。
         */
        @JvmStatic
        fun noteForeground(context: Context, pkg: String) {
            if (pkg.isEmpty() || pkg == context.packageName || isSystemUi(pkg)) return
            val now = System.currentTimeMillis()
            synchronized(routeLock) {
                val last = if (routeEntries.isEmpty()) null else routeEntries[routeEntries.size - 1]
                if (last != null && last.pkg == pkg) return
                val from = if (last == null) "" else last.pkg
                if (last != null && isPaymentApp(last.pkg) && !isPaymentApp(pkg) && last.from == pkg) {
                    val evidence = paymentEvidenceAt[last.pkg] ?: 0L
                    if (evidence >= last.at) {
                        returnedFromPayAt[pkg] = now
                    }
                }
                routeEntries.add(RouteEntry(pkg, now, from))
                while (routeEntries.size > ROUTE_MAX) {
                    routeEntries.removeAt(0)
                }
                while (routeEntries.isNotEmpty() && now - routeEntries[0].at > ROUTE_TTL_MS) {
                    routeEntries.removeAt(0)
                }
            }
        }

        /** 支付 App 出现结果页/收银台：作为"这次访问确实在支付"的证据 */
        private fun markPaymentEvidence(pkg: String, at: Long) {
            synchronized(routeLock) {
                val prev = paymentEvidenceAt[pkg] ?: 0L
                if (at > prev) paymentEvidenceAt[pkg] = at
            }
        }

        /** 记录收银台/提交订单页；宿主 App 的结果页靠它判断"刚刚确实在支付" */
        private fun notePaymentTrigger(pkg: String, lines: List<String>, now: Long) {
            val text = WHITESPACE.matcher(lines.joinToString("")).replaceAll("")
            if (!SCREEN_PAY_TRIGGER.matcher(text).find()) return
            synchronized(routeLock) {
                pendingPayAt[pkg] = now
                val prev = paymentEvidenceAt[pkg] ?: 0L
                if (now > prev) paymentEvidenceAt[pkg] = now
            }
        }

        /** 宿主 App 当前是否处在可接受结果页的支付上下文里 */
        private fun hasHostPaymentContext(context: Context, hostPkg: String, now: Long): Boolean {
            synchronized(routeLock) {
                val returned = returnedFromPayAt[hostPkg] ?: 0L
                if (returned > 0 && now - returned <= HOST_RETURN_WINDOW_MS) return true
                val triggered = pendingPayAt[hostPkg] ?: 0L
                if (triggered > 0 && now - triggered <= HOST_PAY_TRIGGER_WINDOW_MS) return true
            }
            synchronized(sheetLock) {
                ensureFlowsLoaded(context)
                for (i in flows.indices.reversed()) {
                    val f = flows[i]
                    if (now - f.createdAt > FLOW_MATCH_WINDOW_MS) break
                    if (f.hostPkg == hostPkg) return true
                }
            }
            return false
        }

        /** 支付 App 这次访问是从哪个宿主 App 进来的；不是从候选宿主进来的返回空串 */
        private fun originHostOfPayment(context: Context, payPkg: String, now: Long): String {
            var from = ""
            synchronized(routeLock) {
                for (i in routeEntries.indices.reversed()) {
                    val e = routeEntries[i]
                    if (now - e.at > ROUTE_TTL_MS) break
                    if (e.pkg != payPkg) continue
                    from = e.from
                    break
                }
            }
            if (from.isEmpty() || from == payPkg || isPaymentApp(from)) return ""
            return if (isCandidateApp(context, from)) from else ""
        }

        /** 宿主 App 最近一次跳去的支付 App（要求是从该宿主出发），供路线记录使用 */
        private fun lastPayPkgForHost(hostPkg: String, now: Long): String {
            var pay = ""
            synchronized(routeLock) {
                for (i in routeEntries.indices.reversed()) {
                    val e = routeEntries[i]
                    if (now - e.at > FLOW_MATCH_WINDOW_MS) break
                    if (!isPaymentApp(e.pkg)) continue
                    if (e.from == hostPkg) {
                        pay = e.pkg
                        break
                    }
                }
            }
            return pay
        }

        private fun resolveCaptureRoute(context: Context, pkg: String, now: Long): CaptureRoute {
            if (isPaymentApp(pkg)) {
                return CaptureRoute(originHostOfPayment(context, pkg, now), pkg)
            }
            return CaptureRoute(pkg, lastPayPkgForHost(pkg, now))
        }

        /** flows 只在 sheetLock 内访问；进程被杀后从 prefs 恢复，跨 App 去重才不会断 */
        private fun ensureFlowsLoaded(context: Context) {
            if (flowsLoaded) return
            flowsLoaded = true
            val now = System.currentTimeMillis()
            try {
                val raw = prefs(context).getString(KEY_FLOWS, "[]") ?: "[]"
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o == null) continue
                    val createdAt = o.optLong("createdAt", 0L)
                    if (now - createdAt > FLOW_MATCH_WINDOW_MS) continue
                    flows.add(CaptureFlow(
                        o.optDouble("amount", 0.0),
                        o.optBoolean("income", false),
                        createdAt,
                        o.optString("sourcePkg", ""),
                        o.optString("hostPkg", ""),
                        o.optString("payPkg", ""),
                        o.optString("orderNo", ""),
                        o.optString("merchant", "")
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "load capture flows failed", e)
            }
        }

        private fun saveFlowsLocked(context: Context) {
            val now = System.currentTimeMillis()
            val arr = JSONArray()
            var i = 0
            while (i < flows.size) {
                val f = flows[i]
                if (now - f.createdAt > FLOW_MATCH_WINDOW_MS) {
                    flows.removeAt(i)
                    continue
                }
                val o = JSONObject()
                o.put("amount", f.amount)
                o.put("income", f.income)
                o.put("createdAt", f.createdAt)
                o.put("sourcePkg", f.sourcePkg)
                o.put("hostPkg", f.hostPkg)
                o.put("payPkg", f.payPkg)
                o.put("orderNo", f.orderNo)
                o.put("merchant", f.merchant)
                arr.put(o)
                i++
            }
            try {
                prefs(context).edit().putString(KEY_FLOWS, arr.toString()).apply()
            } catch (e: Exception) {
                Log.w(TAG, "save capture flows failed", e)
            }
        }

        private fun recordFlowLocked(context: Context, bill: JSONObject, route: CaptureRoute) {
            ensureFlowsLoaded(context)
            val money = bill.optDouble("money", 0.0)
            val flow = CaptureFlow(
                Math.abs(money),
                money >= 0,
                bill.optLong("createdAt", System.currentTimeMillis()),
                bill.optString("sourcePkg"),
                route.hostPkg,
                route.payPkg,
                bill.optString("orderNo"),
                bill.optString("merchant")
            )
            flow.bill = bill
            flows.add(flow)
            while (flows.size > 30) {
                flows.removeAt(0)
            }
            saveFlowsLocked(context)
        }

        /**
         * 跨 App 同一笔支付：宿主 App 与支付 App 的 bankName、订单号都不一样，
         * 不能按"同渠道+金额"或"同单号"判断，只能靠支付路线把它们合并。
         * 同 App 的重复仍交给原去重规则，避免把同金额的两笔真实消费误合并。
         */
        private fun findMatchingFlowLocked(
            context: Context, pkg: String, money: Double, income: Boolean,
            route: CaptureRoute, now: Long
        ): CaptureFlow? {
            ensureFlowsLoaded(context)
            val amount = Math.abs(money)
            for (i in flows.indices.reversed()) {
                val f = flows[i]
                if (now - f.createdAt > FLOW_MATCH_WINDOW_MS) continue
                if (f.income != income) continue
                if (Math.abs(f.amount - amount) >= 0.01) continue
                if (f.sourcePkg == pkg) continue
                val sourceIsPay = isPaymentApp(f.sourcePkg)
                val newIsPay = isPaymentApp(pkg)
                if (sourceIsPay && !newIsPay) {
                    if (f.hostPkg.isNotEmpty() && f.hostPkg == pkg) return f
                } else if (!sourceIsPay && newIsPay) {
                    if (f.hostPkg == f.sourcePkg && route.hostPkg == f.hostPkg &&
                        (f.payPkg.isEmpty() || f.payPkg == pkg)) return f
                } else if (sourceIsPay && newIsPay) {
                    if (f.hostPkg.isNotEmpty() && f.hostPkg == route.hostPkg) return f
                }
            }
            return null
        }

        /** 把后到的重复捕获并进已排队/已展示的那笔：支付 App 的金额与渠道更权威 */
        private fun mergeBill(target: JSONObject, incoming: JSONObject, incomingPkg: String, flow: CaptureFlow) {
            if (isPaymentApp(incomingPkg)) {
                target.put("money", incoming.optDouble("money", target.optDouble("money", 0.0)))
                target.put("bankName", incoming.optString("bankName", target.optString("bankName")))
            }
            if (target.optString("merchant").isEmpty()) {
                target.put("merchant", incoming.optString("merchant", ""))
            }
            if (target.optString("orderNo").isEmpty()) {
                target.put("orderNo", incoming.optString("orderNo", ""))
            }
            if (flow.orderNo.isEmpty()) flow.orderNo = incoming.optString("orderNo", "")
            if (flow.merchant.isEmpty()) flow.merchant = incoming.optString("merchant", "")
        }

        /**
         * 宿主 App 结果页指纹：同一页面 24 小时内只认一次。
         * 只有期间出现了新的支付动作（刚从支付 App 回来/点过去支付/有新的支付流水）才放行，
         * 这样"付款后再进美团"不会重复弹，"隔天同一笔订单"也不会再弹。
         */
        private fun isDuplicateHostPage(
            context: Context, pkg: String, money: Double, income: Boolean,
            orderNo: String, merchant: String, now: Long
        ): Boolean {
            val merchantKey = WHITESPACE.matcher(merchant).replaceAll("")
            val fp = pkg + "|" + String.format(Locale.US, "%.2f", Math.abs(money)) + "|" +
                (if (income) "in" else "out") + "|" + orderNo + "|" + merchantKey
            val preferences = prefs(context)
            val map = try {
                JSONObject(preferences.getString(KEY_HOST_PAGE_DEDUP, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val stale = ArrayList<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (now - map.optLong(k, 0L) >= HOST_PAGE_DEDUP_WINDOW_MS) stale.add(k)
            }
            for (k in stale) map.remove(k)
            val last = map.optLong(fp, 0L)
            val duplicate = last > 0 && now - last < HOST_PAGE_DEDUP_WINDOW_MS
            val fresh = duplicate && hasFreshPaymentAction(context, pkg, last)
            map.put(fp, now)
            preferences.edit().putString(KEY_HOST_PAGE_DEDUP, map.toString()).apply()
            return duplicate && !fresh
        }

        private fun hasFreshPaymentAction(context: Context, hostPkg: String, since: Long): Boolean {
            synchronized(routeLock) {
                val returned = returnedFromPayAt[hostPkg] ?: 0L
                if (returned > since) return true
                val triggered = pendingPayAt[hostPkg] ?: 0L
                if (triggered > since) return true
            }
            synchronized(sheetLock) {
                ensureFlowsLoaded(context)
                for (i in flows.indices.reversed()) {
                    val f = flows[i]
                    if (f.createdAt <= since) break
                    if (f.hostPkg == hostPkg) return true
                }
            }
            return false
        }

        /**
         * 支付结果页处理流水线（后台线程执行）。
         * @param force true 时跳过白名单与去重（测试注入用）
         * @return 结果 JSON：{"accepted":bool,"reason":"...","bill":{...}?}
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
            val now = System.currentTimeMillis()
            val isPay = isPaymentApp(pkg)
            if (!force) {
                // 宿主 App 的结果页只有在"刚支付完"的路线上下文里才接受；
                // 先判断再记录本页触发词，避免结果页自己给自己背书
                val orderPage = !isPay && SCREEN_ORDER_MGMT_PATTERN.matcher(
                    WHITESPACE.matcher(lines.joinToString("")).replaceAll("")
                ).find()
                val hadContext = if (isPay) true else hasHostPaymentContext(context, pkg, now)
                notePaymentTrigger(pkg, lines, now)
                if (orderPage) {
                    result.put("accepted", false).put("reason", "order page")
                    return result.toString()
                }
                if (!hadContext) {
                    result.put("accepted", false).put("reason", "no payment route")
                    return result.toString()
                }
            }

            val parsed = parseScreenBill(pkg, lines) ?: run {
                result.put("accepted", false).put("reason", "not a payment page")
                return result.toString()
            }

            val money = parsed.getDouble("money")
            val bankName = parsed.getString("bankName")
            val orderNo = parsed.optString("orderNo")
            val merchant = extractScreenMerchant(lines)

            if (!force) {
                // 支付 App 出现结果页本身就是"这次访问在支付"的证据，供宿主结果页判断路线
                if (isPay) markPaymentEvidence(pkg, now)
                // 单号去重：同订单号 24 小时内只弹一次（页面重复渲染、结果页回看都靠它）
                if (orderNo.isNotEmpty() && isDuplicateOrder(context, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate order")
                    return result.toString()
                }
                // 宿主 App 结果页指纹：同一页面 24 小时内不再弹，避免离开后再进入又弹一次
                if (!isPay && isDuplicateHostPage(context, pkg, money, money >= 0, orderNo, merchant, now)) {
                    result.put("accepted", false).put("reason", "duplicate host page")
                    return result.toString()
                }
                // 无单号时退回捕获级指纹：同 App+金额+页面文本头 5 分钟内只处理一次
                if (orderNo.isEmpty()) {
                    val readable = WHITESPACE.matcher(lines.joinToString("\n")).replaceAll("")
                    val head = if (readable.length > 40) readable.substring(0, 40) else readable
                    val dedupKey = pkg + "|" + String.format(Locale.US, "%.2f", Math.abs(money)) + "|" + head
                    if (isDuplicateCapture(context, dedupKey)) {
                        result.put("accepted", false).put("reason", "duplicate capture")
                        return result.toString()
                    }
                }
                // 账单级去重：同渠道+金额 2 分钟内已有入账记录或正在等待确认则丢弃；
                // 两边都有单号且不同时视为两笔交易，不再按金额合并
                if (isDuplicateBill(context, bankName, money, now, orderNo) ||
                            isDuplicatePresented(bankName, money, now, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate bill")
                    return result.toString()
                }
            }

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            val bill = JSONObject()
            bill.put("money", money)
            bill.put("bankName", bankName)
            bill.put("costType", parsed.getString("costType"))
            bill.put("orderRemark", "")
            bill.put("merchant", merchant)
            bill.put("orderNo", orderNo)
            bill.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(now)))
            bill.put("year", calendar.get(Calendar.YEAR))
            bill.put("month", calendar.get(Calendar.MONTH) + 1)
            bill.put("day", calendar.get(Calendar.DAY_OF_MONTH))
            bill.put("sourcePkg", pkg)
            bill.put("capture", "screen")
            bill.put("createdAt", now)

            val presented = presentBill(context, bill, !force)
            if (presented) {
                result.put("accepted", true).put("bill", bill)
            } else {
                result.put("accepted", false).put("reason", "duplicate flow")
            }
            return result.toString()
        }

        /**
         * 通知处理流水线（辅助渠道，同步执行，回调线程非主线程）。
         * @param force true 时跳过白名单与去重（测试注入用）
         * @return 结果 JSON：{"accepted":bool,"reason":"...","bill":{...}?}
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
            val orderNo = parsed.optString("orderNo")
            val now = System.currentTimeMillis()
            val isPay = isPaymentApp(pkg)

            // 宿主 App 的通知同样要处在"刚支付完"的路线里才接受，
            // 否则美团等 App 的历史订单通知也会被当成新支付
            if (!force && !isPay && !hasHostPaymentContext(context, pkg, now)) {
                result.put("accepted", false).put("reason", "no payment route")
                return result.toString()
            }
            if (!force && isPay) markPaymentEvidence(pkg, now)
            val merchant = extractNotificationMerchant(text, subText, bigText, lines)

            if (!force) {
                // 单号去重：同订单号 24 小时内只弹一次（通知更新、screen/notify 双路都靠它）
                if (orderNo.isNotEmpty() && isDuplicateOrder(context, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate order")
                    return result.toString()
                }
                // 宿主 App 结果页指纹：同一笔通知/页面 24 小时内不再弹
                if (!isPay && isDuplicateHostPage(context, pkg, money, money >= 0, orderNo, merchant, now)) {
                    result.put("accepted", false).put("reason", "duplicate host page")
                    return result.toString()
                }
                // 无单号时退回捕获级指纹：同 内容+首金额 5 分钟内只处理一次（微信服务通知会反复 update）
                if (orderNo.isEmpty()) {
                    val firstAmount = String.format(Locale.US, "%.2f", Math.abs(money))
                    val dedupKey = WHITESPACE.matcher(pkg + "|" + title + "|" + text + "|" + firstAmount).replaceAll("")
                    if (isDuplicateCapture(context, dedupKey)) {
                        result.put("accepted", false).put("reason", "duplicate capture")
                        return result.toString()
                    }
                }
                // 账单级去重：同渠道+金额 2 分钟窗口，screen/notify 两路来源互防重复；
                // 两边都有单号且不同时视为两笔交易，不再按金额合并
                if (isDuplicateBill(context, bankName, money, postTime, orderNo) ||
                            isDuplicatePresented(bankName, money, postTime, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate bill")
                    return result.toString()
                }
            }

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = postTime
            val bill = JSONObject()
            bill.put("money", money)
            bill.put("bankName", bankName)
            bill.put("costType", parsed.getString("costType"))
            bill.put("orderRemark", "")
            bill.put("merchant", merchant)
            bill.put("orderNo", orderNo)
            bill.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(postTime)))
            bill.put("year", calendar.get(Calendar.YEAR))
            bill.put("month", calendar.get(Calendar.MONTH) + 1)
            bill.put("day", calendar.get(Calendar.DAY_OF_MONTH))
            bill.put("sourcePkg", pkg)
            bill.put("capture", "notify")
            bill.put("createdAt", now)

            val presented = presentBill(context, bill, !force)
            if (presented) {
                result.put("accepted", true).put("bill", bill)
            } else {
                result.put("accepted", false).put("reason", "duplicate flow")
            }
            return result.toString()
        }

        /**
         * 通知解析：返回 {money(带正负号), bankName, costType, orderNo}，非账单返回 null。
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

            val amount = pickAmount(pkg, readable.split('\n'))
            if (amount <= 0.0) return null
            val income = isRefund || (hasIncome && !hasExpense)

            val parsed = JSONObject()
            parsed.put("money", if (income) amount else -amount)
            parsed.put("bankName", inferPayWay(pkg))
            parsed.put("costType", if (income) "收入" else "消费")
            parsed.put("orderNo", extractOrderNo(readable))
            return parsed
        }

        /**
         * 支付结果页解析：返回 {money(带正负号), bankName, costType, orderNo}，非结果页返回 null。
         * 依赖"支付成功"类完成标记而非泛化关键词，避免在浏览页面时误报；
         * 金额走 pickAmount() 的锚点优先策略，余额/额度/优惠类数字不会进候选。
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

            val amount = pickAmount(pkg, lines)
            if (amount <= 0.0) return null
            val income = hasIncomeMark && !hasExpenseMark

            val parsed = JSONObject()
            parsed.put("money", if (income) amount else -amount)
            parsed.put("bankName", inferPayWay(pkg))
            parsed.put("costType", if (income) "收入" else "消费")
            parsed.put("orderNo", extractOrderNo(lines.joinToString("\n")))
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

        private fun amountRuleFor(pkg: String): AmountRule {
            AMOUNT_RULES[pkg]?.let { return it }
            for (p in AMOUNT_RULE_PREFIXES) {
                if (pkg.startsWith(p.first)) return p.second
            }
            for (prefix in BANK_AMOUNT_PREFIXES) {
                if (pkg.startsWith(prefix)) return BANK_AMOUNT_RULE
            }
            return DEFAULT_AMOUNT_RULE
        }

        /**
         * 金额三级择取：标签锚点 > 结果页独立大字金额 > 带货币单位的上下文扫描。
         * 锚点顺序为"强锚点 > App 专属锚点 > 弱锚点"：强锚点（实付/退款金额）先于
         * App 表里的"订单金额"，退款页不会把原订单金额当成退款额；弱锚点最后兜底。
         * 每一步都按排除词跳过余额/额度/优惠类数字，全部排除时返回 0（宁可漏记不误记）。
         */
        private fun pickAmount(pkg: String, lines: List<String>): Double {
            val rule = amountRuleFor(pkg)
            val excludes = rule.excludes
            for (anchor in STRONG_AMOUNT_ANCHORS) {
                val v = amountAfterAnchor(lines, anchor, excludes, true, false)
                if (v > 0) return v
            }
            for (anchor in rule.anchors) {
                val v = amountAfterAnchor(lines, anchor, excludes, true, false)
                if (v > 0) return v
            }
            for (anchor in WEAK_AMOUNT_ANCHORS) {
                val v = amountAfterAnchor(lines, anchor, excludes, false, true)
                if (v > 0) return v
            }
            val isolated = pickIsolatedAmount(lines, excludes)
            if (isolated > 0) return isolated
            return pickByCurrencyContext(lines, excludes)
        }

        /** 取锚点后的金额；同一行取不到时最多向下看两行（标签与值分行的情况） */
        private fun amountAfterAnchor(
            lines: List<String>, anchor: String, excludes: Array<String>,
            allowBareInteger: Boolean, checkLabelContext: Boolean
        ): Double {
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                // 弱锚点（"金额"）要排除"优惠金额/可用金额"；强锚点标签本身就是交易金额，
                // 不能因为同一行更靠前出现"余额/优惠"就否掉（如"账户余额X，本次支出38.50"）
                if (checkLabelContext && containsExclude(line.substring(maxOf(0, idx - 3), idx), excludes)) continue
                val rest = line.substring(idx + anchor.length)
                val same = firstAmountIn(rest, excludes, allowBareInteger)
                if (same > 0) return same
                var looked = 0
                var j = i + 1
                while (j < lines.size && looked < 2) {
                    val next = lines[j].trim()
                    if (next.isEmpty()) {
                        j++
                        continue
                    }
                    looked++
                    // 下一行若是"账户余额"这类标签，金额不是交易金额
                    if (previousLabelExcluded(lines, j, excludes)) break
                    val v = firstAmountIn(next, excludes, allowBareInteger)
                    if (v > 0) return v
                    // 下一行已有数字但不是金额（日期/单号），不再向下找
                    if (next.any { it in '0'..'9' }) break
                    j++
                }
            }
            return 0.0
        }

        /** 一段文本里的第一个金额：带货币单位或小数点，日期/时间/单号不认 */
        private fun firstAmountIn(text: String, excludes: Array<String>, allowBareInteger: Boolean): Double {
            val cm = CURRENCY_AMOUNT_PATTERN.matcher(text)
            while (cm.find()) {
                var value = ""
                for (i in 1..cm.groupCount()) {
                    val g = cm.group(i)
                    if (g != null && g.isNotEmpty()) {
                        value = g
                        break
                    }
                }
                val amount = toAmount(value)
                if (!isSaneAmount(amount)) continue
                if (looksLikeDateOrTime(text, cm.end())) continue
                if (containsExclude(text.substring(0, cm.start()), excludes)) continue
                return amount
            }
            val lm = LEADING_AMOUNT.matcher(text)
            if (lm.find()) {
                val raw = lm.group(1) ?: return 0.0
                val amount = toAmount(raw)
                if (isSaneAmount(amount) && (allowBareInteger || raw.contains("."))) {
                    val prefix = text.substring(0, lm.start(1))
                    if (!looksLikeDateOrTime(text, lm.end(1)) && !containsExclude(prefix, excludes)) {
                        return amount
                    }
                }
            }
            return 0.0
        }

        /** 结果页大字金额：独立的 ¥xx 行优先，其次是纯小数行；上一行是"余额"类标签则跳过 */
        private fun pickIsolatedAmount(lines: List<String>, excludes: Array<String>): Double {
            var bare = 0.0
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                if (previousLabelExcluded(lines, i, excludes)) continue
                val symbol = SYMBOL_AMOUNT_LINE.matcher(line)
                if (symbol.matches()) return toAmount(symbol.group(1) ?: "")
                if (bare <= 0.0 && i < 15 && !DATE_LINE.matcher(line).find()) {
                    val b = BARE_DECIMAL_LINE.matcher(line)
                    if (b.matches()) bare = toAmount(b.group(1) ?: "")
                }
            }
            return bare
        }

        /** 兜底：逐行找带货币单位的金额，仍按上下文排除余额/优惠 */
        private fun pickByCurrencyContext(lines: List<String>, excludes: Array<String>): Double {
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val m = CURRENCY_AMOUNT_PATTERN.matcher(line)
                while (m.find()) {
                    var value = ""
                    for (g in 1..m.groupCount()) {
                        val part = m.group(g)
                        if (part != null && part.isNotEmpty()) {
                            value = part
                            break
                        }
                    }
                    val amount = toAmount(value)
                    if (!isSaneAmount(amount)) continue
                    if (looksLikeDateOrTime(line, m.end())) continue
                    if (containsExclude(line.substring(0, m.start()), excludes)) continue
                    if (previousLabelExcluded(lines, i, excludes)) continue
                    return amount
                }
            }
            return 0.0
        }

        /** 紧邻的上一行若是"账户余额""可用额度"这类标签，则本行金额不是交易金额 */
        private fun previousLabelExcluded(lines: List<String>, index: Int, excludes: Array<String>): Boolean {
            var j = index - 1
            while (j >= 0) {
                val prev = lines[j].trim()
                j--
                if (prev.isEmpty()) continue
                if (prev.any { it in '0'..'9' }) return false
                return containsExclude(prev, excludes)
            }
            return false
        }

        private fun containsExclude(text: String, excludes: Array<String>): Boolean {
            if (text.isEmpty()) return false
            val normalized = POST_DISCOUNT.matcher(text).replaceAll("")
            if (FULL_REDUCTION.matcher(normalized).find()) return true
            for (w in excludes) {
                if (normalized.contains(w)) return true
            }
            return false
        }

        private fun looksLikeDateOrTime(text: String, end: Int): Boolean {
            if (end >= text.length) return false
            val c = text[end]
            return c == ':' || c == '：' || c == '年' || c == '月' ||
                c == '-' || c == '/' || c == '.' || c == '减'
        }

        private fun isSaneAmount(amount: Double): Boolean {
            return amount > 0 && amount < 100000000.0
        }

        /** 订单/交易单号；取不到返回空串（部分页面/通知不展示单号） */
        private fun extractOrderNo(text: String): String {
            val m = ORDER_NO_PATTERN.matcher(text)
            while (m.find()) {
                val raw = (m.group(1) ?: "").trim().replace(" ", "").replace("\t", "")
                val normalized = raw.uppercase(Locale.US)
                if (normalized.length < 8 || normalized.length > 40) continue
                // 8 位纯数字若像 YYYYMMDD 日期则不算单号
                if (normalized.length == 8 &&
                    (normalized.startsWith("19") || normalized.startsWith("20"))
                ) {
                    continue
                }
                return normalized
            }
            return ""
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

        // ---------- 去重（SharedPreferences 持久化，进程重启后依然有效） ----------

        private fun isDuplicateCapture(context: Context, key: String): Boolean {
            return isDuplicateKey(context, KEY_DEDUP, CAPTURE_DEDUP_WINDOW_MS, key)
        }

        /** 单号去重：同订单号在窗口内只认一次，与金额无关 */
        private fun isDuplicateOrder(context: Context, orderNo: String): Boolean {
            return isDuplicateKey(context, KEY_ORDER_DEDUP, ORDER_DEDUP_WINDOW_MS, orderNo)
        }

        @Synchronized
        private fun isDuplicateKey(context: Context, prefsKey: String, windowMs: Long, key: String): Boolean {
            val prefs = prefs(context)
            val map = try {
                JSONObject(prefs.getString(prefsKey, "{}") ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }
            val now = System.currentTimeMillis()
            val stale = ArrayList<String>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (now - map.optLong(k, 0L) >= windowMs) stale.add(k)
            }
            for (k in stale) map.remove(k)
            val last = map.optLong(key, 0L)
            val duplicate = last > 0 && now - last < windowMs
            if (!duplicate) {
                map.put(key, now)
                prefs.edit().putString(prefsKey, map.toString()).apply()
            }
            return duplicate
        }

        // ---------- 账单级去重 ----------

        /**
         * 账单级去重：同渠道+金额在窗口内视为重复。
         * 本笔与本表都有单号且不同时判定为两笔交易——同金额连续消费不再被误杀。
         */
        private fun isDuplicateBill(
            context: Context, bankName: String, money: Double, createdAt: Long, orderNo: String
        ): Boolean {
            val db = BillDb.openDb(context)
            var cursor: Cursor? = null
            try {
                cursor = db.query("bills", null, "source in (?,?)", arrayOf("notify", "screen"), null, null, "id desc")
                val orderIdx = cursor.getColumnIndex("orderNo")
                while (cursor.moveToNext()) {
                    val existingOrder = if (orderIdx >= 0) (cursor.getString(orderIdx) ?: "") else ""
                    if (orderNo.isNotEmpty() && existingOrder.isNotEmpty()) {
                        if (orderNo == existingOrder) return true
                        continue
                    }
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

        // ---------- 底部确认弹层（唯一的确认入口） ----------

        /** 弹层层去重：同一笔还没处理完时，另一路来源不要重复弹 */
        private fun isDuplicatePresented(
            bankName: String, money: Double, createdAt: Long, orderNo: String
        ): Boolean {
            synchronized(sheetLock) {
                for (b in recentBills) {
                    val bOrder = b.optString("orderNo")
                    if (orderNo.isNotEmpty() && bOrder.isNotEmpty()) {
                        if (orderNo == bOrder) return true
                        continue
                    }
                    if (b.optString("bankName") != bankName) continue
                    if (Math.abs(b.optDouble("money", 0.0) - money) >= 0.01) continue
                    if (Math.abs(b.optLong("createdAt", 0L) - createdAt) <= BILL_DEDUP_WINDOW_MS) return true
                }
            }
            return false
        }

        /** 亮屏且未锁屏时才拉起弹层；熄屏/锁屏时系统不允许在支付页上方显示 Activity */
        private fun isScreenInteractive(context: Context): Boolean {
            return try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                // 键锁状态用显式比较，保持条件可读
                pm.isInteractive && km.isKeyguardLocked == false
            } catch (e: Exception) {
                true
            }
        }

        /**
         * 把一笔解析结果交给底部确认弹层；弹层已经开着时先进内存队列。
         * 跨 App 的同一笔支付在这里合并：宿主 App 与支付 App 的结果页都解析成功后，
         * 只保留先到的那一笔，后到的只用来补全金额/渠道/商户/单号。
         * @return false 表示判定为同一笔支付，没有新增弹层
         */
        private fun presentBill(context: Context, bill: JSONObject, registerFlow: Boolean): Boolean {
            if (registerFlow) {
                val pkg = bill.optString("sourcePkg")
                val money = bill.optDouble("money", 0.0)
                val income = money >= 0
                val now = bill.optLong("createdAt", System.currentTimeMillis())
                val route = resolveCaptureRoute(context, pkg, now)
                synchronized(sheetLock) {
                    val flow = findMatchingFlowLocked(context, pkg, money, income, route, now)
                    if (flow != null) {
                        val target = flow.bill
                        if (target != null) {
                            mergeBill(target, bill, pkg, flow)
                        }
                        if (flow.orderNo.isEmpty()) flow.orderNo = bill.optString("orderNo", "")
                        if (flow.merchant.isEmpty()) flow.merchant = bill.optString("merchant", "")
                        saveFlowsLocked(context)
                        return false
                    }
                    recentBills.add(bill)
                    while (recentBills.size > 20) recentBills.removeAt(0)
                    while (sheetQueue.size >= MAX_SHEETS) sheetQueue.removeAt(0)
                    sheetQueue.add(bill)
                    recordFlowLocked(context, bill, route)
                }
                sheetHandler.post { showNextSheet(context.applicationContext) }
                return true
            }
            synchronized(sheetLock) {
                recentBills.add(bill)
                while (recentBills.size > 20) recentBills.removeAt(0)
                while (sheetQueue.size >= MAX_SHEETS) sheetQueue.removeAt(0)
                sheetQueue.add(bill)
            }
            sheetHandler.post { showNextSheet(context.applicationContext) }
            return true
        }

        /** 弹层已经开着时，把通知点击带来的新账单放回队列，等当前弹层关闭后再弹 */
        @JvmStatic
        fun enqueueSheetBill(context: Context, billJson: String): Boolean {
            val bill = try {
                JSONObject(billJson)
            } catch (e: Exception) {
                return false
            }
            // 通知点击只是把已经捕获过的那笔重新排进弹层，不能再登记一次支付路线
            presentBill(context.applicationContext, bill, false)
            return true
        }

        private fun showNextSheet(context: Context) {
            var next: JSONObject? = null
            synchronized(sheetLock) {
                if (sheetShowing == false && sheetQueue.isNotEmpty()) {
                    next = sheetQueue.removeAt(0)
                    sheetShowing = true
                }
            }
            val bill = next
            if (bill == null) return
            // 预占通知 ID：弹层正常出现时 Activity 会拿它标记"已拉起"，拉不起来时才真正发通知
            val noticeId = nextPendingNoticeId(context)
            if (!isScreenInteractive(context)) {
                // 熄屏/锁屏时 Activity 无法显示在支付页上方：改发通知，点开仍进确认弹层
                postPendingNotice(context, bill, noticeId)
                onSheetClosed(context)
                return
            }
            val cats = JSONArray()
            for (c in recentCostTypes(context, 10)) cats.put(c)
            val intent = Intent(context, BillSheetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.putExtra(BillSheetActivity.EXTRA_BILL, bill.toString())
            intent.putExtra(BillSheetActivity.EXTRA_CATS, cats.toString())
            intent.putExtra(BillSheetActivity.EXTRA_ACCENT, getCardThemeColor(context))
            intent.putExtra(BillSheetActivity.EXTRA_NOTICE_ID, noticeId)
            var started = false
            try {
                context.startActivity(intent)
                started = true
            } catch (e: Exception) {
                Log.e(TAG, "start bill sheet failed", e)
            }
            if (!started) {
                SystemPerms.markPopupBlocked(context)
                postPendingNotice(context, bill, noticeId)
                onSheetClosed(context)
                return
            }
            // 后台启动 Activity 被 ROM 静默拦截时不会抛异常：等一会还没起来就发通知并放行队列。
            // 已经起来又很快被关掉的（wasStarted）不能再补通知，避免用户取消后又收到一条。
            sheetHandler.postDelayed({
                if (BillSheetActivity.isAlive() == false && BillSheetActivity.wasStarted(noticeId) == false) {
                    SystemPerms.markPopupBlocked(context)
                    postPendingNotice(context, bill, noticeId)
                    onSheetClosed(context)
                }
            }, SHEET_START_WATCHDOG_MS)
        }

        /** 弹层关闭后继续处理队列（由 BillSheetActivity.onDestroy 调用） */
        @JvmStatic
        fun onSheetClosed(context: Context) {
            synchronized(sheetLock) {
                sheetShowing = false
            }
            sheetHandler.postDelayed({ showNextSheet(context.applicationContext) }, 250)
        }

        /** 弹层保存：由 BillSheetActivity 调用，把用户确认后的账单写入账单表 */
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
            val cv = ContentValues()
            cv.put("year", bill.optInt("year"))
            cv.put("month", bill.optInt("month"))
            cv.put("day", bill.optInt("day"))
            cv.put("clock", bill.optString("clock"))
            cv.put("money", if (isIncome) amount else -amount)
            cv.put("bankName", bill.optString("bankName"))
            cv.put("costType", if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType)
            cv.put("orderRemark", remark)
            cv.put("source", bill.optString("capture", "notify"))
            cv.put("sourcePkg", bill.optString("sourcePkg"))
            cv.put("createdAt", bill.optLong("createdAt", System.currentTimeMillis()))
            cv.put("orderNo", bill.optString("orderNo"))
            val db = BillDb.openDb(context)
            return try {
                db.insert("bills", null, cv) > 0
            } catch (e: Exception) {
                Log.e(TAG, "insert bill from sheet failed", e)
                false
            } finally {
                db.close()
            }
        }

        /** 卡里的"最近分类"：只读账单里的 costType 字符串，原生不感知分类体系 */
        private fun recentCostTypes(context: Context, limit: Int): ArrayList<String> {
            val out = ArrayList<String>()
            val db = BillDb.openDb(context)
            var cursor: Cursor? = null
            try {
                cursor = db.query("bills", arrayOf("costType"), null, null, null, null, "id desc")
                while (cursor.moveToNext() && out.size < limit) {
                    val t = cursor.getString(0) ?: continue
                    if (t.isNotEmpty() && !out.contains(t)) out.add(t)
                }
            } catch (e: Exception) {
                Log.e(TAG, "query recent cost types failed", e)
            } finally {
                cursor?.close()
                db.close()
            }
            return out
        }

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
        fun backupContent(): String {
            return backupExportContent
        }

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

        /** 账单写入的公开入口：实现委托给 BillDb。 */
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

        /**
         * 按指定日期记一笔（date 为空字符串时取今天）。
         * @param date "YYYY-MM-DD"
         * @return {"success":true,"id":n}
         */
        // ---------- 钱迹 Excel 导入 ----------
        // 选文件由 ImportPickActivity 中转（SAF，无需存储权限）：选中的文件拷到应用私有目录
        // qianji_import.xlsx，路径与状态放在静态字段，页面侧用同步 API 轮询读取。
        // 原生只做「xlsx 解压成表头 + 原始单元格行」和「批量写库」；时间/类型/金额校验、
        // 统计、分类计数与映射、入库字段拼装都在 com.muyian.debt.data 的 Qianji.kt。

        private const val IMPORT_CACHE_NAME = "qianji_import.xlsx"
        private const val IMPORT_MAX_ROWS = 20000
        /** 防止 xlsx 里零散的远端单元格把每行数组撑爆；钱迹导出实际只有十列左右 */
        private const val IMPORT_MAX_COLUMNS = 64
        private val importLock = Any()
        private var importPickState = "idle" // idle | picking | picked | cancelled | error
        private var importPickError = ""
        private var importPickPath = ""
        private var importPickName = ""
        private val importSheetRows = ArrayList<ArrayList<String>>()

        /** 拉起文件选择中转 Activity 选钱迹 Excel；同一时刻只允许一次 */
        @JvmStatic
        fun openImportPicker(context: Context): String {
            return startFilePicker(context, "xlsx")
        }

        /** 拉起文件选择中转 Activity 选本应用备份 JSON */
        @JvmStatic
        fun openBackupPicker(context: Context): String {
            return startFilePicker(context, "json")
        }

        private fun startFilePicker(context: Context, kind: String): String {
            // 先在锁内只标记 busy，锁外统一返回，避免多分支散落 return 导致状态不一致
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
                // 带上异常类型：基座没更新时这里会是 ActivityNotFoundException，页面据此提示重做基座
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
         * 返回 {"ok":true,"header":[…],"width":n,"total":n,"capped":true?}；
         * 数据行缓存在内存，供 getImportSheetRows 分块取用。
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

        /** 分块读取原始数据行：{"total":n,"rows":[[…],…]}，offset/limit 用字符串传参，解析失败时回退默认值 */
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

        // ---------- 通知与存储基建 ----------

        private fun nextPendingNoticeId(context: Context): Int = PendingNotices.nextId(context)

        private fun postPendingNotice(context: Context, bill: JSONObject, noticeId: Int) {
            PendingNotices.post(
                context, bill, noticeId,
                recentCostTypes(context, 10), getCardThemeColor(context)
            )
        }

        @JvmStatic
        fun cancelPendingNotice(context: Context, noticeId: Int) = PendingNotices.cancel(context, noticeId)

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, 0) // Context.MODE_PRIVATE
    }
}
