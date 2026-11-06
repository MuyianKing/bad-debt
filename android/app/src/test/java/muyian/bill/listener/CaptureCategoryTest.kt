package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分类预选的回归：关键词命中、优先级，以及无证据时的默认"三餐"。
 * 这里只测纯逻辑；商户历史的优先级由 CaptureLearning.suggest 在真机上验证。
 */
class CaptureCategoryTest {

    @Test
    fun `外卖与快餐商户归三餐`() {
        assertEquals("三餐", CaptureLearning.keywordCategory("美团外卖"))
        assertEquals("三餐", CaptureLearning.keywordCategory("肯德基宅急送"))
        assertEquals("三餐", CaptureLearning.keywordCategory("沙县小吃"))
    }

    @Test
    fun `茶饮与水果归零食`() {
        assertEquals("零食", CaptureLearning.keywordCategory("瑞幸咖啡"))
        assertEquals("零食", CaptureLearning.keywordCategory("蜜雪冰城"))
        assertEquals("零食", CaptureLearning.keywordCategory("百果园水果店"))
    }

    @Test
    fun `话费优先于手机`() {
        assertEquals("话费网费", CaptureLearning.keywordCategory("中国移动手机话费充值"))
        assertEquals("电器数码", CaptureLearning.keywordCategory("京东手机旗舰店"))
    }

    @Test
    fun `交通日用医疗等常见商户`() {
        assertEquals("交通", CaptureLearning.keywordCategory("滴滴出行"))
        assertEquals("日用品", CaptureLearning.keywordCategory("罗森便利店"))
        assertEquals("医疗", CaptureLearning.keywordCategory("老百姓大药房"))
        assertEquals("娱乐", CaptureLearning.keywordCategory("万达影城"))
    }

    @Test
    fun `没有关键词默认三餐`() {
        assertEquals("三餐", CaptureLearning.keywordCategory("某某科技有限公司"))
        assertEquals("三餐", CaptureLearning.keywordCategory(""))
    }

    @Test
    fun `商户为空时用页面证据兜底`() {
        assertEquals("交通", CaptureLearning.keywordCategory("", "支付成功 打车 18.00"))
        assertEquals("三餐", CaptureLearning.keywordCategory("", "支付成功 实付金额 21.00"))
    }
}
