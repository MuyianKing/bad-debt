package muyian.bill.listener

import java.util.regex.Pattern

/**
 * 采集规则表：App 角色/适配器、页面标记、金额锚点与排除词。
 * 主流程不写 `if (pkg == ...)`，新增 App 只改这里。
 */
object CaptureRules {

    // ---------- 页面标记 ----------

    val FAILURE = Pattern.compile(
        "支付失败|付款失败|扣款失败|交易失败|支付超时|付款超时|支付未完成|交易关闭|订单已关闭|" +
            "订单已取消|已取消|取消支付|未支付|支付异常|退款失败"
    )
    val PENDING = Pattern.compile("处理中|支付处理中|等待支付|待支付|待付款|支付中|确认中")
    val BILL_LIST = Pattern.compile(
        "账单明细|全部账单|收支统计|交易记录|账单列表|本月支出|本月收入|" +
            "收入明细|支出明细|收支明细|最近消息|最近交易|最近记录|消息中心|条新消息|" +
            "最近.{0,20}联系人.{0,20}我的"
    )
    /** 支付 App 首页：有扫码/收付款/卡包入口，或最近消息列表；整页跳过 */
    val PAY_APP_HOME = Pattern.compile(
        "扫一扫.{0,20}收付款|收付款.{0,20}卡包|收付款.{0,20}钱包|钱包.{0,20}收付款|" +
            "最近消息|最近交易|消息中心"
    )
    val ORDER_MGMT = Pattern.compile(
        "申请退款|查看物流|确认收货|待评价|去评价|再来一单|已送达|下单时间|" +
            "我的订单|全部订单|订单列表|订单管理|退款/售后"
    )
    val AD = Pattern.compile(
        "最高|抽奖|限时|立即领取|开通会员|邀请好友|领红包|天天领|活动|立减|优惠券|" +
            "折扣|特惠|秒杀|广告|免费领|0元购"
    )
    val CHAT = Pattern.compile(
        "发送|按住\\s*说话|输入消息|聊天信息|朋友圈|通讯录|语音通话|视频通话|" +
            "消息免打扰|置顶聊天|拍一拍"
    )
    /** 强会话特征：支付结果页上的"发送给朋友"不算 */
    val CHAT_STRICT = Pattern.compile(
        "按住\\s*说话|输入消息|聊天信息|朋友圈|通讯录|语音通话|视频通话|" +
            "消息免打扰|置顶聊天|拍一拍"
    )
    /** 支付凭证特征：微信支付服务号会话里唯一一条凭证可以用来区分"聊天"与"凭证" */
    val RECEIPT_MARK = Pattern.compile(
        "微信支付凭证|交易单号|商户单号|收款方|转账单号|支付凭证|交易流水号|订单号"
    )
    val PAY_TRIGGER = Pattern.compile(
        "确认支付|立即支付|去支付|确认付款|立即付款|去付款|提交订单|收银台|" +
            "选择支付方式|输入支付密码|指纹支付|面容支付|免密支付|同意协议并支付"
    )

    val EXPENSE_MARK = Pattern.compile(
        "支付成功|付款成功|扣款成功|付款完成|交易成功|消费成功|已支付|支付完成|转出成功|转账成功"
    )
    val INCOME_MARK = Pattern.compile(
        "收款成功|收款到账|已到账|已收款|收入|入账|转入成功|存入零钱|存入余额|到账"
    )
    val REFUND_MARK = Pattern.compile("退款成功|退款到账|退回成功|已退款|退款完成|退款已原路退回")
    val REPAYMENT_MARK = Pattern.compile("还款成功|已还款|还款完成|信用卡还款")
    val TRANSFER_SELF_MARK = Pattern.compile(
        "提现成功|充值成功|转入零钱通|转入余额宝|存入零钱|存入余额|转出到卡|本人账户|转入本人|" +
            "提现到账|到账零钱"
    )
    val TRANSFER_OUT_MARK = Pattern.compile("转账成功|转出成功|已转账|转账给")
    /** 转账历史/收款人列表的行标记（不是完成语义） */
    val TRANSFER_ROW = Pattern.compile("转给TA|收到TA")

    /** 所有完成语义的并集：用于判断"同一页是否出现多行交易" */
    val ANY_COMPLETION_MARK = Pattern.compile(
        "支付成功|付款成功|扣款成功|付款完成|交易成功|消费成功|已支付|支付完成|转出成功|转账成功|" +
            "收款成功|收款到账|已到账|已收款|入账|转入成功|存入零钱|存入余额|" +
            "退款成功|退款到账|退回成功|已退款|退款完成|" +
            "还款成功|已还款|还款完成|" +
            "提现成功|充值成功|转入零钱通|转入余额宝|转出到卡|本人账户|转入本人|" +
            // 转账页的列表行：不是完成语义，但用于识别"一页多笔"
            "转给TA|收到TA"
    )

    val EXPENSE_WORD = Pattern.compile("支出|付款|支付|消费|扣款|转出|买单|代扣|缴费")
    val INCOME_WORD = Pattern.compile("退款|退回|返还|收入|收款|到账|入账|转入")
    val NOTIFY_IGNORE = Pattern.compile(
        "验证码|登录提醒|配置更新|待支付|未支付|还未支付|支付失败|付款失败|扣款失败|" +
            "交易关闭|自动取消|订单已关闭|订单已取消|付款超时|处理中|退款审核中|" +
            "退款申请|退款处理中|退款中|申请退款"
    )

    // ---------- 金额 ----------

    /** 余额/额度/优惠/费用/积分/比率类数字一律不进候选。 */
    val EXCLUDES: List<String> = listOf(
        // 余额
        "余额", "账户余额", "可用余额", "零钱", "零钱通", "余额宝", "可用",
        // 额度
        "额度", "限额", "单笔限额", "剩余额度", "可用额度", "上限",
        // 优惠
        "优惠", "立减", "折扣", "原价", "已省", "节省", "红包", "返现", "抵扣", "优惠券", "满减",
        // 费用
        "手续费", "服务费", "运费", "配送费", "打包费",
        // 积分
        "积分", "京豆", "金币", "成长值",
        // 比率
        "汇率", "利率", "费率",
        // 其它
        "尾号", "验证码"
    )

    val STRONG_ANCHORS: List<String> = listOf(
        "实付金额", "实付", "实际支付", "支付金额", "付款金额", "交易金额",
        "消费金额", "扣款金额", "支出金额", "收款金额", "到账金额", "退款金额",
        "转入金额", "转出金额", "转账金额", "取现金额", "存入金额",
        "本次消费", "本次支出", "本次支付", "应付金额", "待付金额", "账单金额", "交易额", "还款金额"
    )

    val WEAK_ANCHORS: List<String> = listOf(
        "金额", "合计", "总计", "应付", "待付", "需付",
        "消费", "支付", "支出", "收入", "转入", "转出", "扣款", "付款", "收款", "入账", "提现", "退款"
    )

    private val ALIPAY_ANCHORS = listOf("付款金额", "支付金额", "交易金额", "订单金额", "实付金额")
    private val WECHAT_ANCHORS = listOf("支付金额", "付款金额", "收款金额", "转账金额", "退款金额")
    private val MEITUAN_ANCHORS = listOf("实付金额", "实付", "订单金额", "支付金额")
    private val JD_ANCHORS = listOf("应付金额", "实付金额", "支付金额", "订单金额")
    private val UNIONPAY_ANCHORS = listOf("交易金额", "支付金额", "订单金额")
    private val BANK_ANCHORS = listOf(
        "交易金额", "支出金额", "消费金额", "转账金额", "取现金额", "存入金额"
    )

    // ---------- 系统界面 ----------

    val SYSTEM_UI_PKGS: Set<String> = setOf(
        "android", "com.android.systemui", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.miui.securitycenter",
        "com.lbe.security.miui"
    )

    fun isSystemUi(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        if (SYSTEM_UI_PKGS.contains(pkg)) return true
        return pkg.contains("systemui")
    }

    // ---------- App 注册表 ----------

    private val BANK_PREFIXES = listOf(
        "com.icbc", "com.chinamworld.", "com.cmbchina.", "com.ccb.", "com.bankcomm.",
        "cn.com.cmbc.", "com.cib.", "com.cebbank.", "com.spdb", "com.pingan.",
        "com.citicbank.", "com.hxb.", "com.psbc.", "com.boc."
    )

    private val EXACT_RULES: List<AppRule> = buildList {
        add(AppRule(
            "com.eg.android.AlipayGphone", AppRole.PAY, AdapterId.ALIPAY, "支付宝",
            ALIPAY_ANCHORS, listOf("余额宝", "花呗", "信用额度"),
            listOf("支付宝", "交易提醒", "支付助手", "余额宝", "花呗"), listOf("支付宝")
        ))
        add(AppRule(
            "com.tencent.mm", AppRole.PAY, AdapterId.WECHAT, "微信",
            WECHAT_ANCHORS, listOf("零钱", "零钱通"),
            listOf("微信支付"), listOf("微信支付")
        ))
        add(AppRule(
            "com.unionpay", AppRole.PAY, AdapterId.UNIONPAY, "云闪付",
            UNIONPAY_ANCHORS, emptyList(), listOf("云闪付", "银联"), listOf("云闪付", "银联")
        ))
        add(AppRule(
            "com.unionpay.tsmservice", AppRole.PAY, AdapterId.UNIONPAY, "云闪付",
            UNIONPAY_ANCHORS, emptyList(), listOf("云闪付", "银联"), listOf("云闪付", "银联")
        ))
        add(AppRule(
            "com.sankuai.meituan", AppRole.HOST, AdapterId.MEITUAN, "美团",
            MEITUAN_ANCHORS, listOf("配送费", "打包费")
        ))
        add(AppRule(
            "com.sankuai.meituan.takeoutnew", AppRole.HOST, AdapterId.MEITUAN, "美团外卖",
            MEITUAN_ANCHORS, listOf("配送费", "打包费")
        ))
        add(AppRule(
            "com.dianping.v1", AppRole.HOST, AdapterId.MEITUAN, "大众点评",
            MEITUAN_ANCHORS, listOf("配送费", "打包费")
        ))
        add(AppRule(
            "com.jingdong.app.mall", AppRole.HOST, AdapterId.JD, "京东",
            JD_ANCHORS, listOf("京豆", "白条")
        ))
        add(AppRule(
            "com.jd.jrapp", AppRole.PAY, AdapterId.JD, "京东金融",
            JD_ANCHORS, listOf("京豆", "白条")
        ))
        val banks = listOf(
            "com.icbc", "com.chinamworld.main", "com.cmbchina.ccd.pluto.cmbActivity",
            "com.ccb.longjiLife", "com.bankcomm.Bankcomm", "cn.com.cmbc.newmbank",
            "com.cib.cibmb", "com.cebbank.mobile.cemb", "com.spdbccc.app",
            "com.pingan.paces.ccms", "com.citicbank.mobile", "com.hxb.mobile.client",
            "com.psbc.mobilebank", "com.boc.bocsoft.mobile"
        )
        for (pkg in banks) {
            add(AppRule(pkg, AppRole.PAY, AdapterId.BANK, "", BANK_ANCHORS, emptyList()))
        }
    }

    private val PREFIX_RULES: List<AppRule> = listOf(
        AppRule("com.alipay.", AppRole.PAY, AdapterId.ALIPAY, "", ALIPAY_ANCHORS, emptyList(),
            listOf("支付宝", "交易提醒", "支付助手"), listOf("支付宝")),
        AppRule("com.unionpay", AppRole.PAY, AdapterId.UNIONPAY, "", UNIONPAY_ANCHORS, emptyList(),
            listOf("云闪付", "银联"), listOf("云闪付", "银联")),
        AppRule("com.sankuai.", AppRole.HOST, AdapterId.MEITUAN, "", MEITUAN_ANCHORS, emptyList()),
        AppRule("com.dianping.", AppRole.HOST, AdapterId.MEITUAN, "", MEITUAN_ANCHORS, emptyList()),
        AppRule("com.jingdong.", AppRole.HOST, AdapterId.JD, "", JD_ANCHORS, emptyList()),
        AppRule("com.jd.", AppRole.HOST, AdapterId.JD, "", JD_ANCHORS, emptyList())
    )

    /** 内置规则：精确包名优先，其次前缀，最后银行前缀。未命中返回 null（由注册表决定是否用户添加）。 */
    fun builtinRule(pkg: String): AppRule? {
        for (rule in EXACT_RULES) if (rule.pkg == pkg) return rule
        for (rule in PREFIX_RULES) if (pkg.startsWith(rule.pkg)) return rule
        if (BANK_PREFIXES.any { pkg.startsWith(it) }) {
            return AppRule(pkg, AppRole.PAY, AdapterId.BANK, "", BANK_ANCHORS, emptyList())
        }
        return null
    }

    fun builtinApps(): List<AppRule> {
        val out = ArrayList<AppRule>()
        out.addAll(EXACT_RULES)
        out.addAll(PREFIX_RULES)
        return out
    }

    /** 只含可直接按包名查询的规则（不含前缀规则）；设置页默认列表用它，避免枚举全部应用。 */
    fun builtinExactApps(): List<AppRule> = EXACT_RULES

    val LABEL_KEYWORDS = listOf(
        "银行", "信用卡", "云闪付", "数字人民币", "翼支付"
    )

    fun defaultAnchors(adapter: AdapterId): List<String> = when (adapter) {
        AdapterId.ALIPAY -> ALIPAY_ANCHORS
        AdapterId.WECHAT -> WECHAT_ANCHORS
        AdapterId.UNIONPAY -> UNIONPAY_ANCHORS
        AdapterId.BANK -> BANK_ANCHORS
        AdapterId.MEITUAN -> MEITUAN_ANCHORS
        AdapterId.JD -> JD_ANCHORS
        AdapterId.GENERIC -> emptyList()
    }

    /** 从文本里取真实渠道；银行 App 不能再统一写成"银行卡"。 */
    fun inferChannel(adapter: AdapterId, ruleLabel: String, text: String): String {
        val bank = extractBankName(text)
        val tail = extractCardTail(text)
        when (adapter) {
            AdapterId.ALIPAY -> return "支付宝"
            AdapterId.WECHAT -> return "微信"
            AdapterId.UNIONPAY -> return "云闪付"
            AdapterId.BANK -> {
                val name = bank.ifEmpty { ruleLabel.ifEmpty { "银行卡" } }
                return if (tail.isNotEmpty()) name + "(" + tail + ")" else name
            }
            else -> Unit
        }
        val detected = detectPayWord(text)
        if (detected.isNotEmpty()) return detected
        return if (bank.isNotEmpty()) {
            if (tail.isNotEmpty()) bank + "(" + tail + ")" else bank
        } else {
            "银行卡"
        }
    }

    fun extractBankName(text: String): String {
        if (text.isEmpty()) return ""
        val m = Pattern.compile("([\\u4e00-\\u9fa5]{2,12}(?:银行|农商行|信用社|信用合作联社))")
            .matcher(text)
        var last = ""
        while (m.find()) {
            val v = m.group(1) ?: ""
            if (v.length >= 3 && !v.contains("手机银行") && !v.contains("网上银行")) last = v
        }
        return last
    }

    fun extractCardTail(text: String): String {
        if (text.isEmpty()) return ""
        val m = Pattern.compile("(?:尾号|末四位|尾数为|卡号后四位)[：:\\s]*([0-9]{4})")
            .matcher(text)
        if (m.find()) return m.group(1) ?: ""
        return ""
    }

    fun detectPayWord(text: String): String {
        if (text.contains("微信支付") || text.contains("零钱")) return "微信"
        if (text.contains("支付宝") || text.contains("花呗") || text.contains("余额宝")) return "支付宝"
        if (text.contains("云闪付") || text.contains("银联")) return "云闪付"
        return ""
    }
}
