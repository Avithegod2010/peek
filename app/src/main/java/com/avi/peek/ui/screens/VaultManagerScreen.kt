package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.repo.VaultEntry
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.rememberPrivateUnlock
import com.avi.peek.util.PrivateAccess
import com.avi.peek.util.fileSize
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VaultViewModel : ViewModel() {
    private val repo = ServiceLocator.repository
    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _entries.value = runCatching { repo.vaultEntries() }.getOrDefault(emptyList())
            _busy.value = false
        }
    }

    fun seal(username: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.sealVaultProfile(username) }
                .onSuccess { _messages.emit("Sealed downloads for @$username") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }

    fun open(username: String) {
        viewModelScope.launch {
            _busy.value = true
            PrivateAccess.reveal()
            runCatching { repo.openVaultProfile(username) }
                .onSuccess { _messages.emit("Opened the vault for @$username") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }

    fun delete(username: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.deleteVaultProfile(username) }
                .onSuccess { _messages.emit("Deleted private downloads for @$username") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }

    fun export(uri: android.net.Uri, passphrase: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { ServiceLocator.privateBackupManager.exportTo(uri, passphrase) }
                .onSuccess { _messages.emit("Encrypted private vault exported") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
        }
    }

    fun import(uri: android.net.Uri, passphrase: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { ServiceLocator.privateBackupManager.importFrom(uri, passphrase) }
                .onSuccess { _messages.emit("Private vault restored") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }
}

@Composable
fun VaultManagerScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel(factory = SimpleViewModelFactory { VaultViewModel() }),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val unlock = rememberPrivateUnlock { viewModel.refresh() }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var showPassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val grouped = entries.groupBy { it.username }
    val total = entries.sumOf { it.bytes }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            pendingExport = uri
            pendingImport = null
            showPassphrase = true
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImport = uri
            pendingExport = null
            showPassphrase = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Private vault",
            subtitle = "${entries.size} files · ${total.fileSize()} sealed on this device",
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Rounded.Storage, contentDescription = "Refresh")
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { exportLauncher.launch("peek-private-vault.peekvault") },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Export encrypted") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    importLauncher.launch(arrayOf("application/octet-stream", "application/json"))
                },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Import backup") }
        }
        if (entries.isEmpty()) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("No private downloads yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Private downloads are sealed with Android Keystore AES-GCM. Enable private downloads in Settings → Privacy & storage, unlock private content, then download a private post.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (username, files) ->
                    item(key = "profile-$username") {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(9.dp).size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("@$username", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${files.size} files · ${files.sumOf { it.bytes }.fileSize()} · " +
                                                "${files.count { it.sealed }} sealed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        enabled = !busy,
                                        onClick = { unlock { viewModel.open(username) } },
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open")
                                    }
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = { viewModel.seal(username) },
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Seal")
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        onClick = { confirmDelete = username },
                                    ) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    items(files, key = { "file-${it.code}" }) { file ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (file.sealed) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    file.caption?.ifBlank { file.code } ?: file.code,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(file.bytes.fileSize(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPassphrase) {
        AlertDialog(
            onDismissRequest = { showPassphrase = false; passphrase = "" },
            title = {
                Text(if (pendingExport != null) "Encrypt this vault backup" else "Unlock this vault backup")
            },
            text = {
                Column {
                    Text(
                        "Use a separate passphrase. It is never stored by Peek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.isNotBlank(),
                    onClick = {
                        val export = pendingExport
                        val import = pendingImport
                        val secret = passphrase
                        passphrase = ""
                        showPassphrase = false
                        pendingExport = null
                        pendingImport = null
                        if (export != null) viewModel.export(export, secret)
                        if (import != null) viewModel.import(import, secret)
                    },
                ) { Text(if (pendingExport != null) "Export" else "Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showPassphrase = false; passphrase = "" }) { Text("Cancel") }
            },
        )
    }

    confirmDelete?.let { username ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete private downloads?") },
            text = { Text("This removes the sealed files and offline state for @$username. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(username)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
