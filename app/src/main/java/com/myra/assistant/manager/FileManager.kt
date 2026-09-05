package com.myra.assistant.manager

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.text.DecimalFormat

/**
 * Storage Analyzer & Junk Cleaner Manager for MYRA.
 */
class FileManager(private val context: Context) {

    data class StorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val percentUsed: Int
    )

    fun getStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val total = totalBlocks * blockSize
        val free = availableBlocks * blockSize
        val used = total - free
        val percent = if (total > 0) ((used * 100) / total).toInt() else 0

        return StorageInfo(total, free, used, percent)
    }

    fun formatStorageSummary(): String {
        val info = getStorageInfo()
        val totalStr = formatSize(info.totalBytes)
        val freeStr = formatSize(info.freeBytes)
        val usedStr = formatSize(info.usedBytes)
        return "Storage Status: $usedStr used out of $totalStr (${info.percentUsed}% full). Free: $freeStr."
    }

    fun cleanAppCache(): String {
        var bytesFreed = 0L
        try {
            bytesFreed += deleteDir(context.cacheDir)
            context.externalCacheDir?.let {
                bytesFreed += deleteDir(it)
            }
        } catch (e: Exception) {
            // ignore
        }
        return if (bytesFreed > 0) {
            "Cleaned ${formatSize(bytesFreed)} of temporary cache and junk files."
        } else {
            "Cache is already clean. No junk files found."
        }
    }

    private fun deleteDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                bytes += if (file.isDirectory) deleteDir(file) else {
                    val len = file.length()
                    if (file.delete()) len else 0L
                }
            }
        }
        val len = dir.length()
        if (dir.delete()) bytes += len
        return bytes
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
