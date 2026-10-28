package muyian.bill.listener

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** 用户可覆盖的 App 注册表与采集设置；内置表在 CaptureRules，覆盖项存本机 SharedPreferences。 */
internal object CaptureRegistry {
    private const val TAG = "MuyianBill"
    private const val PREFS = "muyian_bill"
    private const val KEY_APPS = "capture_app_overrides_v1"
    private const val KEY_SETTINGS = "capture_settings_v1"

    data class Settings(
        val recordTransfers: Boolean = false,
        val debugText: Boolean = false,
        val lowConfidence: Boolean = true
    )

    private val labelCache = ConcurrentHashMap<String, String>()

    fun settings(context: Context): Settings {
        val raw = prefs(context).getString(KEY_SETTINGS, "") ?: ""
        if (raw.isEmpty()) return Settings()
        return try {
            val o = JSONObject(raw)
            Settings(
                recordTransfers = o.optBoolean("recordTransfers", false),
                debugText = o.optBoolean("debugText", false),
                lowConfidence = o.optBoolean("lowConfidence", true)
            )
        } catch (_: Exception) {
            Settings()
        }
    }

    fun setSettings(context: Context, json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val current = settings(context)
            val next = JSONObject()
            next.put("recordTransfers", o.optBoolean("recordTransfers", current.recordTransfers))
            next.put("debugText", o.optBoolean("debugText", current.debugText))
            next.put("lowConfidence", o.optBoolean("lowConfidence", current.lowConfidence))
            prefs(context).edit().putString(KEY_SETTINGS, next.toString()).apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "save capture settings failed", e)
            false
        }
    }

    /** 解析候选 App：内置 → 用户覆盖 → 应用名关键词兜底；返回 null 表示不采集。 */
    fun ruleFor(context: Context, pkg: String): AppRule? {
        if (pkg.isEmpty()) return null
        var rule = CaptureRules.builtinRule(pkg)
        val label = appLabel(context, pkg)
        if (rule != null) {
            rule = rule.copy(pkg = pkg, label = if (rule.label.isEmpty()) label else rule.label)
        } else {
            val override = overrides(context).optJSONObject(pkg)
            if (override != null) {
                val role = parseRole(override.optString("role", AppRole.HOST.name))
                val enabled = override.optBoolean("enabled", true)
                return AppRule(
                    pkg = pkg,
                    role = if (enabled) role else AppRole.OFF,
                    adapter = AdapterId.GENERIC,
                    label = label,
                    builtin = false
                )
            }
            if (!looksLikeBillApp(label)) return null
            rule = AppRule(
                pkg = pkg,
                role = guessRole(label),
                adapter = AdapterId.GENERIC,
                label = label,
                builtin = false
            )
        }
        val override = overrides(context).optJSONObject(pkg)
        if (override != null) {
            val enabled = override.optBoolean("enabled", true)
            val role = parseRole(override.optString("role", rule.role.name))
            rule = rule.copy(role = if (enabled) role else AppRole.OFF)
        }
        return rule
    }

    fun isCandidate(context: Context, pkg: String): Boolean {
        val rule = ruleFor(context, pkg)
        return rule != null && rule.role != AppRole.OFF
    }

    fun isPaymentApp(context: Context, pkg: String): Boolean =
        ruleFor(context, pkg)?.role == AppRole.PAY

    fun isHostApp(context: Context, pkg: String): Boolean =
        ruleFor(context, pkg)?.role == AppRole.HOST

    // ---------- 设置页数据 ----------

    fun appsJson(context: Context, includeAll: Boolean): String {
        val out = JSONObject()
        val arr = org.json.JSONArray()
        val seen = HashSet<String>()
        val overrideMap = overrides(context)
        try {
            if (includeAll) {
                val pm = context.packageManager
                val installed = pm.getInstalledApplications(0)
                for (info in installed) {
                    val pkg = info.packageName ?: continue
                    if (pkg == context.packageName) continue
                    val label = appLabel(context, pkg)
                    if (label.isEmpty()) continue
                    val base = CaptureRules.builtinRule(pkg)
                    val override = overrideMap.optJSONObject(pkg)
                    val hasLauncher = try {
                        pm.getLaunchIntentForPackage(pkg) != null
                    } catch (_: Exception) {
                        false
                    }
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (base == null && override == null && !looksLikeBillApp(label) &&
                        (isSystem || !hasLauncher)
                    ) continue
                    if (!seen.add(pkg)) continue
                    arr.put(appJson(context, pkg, label, base, override))
                }
            } else {
                // 默认只列内置 App + 用户覆盖，避免触发 ROM 的"读取已安装应用"权限
                val pkgs = LinkedHashSet<String>()
                for (rule in CaptureRules.builtinExactApps()) pkgs.add(rule.pkg)
                val keys = overrideMap.keys()
                while (keys.hasNext()) pkgs.add(keys.next())
                for (pkg in pkgs) {
                    if (pkg == context.packageName) continue
                    val label = appLabel(context, pkg)
                    if (label.isEmpty()) continue
                    if (!seen.add(pkg)) continue
                    arr.put(
                        appJson(
                            context, pkg, label,
                            CaptureRules.builtinRule(pkg),
                            overrideMap.optJSONObject(pkg)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "list capture apps failed", e)
        }
        out.put("apps", arr)
        return out.toString()
    }

    private fun appJson(
        context: Context, pkg: String, label: String,
        base: AppRule?, override: JSONObject?
    ): JSONObject {
        val storedRole = when {
            override != null -> parseRole(
                override.optString("role", base?.role?.name ?: "HOST")
            )
            base != null -> base.role
            else -> guessRole(label)
        }
        val enabled = when {
            override != null -> override.optBoolean("enabled", true)
            base != null -> true
            looksLikeBillApp(label) -> true
            else -> false
        }
        val o = JSONObject()
        o.put("pkg", pkg)
        o.put("label", label.ifEmpty { base?.label ?: pkg })
        o.put("role", storedRole.name)
        o.put("enabled", enabled)
        o.put("builtin", base != null || (override == null && looksLikeBillApp(label)))
        return o
    }

    fun appsJson(context: Context): String = appsJson(context, false)

    fun setApp(context: Context, pkg: String, role: String, enabled: Boolean): Boolean {
        if (pkg.isEmpty()) return false
        return try {
            val map = overrides(context)
            val o = JSONObject()
            o.put("role", parseRole(role).name)
            o.put("enabled", enabled)
            map.put(pkg, o)
            prefs(context).edit().putString(KEY_APPS, map.toString()).apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "save capture app override failed", e)
            false
        }
    }

    fun appLabel(context: Context, pkg: String): String {
        val cached = labelCache[pkg]
        if (cached != null) return cached
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            ""
        }
        labelCache[pkg] = label
        return label
    }

    private fun overrides(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_APPS, "") ?: ""
        if (raw.isEmpty()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun looksLikeBillApp(label: String): Boolean {
        if (label.isEmpty()) return false
        for (word in CaptureRules.LABEL_KEYWORDS) {
            if (label.contains(word)) return true
        }
        return false
    }

    private fun guessRole(label: String): AppRole {
        if (label.contains("银行") || label.contains("信用卡")) return AppRole.PAY
        if (label.contains("云闪付") || label.contains("数字人民币") ||
            label.contains("翼支付")
        ) return AppRole.PAY
        return AppRole.HOST
    }

    private fun parseRole(raw: String): AppRole = when (raw.uppercase(java.util.Locale.US)) {
        "PAY" -> AppRole.PAY
        "OFF" -> AppRole.OFF
        else -> AppRole.HOST
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
