package muyian.bill.listener

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 本地分类建议：只做"商户/App → 用户最终选的分类"计数，不联网、不参与金额解析。
 * 数据量很小（最多各 200 个键），存 SharedPreferences 即可。
 */
internal object CaptureLearning {
    private const val TAG = "MuyianBill"
    private const val PREFS = "muyian_bill"
    private const val KEY_MERCHANT = "capture_merchant_category_v1"
    private const val KEY_PKG = "capture_pkg_category_v1"
    private const val MAX_KEYS = 200

    /** 没有历史与关键词时的默认分类：三餐是日常最高频的支出，改一次会被本地学习覆盖。 */
    internal const val DEFAULT_EXPENSE_CATEGORY = "三餐"

    /**
     * 商户/页面证据 → 分类的关键词表，只用于弹层预选。
     * 按"越具体越靠前"排列，避免"手机话费"被"手机"抢成电器数码。
     */
    internal val KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "三餐" to listOf(
            "外卖", "快餐", "餐饮", "餐厅", "饭店", "食堂", "早餐", "午餐", "晚餐", "夜宵",
            "小吃", "米线", "拉面", "麻辣烫", "火锅", "烧烤", "汉堡", "披萨", "面馆",
            "肯德基", "麦当劳", "华莱士", "塔斯汀", "沙县"
        ),
        "零食" to listOf(
            "零食", "奶茶", "咖啡", "水果", "蛋糕", "甜品", "面包", "饼干", "巧克力",
            "冰淇淋", "雪糕", "茶饮", "喜茶", "奈雪", "蜜雪", "瑞幸", "星巴克", "库迪",
            "古茗", "茶百道", "一点点"
        ),
        "日用品" to listOf(
            "超市", "便利店", "沃尔玛", "永辉", "盒马", "罗森", "名创优品", "屈臣氏", "日用"
        ),
        "话费网费" to listOf("话费", "流量", "宽带", "中国移动", "中国联通", "中国电信"),
        "交通" to listOf(
            "地铁", "公交", "打车", "滴滴", "出租", "高铁", "火车票", "12306", "共享单车"
        ),
        "汽车/加油" to listOf("加油", "中石化", "中石油", "充电桩"),
        "医疗" to listOf("药店", "药房", "医院", "诊所", "挂号", "体检"),
        "娱乐" to listOf("电影", "影院", "影城", "游戏", "网吧", "ktv", "门票", "剧本杀"),
        "衣服" to listOf("优衣库", "服装", "服饰", "鞋"),
        "水电煤" to listOf("电费", "水费", "燃气", "煤气"),
        "住房" to listOf("房租", "物业"),
        "学习" to listOf("书店", "图书", "课程", "学费", "培训"),
        "美妆" to listOf("化妆品", "护肤", "美妆", "面膜"),
        "宠物" to listOf("宠物", "猫粮", "狗粮"),
        "烟酒" to listOf("烟酒", "白酒", "啤酒", "香烟"),
        "旅行" to listOf("酒店", "民宿", "机票", "旅行", "景区"),
        "电器数码" to listOf("数码", "电脑", "耳机", "手机"),
        "孩子" to listOf("奶粉", "尿不湿", "玩具", "童装"),
        "请客送礼" to listOf("礼品", "礼物", "鲜花"),
        "发红包" to listOf("红包")
    )

    fun suggest(
        context: Context,
        pkg: String,
        merchant: String,
        direction: Direction,
        extraText: String = ""
    ): String {
        when (direction) {
            Direction.INCOME -> return "收入"
            Direction.REFUND -> return "退款"
            Direction.TRANSFER -> return "转账"
            Direction.REPAYMENT -> return "还款"
            else -> Unit
        }
        val mKey = CaptureText.merchantKey(merchant)
        if (mKey.isNotEmpty()) {
            val hit = topCategory(readMap(context, KEY_MERCHANT).optJSONObject(mKey))
            if (hit.isNotEmpty()) return hit
        }
        if (pkg.isNotEmpty()) {
            val hit = topCategory(readMap(context, KEY_PKG).optJSONObject(pkg))
            if (hit.isNotEmpty()) return hit
        }
        return keywordCategory(merchant, extraText)
    }

    /**
     * 没有历史记录时的纯文本判断：先看商户名，再看页面证据行。
     * 命中关键词返回对应分类，否则返回 [DEFAULT_EXPENSE_CATEGORY]。
     */
    internal fun keywordCategory(merchant: String, extraText: String = ""): String {
        val text = CaptureText.merchantKey(
            if (extraText.isEmpty()) merchant else merchant + " " + extraText
        )
        if (text.isEmpty()) return DEFAULT_EXPENSE_CATEGORY
        for ((category, words) in KEYWORDS) {
            for (word in words) {
                if (text.contains(word)) return category
            }
        }
        return DEFAULT_EXPENSE_CATEGORY
    }

    fun record(context: Context, pkg: String, merchant: String, costType: String) {
        val category = costType.trim()
        if (category.isEmpty() || category == "消费" || category == "收入" ||
            category == "退款" || category == "转账" || category == "还款"
        ) return
        val mKey = CaptureText.merchantKey(merchant)
        if (mKey.isNotEmpty()) {
            bump(context, KEY_MERCHANT, mKey, category)
        }
        if (pkg.isNotEmpty()) {
            bump(context, KEY_PKG, pkg, category)
        }
    }

    private fun bump(context: Context, prefKey: String, key: String, category: String) {
        try {
            val map = readMap(context, prefKey)
            val inner = map.optJSONObject(key) ?: JSONObject()
            inner.put(category, inner.optInt(category, 0) + 1)
            map.put(key, inner)
            if (map.length() > MAX_KEYS) {
                // 简单淘汰：删掉第一个键；本地学习不需要严格 LRU
                val it = map.keys()
                if (it.hasNext()) map.remove(it.next())
            }
            prefs(context).edit().putString(prefKey, map.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "record category learning failed", e)
        }
    }

    private fun topCategory(inner: JSONObject?): String {
        if (inner == null) return ""
        var best = ""
        var bestCount = 0
        val keys = inner.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val c = inner.optInt(k, 0)
            if (c > bestCount) {
                bestCount = c
                best = k
            }
        }
        return best
    }

    private fun readMap(context: Context, prefKey: String): JSONObject {
        val raw = prefs(context).getString(prefKey, "") ?: ""
        if (raw.isEmpty()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().remove(KEY_MERCHANT).remove(KEY_PKG).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear category learning failed", e)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
