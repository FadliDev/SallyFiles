package com.sally.files

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val df = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    private val imgExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

    fun size(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, u[i])
    }

    fun date(t: Long): String = df.format(Date(t))
    fun isImage(f: File) = f.extension.lowercase() in imgExt
}
