package com.avi.peek.ui.screens

import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.SessionHealth
import com.avi.peek.data.repo.SessionAudit
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.rememberPrivateUnlock
import com.avi.peek.util.PrivateAccess
import com.avi.peek.ui.SimpleViewModelFactory
import com.avi.peek.ui.auth.InstagramLoginActivity
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.openUrl
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

class AccountViewModel : ViewModel() {
    private val manager = ServiceLocator.accountManager
    private val repo = ServiceLocator.repository

    val account: StateFlow<AccountState> = manager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _audit = MutableStateFlow<SessionAudit?>(null)
    val audit: StateFlow<SessionAudit?> = _audit.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refreshAudit()
    }

    fun refreshAudit() {
        viewModelScope.launch {
            _audit.value = runCatching { manager.audit() }.getOrNull()
        }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun connect(cookies: List<String>) {
        if (cookies.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val connected = runCatching { manager.connect(cookies) }
                .onFailure { _messages.emit(it.userMessage()) }
                .getOrNull()
            if (connected != null) {
                _messages.emit(
                    connected.username?.let { "Signed in as @$it" } ?: "Instagram session connected",
                )
                _busy.value = false
                runCatching { manager.refreshIdentity() }
                refreshAudit()
            } else {
                _busy.value = false
            }
        }
    }

    fun connectManual(raw: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { manager.connectManual(raw) }
                .onSuccess {
                    _messages.emit("Instagram session connected")
                    runCatching { manager.refreshIdentity() }
                }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refreshAudit()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                manager.disconnect()
                repo.purgePrivateContent()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
                .onSuccess { _messages.emit("Session and private downloads removed from this device") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refreshAudit()
        }
    }

    fun refreshIdentity() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { manager.refreshIdentity() }
                .onSuccess { verified ->
                    _messages.emit(if (verified) "Session checked" else "Couldn't verify the session")
                }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
            refreshAudit()
        }
    }

    fun unlockVault() {
        viewModelScope.launch {
            _busy.value = true
            PrivateAccess.reveal()
            runCatching { repo.openPrivateVault() }
                .onSuccess { _messages.emit("Private vault unlocked for this session") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
        }
    }

    fun sealVault() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.sealPrivateVault() }
                .onSuccess { _messages.emit("Private downloads sealed") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
        }
    }

    fun purgePrivate() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repo.purgePrivateContent() }
                .onSuccess { _messages.emit("Private downloads and cached private posts removed") }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
        }
    }
}

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenAudit: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = SimpleViewModelFactory { AccountViewModel() }),
) {
    SecureScreen()
    val context = LocalContext.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    var showManual by remember { mutableStateOf(false) }
    var manualCookie by remember { mutableStateOf("") }
    val unlock = rememberPrivateUnlock { viewModel.message(it) }

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = result.data
                ?.getStringArrayListExtra(InstagramLoginActivity.EXTRA_COOKIES)
                .orEmpty()
            viewModel.connect(cookies)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Instagram account",
            subtitle = "Optional · public browsing stays available",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountStatusCard(
                account = account,
                busy = busy,
                onSignIn = {
                    loginLauncher.launch(
                        android.content.Intent(context, InstagramLoginActivity::class.java),
                    )
                },
                onSignOut = viewModel::disconnect,
                onRefresh = viewModel::refreshIdentity,
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Session audit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                audit?.let {
                                    "${it.cookieNames.size} cookie names · ${it.state.health.name.lowercase()}"
                                } ?: "Status, cookie names and request history",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onOpenAudit) { Text("Open") }
                    }
                    if (audit?.lastError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            audit!!.lastError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (audit!!.state.health == SessionHealth.EXPIRED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Private vault", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Private downloads are sealed with your device key",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { unlock { viewModel.unlockVault() } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Unlock")
                        }
                        OutlinedButton(
                            onClick = viewModel::sealVault,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Seal")
                        }
                    }
                    TextButton(onClick = viewModel::purgePrivate) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Delete private downloads & cache")
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Advanced: manual session", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Fallback when Instagram blocks the in-app login",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showManual = !showManual }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Key, contentDescription = "Toggle manual session")
                        }
                    }
                    if (showManual) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Paste only your own sessionid cookie or a full Instagram cookie header. It is encrypted on this device. Never share it with anyone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualCookie,
                            onValueChange = { manualCookie = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("sessionid or cookie header") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = manualCookie.isNotBlank() && !busy,
                            onClick = {
                                viewModel.connectManual(manualCookie)
                                manualCookie = ""
                                showManual = false
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Connect session") }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "How Peek handles this",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Peek never sees or stores your password.\n" +
                            "• Only the Instagram session cookies are saved, encrypted with the Android Keystore, on this device.\n" +
                            "• Signing out deletes the saved cookies and clears the WebView session.\n" +
                            "• Nothing is sent to Peek or any other server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What signing in changes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Instagram only shows a private account's posts to accounts that follow it. " +
                            "If you sign in and follow that account, Peek will try to read the same pages your session can see. " +
                            "If you don't follow it, Peek will still show a clear “This account is private” message instead of an empty grid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Instagram may challenge or block web sign-in, and automated reading can conflict with Instagram's terms. " +
                            "Use your own account, keep activity light, and treat private posts as private.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { context.openUrl("https://help.instagram.com/581066165581870") },
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Instagram's terms")
                    }
                }
            }

            Text(
                "Peek is not affiliated with Instagram. Media belongs to its creators.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AccountStatusCard(
    account: AccountState,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (account.signedIn) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        if (account.signedIn) Icons.Rounded.CheckCircle else Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp).size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (account.signedIn) {
                            account.username?.let { "@$it" } ?: "Instagram connected"
                        } else {
                            "Not signed in"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            !account.signedIn -> "Peek still works for public profiles"
                            account.health == SessionHealth.EXPIRED -> "Session expired · reconnect to keep private access"
                            else -> "Private accounts you follow can be requested"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (account.health == SessionHealth.EXPIRED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (account.signedIn && account.savedAt > 0L) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Connected ${(account.savedAt / 1000L).relativeTime()}" +
                        account.userId?.let { " · ID $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", style = MaterialTheme.typography.labelMedium)
                }
            } else if (account.signedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account.health == SessionHealth.EXPIRED) {
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Reconnect") }
                    } else {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Check session") }
                    }
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign out")
                    }
                }
            } else {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in to Instagram")
                }
            }
        }
    }
}
