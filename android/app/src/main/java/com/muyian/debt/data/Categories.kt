package com.muyian.debt.data

import androidx.compose.ui.graphics.Color
import com.muyian.debt.ui.theme.hex
import org.json.JSONArray
import org.json.JSONObject

/** 分类数据的唯一来源：默认分类、本地存储、图标/颜色查询。 */
object Cats {
    const val STORAGE_KEY = "muyian_categories_v2"
    const val DEFAULT_ICON = "grid"

    val PALETTE = listOf(
        "#FF653F", "#4489FE", "#00AAF5", "#B487FE", "#29C184", "#FFC94D",
        "#95D46B", "#8B9FFF", "#F7707A", "#5C7CFA", "#80CBC4", "#F5A65B",
        "#FF85C0", "#4FC3F7", "#FF5E5E", "#FFA83E", "#01BFA3", "#C77DFF",
        "#FA66D4"
    )

    val ICON_CHOICES = listOf(
        "utensils", "cookie", "coffee", "apple", "shirt", "bus", "compass", "smile",
        "paw", "phone", "wine", "book-open", "cart", "home", "lipstick", "pill",
        "red-packet", "gift", "fuel", "gamepad", "camera", "activity", "grid",
        "droplet", "wallet", "award", "trending-up", "briefcase", "rotate-ccw",
        "star", "bell", "clock", "flag"
    )

    private fun top(name: String, icon: String, vararg subs: Pair<String, String>) = Category(
        name, icon, subs.map { SubCat(it.first, it.second) }.toMutableList()
    )

    fun defaultExpense(): MutableList<Category> = mutableListOf(
        top("三餐", "utensils"),
        top("零食", "cookie", "水果" to "apple", "奶茶" to "coffee"),
        top("衣服", "shirt"),
        top("交通", "bus"),
        top("旅行", "compass"),
        top("孩子", "smile"),
        top("宠物", "paw"),
        top("话费网费", "phone"),
        top("烟酒", "wine"),
        top("学习", "book-open"),
        top("日用品", "cart"),
        top("住房", "home"),
        top("美妆", "lipstick"),
        top("医疗", "pill"),
        top("发红包", "red-packet"),
        top("汽车/加油", "fuel"),
        top("娱乐", "gamepad"),
        top("请客送礼", "gift"),
        top("电器数码", "camera"),
        top("运动", "activity"),
        top("其它", "grid"),
        top("水电煤", "droplet")
    )

    fun defaultIncome(): MutableList<Category> = mutableListOf(
        top("工资", "wallet"),
        top("奖金", "award"),
        top("理财", "trending-up"),
        top("兼职", "briefcase"),
        top("红包", "red-packet"),
        top("退款", "rotate-ccw"),
        top("其它收入", "grid")
    )

    fun load(prefs: Prefs): CategoryData {
        val raw = prefs.str(STORAGE_KEY, "")
        if (raw.isNotEmpty()) {
            try {
                val o = JSONObject(raw)
                val e = o.optJSONArray("expense")
                val i = o.optJSONArray("income")
                if (e != null && i != null) {
                    return CategoryData(parseList(e), parseList(i))
                }
            } catch (_: Exception) {
                // 落回默认
            }
        }
        val data = CategoryData(defaultExpense(), defaultIncome())
        save(prefs, data)
        return data
    }

    private fun parseList(arr: JSONArray): MutableList<Category> {
        val out = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) continue
            val icon = o.optString("icon", DEFAULT_ICON).ifEmpty { DEFAULT_ICON }
            val subs = mutableListOf<SubCat>()
            val sa = o.optJSONArray("subs")
            if (sa != null) {
                for (j in 0 until sa.length()) {
                    val s = sa.optJSONObject(j) ?: continue
                    val sn = s.optString("name", "").trim()
                    if (sn.isEmpty()) continue
                    subs.add(SubCat(sn, s.optString("icon", DEFAULT_ICON).ifEmpty { DEFAULT_ICON }))
                }
            }
            out.add(Category(name, icon, subs))
        }
        return out
    }

    fun toJson(data: CategoryData): String {
        fun listJson(list: List<Category>): JSONArray {
            val arr = JSONArray()
            for (c in list) {
                val o = JSONObject()
                o.put("name", c.name)
                o.put("icon", c.icon)
                if (c.subs.isNotEmpty()) {
                    val sa = JSONArray()
                    for (s in c.subs) {
                        sa.put(JSONObject().put("name", s.name).put("icon", s.icon))
                    }
                    o.put("subs", sa)
                }
                arr.put(o)
            }
            return arr
        }
        return JSONObject().put("expense", listJson(data.expense)).put("income", listJson(data.income)).toString()
    }

    fun save(prefs: Prefs, data: CategoryData) {
        prefs.putStr(STORAGE_KEY, toJson(data))
    }

    fun iconOf(data: CategoryData, name: String, type: String? = null): String {
        if (name.isEmpty()) return DEFAULT_ICON
        if (name == "收入") return "wallet"
        fun search(t: String): String? {
            for (c in data.list(t)) {
                if (c.name == name) return c.icon.ifEmpty { DEFAULT_ICON }
                for (s in c.subs) if (s.name == name) return s.icon.ifEmpty { DEFAULT_ICON }
            }
            return null
        }
        if (type != null) return search(type) ?: DEFAULT_ICON
        return search("expense") ?: search("income") ?: DEFAULT_ICON
    }

    fun topNameOf(data: CategoryData, name: String, type: String = "expense"): String {
        if (name.isEmpty()) return "其它"
        for (c in data.list(type)) {
            if (c.name == name) return c.name
            for (s in c.subs) if (s.name == name) return c.name
        }
        if (name != "收入") {
            val other = if (type == "expense") "income" else "expense"
            for (c in data.list(other)) {
                if (c.name == name) return c.name
                for (s in c.subs) if (s.name == name) return c.name
            }
        }
        return name
    }

    fun colorOf(data: CategoryData, name: String, type: String = "expense"): Color {
        val list = data.list(type)
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) return hex(PALETTE[idx % PALETTE.size])
        var hash = 0
        for (ch in name) hash = (hash * 31 + ch.code) and 0x7FFFFFFF
        return hex(PALETTE[hash % PALETTE.size])
    }

    fun parentOf(data: CategoryData, name: String, type: String = "expense"): String {
        for (c in data.list(type)) {
            if (c.subs.any { it.name == name }) return c.name
        }
        return ""
    }

    fun exists(data: CategoryData, name: String, type: String): Boolean {
        return data.list(type).any { c -> c.name == name || c.subs.any { it.name == name } }
    }
}
