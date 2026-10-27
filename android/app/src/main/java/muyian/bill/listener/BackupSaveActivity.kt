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
 * 备份导出的"另存为"中转 Activity：透明壳，拉起系统 ACTION_CREATE_DOCUMENT，
 * 把进程内暂存的备份 JSON 写到用户选的位置（内容不走 Intent，避免大备份超过 Binder 限制）。
 */
class BackupSaveActivity : Activity() {

    companion object {
        const val EXTRA_NAME = "save_name"
        private const val REQ_SAVE_FILE = 4202

        @Volatile
        private var alive = false

        fun isAlive(): Boolean {
            return alive
        }
    }

    private var suggestedName = "muyian_backup.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
        val rawName = intent?.getStringExtra(EXTRA_NAME) ?: ""
        if (rawName.isNotEmpty()) suggestedName = rawName
        if (MuyianBillCore.backupContent().isEmpty()) {
            MuyianBillCore.onBackupSaveError("no content")
            finish()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_SAVE_FILE)
        } catch (e: Exception) {
            Log.w("MuyianBill", "no save document ui", e)
            MuyianBillCore.onBackupSaveError("no file picker")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SAVE_FILE) {
            finish()
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            MuyianBillCore.onBackupSaveCancelled()
            finish()
            return
        }
        try {
            val content = MuyianBillCore.backupContent()
            val os = contentResolver.openOutputStream(uri)
            if (os == null) {
                MuyianBillCore.onBackupSaveError("write failed")
            } else {
                try {
                    os.write(content.toByteArray(Charsets.UTF_8))
                    os.flush()
                } finally {
                    os.close()
                }
                MuyianBillCore.onBackupSaved(queryName(uri))
            }
        } catch (e: Exception) {
            Log.w("MuyianBill", "write backup file failed", e)
            MuyianBillCore.onBackupSaveError("write failed")
        }
        finish()
    }

    /** 取显示文件名，拿不到就用建议名 */
    private fun queryName(uri: Uri): String {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                try {
                    val idx = cursor.getColumnIndex("_display_name")
                    if (idx >= 0 && cursor.moveToFirst()) {
                        val v = cursor.getString(idx)
                        if (v != null && v.isNotEmpty()) return v
                    }
                } finally {
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return suggestedName
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }
}
