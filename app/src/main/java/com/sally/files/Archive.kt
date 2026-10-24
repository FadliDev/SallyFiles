package com.sally.files

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object Archive {
    private val exts = listOf(".zip", ".jar", ".apk", ".tar", ".tar.gz", ".tgz", ".gz", ".7z")

    fun isArchive(f: File): Boolean {
        if (!f.isFile) return false
        val n = f.name.lowercase()
        return exts.any { n.endsWith(it) }
    }

    fun baseName(f: File): String {
        val n = f.name
        val l = n.lowercase()
        for (e in exts.sortedByDescending { it.length }) {
            if (l.endsWith(e) && n.length > e.length) return n.substring(0, n.length - e.length)
        }
        return n
    }

    // ---------- ZIP ----------
    fun zip(files: List<File>, out: File) {
        val outPath = out.canonicalPath
        ZipOutputStream(BufferedOutputStream(out.outputStream(), 64 * 1024)).use { z ->
            files.forEach { add(z, it, it.name, outPath) }
        }
    }

    private fun add(z: ZipOutputStream, f: File, path: String, skip: String) {
        if (f.canonicalPath == skip) return
        if (f.isDirectory) {
            val kids = f.listFiles() ?: emptyArray()
            if (kids.isEmpty()) {
                z.putNextEntry(ZipEntry("$path/"))
                z.closeEntry()
            }
            kids.forEach { add(z, it, "$path/${it.name}", skip) }
        } else {
            val e = ZipEntry(path)
            e.time = f.lastModified()
            z.putNextEntry(e)
            f.inputStream().use { it.copyTo(z, 64 * 1024) }
            z.closeEntry()
        }
    }

    // ---------- EXTRACT ----------
    fun extract(src: File, dest: File) {
        dest.mkdirs()
        val n = src.name.lowercase()
        when {
            n.endsWith(".7z") -> un7z(src, dest)
            n.endsWith(".tar.gz") || n.endsWith(".tgz") ->
                untar(GzipCompressorInputStream(BufferedInputStream(src.inputStream())), dest)
            n.endsWith(".tar") -> untar(BufferedInputStream(src.inputStream()), dest)
            n.endsWith(".gz") -> ungz(src, dest)
            else -> unzip(src, dest)
        }
    }

    /** Cegah zip-slip: hasil harus tetap di dalam folder tujuan */
    private fun safe(dest: File, name: String): File {
        val f = File(dest, name)
        if (!f.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
            throw SecurityException("Entri tidak aman: $name")
        }
        return f
    }

    private fun unzip(src: File, dest: File) {
        ZipFile(src).use { z ->
            val en = z.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                val t = safe(dest, e.name)
                if (e.isDirectory) {
                    t.mkdirs()
                } else {
                    t.parentFile?.mkdirs()
                    z.getInputStream(e).use { i -> t.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
                    if (e.time > 0) t.setLastModified(e.time)
                }
            }
        }
    }

    private fun untar(ins: InputStream, dest: File) {
        TarArchiveInputStream(ins).use { t ->
            var e: ArchiveEntry? = t.nextEntry
            while (e != null) {
                val target = safe(dest, e.name)
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { o -> t.copyTo(o, 64 * 1024) }
                }
                e = t.nextEntry
            }
        }
    }

    private fun ungz(src: File, dest: File) {
        val name = src.name.removeSuffix(".gz").removeSuffix(".GZ").ifEmpty { "output" }
        val t = FileOps.uniqueName(dest, name)
        GzipCompressorInputStream(BufferedInputStream(src.inputStream())).use { i ->
            t.outputStream().use { o -> i.copyTo(o, 64 * 1024) }
        }
    }

    private fun un7z(src: File, dest: File) {
        SevenZFile(src).use { s ->
            var e = s.nextEntry
            val buf = ByteArray(64 * 1024)
            while (e != null) {
                val t = safe(dest, e.name)
                if (e.isDirectory) {
                    t.mkdirs()
                } else {
                    t.parentFile?.mkdirs()
                    t.outputStream().use { o ->
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                        }
                    }
                }
                e = s.nextEntry
            }
        }
    }
}
