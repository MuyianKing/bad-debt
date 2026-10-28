package muyian.bill.listener

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.regex.Pattern

/** 账单 SQLite 的唯一实现：建表/旧库补列、CRUD、批量导入与备份恢复。 */
internal object BillDb {
    private const val TAG = "MuyianBill"
    private const val DB_NAME = "muyian.db"
    private val dbLock = Any()

    @Volatile
    private var schemaReady = false

fun saveBillEx(
    context: Context, date: String, money: String, isIncome: Boolean,
    bankName: String, costType: String, orderRemark: String,
    reimb: Boolean, starred: Boolean
): String {
    val result = JSONObject()
    val amount = try {
        money.trim().toDouble()
    } catch (e: NumberFormatException) {
        result.put("success", false).put("error", "invalid money")
        return result.toString()
    }
    if (amount <= 0) {
        result.put("success", false).put("error", "invalid money")
        return result.toString()
    }
    val now = System.currentTimeMillis()
    val nowCal = Calendar.getInstance()
    nowCal.timeInMillis = now
    var cal = nowCal
    val dateTrim = date.trim()
    if (dateTrim.isNotEmpty()) {
        val parts = dateSplit(dateTrim)
        if (parts == null) {
            result.put("success", false).put("error", "invalid date")
            return result.toString()
        }
        cal = Calendar.getInstance()
        cal.set(parts[0], parts[1] - 1, parts[2], 12, 0, 0)
    }
    val cv = ContentValues()
    cv.put("year", cal.get(Calendar.YEAR))
    cv.put("month", cal.get(Calendar.MONTH) + 1)
    cv.put("day", cal.get(Calendar.DAY_OF_MONTH))
    cv.put("clock", SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(now)))
    cv.put("money", if (isIncome) amount else -amount)
    cv.put("bankName", bankName)
    cv.put("costType", if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType)
    cv.put("orderRemark", orderRemark)
    cv.put("source", "manual")
    cv.put("sourcePkg", "")
    cv.put("createdAt", now)
    cv.put("reimb", if (reimb) 1 else 0)
    cv.put("starred", if (starred) 1 else 0)
    val db = openDb(context)
    val id = try {
        db.insert("bills", null, cv)
    } finally {
        db.close()
    }
    result.put("success", id > 0).put("id", id)
    return result.toString()
}

/**
 * 编辑已入账账单（保留 source/sourcePkg/createdAt）。
 * @param date "YYYY-MM-DD"
 * @return {"success":true}
 */

fun updateBill(
    context: Context, id: String, date: String, money: String, isIncome: Boolean,
    bankName: String, costType: String, orderRemark: String,
    reimb: Boolean, starred: Boolean
): String {
    val result = JSONObject()
    val amount = try {
        money.trim().toDouble()
    } catch (e: NumberFormatException) {
        result.put("success", false).put("error", "invalid money")
        return result.toString()
    }
    if (amount <= 0) {
        result.put("success", false).put("error", "invalid money")
        return result.toString()
    }
    val dateTrim = date.trim()
    val parts = dateSplit(dateTrim)
    if (parts == null) {
        result.put("success", false).put("error", "invalid date")
        return result.toString()
    }
    val cv = ContentValues()
    cv.put("year", parts[0])
    cv.put("month", parts[1])
    cv.put("day", parts[2])
    cv.put("money", if (isIncome) amount else -amount)
    cv.put("bankName", bankName)
    cv.put("costType", if (isIncome) "收入" else if (costType.isEmpty()) "消费" else costType)
    cv.put("orderRemark", orderRemark)
    cv.put("reimb", if (reimb) 1 else 0)
    cv.put("starred", if (starred) 1 else 0)
    val db = openDb(context)
    val updated = try {
        db.update("bills", cv, "id=?", arrayOf(id.trim()))
    } finally {
        db.close()
    }
    result.put("success", updated > 0)
    return result.toString()
}

private fun dateSplit(date: String): IntArray? {
    val m = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(date)
    if (!m.find()) return null
    val y = m.group(1)!!.toInt()
    val mo = m.group(2)!!.toInt()
    val d = m.group(3)!!.toInt()
    if (mo < 1 || mo > 12 || d < 1 || d > 31) return null
    return intArrayOf(y, mo, d)
}

fun getYearBills(context: Context, year: String): String {
    val db = openDb(context)
    var cursor: Cursor? = null
    val array = JSONArray()
    try {
        cursor = db.query(
            "bills", null,
            "year=?",
            arrayOf(year.trim()),
            null, null, "id desc"
        )
        var count = 0
        while (cursor.moveToNext() && count < 5000) {
            array.put(billJson(cursor))
            count++
        }
    } catch (e: Exception) {
        Log.e(TAG, "query year bills failed", e)
    } finally {
        cursor?.close()
        db.close()
    }
    return array.toString()
}

fun getMonthBills(context: Context, year: String, month: String): String {
    val db = openDb(context)
    var cursor: Cursor? = null
    val array = JSONArray()
    try {
        cursor = db.query(
            "bills", null,
            "year=? and month=?",
            arrayOf(year.trim(), month.trim()),
            null, null, "id desc"
        )
        var count = 0
        while (cursor.moveToNext() && count < 500) {
            array.put(billJson(cursor))
            count++
        }
    } catch (e: Exception) {
        Log.e(TAG, "query month bills failed", e)
    } finally {
        cursor?.close()
        db.close()
    }
    return array.toString()
}

fun deleteBill(context: Context, id: String): Boolean {
    val db = openDb(context)
    return try {
        db.delete("bills", "id=?", arrayOf(id.trim())) > 0
    } catch (e: Exception) {
        Log.e(TAG, "delete bill failed", e)
        false
    } finally {
        db.close()
    }
}

/** 统计一组钱迹单号在库中已存在的条数：{"count":n} */
fun countExistingOrders(context: Context, orderNosJson: String): String {
    var count = 0
    val arr = try {
        JSONArray(orderNosJson)
    } catch (e: Exception) {
        null
    }
    if (arr != null) {
        val db = openDb(context)
        var cursor: Cursor? = null
        try {
            val existing = HashSet<String>()
            cursor = db.rawQuery(
                "select orderNo from bills where orderNo is not null and orderNo != ''", null
            )
            while (cursor.moveToNext()) {
                val v = cursor.getString(0)
                if (v != null && v.isNotEmpty()) existing.add(v)
            }
            var i = 0
            while (i < arr.length()) {
                val v = arr.optString(i, "").trim()
                i = i + 1
                if (v.isNotEmpty() && existing.contains(v)) count = count + 1
            }
        } catch (e: Exception) {
            Log.w(TAG, "count existing orders failed", e)
        } finally {
            cursor?.close()
            db.close()
        }
    }
    val out = JSONObject()
    out.put("count", count)
    return out.toString()
}

/**
 * 批量入库：billsJson 是页面映射好的账单数组（金额已带符号，收入 costType 由页面固定为"收入"）。
 * 按 orderNo（钱迹单号）去重：库里已有或本批已插入的都跳过。单事务，返回 {"success","inserted","skipped"}。
 */
fun commitImportBills(context: Context, billsJson: String): String {
    val result = JSONObject()
    val arr = try {
        JSONArray(billsJson)
    } catch (e: Exception) {
        null
    }
    if (arr == null) {
        result.put("success", false).put("error", "bad json")
        return result.toString()
    }
    var inserted = 0
    var skipped = 0
    val db = openDb(context)
    try {
        db.beginTransaction()
        try {
            val existing = HashSet<String>()
            var cursor: Cursor? = null
            try {
                cursor = db.rawQuery(
                    "select orderNo from bills where orderNo is not null and orderNo != ''", null
                )
                while (cursor.moveToNext()) {
                    val v = cursor.getString(0)
                    if (v != null && v.isNotEmpty()) existing.add(v)
                }
            } finally {
                cursor?.close()
            }
            var i = 0
            while (i < arr.length()) {
                val o = arr.optJSONObject(i)
                i = i + 1
                if (o == null) {
                    skipped = skipped + 1
                    continue
                }
                val money = o.optString("money", "0").trim().toDoubleOrNull() ?: 0.0
                if (money == 0.0) {
                    skipped = skipped + 1
                    continue
                }
                val orderNo = o.optString("orderNo", "")
                if (orderNo.isNotEmpty() && existing.contains(orderNo)) {
                    skipped = skipped + 1
                    continue
                }
                val cv = ContentValues()
                cv.put("year", o.optInt("year"))
                cv.put("month", o.optInt("month"))
                cv.put("day", o.optInt("day"))
                cv.put("clock", o.optString("clock"))
                cv.put("money", money)
                cv.put("bankName", o.optString("bankName", "钱迹"))
                cv.put("costType", o.optString("costType", "消费"))
                cv.put("orderRemark", o.optString("orderRemark", ""))
                cv.put("source", "qianji")
                cv.put("sourcePkg", "")
                cv.put("createdAt", o.optLong("createdAt", System.currentTimeMillis()))
                cv.put("reimb", o.optInt("reimb", 0))
                cv.put("starred", o.optInt("starred", 0))
                cv.put("orderNo", orderNo)
                db.insert("bills", null, cv)
                if (orderNo.isNotEmpty()) existing.add(orderNo)
                inserted = inserted + 1
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    } catch (e: Exception) {
        Log.e(TAG, "commit import bills failed", e)
        result.put("success", false).put("error", "db error")
        return result.toString()
    } finally {
        db.close()
    }
    result.put("success", true)
    result.put("inserted", inserted)
    result.put("skipped", skipped)
    return result.toString()
}

// ---------- 本应用备份（导出 / 恢复） ----------

/**
 * 备份恢复用：按原 id 回填账单。库里已有同 id 或同单号的跳过，
 * 因此重复恢复是幂等的；id 为空时退回自增。单事务，返回 {"success","inserted","skipped"}。
 */
fun commitBackupBills(context: Context, billsJson: String): String {
    val result = JSONObject()
    val arr = try {
        JSONArray(billsJson)
    } catch (e: Exception) {
        null
    }
    if (arr == null) {
        result.put("success", false).put("error", "bad json")
        return result.toString()
    }
    var inserted = 0
    var skipped = 0
    val db = openDb(context)
    try {
        db.beginTransaction()
        try {
            val existingIds = HashSet<String>()
            val existingOrders = HashSet<String>()
            var cursor: Cursor? = null
            try {
                cursor = db.rawQuery("select id, orderNo from bills", null)
                while (cursor.moveToNext()) {
                    existingIds.add("" + cursor.getLong(0))
                    val o = cursor.getString(1)
                    if (o != null && o.isNotEmpty()) existingOrders.add(o)
                }
            } finally {
                cursor?.close()
            }
            var i = 0
            while (i < arr.length()) {
                val o = arr.optJSONObject(i)
                i = i + 1
                if (o == null) {
                    skipped = skipped + 1
                    continue
                }
                val money = o.optDouble("money", 0.0)
                if (money == 0.0 || money.isNaN()) {
                    skipped = skipped + 1
                    continue
                }
                val id = o.optLong("id", 0L)
                val orderNo = o.optString("orderNo", "")
                if (id > 0 && existingIds.contains("" + id)) {
                    skipped = skipped + 1
                    continue
                }
                if (orderNo.isNotEmpty() && existingOrders.contains(orderNo)) {
                    skipped = skipped + 1
                    continue
                }
                val cv = ContentValues()
                if (id > 0) cv.put("id", id)
                cv.put("year", o.optInt("year"))
                cv.put("month", o.optInt("month"))
                cv.put("day", o.optInt("day"))
                cv.put("clock", o.optString("clock"))
                cv.put("money", money)
                cv.put("bankName", o.optString("bankName"))
                cv.put("costType", o.optString("costType"))
                cv.put("orderRemark", o.optString("orderRemark", ""))
                cv.put("source", o.optString("source", "manual"))
                cv.put("sourcePkg", o.optString("sourcePkg", ""))
                cv.put("createdAt", o.optLong("createdAt", System.currentTimeMillis()))
                cv.put("reimb", o.optInt("reimb", 0))
                cv.put("starred", o.optInt("starred", 0))
                cv.put("orderNo", orderNo)
                val rowId = db.insert("bills", null, cv)
                if (rowId > 0) {
                    inserted = inserted + 1
                    if (id > 0) existingIds.add("" + id)
                    if (orderNo.isNotEmpty()) existingOrders.add(orderNo)
                } else {
                    skipped = skipped + 1
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    } catch (e: Exception) {
        Log.e(TAG, "commit backup bills failed", e)
        result.put("success", false).put("error", "db error")
        return result.toString()
    } finally {
        db.close()
    }
    result.put("success", true)
    result.put("inserted", inserted)
    result.put("skipped", skipped)
    return result.toString()
}

/** 全量账单（导出用）：按 id 升序分页，limit 上限 2000，返回 {"total":n,"rows":[…]} */
fun getAllBills(context: Context, offset: String, limit: String): String {
    val startRaw = offset.trim().toIntOrNull() ?: 0
    val start = if (startRaw < 0) 0 else startRaw
    var count = limit.trim().toIntOrNull() ?: 1000
    if (count < 1) count = 1
    if (count > 2000) count = 2000
    val out = JSONObject()
    val arr = JSONArray()
    val db = openDb(context)
    var cursor: Cursor? = null
    try {
        cursor = db.rawQuery(
            "select * from bills order by id asc limit " + count + " offset " + start, null
        )
        while (cursor.moveToNext()) arr.put(billJson(cursor))
        out.put("total", countAllBills(db))
    } catch (e: Exception) {
        Log.e(TAG, "query all bills failed", e)
    } finally {
        cursor?.close()
        db.close()
    }
    out.put("rows", arr)
    return out.toString()
}

private fun countAllBills(db: SQLiteDatabase): Int {
    var cursor: Cursor? = null
    try {
        cursor = db.rawQuery("select count(*) from bills", null)
        if (cursor.moveToFirst()) return cursor.getInt(0)
    } catch (e: Exception) {
        Log.e(TAG, "count bills failed", e)
    } finally {
        cursor?.close()
    }
    return 0
}

internal fun openDb(context: Context): SQLiteDatabase {
    val db = SQLiteDatabase.openOrCreateDatabase(
        context.getFilesDir().getAbsolutePath() + "/" + DB_NAME, null
    )
    if (schemaReady) return db
    synchronized(dbLock) {
        if (!schemaReady) {
            db.execSQL(
                "create table if not exists bills(" +
                    "id integer primary key autoincrement," +
                    "year integer,month integer,day integer,clock text," +
                    "money real,bankName text,costType text,orderRemark text," +
                    "source text,sourcePkg text,createdAt integer)"
            )
            // 旧库平滑迁移：报销 / 星标标记（不存在则补列，已存在则忽略报错）
            try {
                db.execSQL("alter table bills add column reimb integer default 0")
            } catch (e: Exception) {
                // column already exists
            }
            try {
                db.execSQL("alter table bills add column starred integer default 0")
            } catch (e: Exception) {
                // column already exists
            }
            // 订单/交易单号：单号去重与事后核对用，旧库补列
            try {
                db.execSQL("alter table bills add column orderNo text")
            } catch (e: Exception) {
                // column already exists
            }
            // 自动记账账本、待确认队列、诊断记录（新增表，不影响旧库）
            CaptureLedger.ensureSchema(db)
            schemaReady = true
        }
    }
    return db
}

private fun billJson(cursor: Cursor): JSONObject {
    val o = JSONObject()
    o.put("id", cursor.getLong(cursor.getColumnIndex("id")))
    o.put("year", cursor.getInt(cursor.getColumnIndex("year")))
    o.put("month", cursor.getInt(cursor.getColumnIndex("month")))
    o.put("day", cursor.getInt(cursor.getColumnIndex("day")))
    o.put("clock", cursor.getString(cursor.getColumnIndex("clock")))
    o.put("money", cursor.getDouble(cursor.getColumnIndex("money")))
    o.put("bankName", cursor.getString(cursor.getColumnIndex("bankName")))
    o.put("costType", cursor.getString(cursor.getColumnIndex("costType")))
    o.put("orderRemark", cursor.getString(cursor.getColumnIndex("orderRemark")))
    o.put("source", cursor.getString(cursor.getColumnIndex("source")))
    o.put("sourcePkg", cursor.getString(cursor.getColumnIndex("sourcePkg")))
    o.put("createdAt", cursor.getLong(cursor.getColumnIndex("createdAt")))
    val reimbIdx = cursor.getColumnIndex("reimb")
    val starredIdx = cursor.getColumnIndex("starred")
    val orderIdx = cursor.getColumnIndex("orderNo")
    o.put("reimb", if (reimbIdx >= 0) cursor.getInt(reimbIdx) else 0)
    o.put("starred", if (starredIdx >= 0) cursor.getInt(starredIdx) else 0)
    o.put("orderNo", if (orderIdx >= 0) (cursor.getString(orderIdx) ?: "") else "")
    return o
}
}
