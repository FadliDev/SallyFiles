package com.sally.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Dlg {
    data object NewDir : Dlg
    data object NewFile : Dlg
    data object Sort : Dlg
    data object AddLoc : Dlg
    data object ManualPath : Dlg
    data object Delete : Dlg
    data object Zip : Dlg
    data class Rename(val n: Node) : Dlg
    data class ArchiveMenu(val n: Node) : Dlg
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    vm: MainViewModel,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenFile: (Node) -> Unit,
    onShare: (List<Node>) -> Unit,
    onPickTree: (String?) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var menu by remember { mutableStateOf(false) }
    var fab by remember { mutableStateOf(false) }

    val selecting = vm.selected.isNotEmpty()
    val selNodes = vm.items.filter { it.key in vm.selected }
    val needAccess = !hasAccess && vm.currentLocation?.path != null

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    BackHandler(enabled = drawer.isOpen || selecting || vm.stack.size > 1) {
        if (drawer.isOpen) scope.launch { drawer.close() } else vm.up()
    }

    fun click(n: Node) {
        when {
            selecting -> vm.toggle(n)
            n.isDir -> vm.enter(n)
            Archive.isArchive(n) && !n.name.endsWith(".apk", true) -> dialog = Dlg.ArchiveMenu(n)
            else -> onOpenFile(n)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Sally Files",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    vm.locations.forEach { loc ->
                        NavigationDrawerItem(
                            label = { Text(loc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = vm.currentLocation == loc,
                            icon = { Icon(painterResource(R.drawable.ic_folder), null) },
                            onClick = { vm.openLocation(loc); scope.launch { drawer.close() } },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Tambah lokasi") },
                    selected = false,
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = { dialog = Dlg.AddLoc; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Pengaturan") },
                    selected = false,
                    icon = { Icon(Icons.Default.Settings, null) },
                    onClick = { vm.inSettings = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                if (!selecting) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(vm.current?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val path = vm.current?.file?.path
                                if (path != null) {
                                    Text(
                                        path, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.Default.Menu, "Menu")
                            }
                        },
                        actions = {
                            if (vm.clipCount > 0) {
                                IconButton(onClick = { vm.paste() }) {
                                    Icon(painterResource(R.drawable.ic_paste), "Tempel")
                                }
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Lainnya") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Folder baru") }, onClick = { menu = false; dialog = Dlg.NewDir })
                                    DropdownMenuItem(text = { Text("File baru") }, onClick = { menu = false; dialog = Dlg.NewFile })
                                    DropdownMenuItem(text = { Text("Urutkan") }, onClick = { menu = false; dialog = Dlg.Sort })
                                    DropdownMenuItem(
                                        text = { Text("File tersembunyi") },
                                        trailingIcon = { if (vm.settings.showHidden) Icon(Icons.Default.Check, null) },
                                        onClick = { menu = false; vm.update { it.copy(showHidden = !it.showHidden) } }
                                    )
                                    DropdownMenuItem(text = { Text("Segarkan") }, onClick = { menu = false; vm.reload() })
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        title = { Text("${vm.selected.size} dipilih") },
                        navigationIcon = {
                            IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Default.Close, "Batal") }
                        },
                        actions = {
                            IconButton(onClick = { vm.copySel() }) { Icon(painterResource(R.drawable.ic_copy), "Salin") }
                            IconButton(onClick = { vm.cutSel() }) { Icon(painterResource(R.drawable.ic_cut), "Potong") }
                            IconButton(onClick = {
                                if (vm.settings.confirmDelete) dialog = Dlg.Delete else vm.deleteSelected()
                            }) { Icon(Icons.Default.Delete, "Hapus") }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Lainnya") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (selNodes.isNotEmpty() && selNodes.none { it.isDir }) {
                                        DropdownMenuItem(text = { Text("Bagikan") }, onClick = { menu = false; onShare(selNodes); vm.clearSelection() })
                                    }
                                    DropdownMenuItem(text = { Text("Kompres ke ZIP") }, onClick = { menu = false; dialog = Dlg.Zip })
                                    if (selNodes.size == 1 && Archive.isArchive(selNodes[0])) {
                                        DropdownMenuItem(text = { Text("Ekstrak") }, onClick = { menu = false; dialog = Dlg.ArchiveMenu(selNodes[0]) })
                                    }
                                    if (selNodes.size == 1) {
                                        DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menu = false; dialog = Dlg.Rename(selNodes[0]) })
                                    }
                                    DropdownMenuItem(text = { Text("Pilih semua") }, onClick = { menu = false; vm.selectAll() })
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!selecting && !needAccess) {
                    Box {
                        FloatingActionButton(onClick = { fab = true }) { Icon(Icons.Default.Add, "Baru") }
                        DropdownMenu(expanded = fab, onDismissRequest = { fab = false }) {
                            DropdownMenuItem(text = { Text("Folder baru") }, onClick = { fab = false; dialog = Dlg.NewDir })
                            DropdownMenuItem(text = { Text("File baru") }, onClick = { fab = false; dialog = Dlg.NewFile })
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Breadcrumb(vm)
                if (vm.busy || vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Spacer(Modifier.height(4.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        needAccess -> Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Izin akses semua file dibutuhkan untuk menjelajah penyimpanan.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRequestAccess) { Text("Beri izin") }
                        }
                        vm.items.isEmpty() && !vm.loading -> Text(
                            vm.emptyText,
                            Modifier.align(Alignment.Center).padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(vm.items, key = { it.key }) { n ->
                                FileRow(n, n.key in vm.selected, onClick = { click(n) }, onLong = { vm.toggle(n) })
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- dialog ----------
    when (val d = dialog) {
        null -> {}
        Dlg.NewDir -> InputDialog("Folder baru", "", onDismiss = { dialog = null }) { vm.newFolder(it) }
        Dlg.NewFile -> InputDialog("File baru", "", onDismiss = { dialog = null }) { vm.newFile(it) }
        is Dlg.Rename -> InputDialog("Ganti nama", d.n.name, onDismiss = { dialog = null }) { vm.rename(d.n, it) }
        Dlg.Zip -> {
            val def = if (selNodes.size == 1) selNodes[0].name.substringBeforeLast('.', selNodes[0].name) + ".zip" else "Archive.zip"
            InputDialog("Nama ZIP", def, onDismiss = { dialog = null }) {
                vm.compress(if (it.endsWith(".zip", true)) it else "$it.zip")
            }
        }
        Dlg.Delete -> ConfirmDialog(
            "Hapus",
            if (selNodes.size == 1) "Hapus \"${selNodes[0].name}\"?" else "Hapus ${selNodes.size} item?",
            "Hapus", onDismiss = { dialog = null }
        ) { vm.deleteSelected() }
        Dlg.Sort -> SortDialog(vm) { dialog = null }
        Dlg.AddLoc -> AddLocationDialog(
            onDismiss = { dialog = null },
            onPickTree = { dialog = null; onPickTree(it) },
            onManual = { dialog = Dlg.ManualPath }
        )
        Dlg.ManualPath -> InputDialog(
            "Path folder", "/storage/emulated/0/", allowSlash = true, onDismiss = { dialog = null }
        ) { vm.addPath(it) }
        is Dlg.ArchiveMenu -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.n.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ClickRow("Ekstrak ke folder \"${Archive.baseName(d.n)}\"", null) { dialog = null; vm.extract(d.n, true) }
                    ClickRow("Ekstrak di sini", null) { dialog = null; vm.extract(d.n, false) }
                    ClickRow("Buka dengan aplikasi lain", null) { dialog = null; onOpenFile(d.n) }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Breadcrumb(vm: MainViewModel) {
    val scroll = rememberScrollState()
    LaunchedEffect(vm.stack.size) {
        delay(50)
        scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        vm.stack.forEachIndexed { i, n ->
            if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = i == vm.stack.lastIndex
            Text(
                n.name,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !last) { vm.goTo(i) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(n: Node, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val icon = when {
        n.isDir -> R.drawable.ic_folder
        Archive.isArchive(n) -> R.drawable.ic_archive
        Fmt.isImage(n) -> R.drawable.ic_image
        else -> R.drawable.ic_file
    }
    val sub = buildString {
        if (!n.isDir) append(Fmt.size(n.size))
        if (n.modified > 0) {
            if (isNotEmpty()) append("  •  ")
            append(Fmt.date(n.modified))
        }
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLong),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (sub.isNotEmpty()) { { Text(sub, maxLines = 1) } } else null
    )
}
