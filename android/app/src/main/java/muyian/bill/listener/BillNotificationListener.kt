package muyian.bill.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 系统通知监听服务（辅助渠道）：把通知的文本、渠道、发送方 key 一起交给 CaptureEngine。
 * 只有渠道/发送方命中支付来源的才可能成为账单，好友聊天里的"我支付了100元"会被拦掉。
 */
class BillNotificationListener : NotificationListenerService() {

    /** 通知回调在主线程：解析 + SQLite 事务必须挪到后台，避免卡住通知栏 */
    private val executor = Executors.newSingleThreadExecutor()

    override fun onDestroy() {
        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val pkg = sbn.packageName ?: return
        try {
            val root = JSONObject()
            root.put("pkg", pkg)
            root.put("channelId", notification.channelId ?: "")
            root.put("category", notification.category ?: "")
            root.put("key", sbn.key ?: "")
            root.put(
                "groupSummary",
                (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )
            root.put(
                "ongoing",
                (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            )
            root.put("title", extras.getCharSequence("android.title")?.toString() ?: "")
            root.put("text", extras.getCharSequence("android.text")?.toString() ?: "")
            root.put("subText", extras.getCharSequence("android.subText")?.toString() ?: "")
            root.put("bigText", extras.getCharSequence("android.bigText")?.toString() ?: "")
            root.put("postTime", sbn.postTime)
            val lines = JSONArray()
            val textLines = extras.getCharSequenceArray("android.textLines")
            if (textLines != null) {
                for (line in textLines) {
                    if (line != null) lines.put(line.toString())
                }
            }
            root.put("lines", lines)
            val snapshot = root.toString()
            val context = applicationContext
            executor.execute {
                try {
                    MuyianBillCore.handleNotificationSnapshot(context, snapshot, false)
                } catch (e: Exception) {
                    Log.e("MuyianBill", "handle notification failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MuyianBill", "build notification snapshot failed", e)
        }
    }
}
