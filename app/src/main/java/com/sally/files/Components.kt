package com.sally.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InputDialog(
    title: String,
    initial: String,
    allowSlash: Boolean = false,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val s = text.trim()
                if (s.isNotEmpty() && (allowSlash || !s.contains('/'))) {
                    onDismiss()
                    onOk(s)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(i); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Text(o, Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun SortDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Urutkan") },
        text = {
            Column {
                listOf("Nama", "Ukuran", "Tanggal").forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.update { it.copy(sortBy = i) } }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == s.sortBy, onClick = null)
                        Text(o, Modifier.padding(start = 16.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.update { it.copy(sortDesc = !it.sortDesc) } }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Urutan menurun", Modifier.weight(1f))
                    Switch(checked = s.sortDesc, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun AddLocationDialog(onDismiss: () -> Unit, onPickTree: (String?) -> Unit, onManual: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah lokasi") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClickRow("Pilih folder (SAF)", "Termux, Acode, kartu SD, atau provider lain") { onPickTree(null) }
                ClickRow("Termux", "Buka picker di provider Termux") { onPickTree("termux") }
                ClickRow("Acode", "Pilih \"Acode\" di sidebar picker") { onPickTree("acode") }
                ClickRow("Path manual", "Mis. /storage/emulated/0/Documents") { onManual() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun ClickRow(title: String, sub: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title) },
        supportingContent = if (sub != null) { { Text(sub) } } else null
    )
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = if (sub != null) { { Text(sub) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    )
}
