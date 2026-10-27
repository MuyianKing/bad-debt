package com.muyian.debt.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * 本地设置存储。旧 uni-app 版本把分类/主题/角色选择存在 databases/DCStorage 里，
 * 同包名升级时这里会做一次只读迁移；新装用户直接用默认值。
 */
class Prefs(context: Context) {
    val sp = context.getSharedPreferences("muyian_native", Context.MODE_PRIVATE)

    init {
        migrateUniStorage(context)
    }

    fun str(key: String, def: String = ""): String = sp.getString(key, def) ?: def

    fun putStr(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    fun bool(key: String, def: Boolean): Boolean {
        val raw = sp.getString(key, null) ?: return sp.getBoolean(key, def)
        // uni-app 的布尔值可能被包成 {"type":"boolean","data":true}
        if (raw.startsWith("{")) {
            return try {
                org.json.JSONObject(raw).optBoolean("data", def)
            } catch (_: Exception) {
                def
            }
        }
        return when (raw.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> def
        }
    }

    fun putBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    private fun migrateUniStorage(context: Context) {
        if (sp.getBoolean("uni_migrated_v1", false)) return
        try {
            val dbFile = context.getDatabasePath("DCStorage")
            if (dbFile.exists()) {
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                try {
                    val tables = ArrayList<String>()
                    db.rawQuery(
                        "select name from sqlite_master where type='table' and name like '%\\_storage' escape '\\'",
                        null
                    ).use { c ->
                        while (c.moveToNext()) c.getString(0)?.let { tables.add(it) }
                    }
                    val editor = sp.edit()
                    for (table in tables) {
                        try {
                            db.rawQuery("select key,value from `$table`", null).use { c ->
                                while (c.moveToNext()) {
                                    val key = c.getString(0) ?: continue
                                    val value = c.getString(1) ?: continue
                                    if (editor.putString(key, value) != null) {
                                        // putString 返回 editor 本身，不需要额外判断
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // 单表读取失败不影响其它键
                        }
                    }
                    editor.putBoolean("uni_migrated_v1", true).apply()
                } finally {
                    db.close()
                }
            } else {
                sp.edit().putBoolean("uni_migrated_v1", true).apply()
            }
        } catch (_: Exception) {
            // 迁移失败不阻断启动，下次启动继续尝试
        }
    }
}
