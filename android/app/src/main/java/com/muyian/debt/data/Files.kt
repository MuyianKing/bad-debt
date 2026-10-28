package com.muyian.debt.data

import android.content.Context
import android.net.Uri
import java.io.File

/** 应用私有目录文件操作：AddScreen 的账单图与 Settings 的玻璃背景图共用。 */
object Files {
    fun copyUriToFiles(context: Context, uri: Uri, prefix: String = "img"): String? = try {
        val file = target(context, prefix)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    fun copyFileToFiles(context: Context, source: File, prefix: String = "img"): String? = try {
        val file = target(context, prefix)
        source.inputStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    fun delete(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            File(path.removePrefix("file://")).delete()
        } catch (_: Exception) {
        }
    }

    private fun target(context: Context, prefix: String): File =
        File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
}
