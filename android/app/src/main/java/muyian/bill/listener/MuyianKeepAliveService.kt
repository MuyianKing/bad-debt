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
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION") // 兼容 API < 26 的旧版通知构建分支
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
