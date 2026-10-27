package com.muyian.debt.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muyian.debt.data.Cats
import com.muyian.debt.data.CategoryData
import com.muyian.debt.data.Prefs

/** 分类数据的单一读写入口；页面拿到的是深拷贝，避免直接改状态。 */
class CategoryState(private val prefs: Prefs) {
    var categories by mutableStateOf(Cats.load(prefs))
        private set

    fun save(data: CategoryData) {
        categories = data
        Cats.save(prefs, data)
    }

    fun snapshot(): CategoryData = categories.deepCopy()
}
