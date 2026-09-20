package com.winlator.cmod.ui.profile

import android.os.Environment
import android.os.StatFs

data class StorageSnapshot(val usedBytes: Long, val totalBytes: Long) {
    val usedPercent: Int
        get() = if (totalBytes > 0) ((usedBytes * 100.0) / totalBytes).toInt().coerceIn(0, 100) else 0

    companion object {
        @JvmField
        val EMPTY = StorageSnapshot(0L, 0L)
    }
}

object StorageStats {
    @JvmStatic
    fun read(): StorageSnapshot = try {
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        val total = statFs.totalBytes
        val free = statFs.availableBytes
        StorageSnapshot(usedBytes = (total - free).coerceAtLeast(0L), totalBytes = total)
    } catch (e: Exception) {
        StorageSnapshot.EMPTY
    }
}
