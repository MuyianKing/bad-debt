package muyian.bill.listener

import android.accessibilityservice.AccessibilityService
import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
 * 后台保活前台服务：用一条最低优先级的常驻通知把进程保持在"前台服务"状态，
 * 降低锁屏/切后台后被系统省电策略回收导致自动记账失效的概率。
 * 只维持进程存活，不读取、不上传任何数据；用户可在设置页关闭。
 * START_STICKY：被系统杀掉后由系统尝试重建，重建时若用户已关闭则立即退出。
 */
class MuyianKeepAliveService : Service() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val CHANNEL_ID = "muyian_keep_alive"
        private const val NOTIFICATION_ID = 2001

        @JvmStatic
        fun start(context: Context) {
            try {
                val intent = Intent(context, MuyianKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "start keep alive service failed", e)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MuyianKeepAliveService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "stop keep alive service failed", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 启动后必须尽快 startForeground，先挂通知再判断用户开关
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }
        if (!MuyianBillCore.isKeepAliveEnabled(this)) {
            stopForeground(true)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        createChannel()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        var pi: PendingIntent? = null
        if (launch != null) {
            launch.addFlags(0x10000000) // Intent.FLAG_ACTIVITY_NEW_TASK
            val flags = 0x08000000 or // PendingIntent.FLAG_UPDATE_CURRENT
                (if (Build.VERSION.SDK_INT >= 31) 0x04000000 else 0) // FLAG_IMMUTABLE
            pi = PendingIntent.getActivity(this, NOTIFICATION_ID, launch, flags)
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder.setContentTitle("孽账正在后台守护")
            .setContentText("自动记账运行中，点击返回应用")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pi)
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(-1) // Notification.PRIORITY_LOW
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService("notification") as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, "后台保活", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "保持应用在后台运行，避免自动记账被系统回收"
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
    }
}

/**
 * 自动记账核心引擎：
 * 候选App过滤 -> 页面黑名单 -> "支付成功"标记解析 -> 单号优先的去重 -> 底部确认弹层。
 * 金额按"锚点标签 > 独立大字金额 > 上下文扫描"三级择取，余额/额度/优惠类数字不进候选。
 * 数据全部保存在本机（SQLite 存账单），不经任何服务端。
 */
class MuyianBillCore private constructor() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val PREFS_NAME = "muyian_bill"
        private const val KEY_DEDUP = "capture_dedup"
        private const val KEY_ORDER_DEDUP = "order_dedup"
        private const val KEY_KEEP_ALIVE = "keep_alive_enabled"
        private const val KEY_CARD_THEME = "card_theme_color"
        /** 底部弹层默认主题色：悟空黄（与前端 --c-primary 一致） */
        private val DEFAULT_CARD_THEME = 0xFFF7A600.toInt()

        /** 弹层队列与去重用的内存状态：不落盘，进程结束即丢弃 */
        private val sheetLock = Any()
        private val sheetQueue = ArrayList<JSONObject>()
        private val recentBills = ArrayList<JSONObject>()
        private val sheetHandler = Handler(Looper.getMainLooper())
        private var sheetShowing = false
        /** 建表/补列只在本进程首次开库时执行一次，避免每次查询都抛 3 次 ALTER 异常 */
        private val dbLock = Any()
        @Volatile
        private var schemaReady = false
        private const val DB_NAME = "muyian.db"
        private const val CAPTURE_DEDUP_WINDOW_MS = 5 * 60 * 1000L
        /** 单号是交易唯一凭证：同单号 24 小时内不再弹层 */
        private const val ORDER_DEDUP_WINDOW_MS = 24 * 3600 * 1000L
        private const val BILL_DEDUP_WINDOW_MS = 2 * 60 * 1000L
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

        /** App 启动时调用：清掉旧版本遗留的草稿/悬浮卡状态，再拉起保活服务 */
        @JvmStatic
        fun init(context: Context) {
            // 旧版本用 SharedPreferences 存待确认草稿与通知跳转 token，现已整体移除
            try {
                prefs(context).edit()
                    .remove("pending_drafts")
                    .remove("pending_open_token")
                    .remove("overlay_enabled")
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "clean legacy prefs failed", e)
            }
            // 旧的"记账待确认"通知渠道一并删掉，避免通知设置里残留
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val nm = context.getSystemService("notification") as NotificationManager
                    nm.deleteNotificationChannel("bill_pending")
                }
            } catch (e: Exception) {
                Log.w(TAG, "delete legacy channel failed", e)
            }
            // 默认开启后台保活；用户明确关闭过则不再拉起
            if (isKeepAliveEnabled(context)) {
                MuyianKeepAliveService.start(context)
            }
        }

        /** 后台保活默认开启：自动记账依赖进程存活，用户可在设置页关闭 */
        @JvmStatic
        fun isKeepAliveEnabled(context: Context): Boolean {
            return try {
                prefs(context).getBoolean(KEY_KEEP_ALIVE, true)
            } catch (e: Exception) {
                true
            }
        }

        /** 开关后台保活前台服务，返回是否设置成功 */
        @JvmStatic
        fun setKeepAliveEnabled(context: Context, enabled: Boolean): Boolean {
            return try {
                prefs(context).edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
                if (enabled) MuyianKeepAliveService.start(context) else MuyianKeepAliveService.stop(context)
                true
            } catch (e: Exception) {
                Log.w(TAG, "set keep alive failed", e)
                false
            }
        }

        /**
         * MIUI/HyperOS 的"后台弹出界面"开关（对应系统的"显示在其他应用上层"）。
         * 确认弹层是 Activity，不用这个权限绘制任何窗口；但 HyperOS 把"后台启动 Activity"
         * 绑在这个 appop 上，未允许时 startActivity 会被 ActivityTaskManager 直接 Abort。
         */
        @JvmStatic
        fun hasOverlayPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 23) return true
            return try {
                Settings.canDrawOverlays(context)
            } catch (e: Exception) {
                false
            }
        }

        /** 打开系统的"显示在其他应用上层"授权页 */
        @JvmStatic
        fun requestOverlayPermission(context: Context): Boolean {
            return try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "open overlay settings failed", e)
                false
            }
        }

        /**
         * 底部确认弹层的主题色：Activity 不认前端 CSS 变量，由页面切换主题时传字面色进来。
         * color 只接受 #RRGGBB，非法值返回 false 并保留旧色。
         */
        @JvmStatic
        fun setCardThemeColor(context: Context, color: String): Boolean {
            val parsed = try {
                Color.parseColor(if (color.startsWith("#")) color else "#$color")
            } catch (e: IllegalArgumentException) {
                null
            }
            if (parsed == null) return false
            return try {
                prefs(context).edit().putInt(KEY_CARD_THEME, parsed).apply()
                true
            } catch (e: Exception) {
                Log.w(TAG, "set card theme failed", e)
                false
            }
        }

        @JvmStatic
        fun getCardThemeColor(context: Context): Int {
            return try {
                prefs(context).getInt(KEY_CARD_THEME, DEFAULT_CARD_THEME)
            } catch (e: Exception) {
                DEFAULT_CARD_THEME
            }
        }

        /** 电池优化白名单状态：true 表示系统不会因省电策略限制本应用后台 */
        @JvmStatic
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 23) return true
            return try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 请求把本应用加入电池优化白名单，返回 {"success":bool,"granted":bool}。
         * 优先弹系统的"不限制"确认框；部分 ROM 不支持该 Intent 时退化为电池优化列表页。
         */
        @JvmStatic
        fun requestIgnoreBatteryOptimizations(context: Context): String {
            val result = JSONObject()
            if (Build.VERSION.SDK_INT < 23 || isIgnoringBatteryOptimizations(context)) {
                result.put("success", true).put("granted", true)
                return result.toString()
            }
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                result.put("success", true).put("granted", false)
                return result.toString()
            } catch (e: Exception) {
                Log.w(TAG, "request ignore battery optimizations failed", e)
            }
            return try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                result.put("success", true).put("granted", false).put("fallback", true)
                result.toString()
            } catch (e: Exception) {
                Log.w(TAG, "open battery optimization settings failed", e)
                result.put("success", false).put("granted", false).toString()
            }
        }

        /** 打开本应用系统详情页：国产 ROM 的自启动/后台管理入口通常在这里 */
        @JvmStatic
        fun openAppSettings(context: Context): Boolean {
            return try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "open app settings failed", e)
                false
            }
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

        /** App 级通知开关（Android 13+ 的 POST_NOTIFICATIONS 也体现在这里）；异常时按已开启处理，避免误报 */
        @JvmStatic
        fun areNotificationsEnabled(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT < 24) true
                else {
                    val nm = context.getSystemService("notification") as NotificationManager
                    nm.areNotificationsEnabled()
                }
            } catch (e: Exception) {
                true
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
            val parsed = parseScreenBill(pkg, lines) ?: run {
                result.put("accepted", false).put("reason", "not a payment page")
                return result.toString()
            }

            val money = parsed.getDouble("money")
            val bankName = parsed.getString("bankName")
            val orderNo = parsed.optString("orderNo")
            val now = System.currentTimeMillis()

            if (!force) {
                // 单号去重：同订单号 24 小时内只弹一次（页面重复渲染、结果页回看都靠它）
                if (orderNo.isNotEmpty() && isDuplicateOrder(context, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate order")
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

            val merchant = extractScreenMerchant(lines)
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

            presentBill(context, bill)

            result.put("accepted", true).put("bill", bill)
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

            if (!force) {
                // 单号去重：同订单号 24 小时内只弹一次（通知更新、screen/notify 双路都靠它）
                if (orderNo.isNotEmpty() && isDuplicateOrder(context, orderNo)) {
                    result.put("accepted", false).put("reason", "duplicate order")
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

            val merchant = extractNotificationMerchant(text, subText, bigText, lines)
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

            presentBill(context, bill)

            result.put("accepted", true).put("bill", bill)
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
                if (symbol.matches()) return toAmount(symbol.group(1))
                if (bare <= 0.0 && i < 15 && !DATE_LINE.matcher(line).find()) {
                    val b = BARE_DECIMAL_LINE.matcher(line)
                    if (b.matches()) bare = toAmount(b.group(1))
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
            val db = openDb(context)
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
                // 结尾用二元表达式而不是 !km.isKeyguardLocked：UTS 的 Kotlin 语法检查会在"以 ! 开头的表达式"上报错
                pm.isInteractive && km.isKeyguardLocked == false
            } catch (e: Exception) {
                true
            }
        }

        /** 把一笔解析结果交给底部确认弹层；弹层已经开着时先进内存队列 */
        private fun presentBill(context: Context, bill: JSONObject) {
            synchronized(sheetLock) {
                recentBills.add(bill)
                while (recentBills.size > 20) recentBills.removeAt(0)
                while (sheetQueue.size >= MAX_SHEETS) sheetQueue.removeAt(0)
                sheetQueue.add(bill)
            }
            sheetHandler.post { showNextSheet(context.applicationContext) }
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
            if (!isScreenInteractive(context)) {
                // 熄屏/锁屏时 Activity 无法显示在支付页上方，这一笔直接放弃（不再有草稿/通知兜底）
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
            var started = false
            try {
                context.startActivity(intent)
                started = true
            } catch (e: Exception) {
                Log.e(TAG, "start bill sheet failed", e)
            }
            if (!started) {
                onSheetClosed(context)
                return
            }
            // 后台启动 Activity 被 ROM 静默拦截时不会抛异常：等一会还没起来就放行队列
            sheetHandler.postDelayed({
                if (BillSheetActivity.isAlive() == false) {
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
            val db = openDb(context)
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
            val db = openDb(context)
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

        private fun openDb(context: Context): SQLiteDatabase {
            val db = SQLiteDatabase.openOrCreateDatabase(
                context.getFilesDir().getAbsolutePath() + "/" + DB_NAME, null
            )
            if (schemaReady) return db
            synchronized(dbLock) {
                if (!schemaReady) {
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
                    // 订单/交易单号：单号去重与事后核对用，旧库补列
                    try {
                        db.execSQL("alter table bills add column orderNo text")
                    } catch (e: Exception) {
                        // column already exists
                    }
                    schemaReady = true
                }
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
            val orderIdx = cursor.getColumnIndex("orderNo")
            o.put("reimb", if (reimbIdx >= 0) cursor.getInt(reimbIdx) else 0)
            o.put("starred", if (starredIdx >= 0) cursor.getInt(starredIdx) else 0)
            o.put("orderNo", if (orderIdx >= 0) (cursor.getString(orderIdx) ?: "") else "")
            return o
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, 0) // Context.MODE_PRIVATE
    }
}


/**
 * 支付后的底部确认弹层。
 *
 * 用透明 Activity 代替悬浮窗：不需要 SYSTEM_ALERT_WINDOW，由无障碍服务直接拉起
 * （AccessibilityService 属于系统绑定服务，不受"后台禁止启动 Activity"的限制）。
 * 布局全部用 Android 框架 View，不引入任何 UI 库；分类仍然只认账单里的 costType 字符串。
 * 弹层是唯一的确认入口：用户点保存才写库，取消/返回/超时/熄屏都直接丢弃这一笔。
 */
class BillSheetActivity : Activity() {

    companion object {
        const val EXTRA_BILL = "bill_json"
        const val EXTRA_CATS = "cats_json"
        const val EXTRA_ACCENT = "accent_color"
        /** 无人操作时自动收起，避免弹层一直压在支付页上 */
        private const val TIMEOUT_MS = 60 * 1000L

        @Volatile
        private var alive = false

        fun isAlive(): Boolean {
            return alive
        }
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var billJson = ""
    private var income = false
    private var costType = ""
    private var accent = 0xFFF7A600.toInt()
    private var accentTint = 0
    private var amountInput: EditText? = null
    private var remarkInput: EditText? = null
    private var sheetHost: View? = null
    private var dimView: View? = null
    private var outChip: TextView? = null
    private var inChip: TextView? = null
    private val categoryChips = ArrayList<Pair<TextView, String>>()
    private var closing = false
    private var screenReceiver: BroadcastReceiver? = null
    private val timeoutTask = Runnable { closeSheet() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
        val raw = intent?.getStringExtra(EXTRA_BILL) ?: ""
        if (raw.isEmpty()) {
            finish()
            return
        }
        val parsed = try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
        if (parsed == null) {
            finish()
            return
        }
        billJson = raw
        income = parsed.optDouble("money", 0.0) >= 0
        costType = parsed.optString("costType")
        accent = intent?.getIntExtra(EXTRA_ACCENT, 0xFFF7A600.toInt()) ?: 0xFFF7A600.toInt()
        accentTint = lighten(accent, 0.86f)

        // 透明窗口：自己画遮罩，关掉系统自带的 dim
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        val cats = parseCats(intent?.getStringExtra(EXTRA_CATS) ?: "[]")
        setContentView(buildRoot(parsed, cats))
        playEnter()
        uiHandler.postDelayed(timeoutTask, TIMEOUT_MS)
        registerScreenOff()
    }

    override fun onBackPressed() {
        closeSheet()
    }

    override fun onDestroy() {
        alive = false
        uiHandler.removeCallbacks(timeoutTask)
        unregisterScreenOff()
        super.onDestroy()
        try {
            MuyianBillCore.onSheetClosed(applicationContext)
        } catch (e: Exception) {
            Log.w("MuyianBill", "notify sheet closed failed", e)
        }
    }

    private fun parseCats(raw: String): ArrayList<String> {
        val list = ArrayList<String>()
        try {
            val array = JSONArray(raw)
            var i = 0
            while (i < array.length()) {
                val name = array.optString(i, "")
                if (name.isNotEmpty()) list.add(name)
                i++
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun buildRoot(data: JSONObject, cats: List<String>): View {
        val root = FrameLayout(this)

        val dim = View(this)
        dim.setBackgroundColor(Color.BLACK)
        dim.alpha = 0f
        dimView = dim
        root.addView(dim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val scroller = ScrollView(this)
        scroller.isFillViewport = false
        sheetHost = scroller

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(14), dp(20), dp(22))
        box.background = roundedTop(Color.WHITE, 22)
        box.elevation = dp(12).toFloat()
        scroller.addView(box, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        val handle = View(this)
        handle.background = rounded(0xFFE3E5EA.toInt(), 2)
        val handleLp = LinearLayout.LayoutParams(dp(36), dp(4))
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.bottomMargin = dp(14)
        box.addView(handle, handleLp)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "自动识别到一笔账单"
        title.textSize = 15f
        title.setTextColor(0xFF1D2129.toInt())
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(this)
        close.text = "×"
        close.textSize = 22f
        close.setTextColor(0xFF8A90A0.toInt())
        close.setPadding(dp(12), 0, 0, dp(2))
        close.setOnClickListener { closeSheet() }
        header.addView(close)
        box.addView(header)

        val meta = TextView(this)
        val bank = data.optString("bankName")
        val clock = data.optString("clock")
        meta.text = if (bank.isEmpty()) clock else bank + " · " + clock
        meta.textSize = 12f
        meta.setTextColor(0xFF8A90A0.toInt())
        meta.setPadding(0, dp(2), 0, dp(10))
        box.addView(meta)

        val amountRow = LinearLayout(this)
        amountRow.orientation = LinearLayout.HORIZONTAL
        amountRow.gravity = Gravity.CENTER_VERTICAL
        amountRow.setPadding(0, dp(4), 0, dp(6))
        val symbol = TextView(this)
        symbol.text = "¥"
        symbol.textSize = 22f
        symbol.setTextColor(0xFF1D2129.toInt())
        amountRow.addView(symbol)
        val amount = EditText(this)
        amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        amount.setText(String.format(Locale.US, "%.2f", Math.abs(data.optDouble("money", 0.0))))
        amount.setSelection(amount.text.length)
        amount.textSize = 28f
        amount.setTextColor(typeColor(income))
        amount.setBackgroundColor(Color.TRANSPARENT)
        amount.setPadding(dp(4), 0, dp(4), 0)
        amountInput = amount
        amountRow.addView(amount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val out = chip("支出")
        val inc = chip("收入")
        outChip = out
        inChip = inc
        amountRow.addView(out)
        amountRow.addView(inc)
        out.setOnClickListener { setIncome(false) }
        inc.setOnClickListener { setIncome(true) }
        box.addView(amountRow)

        val catLabel = TextView(this)
        catLabel.text = "分类"
        catLabel.textSize = 12f
        catLabel.setTextColor(0xFF8A90A0.toInt())
        catLabel.setPadding(0, dp(10), 0, dp(6))
        box.addView(catLabel)

        val chipScroller = HorizontalScrollView(this)
        chipScroller.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipScroller.addView(chipRow)
        val names = ArrayList<String>()
        if (costType.isNotEmpty()) names.add(costType)
        for (c in cats) {
            if (!names.contains(c)) names.add(c)
        }
        if (names.isEmpty()) names.add(if (income) "收入" else "消费")
        for (name in names.take(10)) {
            val view = chip(name)
            categoryChips.add(view to name)
            chipRow.addView(view)
            view.setOnClickListener { selectCategory(name) }
        }
        box.addView(chipScroller)
        selectCategory(names[0])

        val remark = EditText(this)
        remark.hint = "备注（可选）"
        remark.setText(data.optString("merchant"))
        remark.textSize = 15f
        remark.setSingleLine(true)
        remark.setBackgroundColor(Color.TRANSPARENT)
        remark.setPadding(0, dp(12), 0, dp(12))
        remarkInput = remark
        box.addView(remark)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END
        buttons.setPadding(0, dp(8), 0, 0)
        val cancel = TextView(this)
        cancel.text = "取消"
        cancel.textSize = 15f
        cancel.setTextColor(0xFF8A90A0.toInt())
        cancel.setPadding(dp(22), dp(10), dp(22), dp(10))
        cancel.background = rounded(0xFFF4F6FA.toInt(), 16)
        cancel.setOnClickListener { closeSheet() }
        buttons.addView(cancel)
        val save = TextView(this)
        save.text = "保存"
        save.textSize = 15f
        save.setTextColor(Color.WHITE)
        save.setPadding(dp(28), dp(10), dp(28), dp(10))
        save.background = rounded(accent, 16)
        save.setOnClickListener { saveBill() }
        val saveLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        saveLp.leftMargin = dp(10)
        buttons.addView(save, saveLp)
        box.addView(buttons)

        val sheetLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        sheetLp.gravity = Gravity.BOTTOM
        root.addView(scroller, sheetLp)

        setIncome(income)
        return root
    }

    private fun saveBill() {
        val text = amountInput?.text?.toString() ?: ""
        val value = try {
            text.trim().toDouble()
        } catch (e: NumberFormatException) {
            0.0
        }
        if (value <= 0) {
            Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = MuyianBillCore.saveSheetBill(
            this, billJson, text, income, costType,
            remarkInput?.text?.toString()?.trim() ?: ""
        )
        if (ok == false) {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        closeSheet()
    }

    private fun playEnter() {
        val host = sheetHost ?: return
        host.post {
            val height = host.height.toFloat()
            if (height <= 0f) return@post
            host.translationY = height
            host.animate().translationY(0f).setDuration(240).start()
            dimView?.animate()?.alpha(0.35f)?.setDuration(240)?.start()
        }
    }

    private fun closeSheet() {
        if (closing) return
        closing = true
        uiHandler.removeCallbacks(timeoutTask)
        val host = sheetHost
        val dim = dimView
        if (host != null) {
            host.animate().translationY(host.height.toFloat()).setDuration(180)
                .withEndAction { finish() }.start()
            uiHandler.postDelayed({ finish() }, 260)
        } else {
            finish()
        }
        dim?.animate()?.alpha(0f)?.setDuration(180)?.start()
    }

    private fun registerScreenOff() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == Intent.ACTION_SCREEN_OFF) closeSheet()
            }
        }
        try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenReceiver = receiver
        } catch (e: Exception) {
            // 少数 ROM 限制动态注册，忽略即可
        }
    }

    private fun unregisterScreenOff() {
        val receiver = screenReceiver ?: return
        screenReceiver = null
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun typeColor(value: Boolean): Int {
        return if (value) 0xFF22B877.toInt() else 0xFFF0655E.toInt()
    }

    private fun setIncome(value: Boolean) {
        income = value
        amountInput?.setTextColor(typeColor(value))
        outChip?.let { styleTypeChip(it, value == false) }
        inChip?.let { styleTypeChip(it, value) }
    }

    private fun styleTypeChip(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) 0xFF1D2129.toInt() else 0xFF8A90A0.toInt())
        view.background = rounded(if (selected) accentTint else 0xFFF4F6FA.toInt(), 14)
    }

    private fun selectCategory(name: String) {
        costType = name
        for ((view, value) in categoryChips) {
            val selected = value == name
            view.setTextColor(if (selected) accent else 0xFF5B6170.toInt())
            view.background = rounded(if (selected) accentTint else 0xFFF4F6FA.toInt(), 14)
        }
    }

    private fun chip(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = 13f
        view.setTextColor(0xFF5B6170.toInt())
        view.background = rounded(0xFFF4F6FA.toInt(), 14)
        view.setPadding(dp(12), dp(7), dp(12), dp(7))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = dp(8)
        view.layoutParams = lp
        return view
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /** 把强调色向白色混合，得到选中项的淡底 */
    private fun lighten(color: Int, ratio: Float): Int {
        fun ch(v: Int): Int = (v + (255 - v) * ratio).toInt()
        return Color.rgb(ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(radiusDp).toFloat()
        return drawable
    }

    /** 只有顶部两角圆角的白色面板 */
    private fun roundedTop(color: Int, radiusDp: Int): GradientDrawable {
        val r = dp(radiusDp).toFloat()
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
        return drawable
    }
}
