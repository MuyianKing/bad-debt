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
