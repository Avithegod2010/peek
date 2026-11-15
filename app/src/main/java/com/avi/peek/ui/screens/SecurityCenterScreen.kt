package com.avi.peek.ui.screens

import android.webkit.CookieManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VpnKey
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.avi.peek.R
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
import com.avi.peek.util.PrivateAccess
import com.avi.peek.util.fileSize
import com.avi.peek.util.relativeTime
import com.avi.peek.util.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecuritySnapshot(
    val audit: SessionAudit? = null,
    val vaultBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val privateMedia: Int = 0,
    val privateDownloads: Int = 0,
    val appLock: Boolean = false,
    val panicMode: Boolean = false,
)

class SecurityCenterViewModel : ViewModel() {
    private val manager = ServiceLocator.accountManager
    private val repo = ServiceLocator.repository

    private val _snapshot = MutableStateFlow(SecuritySnapshot())
    val snapshot: StateFlow<SecuritySnapshot> = _snapshot.asStateFlow()

    val events: StateFlow<List<RequestEvent>> = ServiceLocator.requestLog.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = runCatching {
                ServiceLocator.settings.settings.first()
            }.getOrNull()
            val counts = runCatching { repo.privateMediaCounts() }.getOrDefault(0 to 0)
            _snapshot.value = SecuritySnapshot(
                audit = runCatching { manager.audit() }.getOrNull(),
                vaultBytes = runCatching { repo.privateVaultSize() }.getOrDefault(0L),
                databaseBytes = runCatching { repo.databaseSize() }.getOrDefault(0L),
                privateMedia = counts.first,
                privateDownloads = counts.second,
                appLock = settings?.appLock == true,
                panicMode = settings?.privateContentHidden == true,
            )
        }
    }

    fun wipePrivate() {
        viewModelScope.launch {
            PrivateAccess.hide()
            runCatching {
                repo.purgePrivateContent()
                ServiceLocator.settings.setPrivateContentHidden(true)
            }.onSuccess { _messages.emit("Private downloads and cached private posts removed") }
                .onFailure { _messages.emit(it.userMessage()) }
            refresh()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                manager.disconnect()
                repo.purgePrivateContent()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }.onSuccess { _messages.emit("Signed out and removed the session") }
                .onFailure { _messages.emit(it.userMessage()) }
            refresh()
        }
    }

    fun clearLog() {
        ServiceLocator.requestLog.clear()
        refresh()
    }
}

@Composable
fun SecurityCenterScreen(
    onBack: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenRateLimits: () -> Unit,
    viewModel: SecurityCenterViewModel = viewModel(
        factory = SimpleViewModelFactory { SecurityCenterViewModel() },
    ),
) {
    SecureScreen()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = stringResource(R.string.security_title),
            subtitle = stringResource(R.string.security_subtitle),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "session") {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.security_session),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            snapshot.audit?.state?.let { state ->
                                when {
                                    !state.signedIn -> "Not signed in — public browsing only"
                                    state.health == SessionHealth.EXPIRED -> "Session expired — reconnect needed"
                                    else -> "Signed in as @${state.username ?: "Instagram"}"
                                }
                            } ?: "Not signed in — public browsing only",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${snapshot.audit?.cookieNames?.size ?: 0} cookie names · values are never shown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenAudit, shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.security_audit))
                            }
                            if (snapshot.audit?.state?.signedIn == true) {
                                TextButton(onClick = viewModel::signOut) {
                                    Text(stringResource(R.string.security_sign_out))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "data") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.security_data),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SecurityLine("Database metadata (captions, URLs, dates)", snapshot.databaseBytes.fileSize())
                        SecurityLine("Sealed private vault", snapshot.vaultBytes.fileSize())
                        SecurityLine("Private media rows", "${snapshot.privateMedia} · ${snapshot.privateDownloads} offline")
                        SecurityLine("App lock", if (snapshot.appLock) "On" else "Off")
                        SecurityLine("Panic mode", if (snapshot.panicMode) "Private content hidden" else "Off")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenVault, shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.security_vault))
                            }
                            TextButton(onClick = viewModel::wipePrivate) {
                                Text(
                                    stringResource(R.string.security_wipe),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "network") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.security_network),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("instagram.com and Instagram's CDN — profiles, posts, comments, highlights, media", style = MaterialTheme.typography.bodySmall)
                        Text("DuckDuckGo HTML search — only when you search by name", style = MaterialTheme.typography.bodySmall)
                        Text("No Peek server, analytics or crash reporting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item(key = "actions") {
                TextButton(onClick = onOpenRateLimits, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.security_rate_limits))
                }
                TextButton(onClick = viewModel::clearLog, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.security_clear_log))
                }
            }

            item(key = "events-title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Recent network outcomes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (events.isEmpty()) {
                item(key = "events-empty") {
                    Text(
                        "No requests logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(events.asReversed().take(12), key = { "${it.at}-${it.label}-${it.outcome}" }) { event ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                event.label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (event.rateLimited) "rate-limited" else event.outcome,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (event.rateLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
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
private fun SecurityLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
