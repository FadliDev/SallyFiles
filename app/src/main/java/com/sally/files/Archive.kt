package com.sally.files

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Archive {
    private val exts = listOf(".zip", ".jar", ".apk", ".tar.gz", ".tgz", ".tar", ".gz", ".7z")

    fun isArchive(n: Node): Boolean {
        if (n.isDir) return false
        val l = n.name.lowercase()
        return exts.any { l.endsWith(it) }
    }

    fun baseName(n: Node): String {
        val l = n.name.lowercase()
        for (e in exts) {
            if (l.endsWith(e) && n.name.length > e.length) return n.name.substring(0, n.name.length - e.length)
        }
        return n.name
    }

    // ---------- ZIP ----------
    fun zip(v: Vfs, files: List<Node>, outDir: Node, outName: String) {
        val out = v.createFile(outDir, outName)
        ZipOutputStream(BufferedOutputStream(v.openOut(out), 64 * 1024)).use { z ->
            files.forEach { add(v, z, it, it.name, out.key) }
        }
    }

    private fun add(v: Vfs, z: ZipOutputStream, n: Node, path: String, skip: String) {
        if (n.key == skip) return
        if (n.isDir) {
            val kids = v.list(n) ?: emptyList()
            if (kids.isEmpty()) {
                z.putNextEntry(ZipEntry("$path/"))
                z.closeEntry()
            }
            kids.forEach { add(v, z, it, "$path/${it.name}", skip) }
        } else {
            val e = ZipEntry(path)
            if (n.modified > 0) e.time = n.modified
            z.putNextEntry(e)
            v.openIn(n).use { it.copyTo(z, 64 * 1024) }
            z.closeEntry()
        }
    }

    // ---------- EKSTRAK ----------
    fun extract(v: Vfs, src: Node, dest: Node) {
        val n = src.name.lowercase()
        val cache = HashMap<String, Node>()
        when {
            n.endsWith(".7z") -> un7z(v, src, dest, cache)
            n.endsWith(".tar.gz") || n.endsWith(".tgz") ->
                v.openIn(src).buffered().use { untar(v, GzipCompressorInputStream(it), dest, cache) }
            n.endsWith(".tar") -> v.openIn(src).buffered().use { untar(v, it, dest, cache) }
            n.endsWith(".gz") -> ungz(v, src, dest)
            else -> unzip(v, src, dest, cache)
        }
    }

    private fun ensureDir(v: Vfs, root: Node, parts: List<String>, cache: HashMap<String, Node>): Node {
        var cur = root
        var path = ""
        for (p in parts) {
            path += "/$p"
            val parent = cur
            cur = cache.getOrPut(path) { v.child(parent, p)?.takeIf { it.isDir } ?: v.createDir(parent, p) }
        }
        return cur
    }

    /** Tulis satu entri arsip ke folder tujuan (dengan perlindungan zip-slip) */
    private fun writeEntry(
        v: Vfs, dest: Node, cache: HashMap<String, Node>,
        rawName: String, isDir: Boolean, copier: (OutputStream) -> Unit
    ) {
        val parts = rawName.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw SecurityException("Entri tidak aman: $rawName")
        if (parts.isEmpty()) return
        if (isDir) {
            ensureDir(v, dest, parts, cache)
            return
        }
        val parent = ensureDir(v, dest, parts.dropLast(1), cache)
        val f = v.createFile(parent, parts.last())
        v.openOut(f).use { copier(it) }
    }

    private fun unzip(v: Vfs, src: Node, dest: Node, cache: HashMap<String, Node>) {
        ZipInputStream(BufferedInputStream(v.openIn(src))).use { z ->
            var e = z.nextEntry
            while (e != null) {
                writeEntry(v, dest, cache, e.name, e.isDirectory) { o -> z.copyTo(o, 64 * 1024) }
                z.closeEntry()
                e = z.nextEntry
            }
        }
    }

    private fun untar(v: Vfs, ins: InputStream, dest: Node, cache: HashMap<String, Node>) {
        TarArchiveInputStream(ins).use { t ->
            var e: ArchiveEntry? = t.nextEntry
            while (e != null) {
                writeEntry(v, dest, cache, e.name, e.isDirectory) { o -> t.copyTo(o, 64 * 1024) }
                e = t.nextEntry
            }
        }
    }

    private fun ungz(v: Vfs, src: Node, dest: Node) {
        val name = src.name.removeSuffix(".gz").removeSuffix(".GZ").ifEmpty { "output" }
        val t = v.createFile(dest, v.uniqueName(dest, name))
        GzipCompressorInputStream(BufferedInputStream(v.openIn(src))).use { i ->
            v.openOut(t).use { o -> i.copyTo(o, 64 * 1024) }
        }
    }

    private fun un7z(v: Vfs, src: Node, dest: Node, cache: HashMap<String, Node>) {
        // SevenZFile butuh File; kalau sumbernya SAF, salin dulu ke cache
        var tmp: File? = null
        val f: File = src.file ?: File.createTempFile("x7z", ".7z", v.cacheDir).also { t ->
            tmp = t
            v.openIn(src).use { i -> t.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        }
        try {
            SevenZFile(f).use { s ->
                val buf = ByteArray(64 * 1024)
                var e = s.nextEntry
                while (e != null) {
                    writeEntry(v, dest, cache, e.name ?: "", e.isDirectory) { o ->
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                        }
                    }
                    e = s.nextEntry
                }
            }
        } finally {
            tmp?.delete()
        }
    }
}
