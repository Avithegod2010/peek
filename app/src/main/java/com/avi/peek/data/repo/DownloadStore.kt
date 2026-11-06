package com.avi.peek.data.repo

import android.content.Context
import java.io.File

class DownloadStore(context: Context) {

    val root: File = File(context.filesDir, "downloads").apply { mkdirs() }

    fun directoryFor(code: String): File = File(root, code).apply { mkdirs() }

    fun deleteFor(code: String) {
        File(root, code).deleteRecursively()
    }

    fun sizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun localVideo(code: String): File? {
        val dir = File(root, code)
        if (!dir.exists()) return null
        return dir.listFiles()?.firstOrNull { it.extension == "mp4" }
    }
}
