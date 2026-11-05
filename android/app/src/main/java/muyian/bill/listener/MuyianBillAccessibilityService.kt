package muyian.bill.listener

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.HashSet
import java.util.concurrent.Executors

/**
 * 无障碍服务：用户在系统"无障碍"中开启后由系统 bind。
 * 采集当前候选 App 窗口的文本 + 位置 + 字号，交给 CaptureEngine 解析。
 * 采集层不再做任何金额/页面语义判断。
 */
class MuyianBillAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MuyianBill"
        private const val THROTTLE_MS = 600L
        /** 同签名只在短时间内跳过：账本才是去重边界，采集层不能永久吞掉第二笔同额交易 */
        private const val SIGNATURE_TTL_MS = 2_000L
        private const val MAX_NODES = 600
        private const val MAX_DEPTH = 30
        private const val ALIPAY_PKG = "com.eg.android.AlipayGphone"
        /** 支付宝成功页渐进渲染：快慢两遍解析，签名去重保证已稳定的页面不会重复解析 */
        private val ALIPAY_PASS_DELAYS = longArrayOf(160L, 450L)
        private val executor = Executors.newSingleThreadExecutor()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lastEventAt = HashMap<String, Long>()
    private val lastSignature = HashMap<String, String>()
    private val lastSignatureAt = HashMap<String, Long>()
    private val pendingPages = HashSet<String>()
    private var alipayPassesScheduled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务被系统重建时，结果页可能早已渲染完成、不会再有事件：主动补采一次
        handler.postDelayed({
            val pkg = rootInActiveWindow?.packageName?.toString() ?: return@postDelayed
            if (pkg != packageName && MuyianBillCore.isCandidateApp(applicationContext, pkg)) {
                processWindow(pkg)
            }
        }, 800)
        // 进程被杀后服务重绑，主进程不一定打开过：把没处理完的待确认队列捞回来
        handler.postDelayed({
            try {
                MuyianBillCore.restoreCaptureQueue(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "restore capture queue failed", e)
            }
        }, 400)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // 记路线只认窗口切换：宿主 App → 支付 App → 宿主 App 是判定"同一笔支付"的关键
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            MuyianBillCore.noteForeground(applicationContext, pkg)
        }
        if (!MuyianBillCore.isCandidateApp(applicationContext, pkg)) return

        val now = System.currentTimeMillis()
        // 支付宝结果页是渐进渲染的：固定跑快慢两遍，不走 600ms 节流
        if (pkg == ALIPAY_PKG) {
            scheduleAlipayPasses()
            return
        }
        val last = lastEventAt[pkg] ?: 0L
        if (now - last >= THROTTLE_MS) {
            lastEventAt[pkg] = now
            processWindow(pkg)
        } else if (pendingPages.add(pkg)) {
            // 节流窗口内的事件不能直接丢：页面可能恰好在窗口内渲染完成
            handler.postDelayed(Runnable {
                pendingPages.remove(pkg)
                lastEventAt[pkg] = System.currentTimeMillis()
                processWindow(pkg)
            }, THROTTLE_MS)
        }
    }

    private fun scheduleAlipayPasses() {
        if (alipayPassesScheduled) return
        alipayPassesScheduled = true
        val lastDelay = ALIPAY_PASS_DELAYS[ALIPAY_PASS_DELAYS.size - 1]
        for (delay in ALIPAY_PASS_DELAYS) {
            handler.postDelayed({
                if (delay == lastDelay) alipayPassesScheduled = false
                processWindow(ALIPAY_PKG)
            }, delay)
        }
    }

    private fun processWindow(pkg: String) {
        val root = findRoot(pkg) ?: return
        val items = ArrayList<UiText>()
        collectTexts(root, items, 0, intArrayOf(MAX_NODES), HashSet())
        if (pkg == "com.tencent.mm" && items.isEmpty()) {
            // 微信 LiteApp 支付页：内容只在 content-desc，且节点被标记为不可见。
            // 只在这种"可见节点为空"的情况下补收，避免把普通微信页面的隐藏文本带进来。
            collectContentDescs(root, items, 0, intArrayOf(MAX_NODES), HashSet())
        }
        if (items.isEmpty()) return
        items.sortWith(compareBy<UiText> { it.top }.thenBy { it.left })

        val now = System.currentTimeMillis()
        val signature = pkg + "#" + CaptureText.hash(
            items.joinToString("|") { it.text + "@" + it.top }
        )
        val lastAt = lastSignatureAt[pkg] ?: 0L
        if (signature == lastSignature[pkg] && now - lastAt < SIGNATURE_TTL_MS) return
        lastSignature[pkg] = signature
        lastSignatureAt[pkg] = now

        val snapshot = buildSnapshot(items, now)
        executor.execute(Runnable {
            try {
                MuyianBillCore.handleScreenSnapshot(
                    applicationContext, pkg, snapshot, false
                )
            } catch (e: Exception) {
                Log.e(TAG, "handle screen failed", e)
            }
        })
    }

    /** 优先从交互窗口里选包名匹配且层级最高的窗口，避免切回宿主时 root 还停在支付 App。 */
    private fun findRoot(pkg: String): AccessibilityNodeInfo? {
        // 微信 LiteApp/支付页的活动窗口内容只挂在 rootInActiveWindow 上，
        // windows 里同名窗口的根节点可能是空的；先试当前活动窗口。
        val active = rootInActiveWindow
        if (active?.packageName?.toString() == pkg) return active
        var best: AccessibilityNodeInfo? = null
        var bestLayer = Int.MIN_VALUE
        try {
            for (window in windows) {
                val root = window.root ?: continue
                if (root.packageName?.toString() != pkg) continue
                // windows 不保证顺序；账单详情可能叠在账单列表上，必须按 layer 取最上层
                if (window.layer > bestLayer) {
                    bestLayer = window.layer
                    best = root
                }
            }
        } catch (_: Exception) {
            // 部分 ROM 不返回 windows，退回 rootInActiveWindow
        }
        if (best == null) {
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == pkg) best = root
        }
        return best
    }

    private fun collectTexts(
        node: AccessibilityNodeInfo?, out: ArrayList<UiText>, depth: Int,
        budget: IntArray, seen: HashSet<String>
    ) {
        if (node == null || depth > MAX_DEPTH || budget[0] <= 0) return
        if (node.isVisibleToUser) {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val value = if (!text.isNullOrBlank()) text else if (!desc.isNullOrBlank()) desc else ""
            if (value.isNotEmpty()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val key = value + "@" + rect.left + "," + rect.top
                if (seen.add(key)) {
                    out.add(
                        UiText(
                            text = value,
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            sizePx = rect.height(),
                            className = node.className?.toString() ?: "",
                            viewId = node.viewIdResourceName ?: "",
                            depth = depth
                        )
                    )
                    budget[0]--
                }
            }
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1, budget, seen)
            if (budget[0] <= 0) return
        }
    }

    /** 微信 LiteApp 支付页：文本只在 contentDescription 里，且节点不可见。 */
    private fun collectContentDescs(
        node: AccessibilityNodeInfo?, out: ArrayList<UiText>, depth: Int,
        budget: IntArray, seen: HashSet<String>
    ) {
        if (node == null || depth > MAX_DEPTH || budget[0] <= 0) return
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val key = desc + "@" + rect.left + "," + rect.top
            if (seen.add(key)) {
                out.add(
                    UiText(
                        text = desc,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                        sizePx = rect.height(),
                        className = node.className?.toString() ?: "",
                        viewId = node.viewIdResourceName ?: "",
                        depth = depth
                    )
                )
                budget[0]--
            }
        }
        for (i in 0 until node.childCount) {
            collectContentDescs(node.getChild(i), out, depth + 1, budget, seen)
            if (budget[0] <= 0) return
        }
    }

    private fun buildSnapshot(items: List<UiText>, now: Long): String {
        val root = JSONObject()
        root.put("eventType", 0)
        root.put("observedAt", now)
        root.put("windowTitle", "")
        val arr = JSONArray()
        for (item in items) {
            val o = JSONObject()
            o.put("t", item.text)
            o.put("l", item.left)
            o.put("top", item.top)
            o.put("r", item.right)
            o.put("b", item.bottom)
            o.put("s", item.sizePx)
            o.put("c", item.className)
            o.put("id", item.viewId)
            o.put("d", item.depth)
            arr.put(o)
        }
        root.put("items", arr)
        return root.toString()
    }

    override fun onInterrupt() {
    }
}
