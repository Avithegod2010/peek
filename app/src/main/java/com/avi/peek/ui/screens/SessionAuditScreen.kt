package com.avi.peek.ui.screens

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.SessionHealth
import com.avi.peek.data.repo.RequestEvent
import com.avi.peek.data.repo.SessionAudit
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.fileSize
import com.avi.peek.util.relativeTime
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionAuditViewModel : ViewModel() {
    private val manager = ServiceLocator.accountManager
    private val repo = ServiceLocator.repository

    private val _audit = MutableStateFlow<SessionAudit?>(null)
    val audit: StateFlow<SessionAudit?> = _audit.asStateFlow()

    private val _vaultBytes = MutableStateFlow(0L)
    val vaultBytes: StateFlow<Long> = _vaultBytes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val events: StateFlow<List<RequestEvent>> = ServiceLocator.requestLog.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _audit.value = runCatching { manager.audit() }.getOrNull()
            _vaultBytes.value = runCatching { repo.privateVaultSize() }.getOrDefault(0L)
            _busy.value = false
        }
    }

    fun verify() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { manager.refreshIdentity() }
                .onSuccess { verified ->
                    _messages.emit(if (verified) "Session verified" else "Couldn't verify the session")
                }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }

    fun clearPrivate() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.purgePrivateContent() }
                .onSuccess { _messages.emit("Private downloads and cache removed") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                manager.disconnect()
                repo.purgePrivateContent()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
                .onSuccess { _messages.emit("Signed out") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refresh()
        }
    }
}

@Composable
fun SessionAuditScreen(
    onBack: () -> Unit,
    viewModel: SessionAuditViewModel = viewModel(
        factory = SimpleViewModelFactory { SessionAuditViewModel() },
    ),
) {
    SecureScreen()
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    val vaultBytes by viewModel.vaultBytes.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Session audit",
            subtitle = "What Peek knows about this Instagram session",
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = viewModel::refresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "status") {
                val state = audit?.state
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state?.health == SessionHealth.EXPIRED) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state?.signedIn == true) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    state?.signedIn != true -> "Not signed in"
                                    state.health == SessionHealth.EXPIRED -> "Session expired"
                                    state.health == SessionHealth.HEALTHY -> "Session healthy"
                                    else -> "Session unverified"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state?.username != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("@${state.username}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cookie names only · values are never shown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            audit?.cookieNames?.forEach { name ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                        if (state == null || !state.signedIn) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Open Account to connect an optional Instagram session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "times") {
                val item = audit
                if (item != null) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            AuditLine("Saved", item.createdAt)
                            AuditLine("Last verified", item.lastVerifiedAt)
                            AuditLine("Last successful request", item.lastSuccessAt)
                            if (item.expiredAt > 0L) AuditLine("Expired", item.expiredAt)
                            if (item.lastError != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        item.lastError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Text(
                                "Private vault: ${vaultBytes.fileSize()} sealed on this device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && audit?.state?.signedIn == true,
                        onClick = viewModel::verify,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Verify") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = viewModel::clearPrivate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear private")
                    }
                }
                if (audit?.state?.signedIn == true) {
                    TextButton(
                        enabled = !busy,
                        onClick = viewModel::signOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Sign out and remove private downloads")
                    }
                }
            }

            item(key = "events-title") {
                Text(
                    "Recent requests",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (events.isEmpty()) {
                item(key = "events-empty") {
                    Text(
                        "No requests logged yet. Profile reads, page loads, post details, comments and downloads appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(events.asReversed().take(60), key = { "${it.at}-${it.label}-${it.outcome}" }) { event ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (event.rateLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    event.outcome,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (event.rateLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                (event.at / 1000L).relativeTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLine(label: String, at: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (at > 0L) (at / 1000L).relativeTime() else "Never",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
