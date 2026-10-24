package com.sally.files

import java.io.File
import java.io.IOException

object FileOps {

    /** Nama unik di folder tujuan: "a.txt" -> "a (1).txt" */
    fun uniqueName(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    fun copy(src: File, dstDir: File): File {
        if (src.isDirectory) {
            val s = src.canonicalPath
            val d = dstDir.canonicalPath
            if (d == s || d.startsWith(s + File.separator)) {
                throw IOException("Tidak bisa menyalin folder ke dalam dirinya sendiri")
            }
        }
        val target = uniqueName(dstDir, src.name)
        if (src.isDirectory) {
            if (!target.mkdirs()) throw IOException("Gagal membuat ${target.name}")
            src.listFiles()?.forEach { copy(it, target) }
        } else {
            src.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
            target.setLastModified(src.lastModified())
        }
        return target
    }

    fun move(src: File, dstDir: File) {
        if (src.parentFile?.canonicalPath == dstDir.canonicalPath) return
        if (src.isDirectory) {
            val s = src.canonicalPath
            val d = dstDir.canonicalPath
            if (d == s || d.startsWith(s + File.separator)) {
                throw IOException("Tidak bisa memindah folder ke dalam dirinya sendiri")
            }
        }
        val target = uniqueName(dstDir, src.name)
        if (!src.renameTo(target)) {
            copy(src, dstDir)
            if (!delete(src)) throw IOException("Gagal menghapus sumber ${src.name}")
        }
    }

    fun delete(f: File): Boolean = f.deleteRecursively()
}
