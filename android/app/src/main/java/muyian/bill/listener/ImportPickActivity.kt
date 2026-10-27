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
 * 钱迹导入的文件选择中转 Activity：透明主题，拉起系统文件选择器（SAF），
 * 把选中的文件拷进应用私有目录（qianji_import.xlsx）后通知 MuyianBillCore，由页面侧轮询。
 * 通过 ContentResolver 读用户选中的 Uri，不需要任何存储权限。
 */
class ImportPickActivity : Activity() {

    companion object {
        const val EXTRA_KIND = "pick_kind"
        private const val REQ_PICK_FILE = 4201

        @Volatile
        private var alive = false

        fun isAlive(): Boolean {
            return alive
        }
    }

    private var kind = "xlsx"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
        kind = intent?.getStringExtra(EXTRA_KIND) ?: "xlsx"
        val isJson = kind == "json"
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        // 有些文件管理器会把类型标成 octet-stream，额外放开候选类型
        val mimes = if (isJson) {
            arrayOf("application/json", "text/plain", "application/octet-stream")
        } else {
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/zip",
                "application/octet-stream"
            )
        }
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_FILE)
        } catch (e: Exception) {
            Log.w("MuyianBill", "no file picker available", e)
            MuyianBillCore.onImportPickError("no file picker")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_FILE) {
            finish()
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            MuyianBillCore.onImportPickCancelled()
            finish()
            return
        }
        try {
            val targetName = if (kind == "json") "muyian_backup.json" else "qianji_import.xlsx"
            val out = File(getFilesDir(), targetName)
            val ins = contentResolver.openInputStream(uri)
            if (ins == null) {
                MuyianBillCore.onImportPickError("unreadable file")
            } else {
                val fos = FileOutputStream(out)
                try {
                    val buf = ByteArray(64 * 1024)
                    var n = ins.read(buf)
                    while (n >= 0) {
                        fos.write(buf, 0, n)
                        n = ins.read(buf)
                    }
                } finally {
                    fos.close()
                    ins.close()
                }
                MuyianBillCore.onImportPicked(out.getAbsolutePath(), queryName(uri))
            }
        } catch (e: Exception) {
            Log.w("MuyianBill", "copy picked file failed", e)
            MuyianBillCore.onImportPickError("copy failed")
        }
        finish()
    }

    /** 取显示文件名，拿不到就退回固定名 */
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
        return if (kind == "json") "muyian_backup.json" else "qianji_import.xlsx"
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }
}
