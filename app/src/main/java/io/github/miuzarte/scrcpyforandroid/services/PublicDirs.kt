package io.github.miuzarte.scrcpyforandroid.services

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object PublicDirs {
    private const val ROOT_DIRECTORY = "Scrcpy"

    const val DOWNLOADS_RELATIVE_PATH = "Download/Scrcpy"

    fun transferDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ROOT_DIRECTORY,
        )
    }

    fun recordDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            ROOT_DIRECTORY,
        )
    }

    /**
     * Android 10 起 MediaStore 能免权限往「下载」目录插条目；更早的版本改公有目录要
     * `WRITE_EXTERNAL_STORAGE` 而清单里没有该权限，只能让用户自己挑位置。
     */
    fun canWriteDownloads(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * 打开写入端才建条目，[PublicDownloadsSink.finish] 才把条目定下；拿到 sink 不调它会在「下载」目录
     * 留下一条挂 `IS_PENDING` 的条目。返回 null 时调用方退回让用户自己挑位置。
     */
    fun openDownloadsSink(context: Context, fileName: String, mimeType: String): PublicDownloadsSink? {
        if (!canWriteDownloads()) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        val stream = runCatching { resolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return PublicDownloadsSink(stream) { complete -> settleEntry(resolver, uri, complete) }
    }

    /**
     * 撤下 pending 要命中这一条才算出货；撤不下或没命中都就地删一次，不留看不见的半成品。
     * 返回条目是否对外可见。
     */
    private fun settleEntry(resolver: ContentResolver, uri: Uri, complete: Boolean): Boolean {
        if (!complete) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        val updated = runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }.getOrNull()
        if (updated == null || updated <= 0) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        return true
    }
}

/** [PublicDirs.openDownloadsSink] 打开的写入端，写完调 [finish] 定下条目。 */
class PublicDownloadsSink internal constructor(
    val stream: OutputStream,
    private val settle: (Boolean) -> Boolean,
) {
    /** [complete] 为 false 表示内容没写全；撤不下或没命中时条目已被删掉，返回 false。 */
    fun finish(complete: Boolean): Boolean = settle(complete)
}
