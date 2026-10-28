package muyian.bill.listener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 「待确认」通知：弹层拉不起来时的唯一兜底入口。
 * 通知本身不写库，点击后仍进入 BillSheetActivity，由用户点保存才入账。
 */
internal object PendingNotices {
    private const val TAG = "MuyianBill"
    private const val PREFS_NAME = "muyian_bill"
    private const val KEY_NOTICE_SEQ = "pending_notice_seq"
    private const val CHANNEL_PENDING = "bill_pending"
    private const val PENDING_NOTICE_BASE_ID = 20000
    private const val PENDING_NOTICE_TTL_MS = 24 * 3600 * 1000L

    private val lock = Any()
    private var seqCache = 0

    /** 生成互不冲突的通知 ID：正常拉起的弹层也会预占一个，供 Activity 取消兜底通知。 */
    fun nextId(context: Context): Int {
        synchronized(lock) {
            var seq = seqCache
            try {
                seq = prefs(context).getInt(KEY_NOTICE_SEQ, seqCache)
            } catch (_: Exception) {
            }
            seq += 1
            if (seq > 100000) seq = 1
            seqCache = seq
            try {
                prefs(context).edit().putInt(KEY_NOTICE_SEQ, seq).apply()
            } catch (_: Exception) {
            }
            return PENDING_NOTICE_BASE_ID + seq
        }
    }

    /** @return false 表示通知没发出去（无权限/系统异常），调用方应保留待确认队列以便下次恢复。 */
    fun post(
        context: Context,
        bill: JSONObject,
        noticeId: Int,
        cats: List<String>,
        accent: Int
    ): Boolean {
        try {
            createChannel(context)
            val nm = context.getSystemService("notification") as NotificationManager
            val catsJson = JSONArray()
            for (c in cats) catsJson.put(c)
            val intent = Intent(context, BillSheetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.putExtra(BillSheetActivity.EXTRA_BILL, bill.toString())
            intent.putExtra(BillSheetActivity.EXTRA_CATS, catsJson.toString())
            intent.putExtra(BillSheetActivity.EXTRA_ACCENT, accent)
            intent.putExtra(BillSheetActivity.EXTRA_NOTICE_ID, noticeId)
            val pi = PendingIntent.getActivity(
                context, noticeId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(noticeId, build(context, bill, pi))
            return true
        } catch (e: Exception) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时 notify 会静默失败，只能记日志
            Log.w(TAG, "post pending notice failed", e)
            return false
        }
    }

    fun cancel(context: Context, noticeId: Int) {
        if (noticeId <= 0) return
        try {
            val nm = context.getSystemService("notification") as NotificationManager
            nm.cancel(noticeId)
        } catch (e: Exception) {
            Log.w(TAG, "cancel pending notice failed", e)
        }
    }

    fun ensureChannel(context: Context) = createChannel(context)

    /** 待确认通知渠道：IMPORTANCE_HIGH 才能在锁屏/后台弹出提醒。 */
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val nm = context.getSystemService("notification") as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_PENDING, "待确认账单", NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "确认弹层无法弹出时提醒你补记这一笔；点开后仍进确认弹层"
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.w(TAG, "create pending channel failed", e)
        }
    }

    /** 锁屏上只显示「有待确认账单」，金额放在 private 版本里，避免锁屏泄露。 */
    @Suppress("DEPRECATION")
    private fun build(context: Context, bill: JSONObject, pi: PendingIntent): Notification {
        val money = bill.optDouble("money", 0.0)
        val typeText = if (money >= 0) "收入" else "支出"
        val amountText = String.format(Locale.US, "%.2f", Math.abs(money))
        val bank = bill.optString("bankName")
        val clock = bill.optString("clock")
        var text = "¥" + amountText
        if (bank.isNotEmpty()) text = text + " · " + bank
        if (clock.isNotEmpty()) text = text + " · " + clock

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_PENDING)
        } else {
            Notification.Builder(context)
        }
        builder.setContentTitle("有一笔待确认" + typeText)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(context.applicationInfo.icon)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)

        val publicBuilder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_PENDING)
        } else {
            Notification.Builder(context)
        }
        publicBuilder.setContentTitle("有一笔待确认账单")
            .setContentText("点此打开确认弹层")
            .setSmallIcon(context.applicationInfo.icon)
            .setAutoCancel(true)
            .setContentIntent(pi)
        builder.setPublicVersion(publicBuilder.build())

        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_HIGH)
            builder.setDefaults(Notification.DEFAULT_ALL)
        } else {
            builder.setTimeoutAfter(PENDING_NOTICE_TTL_MS)
        }
        return builder.build()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)
}
