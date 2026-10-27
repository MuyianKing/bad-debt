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
        // 记路线只认窗口切换：宿主 App → 支付 App → 宿主 App 是判定"同一笔支付"的关键
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            MuyianBillCore.noteForeground(applicationContext, pkg)
        }
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
        // 事件包名与当前窗口不一定一致：切回宿主 App 的一瞬间 root 可能还停在支付 App，
        // 直接采信会把支付宝结果页算成美团的结果页，进而重复弹层
        val rootPkg = root.packageName?.toString() ?: ""
        if (rootPkg != pkg) return
        val lines = ArrayList<String>()
        collectTexts(root, lines, 0, intArrayOf(MAX_NODES))
        if (Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
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
