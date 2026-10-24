package com.sally.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    val vfs = Vfs(app)

    var settings by mutableStateOf(repo.loadSettings())
        private set
    var custom by mutableStateOf(repo.loadLocations())
        private set
    val builtin: List<Location> = buildBuiltin(app)
    val locations: List<Location> get() = builtin + custom

    var currentLocation by mutableStateOf<Location?>(null)
        private set
    var stack by mutableStateOf<List<Node>>(emptyList())
        private set
    var items by mutableStateOf<List<Node>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var busy by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var emptyText by mutableStateOf("Folder kosong")
        private set
    var clipCount by mutableStateOf(0)
        private set
    var inSettings by mutableStateOf(false)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var clip: List<Node> = emptyList()
    private var clipCut = false
    private var clipFrom = ""

    val current: Node? get() = stack.lastOrNull()

    init {
        openLocation(builtin.first())
    }

    private fun msg(s: String) { messages.tryEmit(s) }

    // ---------- lokasi ----------
    fun openLocation(loc: Location) {
        try {
            val root = if (loc.path != null) vfs.fromFile(File(loc.path)).copy(name = loc.name)
            else vfs.safRoot(Uri.parse(loc.tree!!)).copy(name = loc.name)
            currentLocation = loc
            stack = listOf(root)
            selected = emptySet()
            inSettings = false
            load()
        } catch (e: Exception) {
            msg("Gagal membuka lokasi: ${e.message}")
        }
    }

    fun addTree(uri: Uri) {
        val s = uri.toString()
        val existing = locations.firstOrNull { it.tree == s }
        if (existing != null) { openLocation(existing); return }
        viewModelScope.launch(Dispatchers.IO) {
            val name = docName(uri)
                ?: Uri.decode(uri.lastPathSegment ?: "").substringAfterLast('/').substringAfterLast(':').ifEmpty { "Folder" }
            withContext(Dispatchers.Main) {
                val loc = Location(name, tree = s)
                custom = custom + loc
                repo.saveLocations(custom)
                openLocation(loc)
            }
        }
    }

    fun addPath(path: String) {
        val f = File(path.trim())
        if (!f.isDirectory) { msg("Folder tidak ditemukan"); return }
        val p = f.absolutePath
        val existing = locations.firstOrNull { it.path == p }
        if (existing != null) { openLocation(existing); return }
        val loc = Location(f.name.ifEmpty { "/" }, path = p)
        custom = custom + loc
        repo.saveLocations(custom)
        openLocation(loc)
    }

    fun removeLocation(loc: Location) {
        if (loc !in custom) return
        custom = custom - loc
        repo.saveLocations(custom)
        if (loc.tree != null) {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(loc.tree),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        if (currentLocation == loc) openLocation(builtin.first())
    }

    private fun docName(tree: Uri): String? = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        getApplication<Application>().contentResolver.query(
            doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    // ---------- list ----------
    fun reload() = load()

    private fun load() {
        val dir = current ?: return
        val s = settings
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            val raw = vfs.list(dir)
            val sorted = raw?.filter { s.showHidden || !it.name.startsWith(".") }?.let { sortNodes(it, s) }
            withContext(Dispatchers.Main) {
                if (current?.key == dir.key) {
                    items = sorted ?: emptyList()
                    emptyText = if (raw == null) "Folder tidak bisa dibuka.\nCek izin akses." else "Folder kosong"
                    val keys = items.map { it.key }.toSet()
                    selected = selected.filter { it in keys }.toSet()
                    loading = false
                }
            }
        }
    }

    private fun sortNodes(l: List<Node>, s: AppSettings): List<Node> {
        val base: Comparator<Node> = when (s.sortBy) {
            1 -> compareBy<Node> { it.size }
            2 -> compareBy<Node> { it.modified }
            else -> compareBy<Node, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val cmp = if (s.sortDesc) base.reversed() else base
        return l.sortedWith(compareByDescending<Node> { it.isDir }.then(cmp))
    }

    // ---------- navigasi ----------
    fun enter(n: Node) { stack = stack + n; selected = emptySet(); load() }
    fun goTo(index: Int) { stack = stack.take(index + 1); selected = emptySet(); load() }

    /** Dipanggil saat back: tutup seleksi atau naik satu folder. Return true kalau ditangani */
    fun up(): Boolean {
        if (selected.isNotEmpty()) { selected = emptySet(); return true }
        if (stack.size > 1) { stack = stack.dropLast(1); load(); return true }
        return false
    }

    // ---------- seleksi ----------
    fun toggle(n: Node) { selected = if (n.key in selected) selected - n.key else selected + n.key }
    fun selectAll() { selected = items.map { it.key }.toSet() }
    fun clearSelection() { selected = emptySet() }
    fun selectedNodes(): List<Node> = items.filter { it.key in selected }

    // ---------- operasi ----------
    private fun task(done: String = "Selesai", block: () -> Unit) {
        if (busy) { msg("Masih memproses…"); return }
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            var err: String? = null
            try { block() } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            withContext(Dispatchers.Main) {
                busy = false
                selected = emptySet()
                msg(err ?: done)
                load()
            }
        }
    }

    fun copySel() { clip = selectedNodes(); clipCut = false; clipFrom = current?.key ?: ""; clipCount = clip.size; selected = emptySet(); msg("Disalin") }
    fun cutSel() { clip = selectedNodes(); clipCut = true; clipFrom = current?.key ?: ""; clipCount = clip.size; selected = emptySet(); msg("Dipotong") }

    fun paste() {
        val dir = current ?: return
        val files = clip
        val cut = clipCut
        if (cut && clipFrom == dir.key) { clip = emptyList(); clipCount = 0; return }
        task("Tempel selesai") { files.forEach { if (cut) vfs.move(it, dir) else vfs.copy(it, dir) } }
        if (cut) { clip = emptyList(); clipCount = 0 }
    }

    fun deleteSelected() {
        val l = selectedNodes()
        task("Dihapus") { l.forEach { if (!vfs.delete(it)) throw IOException("Gagal menghapus ${it.name}") } }
    }

    fun rename(n: Node, name: String) {
        if (items.any { it.name == name }) { msg("Nama sudah dipakai"); return }
        task("Nama diganti") { if (!vfs.rename(n, name)) throw IOException("Gagal mengganti nama") }
    }

    fun newFolder(name: String) {
        val dir = current ?: return
        if (items.any { it.name == name }) { msg("Nama sudah dipakai"); return }
        task("Folder dibuat") { vfs.createDir(dir, name) }
    }

    fun newFile(name: String) {
        val dir = current ?: return
        if (items.any { it.name == name }) { msg("Nama sudah dipakai"); return }
        task("File dibuat") { vfs.createFile(dir, name) }
    }

    fun compress(name: String) {
        val dir = current ?: return
        val l = selectedNodes()
        task("ZIP dibuat") { Archive.zip(vfs, l, dir, vfs.uniqueName(dir, name)) }
    }

    fun extract(n: Node, intoFolder: Boolean) {
        val dir = current ?: return
        task("Ekstrak selesai") {
            val dest = if (intoFolder) vfs.createDir(dir, vfs.uniqueName(dir, Archive.baseName(n))) else dir
            Archive.extract(vfs, n, dest)
        }
    }

    // ---------- pengaturan ----------
    fun update(f: (AppSettings) -> AppSettings) {
        settings = f(settings)
        repo.saveSettings(settings)
        load()
    }

    companion object {
        private fun buildBuiltin(ctx: Context): List<Location> {
            val l = ArrayList<Location>()
            l.add(Location("Penyimpanan internal", path = Environment.getExternalStorageDirectory().absolutePath))
            ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { i, f ->
                val p = f.absolutePath.substringBefore("/Android/")
                if (p != f.absolutePath) l.add(Location(if (i == 0) "Kartu SD" else "Kartu SD ${i + 1}", path = p))
            }
            l.add(Location("Download", path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath))
            l.add(Location("DCIM", path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath))
            return l
        }
    }
}
