package com.sally.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Satu item file/folder: bisa File biasa atau dokumen SAF (Termux, Acode, SD card, dll) */
data class Node(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val file: File? = null,
    val uri: Uri? = null,   // URI dokumen (SAF)
    val tree: Uri? = null   // URI tree root (SAF)
) {
    val key: String get() = file?.absolutePath ?: uri.toString()
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

class Vfs(private val ctx: Context) {
    private val cr get() = ctx.contentResolver
    val cacheDir: File get() = ctx.cacheDir

    fun fromFile(f: File) = Node(
        f.name.ifEmpty { f.path }, f.isDirectory,
        if (f.isDirectory) 0 else f.length(), f.lastModified(), file = f
    )

    fun safRoot(tree: Uri): Node {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        return Node(docId.substringAfterLast('/').ifEmpty { docId }, true, 0, 0, uri = doc, tree = tree)
    }

    // ---------- baca ----------
    fun list(dir: Node): List<Node>? =
        if (dir.file != null) dir.file.listFiles()?.map { fromFile(it) } else listSaf(dir)

    private fun listSaf(dir: Node): List<Node>? {
        val tree = dir.tree ?: return null
        val docUri = dir.uri ?: return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(docUri))
        val out = ArrayList<Node>()
        try {
            val cols = arrayOf(
                Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
            )
            val c = cr.query(children, cols, null, null, null) ?: return null
            c.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    out.add(
                        Node(
                            it.getString(1) ?: id,
                            it.getString(2) == Document.MIME_TYPE_DIR,
                            if (it.isNull(3)) 0 else it.getLong(3),
                            if (it.isNull(4)) 0 else it.getLong(4),
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            tree = tree
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return null
        }
        return out
    }

    fun child(dir: Node, name: String): Node? =
        if (dir.file != null) File(dir.file, name).takeIf { it.exists() }?.let { fromFile(it) }
        else list(dir)?.firstOrNull { it.name == name }

    fun openIn(n: Node): InputStream {
        val f = n.file
        if (f != null) return f.inputStream()
        return cr.openInputStream(n.uri!!) ?: throw IOException("Tidak bisa membaca ${n.name}")
    }

    fun openOut(n: Node): OutputStream {
        val f = n.file
        if (f != null) return f.outputStream()
        return cr.openOutputStream(n.uri!!, "wt") ?: throw IOException("Tidak bisa menulis ${n.name}")
    }

    // ---------- tulis ----------
    fun createDir(parent: Node, name: String): Node {
        if (parent.file != null) {
            val f = File(parent.file, name)
            if (!f.isDirectory && !f.mkdirs()) throw IOException("Gagal membuat folder $name")
            return fromFile(f)
        }
        val u = DocumentsContract.createDocument(cr, parent.uri!!, Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Gagal membuat folder $name")
        return Node(name, true, 0, 0, uri = u, tree = parent.tree)
    }

    fun createFile(parent: Node, name: String): Node {
        if (parent.file != null) {
            val f = File(parent.file, name)
            f.parentFile?.mkdirs()
            if (!f.exists()) f.createNewFile()
            return fromFile(f)
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val u = DocumentsContract.createDocument(cr, parent.uri!!, mime, name)
            ?: throw IOException("Gagal membuat file $name")
        return Node(name, false, 0, 0, uri = u, tree = parent.tree)
    }

    fun delete(n: Node): Boolean =
        if (n.file != null) n.file.deleteRecursively()
        else try { DocumentsContract.deleteDocument(cr, n.uri!!) } catch (e: Exception) { false }

    fun rename(n: Node, newName: String): Boolean =
        if (n.file != null) n.file.renameTo(File(n.file.parentFile, newName))
        else try { DocumentsContract.renameDocument(cr, n.uri!!, newName) != null } catch (e: Exception) { false }

    fun uniqueName(dir: Node, name: String): String {
        val names = (list(dir) ?: emptyList()).map { it.name }.toSet()
        if (name !in names) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while ("$base ($i)$ext" in names) i++
        return "$base ($i)$ext"
    }

    // ---------- salin / pindah ----------
    private fun isInside(dst: Node, src: Node): Boolean {
        if (!src.isDir) return false
        if (src.file != null && dst.file != null) {
            val s = src.file.canonicalPath
            val d = dst.file.canonicalPath
            return d == s || d.startsWith(s + File.separator)
        }
        if (src.uri != null && dst.uri != null) {
            val s = src.uri.toString()
            val d = dst.uri.toString()
            return d == s || d.startsWith(s + "%2F") || d.startsWith("$s/")
        }
        return false
    }

    fun copy(src: Node, dstDir: Node): Node {
        if (isInside(dstDir, src)) throw IOException("Tidak bisa menyalin folder ke dalam dirinya sendiri")
        if (src.isDir) {
            val d = createDir(dstDir, uniqueName(dstDir, src.name))
            list(src)?.forEach { copy(it, d) }
            return d
        }
        val t = createFile(dstDir, uniqueName(dstDir, src.name))
        openIn(src).use { i -> openOut(t).use { o -> i.copyTo(o, 64 * 1024) } }
        if (t.file != null && src.modified > 0) t.file.setLastModified(src.modified)
        return t
    }

    fun move(src: Node, dstDir: Node) {
        if (isInside(dstDir, src)) throw IOException("Tidak bisa memindah folder ke dalam dirinya sendiri")
        if (src.file != null && dstDir.file != null) {
            if (src.file.parentFile?.canonicalPath == dstDir.file.canonicalPath) return
            val target = File(dstDir.file, uniqueName(dstDir, src.name))
            if (src.file.renameTo(target)) return
        }
        copy(src, dstDir)
        if (!delete(src)) throw IOException("Gagal menghapus sumber ${src.name}")
    }
}
