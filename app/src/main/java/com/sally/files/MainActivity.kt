package com.sally.files

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val root: File = Environment.getExternalStorageDirectory()
    private var currentDir: File = root
    private var items: List<File> = emptyList()
    private val selected = LinkedHashSet<File>()
    private var clipboard: List<File> = emptyList()
    private var clipCut = false
    private var showHidden = false
    private var sortBy = 0 // 0 nama, 1 ukuran, 2 tanggal
    private var busy = false

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progress: LinearProgressIndicator
    private lateinit var empty: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        showHidden = prefs.getBoolean("hidden", false)
        sortBy = prefs.getInt("sort", 0)

        toolbar = findViewById(R.id.toolbar)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        fab = findViewById(R.id.fab)

        toolbar.inflateMenu(R.menu.main)
        toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        toolbar.setNavigationOnClickListener { selected.clear(); refreshUi() }

        adapter = FileAdapter(::onItemClick, ::onItemLong)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        empty.setOnClickListener { if (!hasAccess()) requestAccess() }
        fab.setOnClickListener {
            MaterialAlertDialogBuilder(this).setItems(arrayOf("Folder baru", "File baru")) { _, w ->
                if (w == 0) newItem(true) else newItem(false)
            }.show()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val parent = currentDir.parentFile
                when {
                    selected.isNotEmpty() -> { selected.clear(); refreshUi() }
                    currentDir.path != root.path && parent != null -> go(parent)
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!busy) load()
    }

    // ---------- izin ----------
    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        load()
    }

    // ---------- list ----------
    private fun go(dir: File) {
        selected.clear()
        currentDir = dir
        load()
    }

    private fun load() {
        if (!hasAccess()) {
            items = emptyList()
            empty.text = "Izin akses file dibutuhkan.\nKetuk untuk memberi izin"
            refreshUi()
            return
        }
        val all = currentDir.listFiles()
        val cmp: Comparator<File> = when (sortBy) {
            1 -> compareBy<File> { if (it.isDirectory) 0L else it.length() }
            2 -> compareByDescending<File> { it.lastModified() }
            else -> compareBy<File, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        items = (all ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
        selected.retainAll(items.toSet())
        empty.text = if (all == null) "Folder tidak bisa dibuka" else "Folder kosong"
        refreshUi()
    }

    private fun refreshUi() {
        adapter.submit(items, selected)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val sel = selected.isNotEmpty()
        toolbar.title = if (sel) "${selected.size} dipilih" else currentDir.name.ifEmpty { "/" }
        toolbar.subtitle = if (sel) null else currentDir.path
        toolbar.navigationIcon = if (sel) ContextCompat.getDrawable(this, R.drawable.ic_close) else null
        fab.visibility = if (sel) View.GONE else View.VISIBLE

        val m = toolbar.menu
        fun vis(id: Int, v: Boolean) { m.findItem(id)?.isVisible = v }
        vis(R.id.action_paste, !sel && clipboard.isNotEmpty())
        vis(R.id.action_new_folder, !sel)
        vis(R.id.action_new_file, !sel)
        vis(R.id.action_sort, !sel)
        vis(R.id.action_hidden, !sel)
        vis(R.id.action_copy, sel)
        vis(R.id.action_cut, sel)
        vis(R.id.action_delete, sel)
        vis(R.id.action_compress, sel)
        vis(R.id.action_select_all, sel)
        vis(R.id.action_rename, selected.size == 1)
        vis(R.id.action_extract, selected.size == 1 && Archive.isArchive(selected.first()))
        m.findItem(R.id.action_hidden)?.isChecked = showHidden
    }

    // ---------- klik ----------
    private fun onItemClick(f: File) {
        if (selected.isNotEmpty()) { toggle(f); return }
        when {
            f.isDirectory -> go(f)
            Archive.isArchive(f) && !f.name.endsWith(".apk", true) -> archiveDialog(f)
            else -> openExternal(f)
        }
    }

    private fun onItemLong(f: File) = toggle(f)

    private fun toggle(f: File) {
        if (!selected.remove(f)) selected.add(f)
        refreshUi()
    }

    private fun openExternal(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        } catch (e: Exception) {
            toast(e.message ?: "Gagal membuka file")
        }
    }

    private fun archiveDialog(f: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(f.name)
            .setItems(arrayOf("Ekstrak ke folder \"${Archive.baseName(f)}\"", "Ekstrak di sini", "Buka dengan aplikasi lain")) { _, w ->
                when (w) {
                    0 -> extract(f, FileOps.uniqueName(currentDir, Archive.baseName(f)))
                    1 -> extract(f, currentDir)
                    else -> openExternal(f)
                }
            }.show()
    }

    // ---------- menu ----------
    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.action_copy -> { clipboard = selected.toList(); clipCut = false; selected.clear(); toast("Disalin"); refreshUi() }
            R.id.action_cut -> { clipboard = selected.toList(); clipCut = true; selected.clear(); toast("Dipotong"); refreshUi() }
            R.id.action_paste -> paste()
            R.id.action_delete -> confirmDelete()
            R.id.action_compress -> compress()
            R.id.action_extract -> archiveDialog(selected.first())
            R.id.action_rename -> rename(selected.first())
            R.id.action_select_all -> { selected.addAll(items); refreshUi() }
            R.id.action_new_folder -> newItem(true)
            R.id.action_new_file -> newItem(false)
            R.id.action_sort -> {
                MaterialAlertDialogBuilder(this).setTitle("Urutkan")
                    .setSingleChoiceItems(arrayOf("Nama", "Ukuran", "Tanggal"), sortBy) { d, w ->
                        sortBy = w
                        getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("sort", w).apply()
                        d.dismiss(); load()
                    }.show()
            }
            R.id.action_hidden -> {
                showHidden = !showHidden
                getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("hidden", showHidden).apply()
                load()
            }
            else -> return false
        }
        return true
    }

    private fun paste() {
        val files = clipboard
        val cut = clipCut
        val dir = currentDir
        runTask {
            files.forEach { if (cut) FileOps.move(it, dir) else FileOps.copy(it, dir) }
        }
        if (cut) clipboard = emptyList()
    }

    private fun confirmDelete() {
        val files = selected.toList()
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus")
            .setMessage(if (files.size == 1) "Hapus \"${files[0].name}\"?" else "Hapus ${files.size} item?")
            .setPositiveButton("Hapus") { _, _ ->
                runTask { files.forEach { if (!FileOps.delete(it)) throw IOException("Gagal menghapus ${it.name}") } }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun compress() {
        val files = selected.toList()
        val def = if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name } else "Archive"
        val dir = currentDir
        input("Nama ZIP", "$def.zip") { n ->
            val name = if (n.endsWith(".zip", true)) n else "$n.zip"
            val out = FileOps.uniqueName(dir, name)
            runTask { Archive.zip(files, out) }
        }
    }

    private fun extract(f: File, dest: File) = runTask { Archive.extract(f, dest) }

    private fun rename(f: File) {
        input("Ganti nama", f.name) { n ->
            val t = File(f.parentFile, n)
            when {
                t.exists() -> toast("Nama sudah dipakai")
                !f.renameTo(t) -> toast("Gagal mengganti nama")
                else -> { selected.clear(); load() }
            }
        }
    }

    private fun newItem(folder: Boolean) {
        input(if (folder) "Folder baru" else "File baru", "") { n ->
            val t = File(currentDir, n)
            if (t.exists()) { toast("Nama sudah dipakai"); return@input }
            val ok = try { if (folder) t.mkdirs() else t.createNewFile() } catch (e: IOException) { false }
            if (!ok) toast("Gagal membuat")
            load()
        }
    }

    // ---------- helper ----------
    private fun input(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSingleLine(); selectAll() }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply { setPadding(pad, 8, pad, 0); addView(et) }
        MaterialAlertDialogBuilder(this).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ ->
                val s = et.text.toString().trim()
                when {
                    s.isEmpty() -> {}
                    s.contains('/') -> toast("Nama tidak boleh mengandung /")
                    else -> ok(s)
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun runTask(block: () -> Unit) {
        if (busy) { toast("Masih memproses…"); return }
        busy = true
        progress.visibility = View.VISIBLE
        io.execute {
            var err: String? = null
            try { block() } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            runOnUiThread {
                busy = false
                progress.visibility = View.GONE
                selected.clear()
                toast(err ?: "Selesai")
                load()
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }
}
