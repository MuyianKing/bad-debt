package com.muyian.debt.data

import android.content.Context
import kotlinx.coroutines.delay
import muyian.bill.listener.MuyianBillCore
import org.json.JSONObject

enum class PickKind { QIANJI, BACKUP }

sealed class OpenResult {
    data object Ok : OpenResult()
    data class Error(val message: String) : OpenResult()
}

sealed class PickResult {
    data class Picked(val path: String, val name: String) : PickResult()
    data object Cancelled : PickResult()
    data class Error(val message: String) : PickResult()
    data object Timeout : PickResult()
}

sealed class SaveResult {
    data class Saved(val name: String) : SaveResult()
    data object Cancelled : SaveResult()
    data class Error(val message: String) : SaveResult()
    data object Timeout : SaveResult()
}

sealed class ReadResult {
    data class Ok(val text: String) : ReadResult()
    data class Error(val message: String) : ReadResult()
}

/**
 * SAF 选文件/保存的“拉起 + 轮询”封装。
 * 原生侧仍用同步方法暴露状态，这里把轮询、超时和 JSON 解析收在一处。
 */
object FilePick {
    private const val PICK_TIMEOUT_MS = 90_000L
    private const val SAVE_TIMEOUT_MS = 120_000L

    fun open(context: Context, kind: PickKind): OpenResult {
        val raw = try {
            when (kind) {
                PickKind.QIANJI -> MuyianBillCore.openImportPicker(context)
                PickKind.BACKUP -> MuyianBillCore.openBackupPicker(context)
            }
        } catch (e: Exception) {
            return OpenResult.Error(e.javaClass.name)
        }
        return rawResult(raw, "无法打开文件选择器")
    }

    suspend fun awaitPick(
        context: Context,
        timeoutMs: Long = PICK_TIMEOUT_MS,
        pollMs: Long = 500
    ): PickResult {
        val start = System.currentTimeMillis()
        while (true) {
            delay(pollMs)
            val st = try {
                JSONObject(MuyianBillCore.getImportPickState(context))
            } catch (_: Exception) {
                JSONObject()
            }
            when (st.optString("state")) {
                "picked" -> return PickResult.Picked(
                    st.optString("path", ""),
                    st.optString("name", "")
                )
                "cancelled" -> return PickResult.Cancelled
                "error" -> return PickResult.Error(st.optString("error", "未知原因"))
            }
            if (System.currentTimeMillis() - start > timeoutMs) return PickResult.Timeout
        }
    }

    fun openExport(context: Context, content: String, name: String): OpenResult {
        val raw = try {
            MuyianBillCore.openBackupExport(context, content, name)
        } catch (e: Exception) {
            return OpenResult.Error(e.javaClass.name)
        }
        return rawResult(raw, "无法打开保存界面")
    }

    suspend fun awaitSave(
        context: Context,
        timeoutMs: Long = SAVE_TIMEOUT_MS,
        pollMs: Long = 400
    ): SaveResult {
        val start = System.currentTimeMillis()
        while (true) {
            delay(pollMs)
            val st = try {
                JSONObject(MuyianBillCore.getBackupSaveState(context))
            } catch (_: Exception) {
                JSONObject()
            }
            when (st.optString("state")) {
                "saved" -> return SaveResult.Saved(st.optString("name", ""))
                "cancelled" -> return SaveResult.Cancelled
                "error" -> return SaveResult.Error(st.optString("error", "未知原因"))
            }
            if (System.currentTimeMillis() - start > timeoutMs) return SaveResult.Timeout
        }
    }

    fun readBackup(context: Context, path: String): ReadResult {
        val raw = try {
            JSONObject(MuyianBillCore.readBackupText(context, path))
        } catch (_: Exception) {
            JSONObject()
        }
        if (!raw.optBoolean("ok")) {
            return ReadResult.Error(raw.optString("error", "读取备份失败"))
        }
        return ReadResult.Ok(raw.optString("text", "{}"))
    }

    private fun rawResult(raw: String, prefix: String): OpenResult = try {
        val o = JSONObject(raw)
        if (o.optBoolean("success", false)) OpenResult.Ok
        else OpenResult.Error("$prefix：${o.optString("error", "未知原因")}")
    } catch (_: Exception) {
        OpenResult.Error(prefix)
    }
}
