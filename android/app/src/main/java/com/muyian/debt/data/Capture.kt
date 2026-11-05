package com.muyian.debt.data

import android.content.Context
import muyian.bill.listener.MuyianBillCore
import org.json.JSONObject

data class CaptureSettingsData(
    val recordTransfers: Boolean = false,
    val debugText: Boolean = false,
    val lowConfidence: Boolean = true
)

data class CaptureApp(
    val pkg: String,
    val label: String,
    val role: String,
    val enabled: Boolean,
    val builtin: Boolean
)

data class CaptureReasonCount(val reason: String, val label: String, val count: Int)

data class CaptureAppStat(
    val pkg: String = "",
    val app: String = "",
    val captured: Int = 0,
    val saved: Int = 0,
    val discarded: Int = 0,
    val rejected: Int = 0,
    val edited: Int = 0
)

data class CaptureKindStat(
    val kind: String = "",
    val label: String = "",
    val captured: Int = 0,
    val saved: Int = 0,
    val discarded: Int = 0,
    val rejected: Int = 0,
    val edited: Int = 0
)

data class CaptureReconcile(
    val screen: Int = 0,
    val notify: Int = 0,
    val matched: Int = 0,
    val screenOnly: Int = 0,
    val notifyOnly: Int = 0
)

data class CaptureSummary(
    val pending: Int = 0,
    val accepted: Int = 0,
    val saved: Int = 0,
    val discarded: Int = 0,
    val rejected: Int = 0,
    val merged: Int = 0,
    val amountEdited: Int = 0,
    val reasons: List<CaptureReasonCount> = emptyList(),
    val reconcile: CaptureReconcile = CaptureReconcile()
)

data class CaptureTrace(
    val id: Long,
    val at: Long,
    val pkg: String,
    val app: String,
    val source: String,
    val kind: String,
    val kindLabel: String,
    val result: String,
    val reason: String,
    val reasonLabel: String,
    val amount: Double,
    val direction: String,
    val channel: String,
    val confidence: Double,
    val needsReview: Boolean,
    val evidence: String,
    val text: String
)

data class CaptureReparse(
    val kindLabel: String = "",
    val reasonLabel: String = "",
    val amount: Double = 0.0,
    val direction: String = "",
    val merchant: String = "",
    val orderNo: String = "",
    val channel: String = "",
    val confidence: Double = 0.0,
    val needsReview: Boolean = false
) {
    fun summary(): String {
        if (reasonLabel.isNotEmpty() && amount <= 0.0) return "未识别：$reasonLabel"
        val sign = if (direction == "INCOME" || direction == "REFUND") "+" else "-"
        return buildString {
            append(kindLabel)
            if (amount > 0.0) append(" · $sign").append(String.format(java.util.Locale.US, "%.2f", amount))
            if (direction.isNotEmpty()) append(" · ").append(direction)
            if (channel.isNotEmpty()) append(" · ").append(channel)
            if (merchant.isNotEmpty()) append("\n商户：").append(merchant)
            if (orderNo.isNotEmpty()) append("\n单号：").append(orderNo)
            if (confidence > 0.0) {
                append("\n置信度：").append(String.format(java.util.Locale.US, "%.2f", confidence))
            }
            if (needsReview) append("（建议核对）")
            if (reasonLabel.isNotEmpty()) append("\n原因：").append(reasonLabel)
        }
    }
}

data class CaptureReplaySample(
    val app: String = "",
    val oldKindLabel: String = "",
    val newKindLabel: String = "",
    val oldAmount: Double = 0.0,
    val newAmount: Double = 0.0,
    val oldReasonLabel: String = "",
    val newReasonLabel: String = ""
)

data class CaptureReplaySummary(
    val total: Int = 0,
    val withText: Int = 0,
    val changed: Int = 0,
    val kindChanged: Int = 0,
    val amountChanged: Int = 0,
    val newlyParsed: Int = 0,
    val lostParse: Int = 0,
    val samples: List<CaptureReplaySample> = emptyList()
) {
    fun summary(): String {
        if (total == 0) return "没有可回放的识别记录"
        if (withText == 0) {
            return "共 $total 条记录，但没有保存页面文本。\n请先开启「记录页面文本（调试）」并触发几笔。"
        }
        val b = StringBuilder()
        b.append("回放 ").append(total).append(" 条，有文本 ").append(withText).append(" 条\n")
        b.append("变化 ").append(changed).append(" 条：类型 ").append(kindChanged)
            .append(" · 金额 ").append(amountChanged)
            .append(" · 新增可识别 ").append(newlyParsed)
            .append(" · 变为不可识别 ").append(lostParse)
        if (samples.isNotEmpty()) {
            b.append("\n\n示例：")
            for (s in samples.take(5)) {
                b.append("\n").append(s.app.ifEmpty { "未知应用" }).append("：")
                    .append(s.oldKindLabel.ifEmpty { "未知" }).append(" → ")
                    .append(s.newKindLabel.ifEmpty { "未知" })
                if (s.oldAmount > 0 || s.newAmount > 0) {
                    b.append(" · ").append(money(s.oldAmount)).append(" → ").append(money(s.newAmount))
                }
            }
        }
        return b.toString()
    }

    private fun money(v: Double): String =
        String.format(java.util.Locale.US, "%.2f", v)
}

data class CaptureDiagnostics(
    val summary: CaptureSummary = CaptureSummary(),
    val traces: List<CaptureTrace> = emptyList(),
    val apps: List<CaptureAppStat> = emptyList(),
    val kinds: List<CaptureKindStat> = emptyList(),
    val settings: CaptureSettingsData = CaptureSettingsData()
)

/** 自动记账诊断与 App 注册表的页面侧封装：页面不直接拼原生 JSON。 */
object Capture {
    fun load(context: Context, limit: Int = 200): CaptureDiagnostics {
        val raw = runCatching {
            MuyianBillCore.getCaptureDiagnostics(context, limit)
        }.getOrNull() ?: return CaptureDiagnostics()
        return try {
            val o = JSONObject(raw)
            val s = o.optJSONObject("summary") ?: JSONObject()
            val reasons = ArrayList<CaptureReasonCount>()
            val reasonArr = s.optJSONArray("reasons") ?: org.json.JSONArray()
            for (i in 0 until reasonArr.length()) {
                val r = reasonArr.optJSONObject(i) ?: continue
                reasons.add(
                    CaptureReasonCount(
                        r.optString("reason", ""),
                        r.optString("label", ""),
                        r.optInt("count", 0)
                    )
                )
            }
            val traces = ArrayList<CaptureTrace>()
            val arr = o.optJSONArray("traces") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                traces.add(
                    CaptureTrace(
                        id = t.optLong("id"),
                        at = t.optLong("at"),
                        pkg = t.optString("pkg", ""),
                        app = t.optString("app", ""),
                        source = t.optString("source", ""),
                        kind = t.optString("kind", ""),
                        kindLabel = t.optString("kindLabel", ""),
                        result = t.optString("result", ""),
                        reason = t.optString("reason", ""),
                        reasonLabel = t.optString("reasonLabel", ""),
                        amount = t.optDouble("amount", 0.0),
                        direction = t.optString("direction", ""),
                        channel = t.optString("channel", ""),
                        confidence = t.optDouble("confidence", 0.0),
                        needsReview = t.optBoolean("needsReview", false),
                        evidence = t.optString("evidence", ""),
                        text = t.optString("text", "")
                    )
                )
            }
            val appStats = ArrayList<CaptureAppStat>()
            val appArr = s.optJSONArray("apps") ?: org.json.JSONArray()
            for (i in 0 until appArr.length()) {
                val a = appArr.optJSONObject(i) ?: continue
                appStats.add(
                    CaptureAppStat(
                        pkg = a.optString("pkg", ""),
                        app = a.optString("app", ""),
                        captured = a.optInt("captured", 0),
                        saved = a.optInt("saved", 0),
                        discarded = a.optInt("discarded", 0),
                        rejected = a.optInt("rejected", 0),
                        edited = a.optInt("edited", 0)
                    )
                )
            }
            val kindStats = ArrayList<CaptureKindStat>()
            val kindArr = s.optJSONArray("kinds") ?: org.json.JSONArray()
            for (i in 0 until kindArr.length()) {
                val k = kindArr.optJSONObject(i) ?: continue
                kindStats.add(
                    CaptureKindStat(
                        kind = k.optString("kind", ""),
                        label = k.optString("kindLabel", ""),
                        captured = k.optInt("captured", 0),
                        saved = k.optInt("saved", 0),
                        discarded = k.optInt("discarded", 0),
                        rejected = k.optInt("rejected", 0),
                        edited = k.optInt("edited", 0)
                    )
                )
            }
            CaptureDiagnostics(
                summary = CaptureSummary(
                    pending = s.optInt("pending", 0),
                    accepted = s.optInt("accepted", 0),
                    saved = s.optInt("saved", 0),
                    discarded = s.optInt("discarded", 0),
                    rejected = s.optInt("rejected", 0),
                    merged = s.optInt("merged", 0),
                    amountEdited = s.optInt("amountEdited", 0),
                    reasons = reasons,
                    reconcile = s.optJSONObject("reconcile")?.let { r ->
                        CaptureReconcile(
                            screen = r.optInt("screen", 0),
                            notify = r.optInt("notify", 0),
                            matched = r.optInt("matched", 0),
                            screenOnly = r.optInt("screenOnly", 0),
                            notifyOnly = r.optInt("notifyOnly", 0)
                        )
                    } ?: CaptureReconcile()
                ),
                traces = traces,
                apps = appStats,
                kinds = kindStats,
                settings = parseSettings(o.optJSONObject("settings"))
            )
        } catch (_: Exception) {
            CaptureDiagnostics()
        }
    }

    fun apps(context: Context, includeAll: Boolean = false): List<CaptureApp> {
        val raw = runCatching {
            MuyianBillCore.getCaptureApps(context, includeAll)
        }.getOrNull() ?: return emptyList()
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("apps") ?: org.json.JSONArray()
            val out = ArrayList<CaptureApp>()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                out.add(
                    CaptureApp(
                        pkg = a.optString("pkg", ""),
                        label = a.optString("label", ""),
                        role = a.optString("role", "HOST"),
                        enabled = a.optBoolean("enabled", true),
                        builtin = a.optBoolean("builtin", false)
                    )
                )
            }
            out.sortedWith(compareByDescending<CaptureApp> { it.enabled }.thenBy { it.label })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setApp(context: Context, pkg: String, role: String, enabled: Boolean): Boolean =
        runCatching { MuyianBillCore.setCaptureApp(context, pkg, role, enabled) }.getOrDefault(false)

    fun settings(context: Context): CaptureSettingsData {
        val raw = runCatching { MuyianBillCore.getCaptureSettings(context) }.getOrNull() ?: return CaptureSettingsData()
        return try {
            parseSettings(JSONObject(raw))
        } catch (_: Exception) {
            CaptureSettingsData()
        }
    }

    fun saveSettings(context: Context, settings: CaptureSettingsData): Boolean {
        val o = JSONObject()
        o.put("recordTransfers", settings.recordTransfers)
        o.put("debugText", settings.debugText)
        o.put("lowConfidence", settings.lowConfidence)
        return runCatching { MuyianBillCore.setCaptureSettings(context, o.toString()) }.getOrDefault(false)
    }

    fun clear(context: Context) {
        runCatching { MuyianBillCore.clearCaptureDiagnostics(context) }
    }

    /** 用诊断里保存的页面文本重新跑一遍解析，不写账本、不弹层。 */
    fun reparse(context: Context, pkg: String, source: String, text: String): CaptureReparse {
        if (pkg.isEmpty() || text.isEmpty()) return CaptureReparse(reasonLabel = "没有保存页面文本")
        val raw = runCatching {
            MuyianBillCore.reparseCapture(context, pkg, source, text)
        }.getOrNull() ?: return CaptureReparse(reasonLabel = "解析失败")
        return try {
            val o = JSONObject(raw)
            CaptureReparse(
                kindLabel = o.optString("kindLabel", ""),
                reasonLabel = o.optString("reasonLabel", ""),
                amount = o.optDouble("amount", 0.0),
                direction = o.optString("direction", ""),
                merchant = o.optString("merchant", ""),
                orderNo = o.optString("orderNo", ""),
                channel = o.optString("channel", ""),
                confidence = o.optDouble("confidence", 0.0),
                needsReview = o.optBoolean("needsReview", false)
            )
        } catch (_: Exception) {
            CaptureReparse(reasonLabel = "解析失败")
        }
    }

    /** 影子回放：用当前解析规则重跑已保存的页面文本，不写账本、不弹层。 */
    fun replay(context: Context, limit: Int = 200): CaptureReplaySummary {
        val raw = runCatching {
            MuyianBillCore.replayCaptureTraces(context, limit)
        }.getOrNull() ?: return CaptureReplaySummary()
        return try {
            val o = JSONObject(raw)
            val samples = ArrayList<CaptureReplaySample>()
            val arr = o.optJSONArray("samples") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                samples.add(
                    CaptureReplaySample(
                        app = s.optString("app", ""),
                        oldKindLabel = s.optString("oldKindLabel", ""),
                        newKindLabel = s.optString("newKindLabel", ""),
                        oldAmount = s.optDouble("oldAmount", 0.0),
                        newAmount = s.optDouble("newAmount", 0.0),
                        oldReasonLabel = s.optString("oldReasonLabel", ""),
                        newReasonLabel = s.optString("newReasonLabel", "")
                    )
                )
            }
            CaptureReplaySummary(
                total = o.optInt("total", 0),
                withText = o.optInt("withText", 0),
                changed = o.optInt("changed", 0),
                kindChanged = o.optInt("kindChanged", 0),
                amountChanged = o.optInt("amountChanged", 0),
                newlyParsed = o.optInt("newlyParsed", 0),
                lostParse = o.optInt("lostParse", 0),
                samples = samples
            )
        } catch (_: Exception) {
            CaptureReplaySummary()
        }
    }

    private fun parseSettings(o: JSONObject?): CaptureSettingsData {
        if (o == null) return CaptureSettingsData()
        return CaptureSettingsData(
            recordTransfers = o.optBoolean("recordTransfers", false),
            debugText = o.optBoolean("debugText", false),
            lowConfidence = o.optBoolean("lowConfidence", true)
        )
    }
}
