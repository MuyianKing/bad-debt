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
 * 支付后的底部确认弹层。
 *
 * 用透明 Activity 代替悬浮窗：不用 WindowManager 绘制窗口；但 MIUI/HyperOS 仍要求
 * SYSTEM_ALERT_WINDOW 才允许无障碍服务在后台启动 Activity。
 * 布局全部用 Android 框架 View，不引入任何 UI 库；分类仍然只认账单里的 costType 字符串。
 * 弹层是唯一的确认入口：用户点保存才写库；取消/返回/超时/弹层显示后熄屏都直接丢弃这一笔。
 * 弹层从一开始就拉不起来（熄屏/锁屏/ROM 拦截）时，MuyianBillCore 会发一条待确认通知兜底，
 * 点开仍回到这里确认，通知本身不写库。
 */
class BillSheetActivity : Activity() {

    companion object {
        const val EXTRA_BILL = "bill_json"
        const val EXTRA_CATS = "cats_json"
        const val EXTRA_ACCENT = "accent_color"
        const val EXTRA_NOTICE_ID = "notice_id"
        /** 无人操作时自动收起，避免弹层一直压在支付页上 */
        private const val TIMEOUT_MS = 60 * 1000L

        @Volatile
        private var alive = false

        /** 已经真正拉起过的通知 ID：启动看门狗用它区分"从未出现"和"出现后又被关掉" */
        private val startedNoticeIds = ArrayList<Int>()

        fun isAlive(): Boolean {
            return alive
        }

        fun markStarted(noticeId: Int) {
            if (noticeId <= 0) return
            synchronized(startedNoticeIds) {
                if (!startedNoticeIds.contains(noticeId)) startedNoticeIds.add(noticeId)
                while (startedNoticeIds.size > 64) startedNoticeIds.removeAt(0)
            }
        }

        fun wasStarted(noticeId: Int): Boolean {
            if (noticeId <= 0) return false
            var hit = false
            synchronized(startedNoticeIds) {
                hit = startedNoticeIds.contains(noticeId)
            }
            return hit
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
    private var noticeId = 0
    private var screenReceiver: BroadcastReceiver? = null
    private val timeoutTask = Runnable { closeSheet() }
    private val finishTask = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
        val raw = intent?.getStringExtra(EXTRA_BILL) ?: ""
        if (raw.isEmpty()) {
            finish()
            return
        }

        // 透明窗口：自己画遮罩，关掉系统自带的 dim
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= 21) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        val extraAccent = intent?.getIntExtra(EXTRA_ACCENT, 0xFFF7A600.toInt()) ?: 0xFFF7A600.toInt()
        val catsRaw = intent?.getStringExtra(EXTRA_CATS) ?: "[]"
        val extraNoticeId = intent?.getIntExtra(EXTRA_NOTICE_ID, 0) ?: 0
        if (!bindBill(raw, catsRaw, extraAccent, extraNoticeId)) {
            finish()
            return
        }
        uiHandler.postDelayed(timeoutTask, TIMEOUT_MS)
        registerScreenOff()
    }

    /** 绑定一笔账单并重建弹层内容：首次创建和通知点击换单共用 */
    private fun bindBill(raw: String, catsRaw: String, accentValue: Int, noticeIdValue: Int): Boolean {
        val parsed = try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
        if (parsed == null) return false
        billJson = raw
        income = parsed.optDouble("money", 0.0) >= 0
        costType = parsed.optString("costType")
        accent = accentValue
        accentTint = lighten(accent, 0.86f)
        noticeId = noticeIdValue
        if (noticeId > 0) {
            markStarted(noticeId)
            // 弹层已经出现，撤掉兜底通知并清掉"被系统拦截"标记
            MuyianBillCore.cancelPendingNotice(this, noticeId)
            MuyianBillCore.clearPopupBlocked(this)
        }
        val cats = parseCats(catsRaw)
        categoryChips.clear()
        setContentView(buildRoot(parsed, cats))
        playEnter()
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        val raw = intent.getStringExtra(EXTRA_BILL) ?: return
        val extraNoticeId = intent.getIntExtra(EXTRA_NOTICE_ID, 0)
        MuyianBillCore.cancelPendingNotice(this, extraNoticeId)
        // 当前弹层还在：放回队列，等这一笔处理完再弹，不覆盖用户正在编辑的内容
        MuyianBillCore.enqueueSheetBill(this, raw)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        closeSheet()
    }

    override fun onDestroy() {
        alive = false
        uiHandler.removeCallbacks(timeoutTask)
        uiHandler.removeCallbacks(finishTask)
        unregisterScreenOff()
        // 取消/超时/熄屏/保存都撤掉对应的兜底通知；取消即丢弃这一笔
        MuyianBillCore.cancelPendingNotice(applicationContext, noticeId)
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
            host.animate().translationY(host.height.toFloat()).setDuration(180).start()
            uiHandler.postDelayed(finishTask, 260)
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
