package muyian.bill.listener

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 采集引擎：观察 → 解析 → 门禁 → 账本 → 弹层。
 * 只负责产出"待确认账单"，永不直接写 bills；写库仍由 BillSheetActivity 的用户保存触发。
 */
internal object CaptureEngine {
    private const val TAG = "MuyianBill"

    fun handleScreen(
        context: Context,
        pkg: String,
        items: List<UiText>,
        windowTitle: String,
        eventType: Int,
        observedAt: Long,
        force: Boolean
    ): String {
        val now = if (observedAt > 0) observedAt else System.currentTimeMillis()
        val settings = CaptureRegistry.settings(context)
        val rule = CaptureRegistry.ruleFor(context, pkg)
            ?: if (force) AppRule(pkg, AppRole.HOST, AdapterId.GENERIC, "", builtin = false) else null

        if (!force) {
            val sourceReason = CaptureGates.sourceReason(pkg, context.packageName, rule)
            if (sourceReason != null) {
                if (rule != null) {
                    trace(context, settings, now, pkg, CaptureSource.SCREEN,
                        PageKind.UNKNOWN, "rejected", sourceReason, "", null, "")
                }
                return rejected(sourceReason)
            }
        }
        val effectiveRule = rule ?: return rejected(Reason.NOT_CANDIDATE)
        val obs = ScreenObservation(
            pkg = pkg,
            windowTitle = windowTitle,
            eventType = eventType,
            items = items,
            observedAt = now
        )
        val parsed = CaptureParser.parseScreenDetailed(
            effectiveRule, obs, ParseOptions(settings.recordTransfers)
        )
        val analysis = parsed.analysis
        val classification = parsed.classification
        val debugText = if (settings.debugText) items.joinToString("\n") { it.text } else ""

        if (analysis.kind == PageKind.CASHIER) {
            if (!force) {
                val intent = CaptureParser.parseCashierIntent(effectiveRule, obs)
                if (intent != null) {
                    CaptureRoute.markIntent(
                        context, pkg, now, intent, effectiveRule.role == AppRole.HOST
                    )
                } else if (effectiveRule.role == AppRole.PAY) {
                    CaptureRoute.markCashier(context, pkg, now)
                } else if (effectiveRule.role == AppRole.HOST) {
                    CaptureRoute.markHostTrigger(context, pkg, now)
                }
            }
            trace(context, settings, now, pkg, CaptureSource.SCREEN, analysis.kind,
                "rejected", Reason.PAGE_CASHIER, "", null, debugText, classification)
            return rejected(Reason.PAGE_CASHIER)
        }

        var facts = analysis.facts
        // 成功页经常只有"支付成功"没有金额（闲鱼成功页、微信 LiteApp 成功页）：
        // 宿主页用同路线 intent，支付 App 页用同一支付会话的 intent 补齐。
        if (facts == null && analysis.kind == PageKind.PAY_SUCCESS) {
            val intent = when (effectiveRule.role) {
                AppRole.HOST -> CaptureRoute.intentFor(context, pkg, now)
                AppRole.PAY -> CaptureRoute.intentForPayment(context, pkg, now)
                else -> null
            }
            if (intent != null) {
                facts = Facts(
                    pageKind = PageKind.PAY_SUCCESS,
                    direction = Direction.EXPENSE,
                    amount = intent.amount,
                    tier = AmountTier.STRONG_LABEL,
                    amountConfidence = intent.confidence,
                    needsReview = false,
                    channel = intent.channel.ifEmpty { "银行卡" },
                    merchant = intent.merchant,
                    orderNo = "",
                    occurredAt = now,
                    completionMark = analysis.completionMark.ifEmpty { "支付成功" },
                    confidence = 0.88,
                    suppressReason = null
                )
            }
        }
        if (facts == null) {
            val reason = analysis.reason ?: CaptureGates.pageReason(analysis.kind)
                ?: Reason.NO_COMPLETION_MARK
            trace(context, settings, now, pkg, CaptureSource.SCREEN, analysis.kind,
                "rejected", reason, "", null, debugText, classification)
            return rejected(reason)
        }

        if (!force) {
            val hasContext = CaptureRoute.hasHostContext(context, pkg, now) ||
                CaptureRoute.intentFor(context, pkg, now) != null
            val contextReason = CaptureGates.contextReason(effectiveRule, hasContext)
            if (contextReason != null) {
                trace(context, settings, now, pkg, CaptureSource.SCREEN, analysis.kind,
                    "rejected", contextReason, "", facts, debugText, classification)
                return rejected(contextReason)
            }
        }
        val semantic = CaptureGates.semanticReason(facts)
        if (semantic != null && !force) {
            trace(context, settings, now, pkg, CaptureSource.SCREEN, analysis.kind,
                "rejected", semantic, "", facts, debugText, classification)
            return rejected(semantic)
        }
        val extra = extraReason(settings, effectiveRule, facts, CaptureSource.SCREEN, now)
        if (extra != null && !force) {
            trace(context, settings, now, pkg, CaptureSource.SCREEN, analysis.kind,
                "rejected", extra, "", facts, debugText, classification)
            return rejected(extra)
        }
        if (!force && effectiveRule.role == AppRole.PAY) {
            CaptureRoute.markEvidence(context, pkg, now)
        }

        if (force) {
            val bill = buildBill(context, pkg, CaptureSource.SCREEN, facts, "", now)
            val ok = CapturePresenter.present(context, bill)
            return if (ok) accepted(bill, Reason.ACCEPTED) else rejected(Reason.QUEUE_FULL)
        }

        val session = CaptureRoute.sessionFor(context, pkg, now)
        val built = CaptureIdentity.build(
            facts, CaptureSource.SCREEN, pkg,
            effectiveRule.role == AppRole.HOST, session, now
        )
        val txnId = built.first
        val candidate = Candidate(txnId, CaptureSource.SCREEN, pkg, facts, built.second, now)
        val result = deliver(context, settings, candidate, analysis, debugText, classification)
        if ((effectiveRule.role == AppRole.HOST || effectiveRule.role == AppRole.PAY) &&
            analysis.kind == PageKind.PAY_SUCCESS
        ) {
            CaptureRoute.clearIntent(context)
        }
        return result
    }

    fun handleNotification(
        context: Context, obs: NotificationObservation, force: Boolean
    ): String {
        // 账本窗口/路线用处理时间；账单日期用通知的 postTime（由解析器写进 Facts.occurredAt）
        val now = System.currentTimeMillis()
        val settings = CaptureRegistry.settings(context)
        val rule = CaptureRegistry.ruleFor(context, obs.pkg)
            ?: if (force) AppRule(obs.pkg, AppRole.HOST, AdapterId.GENERIC, "", builtin = false) else null
        if (!force) {
            val sourceReason = CaptureGates.sourceReason(obs.pkg, context.packageName, rule)
            if (sourceReason != null) {
                if (rule != null) {
                    trace(context, settings, now, obs.pkg, CaptureSource.NOTIFICATION,
                        PageKind.UNKNOWN, "rejected", sourceReason, "", null, "")
                }
                return rejected(sourceReason)
            }
        }
        val effectiveRule = rule ?: return rejected(Reason.NOT_CANDIDATE)
        val parsed = CaptureParser.parseNotificationDetailed(
            effectiveRule, obs, ParseOptions(settings.recordTransfers)
        )
        val analysis = parsed.analysis
        val classification = parsed.classification
        val debugText = if (settings.debugText) {
            listOf(obs.title, obs.subText, obs.text, obs.bigText).joinToString("\n")
        } else {
            ""
        }
        val facts = analysis.facts
        if (facts == null) {
            val reason = analysis.reason ?: CaptureGates.pageReason(analysis.kind)
                ?: Reason.NO_COMPLETION_MARK
            trace(context, settings, now, obs.pkg, CaptureSource.NOTIFICATION, analysis.kind,
                "rejected", reason, "", null, debugText, classification)
            return rejected(reason)
        }
        if (!force) {
            val contextReason = CaptureGates.contextReason(
                effectiveRule, CaptureRoute.hasHostContext(context, obs.pkg, now)
            )
            if (contextReason != null) {
                trace(context, settings, now, obs.pkg, CaptureSource.NOTIFICATION, analysis.kind,
                    "rejected", contextReason, "", facts, debugText, classification)
                return rejected(contextReason)
            }
        }
        val semantic = CaptureGates.semanticReason(facts)
        if (semantic != null && !force) {
            trace(context, settings, now, obs.pkg, CaptureSource.NOTIFICATION, analysis.kind,
                "rejected", semantic, "", facts, debugText, classification)
            return rejected(semantic)
        }
        val extra = extraReason(settings, effectiveRule, facts, CaptureSource.NOTIFICATION, now)
        if (extra != null && !force) {
            trace(context, settings, now, obs.pkg, CaptureSource.NOTIFICATION, analysis.kind,
                "rejected", extra, "", facts, debugText, classification)
            return rejected(extra)
        }
        if (!force && effectiveRule.role == AppRole.PAY) {
            CaptureRoute.markEvidence(context, obs.pkg, now)
        }
        if (force) {
            val bill = buildBill(context, obs.pkg, CaptureSource.NOTIFICATION, facts, "", now)
            val ok = CapturePresenter.present(context, bill)
            return if (ok) accepted(bill, Reason.ACCEPTED) else rejected(Reason.QUEUE_FULL)
        }
        val session = CaptureRoute.sessionFor(context, obs.pkg, now)
        val built = CaptureIdentity.build(
            facts, CaptureSource.NOTIFICATION, obs.pkg, false, session, now
        )
        val candidate = Candidate(built.first, CaptureSource.NOTIFICATION, obs.pkg, facts, built.second, now)
        return deliver(context, settings, candidate, analysis, debugText, classification)
    }

    private fun deliver(
        context: Context,
        settings: CaptureRegistry.Settings,
        candidate: Candidate,
        analysis: PageAnalysis,
        debugText: String,
        classification: Classification? = null
    ): String {
        val facts = candidate.facts
        if (CaptureLedger.countPending(context) >= CapturePresenter.MAX_QUEUE &&
            !CapturePresenter.contains(candidate.txnId)
        ) {
            trace(context, settings, candidate.createdAt, candidate.sourcePkg, candidate.source,
                analysis.kind, "rejected", Reason.QUEUE_FULL, candidate.txnId, facts, debugText, classification)
            return rejected(Reason.QUEUE_FULL)
        }
        val claim = CaptureLedger.claim(context, candidate)
        when (claim.kind) {
            CaptureLedger.ClaimKind.DUPLICATE -> {
                val reason = claim.reason ?: Reason.DUPLICATE_CAPTURE
                trace(context, settings, candidate.createdAt, candidate.sourcePkg, candidate.source,
                    analysis.kind, "duplicate", reason, candidate.txnId, facts, debugText, classification)
                return rejected(reason)
            }
            CaptureLedger.ClaimKind.MERGED -> {
                val bill = buildBill(
                    context, candidate.sourcePkg, candidate.source, facts, claim.txnId, candidate.createdAt
                )
                if (candidate.sourcePkg.isNotEmpty() &&
                    CaptureRegistry.isPaymentApp(context, candidate.sourcePkg)
                ) {
                    val merged = CaptureLedger.mergePending(
                        context, claim.txnId, bill.toString(), true
                    )
                    if (merged != null) {
                        CapturePresenter.merge(claim.txnId, merged)
                    }
                }
                trace(context, settings, candidate.createdAt, candidate.sourcePkg, candidate.source,
                    analysis.kind, "merged", Reason.DUPLICATE_SESSION, claim.txnId, facts, debugText, classification)
                return rejected(Reason.DUPLICATE_SESSION)
            }
            CaptureLedger.ClaimKind.CLAIMED -> {
                val bill = buildBill(
                    context, candidate.sourcePkg, candidate.source, facts, candidate.txnId, candidate.createdAt
                )
                val presented = try {
                    CapturePresenter.present(context, bill)
                } catch (e: Exception) {
                    Log.e(TAG, "present capture failed", e)
                    false
                }
                if (!presented) {
                    CaptureLedger.release(context, candidate.txnId)
                    trace(context, settings, candidate.createdAt, candidate.sourcePkg, candidate.source,
                        analysis.kind, "rejected", Reason.QUEUE_FULL, candidate.txnId, facts, debugText, classification)
                    return rejected(Reason.QUEUE_FULL)
                }
                val reason = CaptureGates.acceptedReason(facts)
                trace(context, settings, candidate.createdAt, candidate.sourcePkg, candidate.source,
                    analysis.kind, "accepted", reason, candidate.txnId, facts, debugText, classification)
                return accepted(bill, reason)
            }
        }
    }

    private fun extraReason(
        settings: CaptureRegistry.Settings,
        rule: AppRule,
        facts: Facts,
        source: CaptureSource,
        now: Long
    ): Reason? {
        // 支付 App 里翻到很久以前的结果页：即使解析成功也不能当成刚发生的一笔
        if (source == CaptureSource.SCREEN && rule.role == AppRole.PAY &&
            facts.occurredAt > 0 && now - facts.occurredAt > 10L * 60 * 1000L
        ) return Reason.STALE_PAGE
        // 通知服务偶发重放旧通知：超过 30 分钟的不再当成新交易
        if (source == CaptureSource.NOTIFICATION && facts.occurredAt > 0 &&
            now - facts.occurredAt > 30L * 60 * 1000L
        ) return Reason.STALE_PAGE
        if (!settings.lowConfidence && (facts.needsReview || facts.confidence < 0.60)) {
            return Reason.LOW_CONFIDENCE
        }
        return null
    }

    private fun buildBill(
        context: Context,
        pkg: String,
        source: CaptureSource,
        facts: Facts,
        txnId: String,
        createdAt: Long
    ): JSONObject {
        val signed = CaptureGates.signedAmount(facts)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = facts.occurredAt
        val costType = when (facts.pageKind) {
            PageKind.REFUND_SUCCESS -> "退款"
            PageKind.TRANSFER_SUCCESS -> "转账"
            PageKind.REPAYMENT_SUCCESS -> "还款"
            PageKind.RECEIVE_SUCCESS -> "收入"
            else -> CaptureLearning.suggest(context, pkg, facts.merchant, facts.direction)
        }
        val bill = JSONObject()
        bill.put("money", signed)
        bill.put("bankName", facts.channel)
        bill.put("costType", costType)
        bill.put("orderRemark", "")
        bill.put("merchant", facts.merchant)
        bill.put("orderNo", facts.orderNo)
        bill.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(facts.occurredAt)))
        bill.put("year", calendar.get(Calendar.YEAR))
        bill.put("month", calendar.get(Calendar.MONTH) + 1)
        bill.put("day", calendar.get(Calendar.DAY_OF_MONTH))
        bill.put("sourcePkg", pkg)
        bill.put("capture", if (source == CaptureSource.SCREEN) "screen" else "notify")
        bill.put("createdAt", createdAt)
        bill.put("txnId", txnId)
        bill.put("pageKind", facts.pageKind.name)
        bill.put("direction", facts.direction.name)
        bill.put("needsReview", facts.needsReview)
        bill.put("confidence", facts.confidence)
        return bill
    }

    // ---------- 观察 JSON（采集层 <-> 引擎） ----------

    fun screenJson(context: Context, pkg: String, snapshotJson: String, force: Boolean): String {
        val items = ArrayList<UiText>()
        var title = ""
        var eventType = 0
        var observedAt = System.currentTimeMillis()
        try {
            val o = JSONObject(snapshotJson)
            title = o.optString("windowTitle", "")
            eventType = o.optInt("eventType", 0)
            observedAt = o.optLong("observedAt", observedAt)
            val arr = o.optJSONArray("items") ?: JSONArray()
            var i = 0
            while (i < arr.length()) {
                val it = arr.optJSONObject(i)
                i++
                if (it == null) continue
                val text = it.optString("t", "")
                if (text.isEmpty()) continue
                items.add(
                    UiText(
                        text = text,
                        left = it.optInt("l", 0),
                        top = it.optInt("top", 0),
                        right = it.optInt("r", 0),
                        bottom = it.optInt("b", 0),
                        sizePx = it.optInt("s", 0),
                        className = it.optString("c", ""),
                        viewId = it.optString("id", ""),
                        depth = it.optInt("d", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parse screen snapshot failed", e)
        }
        return handleScreen(context, pkg, items, title, eventType, observedAt, force)
    }

    fun notificationJson(context: Context, json: String, force: Boolean): String {
        val obs = try {
            val o = JSONObject(json)
            val lines = ArrayList<String>()
            val arr = o.optJSONArray("lines") ?: JSONArray()
            var i = 0
            while (i < arr.length()) {
                val line = arr.optString(i, "")
                if (line.isNotEmpty()) lines.add(line)
                i++
            }
            NotificationObservation(
                pkg = o.optString("pkg", ""),
                channelId = o.optString("channelId", ""),
                category = o.optString("category", ""),
                notificationKey = o.optString("key", ""),
                groupSummary = o.optBoolean("groupSummary", false),
                ongoing = o.optBoolean("ongoing", false),
                title = o.optString("title", ""),
                text = o.optString("text", ""),
                subText = o.optString("subText", ""),
                bigText = o.optString("bigText", ""),
                lines = lines,
                postTime = o.optLong("postTime", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "parse notification snapshot failed", e)
            return rejected(Reason.PAGE_UNKNOWN)
        }
        return handleNotification(context, obs, force)
    }

    // ---------- 恢复与诊断 ----------

    fun restore(context: Context) {
        CaptureLedger.purge(context)
        CapturePresenter.restore(context)
    }

    fun diagnosticsJson(context: Context, limit: Int): String {
        val traces = CaptureLedger.traces(context, limit)
        val out = JSONObject()
        val arr = JSONArray()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val stats = CaptureStats.today(context, dayStart)

        for (t in traces) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("at", t.createdAt)
            o.put("pkg", t.pkg)
            o.put("app", CaptureRegistry.appLabel(context, t.pkg))
            o.put("source", t.source)
            o.put("kind", t.kind)
            o.put("kindLabel", kindLabel(t.kind))
            o.put("result", t.result)
            o.put("reason", t.reason)
            o.put("reasonLabel", reasonLabel(t.reason))
            o.put("amount", t.amount)
            o.put("direction", t.direction)
            o.put("channel", t.channel)
            o.put("orderNo", t.orderNo)
            o.put("confidence", t.confidence)
            o.put("needsReview", t.needsReview)
            if (t.evidence.isNotEmpty()) o.put("evidence", t.evidence)
            if (t.text.isNotEmpty()) o.put("text", t.text)
            arr.put(o)
        }

        val summary = JSONObject()
        summary.put("pending", CaptureLedger.countPending(context))
        summary.put("accepted", stats.captured)
        summary.put("captured", stats.captured)
        summary.put("saved", stats.saved)
        summary.put("discarded", stats.discarded)
        summary.put("rejected", stats.rejected)
        summary.put("merged", stats.merged)
        summary.put("amountEdited", stats.edited)

        val reasonArr = JSONArray()
        for ((k, v) in stats.reasons.entries.sortedByDescending { it.value }.take(8)) {
            val o = JSONObject()
            o.put("reason", k)
            o.put("label", reasonLabel(k))
            o.put("count", v)
            reasonArr.put(o)
        }
        summary.put("reasons", reasonArr)

        val appArr = JSONArray()
        for (a in stats.apps.take(12)) {
            val o = JSONObject()
            o.put("pkg", a.pkg)
            o.put("app", CaptureRegistry.appLabel(context, a.pkg).ifEmpty { a.pkg })
            o.put("captured", a.captured)
            o.put("saved", a.saved)
            o.put("discarded", a.discarded)
            o.put("rejected", a.rejected)
            o.put("edited", a.edited)
            appArr.put(o)
        }
        summary.put("apps", appArr)

        val kindArr = JSONArray()
        for (k in stats.kinds.take(12)) {
            val o = JSONObject()
            o.put("kind", k.kind)
            o.put("kindLabel", kindLabel(k.kind))
            o.put("captured", k.captured)
            o.put("saved", k.saved)
            o.put("discarded", k.discarded)
            o.put("rejected", k.rejected)
            o.put("edited", k.edited)
            kindArr.put(o)
        }
        summary.put("kinds", kindArr)

        val editedArr = JSONArray()
        for (a in stats.apps.sortedByDescending { it.edited }.filter { it.edited > 0 }.take(5)) {
            val o = JSONObject()
            o.put("pkg", a.pkg)
            o.put("app", CaptureRegistry.appLabel(context, a.pkg).ifEmpty { a.pkg })
            o.put("count", a.edited)
            editedArr.put(o)
        }
        summary.put("editedByApp", editedArr)

        val reconcile = JSONObject()
        reconcile.put("screen", stats.reconcile.screen)
        reconcile.put("notify", stats.reconcile.notify)
        reconcile.put("matched", stats.reconcile.matched)
        reconcile.put("screenOnly", stats.reconcile.screenOnly)
        reconcile.put("notifyOnly", stats.reconcile.notifyOnly)
        summary.put("reconcile", reconcile)
        out.put("summary", summary)
        out.put("traces", arr)
        out.put("settings", JSONObject(settingsJson(context)))
        return out.toString()
    }

    fun appsJson(context: Context, includeAll: Boolean): String =
        CaptureRegistry.appsJson(context, includeAll)

    fun appsJson(context: Context): String = CaptureRegistry.appsJson(context, false)

    /** 诊断页「重新识别」：只跑解析，不写账本、不弹层。 */
    fun reparse(context: Context, pkg: String, source: String, text: String): String {
        val analysis = analyzeText(context, pkg, source, text)
        val out = JSONObject()
        out.put("kind", analysis.kind.name)
        out.put("kindLabel", analysis.kind.label)
        out.put("reason", analysis.reason?.name ?: "")
        out.put("reasonLabel", analysis.reason?.label ?: "")
        val facts = analysis.facts
        if (facts != null) {
            out.put("amount", facts.amount)
            out.put("direction", facts.direction.name)
            out.put("merchant", facts.merchant)
            out.put("orderNo", facts.orderNo)
            out.put("channel", facts.channel)
            out.put("confidence", facts.confidence)
            out.put("needsReview", facts.needsReview)
        }
        return out.toString()
    }

    /** 影子回放：用当前解析规则重跑已保存的页面文本，对比历史决策。 */
    fun replayTraces(context: Context, limit: Int): String {
        val traces = CaptureLedger.traces(context, limit)
        var total = 0
        var withText = 0
        var changed = 0
        var kindChanged = 0
        var amountChanged = 0
        var newlyParsed = 0
        var lostParse = 0
        val samples = JSONArray()
        for (t in traces) {
            total++
            if (t.text.isEmpty()) continue
            withText++
            val analysis = analyzeText(context, t.pkg, t.source, t.text)
            val oldHadFacts = t.amount > 0
            val newFacts = analysis.facts
            val newHadFacts = newFacts != null
            val oldKind = t.kind
            val newKind = analysis.kind.name
            val oldAmount = t.amount
            val newAmount = newFacts?.amount ?: 0.0
            val kindDiff = oldKind != newKind
            val amountDiff = Math.abs(oldAmount - newAmount) > 0.005
            if (kindDiff) kindChanged++
            if (amountDiff) amountChanged++
            if (!oldHadFacts && newHadFacts) newlyParsed++
            if (oldHadFacts && !newHadFacts) lostParse++
            val rowChanged = kindDiff || amountDiff || oldHadFacts != newHadFacts
            if (rowChanged) changed++
            if (rowChanged && samples.length() < 20) {
                val o = JSONObject()
                o.put("id", t.id)
                o.put("at", t.createdAt)
                o.put("app", CaptureRegistry.appLabel(context, t.pkg).ifEmpty { t.pkg })
                o.put("oldKind", oldKind)
                o.put("oldKindLabel", kindLabel(oldKind))
                o.put("newKind", newKind)
                o.put("newKindLabel", analysis.kind.label)
                o.put("oldAmount", oldAmount)
                o.put("newAmount", newAmount)
                o.put("oldReason", t.reason)
                o.put("oldReasonLabel", reasonLabel(t.reason))
                o.put("newReason", analysis.reason?.name ?: "")
                o.put("newReasonLabel", analysis.reason?.label ?: "")
                o.put("oldDirection", t.direction)
                o.put("newDirection", newFacts?.direction?.name ?: "")
                samples.put(o)
            }
        }
        val out = JSONObject()
        out.put("total", total)
        out.put("withText", withText)
        out.put("changed", changed)
        out.put("kindChanged", kindChanged)
        out.put("amountChanged", amountChanged)
        out.put("newlyParsed", newlyParsed)
        out.put("lostParse", lostParse)
        out.put("samples", samples)
        return out.toString()
    }

    private fun analyzeText(
        context: Context,
        pkg: String,
        source: String,
        text: String
    ): PageAnalysis {
        val settings = CaptureRegistry.settings(context)
        val rule = CaptureRegistry.ruleFor(context, pkg)
            ?: AppRule(pkg, AppRole.HOST, AdapterId.GENERIC, "", builtin = false)
        val now = System.currentTimeMillis()
        return if (source == "notify") {
            val lines = text.split('\n')
            val title = lines.firstOrNull() ?: ""
            val body = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""
            CaptureParser.parseNotification(
                rule,
                NotificationObservation(
                    pkg = pkg, title = title, text = body,
                    bigText = body, postTime = now
                ),
                ParseOptions(settings.recordTransfers)
            )
        } else {
            val items = text.split('\n').mapIndexed { i, line ->
                UiText(line, top = i * 60, bottom = i * 60 + 48, sizePx = 30)
            }
            CaptureParser.parseScreen(
                rule,
                ScreenObservation(pkg = pkg, items = items, observedAt = now),
                ParseOptions(settings.recordTransfers)
            )
        }
    }

    fun setApp(context: Context, pkg: String, role: String, enabled: Boolean): Boolean =
        CaptureRegistry.setApp(context, pkg, role, enabled)

    fun settingsJson(context: Context): String {
        val s = CaptureRegistry.settings(context)
        val o = JSONObject()
        o.put("recordTransfers", s.recordTransfers)
        o.put("debugText", s.debugText)
        o.put("lowConfidence", s.lowConfidence)
        return o.toString()
    }

    fun setSettings(context: Context, json: String): Boolean =
        CaptureRegistry.setSettings(context, json)

    fun clearDiagnostics(context: Context) {
        CaptureLedger.clearTraces(context)
        CaptureLearning.clear(context)
    }

    // ---------- 结果 ----------

    private fun accepted(bill: JSONObject, reason: Reason): String {
        val result = JSONObject()
        result.put("accepted", true)
        result.put("reason", reason.name)
        result.put("reasonLabel", reason.label)
        result.put("bill", bill)
        return result.toString()
    }

    private fun rejected(reason: Reason): String {
        val result = JSONObject()
        result.put("accepted", false)
        result.put("reason", reason.name)
        result.put("reasonLabel", reason.label)
        return result.toString()
    }

    private fun trace(
        context: Context,
        settings: CaptureRegistry.Settings,
        at: Long,
        pkg: String,
        source: CaptureSource,
        kind: PageKind,
        result: String,
        reason: Reason,
        txnId: String,
        facts: Facts?,
        text: String,
        classification: Classification? = null
    ) {
        val stored = if (settings.debugText) text else ""
        val evidence = classificationEvidence(classification, settings.debugText)
        CaptureLedger.trace(
            context, at, pkg,
            if (source == CaptureSource.SCREEN) "screen" else "notify",
            kind.name, result, reason.name, txnId, facts, evidence, stored
        )
    }

    /** 分类证据：默认只存信号种类/权重，开了调试文本才带命中片段。 */
    private fun classificationEvidence(
        classification: Classification?,
        includeText: Boolean
    ): String {
        if (classification == null) return ""
        return try {
            val o = JSONObject()
            o.put("score", classification.confidence)
            o.put("margin", classification.margin)
            o.put("kind", classification.kind.name)
            val arr = JSONArray()
            for (signal in classification.signals.take(8)) {
                val so = JSONObject()
                so.put("k", signal.kind.name)
                so.put("w", signal.weight)
                signal.reason?.let { so.put("r", it.name) }
                if (includeText && signal.evidence.isNotEmpty()) {
                    so.put("e", signal.evidence.take(80))
                }
                arr.put(so)
            }
            o.put("signals", arr)
            o.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun kindLabel(name: String): String {
        return try {
            PageKind.valueOf(name).label
        } catch (_: Exception) {
            name
        }
    }

    private fun reasonLabel(name: String): String {
        return try {
            Reason.valueOf(name).label
        } catch (_: Exception) {
            name
        }
    }
}
