package com.sally.files

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var access by mutableStateOf(true)

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            vm.addTree(uri)
        }
    }

    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = hasAccess()
        vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        access = hasAccess()
        setContent {
            SallyTheme(vm.settings) {
                if (vm.inSettings) {
                    SettingsScreen(vm, onPickTree = ::launchPicker)
                } else {
                    BrowserScreen(
                        vm = vm,
                        hasAccess = access,
                        onRequestAccess = ::requestAccess,
                        onOpenFile = ::openNode,
                        onShare = ::shareNodes,
                        onPickTree = ::launchPicker
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        access = hasAccess()
        vm.reload()
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
            reqPerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    // ---------- SAF picker (Termux, Acode, SD card, dll) ----------
    private fun launchPicker(preset: String?) {
        val hint: Uri? = when (preset) {
            "termux" -> DocumentsContract.buildTreeDocumentUri("com.termux.documents", "/data/data/com.termux/files/home")
            else -> null
        }
        if (preset == "acode") {
            Toast.makeText(this, "Pilih \"Acode\" di sidebar picker, lalu tekan \"Gunakan folder ini\"", Toast.LENGTH_LONG).show()
        }
        try {
            pickTree.launch(hint)
        } catch (e: Exception) {
            Toast.makeText(this, "Picker folder tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- buka & bagikan ----------
    private fun uriOf(n: Node): Uri =
        n.file?.let { FileProvider.getUriForFile(this, "$packageName.provider", it) } ?: n.uri!!

    private fun openNode(n: Node) {
        try {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.ext) ?: "*/*"
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(n), mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Gagal membuka file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNodes(list: List<Node>) {
        try {
            val uris = ArrayList<Uri>(list.map { uriOf(it) })
            val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            i.type = "*/*"
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Bagikan"))
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Gagal membagikan", Toast.LENGTH_SHORT).show()
        }
    }
}
