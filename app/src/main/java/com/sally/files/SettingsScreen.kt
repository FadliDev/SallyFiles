package com.sally.files

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

private enum class SDlg { Theme, Sort, AddLoc, ManualPath }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onPickTree: (String?) -> Unit) {
    BackHandler { vm.inSettings = false }
    val s = vm.settings
    var dlg by remember { mutableStateOf<SDlg?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = { vm.inSettings = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SectionTitle("Tampilan")
            ClickRow("Tema", listOf("Ikuti sistem", "Terang", "Gelap")[s.themeMode]) { dlg = SDlg.Theme }
            SwitchRow("Hitam AMOLED", "Latar hitam pekat saat mode gelap", s.amoled) { v -> vm.update { it.copy(amoled = v) } }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Warna dinamis", "Ikuti warna wallpaper (Material You), mematikan aksen biru", s.dynamicColor) { v ->
                    vm.update { it.copy(dynamicColor = v) }
                }
            }

            SectionTitle("Berkas")
            SwitchRow("Tampilkan file tersembunyi", "File yang diawali titik", s.showHidden) { v -> vm.update { it.copy(showHidden = v) } }
            ClickRow("Urutkan berdasarkan", listOf("Nama", "Ukuran", "Tanggal")[s.sortBy]) { dlg = SDlg.Sort }
            SwitchRow("Urutan menurun", null, s.sortDesc) { v -> vm.update { it.copy(sortDesc = v) } }
            SwitchRow("Konfirmasi sebelum hapus", null, s.confirmDelete) { v -> vm.update { it.copy(confirmDelete = v) } }

            SectionTitle("Lokasi kustom")
            if (vm.custom.isEmpty()) {
                ClickRow("Belum ada lokasi kustom", "Tambah folder Termux, Acode, SD card, atau path lain", { dlg = SDlg.AddLoc })
            }
            vm.custom.forEach { loc ->
                ListItem(
                    headlineContent = { Text(loc.name) },
                    supportingContent = {
                        Text(loc.path ?: ("SAF • " + (Uri.parse(loc.tree!!).authority ?: "")))
                    },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.removeLocation(loc) }) { Icon(Icons.Default.Delete, "Hapus lokasi") }
                    }
                )
            }
            if (vm.custom.isNotEmpty()) ClickRow("Tambah lokasi", null) { dlg = SDlg.AddLoc }

            SectionTitle("Tentang")
            ClickRow("Sally Files", "Versi 2.0 • Jetpack Compose • Material 3") {}
        }
    }

    when (dlg) {
        null -> {}
        SDlg.Theme -> ChoiceDialog(
            "Tema", listOf("Ikuti sistem", "Terang", "Gelap"), s.themeMode,
            onSelect = { i -> vm.update { it.copy(themeMode = i) } }, onDismiss = { dlg = null }
        )
        SDlg.Sort -> SortDialog(vm) { dlg = null }
        SDlg.AddLoc -> AddLocationDialog(
            onDismiss = { dlg = null },
            onPickTree = { dlg = null; onPickTree(it) },
            onManual = { dlg = SDlg.ManualPath }
        )
        SDlg.ManualPath -> InputDialog(
            "Path folder", "/storage/emulated/0/", allowSlash = true, onDismiss = { dlg = null }
        ) { vm.addPath(it) }
    }
}
