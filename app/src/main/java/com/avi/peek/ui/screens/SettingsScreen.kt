package com.avi.peek.ui.screens

import android.Manifest
import android.app.Activity
import android.app.LocaleManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import android.os.LocaleList
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avi.peek.BuildConfig
import com.avi.peek.data.local.AccountState
import com.avi.peek.data.local.PaletteStyle
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.PrivateDownloadPolicy
import com.avi.peek.data.local.ThemeMode
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.rememberPrivateUnlock
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.fileSize
import com.avi.peek.util.openUrl
import com.avi.peek.util.relativeTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAudit: () -> Unit = {},
    onOpenRateLimits: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by ServiceLocator.accountManager.state
        .collectAsStateWithLifecycle(initialValue = AccountState())
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val unlockPrivate = rememberPrivateUnlock { viewModel.syncMessage(it) }
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val haptics = LocalPeekHaptics.current
    val context = LocalContext.current
    var confirmWipe by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setNotifications(true)
            viewModel.syncMessage("Notifications on")
        } else {
            viewModel.syncMessage("Notification permission denied")
        }
    }

    var backupAction by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) backupAction = uri to false }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) backupAction = uri to true }

    val archiveHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri -> if (uri != null) viewModel.exportArchive(uri, html = true) }

    val archiveJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportArchive(uri, html = false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
        viewModel.refreshStorage()
    }

    fun toggleNotifications(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotifications(enabled)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SimpleTopBar(
            title = "Settings",
            subtitle = "Tune the quiet",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "appearance") {
                SettingsSection("Appearance", Icons.Rounded.Palette) {
                    ThemeModePicker(settings.themeMode) {
                        viewModel.setThemeMode(it)
                        haptics.tap()
                    }
                    LanguagePicker()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitch(
                            icon = Icons.Rounded.Contrast,
                            title = "Material You",
                            subtitle = "Use the wallpaper palette on Android 12+",
                            checked = settings.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it); haptics.tap() },
                        )
                    }
                    if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        PalettePicker(settings.palette) {
                            viewModel.setPalette(it)
                            haptics.tap()
                        }
                    }
                    CustomAccentPicker(
                        current = settings.customAccent,
                        saturation = settings.accentSaturation,
                        value = settings.accentValue,
                        onAccent = {
                            viewModel.setCustomAccent(it)
                            haptics.tap()
                        },
                        onSaturation = viewModel::setAccentSaturation,
                        onValue = viewModel::setAccentValue,
                    )
                    FontScaleSlider(settings.fontScale) {
                        viewModel.setFontScale(it)
                    }
                    SettingsSwitch(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "AMOLED black",
                        subtitle = "Pure black surfaces in dark mode",
                        checked = settings.amoled,
                        onCheckedChange = { viewModel.setAmoled(it); haptics.tap() },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Contrast,
                        title = "High contrast",
                        subtitle = "Stronger text and outlines",
                        checked = settings.highContrast,
                        onCheckedChange = { viewModel.setHighContrast(it); haptics.tap() },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptics",
                        subtitle = "Tiny taps for likes, follows and navigation",
                        checked = settings.haptics,
                        onCheckedChange = {
                            viewModel.setHaptics(it)
                            if (it) haptics.confirm()
                        },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Animation,
                        title = "Reduce motion",
                        subtitle = "Calmer transitions and no particle bursts",
                        checked = settings.reduceMotion,
                        onCheckedChange = { viewModel.setReduceMotion(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Smartphone,
                        title = "One-handed mode",
                        subtitle = "Put a search action near the bottom of Home",
                        checked = settings.oneHanded,
                        onCheckedChange = { viewModel.setOneHanded(it) },
                    )
                }
            }

            item(key = "sync") {
                SettingsSection("Sync", Icons.Rounded.Sync) {
                    SettingsSwitch(
                        icon = Icons.Rounded.Refresh,
                        title = "Refresh when Peek opens",
                        subtitle = "At most once every ten minutes",
                        checked = settings.syncOnOpen,
                        onCheckedChange = { viewModel.setSyncOnOpen(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Download,
                        title = "Richer feed",
                        subtitle = "Fetch like counts and video details for the newest posts",
                        checked = settings.enrichFeed,
                        onCheckedChange = { viewModel.setEnrichFeed(it) },
                    )
                    SyncDepthSlider(settings.syncDepth) { viewModel.setSyncDepth(it) }
                    SettingsSwitch(
                        icon = Icons.Rounded.Wifi,
                        title = "Wi-Fi only",
                        subtitle = "Refresh and download over Wi-Fi only",
                        checked = settings.wifiOnlySync,
                        onCheckedChange = { viewModel.setWifiOnly(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Charge only for downloads",
                        subtitle = "Hold the download queue until the phone is charging",
                        checked = settings.downloadChargingOnly,
                        onCheckedChange = { viewModel.setDownloadChargingOnly(it) },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Download,
                        title = "Max posts per profile download",
                        subtitle = if (settings.downloadQueuePerProfile == 0) {
                            "No limit · whole profile"
                        } else {
                            "Newest ${settings.downloadQueuePerProfile} posts"
                        },
                        onClick = {
                            val next = when (settings.downloadQueuePerProfile) {
                                0 -> 10
                                10 -> 25
                                25 -> 50
                                50 -> 100
                                100 -> 250
                                else -> 0
                            }
                            viewModel.setDownloadQueuePerProfile(next)
                        },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Sync,
                        title = "Background sync",
                        subtitle = "Peek checks your circle periodically",
                        checked = settings.backgroundSync,
                        onCheckedChange = { viewModel.setBackgroundSync(it) },
                    )
                    if (settings.backgroundSync) {
                        IntervalPicker(settings.backgroundIntervalHours) { viewModel.setBackgroundInterval(it) }
                        SettingsSwitch(
                            icon = Icons.Rounded.Notifications,
                            title = "New post notifications",
                            subtitle = "A quiet heads-up, never a feed",
                            checked = settings.notifications,
                            onCheckedChange = { toggleNotifications(it) },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { viewModel.syncNow(); haptics.tap() },
                            enabled = !sync.running,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (sync.running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (sync.running) sync.label.ifBlank { "Syncing" } else "Sync now")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (settings.lastFullSync > 0) "Last sync ${(settings.lastFullSync / 1000).relativeTime()}" else "Not synced yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "playback") {
                SettingsSection("Playback", Icons.Rounded.PlayCircle) {
                    SettingsSwitch(
                        icon = Icons.Rounded.PlayCircle,
                        title = "Autoplay reels",
                        subtitle = "Play the reel you land on",
                        checked = settings.autoplayReels,
                        onCheckedChange = { viewModel.setAutoplayReels(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.VolumeOff,
                        title = "Mute by default",
                        subtitle = "You can unmute from any player",
                        checked = settings.muteVideos,
                        onCheckedChange = { viewModel.setMuteVideos(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.DataSaverOn,
                        title = "Data saver",
                        subtitle = "Prefer smaller videos when downloading",
                        checked = settings.dataSaver,
                        onCheckedChange = { viewModel.setDataSaver(it) },
                    )
                }
            }

            item(key = "account") {
                SettingsSection("Instagram account", Icons.Rounded.AccountCircle) {
                    SettingsClickRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = if (account.signedIn) {
                            account.username?.let { "@$it" } ?: "Instagram connected"
                        } else {
                            "Sign in (optional)"
                        },
                        subtitle = if (account.signedIn) {
                            if (account.health == com.avi.peek.data.local.SessionHealth.EXPIRED) {
                                "Session expired · tap to reconnect"
                            } else {
                                "Session stored encrypted on this device · tap to manage"
                            }
                        } else {
                            "View private accounts you follow · public browsing stays available"
                        },
                        onClick = onOpenAccount,
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.History,
                        title = "Session audit",
                        subtitle = "Cookie names, expiry state and recent request outcomes",
                        onClick = onOpenAudit,
                    )
                    Text(
                        "Peek never stores your Instagram password and never sends your session anywhere.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            item(key = "privacy") {
                SettingsSection("Privacy & storage", Icons.Rounded.Lock) {
                    SettingsClickRow(
                        icon = Icons.Rounded.Security,
                        title = "Security center",
                        subtitle = "Session, data map, network destinations and wipe actions",
                        onClick = onOpenSecurity,
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Hide like counts",
                        subtitle = "Keep the numbers out of your feed",
                        checked = settings.hideLikeCounts,
                        onCheckedChange = { viewModel.setHideLikeCounts(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric app lock",
                        subtitle = "Require a biometric or screen lock next time Peek opens",
                        checked = settings.appLock,
                        onCheckedChange = { viewModel.setAppLock(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Description,
                        title = "Caption sidecars in the gallery",
                        subtitle = "Write a small JSON caption/date file with each gallery save",
                        checked = settings.gallerySidecars,
                        onCheckedChange = { viewModel.setGallerySidecars(it) },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Lock,
                        title = "Private downloads",
                        subtitle = settings.privateDownloadPolicy.label +
                            " · private media never goes to the public gallery",
                        onClick = {
                            viewModel.setPrivateDownloadPolicy(
                                if (settings.privateDownloadPolicy == PrivateDownloadPolicy.BLOCK) {
                                    PrivateDownloadPolicy.ALLOW
                                } else {
                                    PrivateDownloadPolicy.BLOCK
                                },
                            )
                        },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Lock,
                        title = "Seal private downloads",
                        subtitle = "Encrypt private media at rest with the Android Keystore",
                        checked = settings.privateVault,
                        onCheckedChange = { viewModel.setPrivateVault(it) },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.LockOpen,
                        title = "Unlock private vault",
                        subtitle = "Required for private downloads and sealed offline media",
                        onClick = {
                            unlockPrivate { viewModel.revealPrivateContent() }
                        },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Panic mode · hide private content",
                        subtitle = "Hide private profiles, posts and downloads until biometric unlock",
                        checked = settings.privateContentHidden,
                        onCheckedChange = { hide ->
                            if (hide) {
                                viewModel.hidePrivateContent()
                            } else {
                                unlockPrivate { viewModel.revealPrivateContent() }
                            }
                        },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = "Clear private content",
                        subtitle = "Delete private downloads and cached private posts",
                        destructive = true,
                        onClick = viewModel::clearPrivateContent,
                    )
                    SettingsInfoRow(
                        icon = Icons.Rounded.Storage,
                        title = "Storage used",
                        subtitle = "Offline ${storage.first.fileSize()} · image cache ${storage.second.fileSize()}",
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Speed,
                        title = "Request budget & rate limits",
                        subtitle = "Hourly cap, quiet hours, backoff and recent outcomes",
                        onClick = onOpenRateLimits,
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Storage,
                        title = "Image cache limit",
                        subtitle = "${settings.imageCacheMb} MB · applied on the next app start",
                        onClick = {
                            val next = when (settings.imageCacheMb) {
                                128 -> 256
                                256 -> 512
                                512 -> 1024
                                else -> 128
                            }
                            viewModel.setImageCacheMb(next)
                        },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.DeleteSweep,
                        title = "Clear image cache",
                        subtitle = "Keeps bookmarks and downloads",
                        onClick = { viewModel.clearImageCache() },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Lock,
                        title = "Delete all local data",
                        subtitle = "Profiles, follows, saves and downloads",
                        destructive = true,
                        onClick = { confirmWipe = true },
                    )
                }
            }

            item(key = "backup") {
                SettingsSection("Backup", Icons.Rounded.Backup) {
                    SettingsClickRow(
                        icon = Icons.Rounded.Backup,
                        title = "Export backup",
                        subtitle = "Passphrase-encrypted by default · follows, saves and likes",
                        onClick = { exportLauncher.launch("peek-backup.peek") },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Restore,
                        title = "Restore backup",
                        subtitle = "Open an encrypted or legacy JSON Peek backup",
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/octet-stream", "application/json", "text/plain"),
                            )
                        },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Lock,
                        title = "Include private content in backups",
                        subtitle = "Off by default · private follows, saves and likes are omitted",
                        checked = settings.includePrivateInBackups,
                        onCheckedChange = { viewModel.setIncludePrivateInBackups(it) },
                    )
                    SettingsSwitch(
                        icon = Icons.Rounded.Code,
                        title = "Include private content in archives",
                        subtitle = "Off by default · excludes private captions and local files",
                        checked = settings.includePrivateInArchives,
                        onCheckedChange = { viewModel.setIncludePrivateInArchives(it) },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Code,
                        title = "Export HTML archive",
                        subtitle = "A readable page of cached posts, captions and images",
                        onClick = { archiveHtmlLauncher.launch("peek-archive.html") },
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Code,
                        title = "Export JSON archive",
                        subtitle = "Metadata for your cached, saved and liked posts",
                        onClick = { archiveJsonLauncher.launch("peek-archive.json") },
                    )
                }
            }

            item(key = "about") {
                SettingsSection("About", Icons.Rounded.Info) {
                    SettingsInfoRow(
                        icon = Icons.Rounded.Info,
                        title = "Peek ${BuildConfig.VERSION_NAME}",
                        subtitle = "An offline-first viewer for public Instagram profiles.",
                    )
                    SettingsClickRow(
                        icon = Icons.Rounded.Code,
                        title = "Source code",
                        subtitle = "github.com/Avithegod2010/peek",
                        onClick = { context.openUrl("https://github.com/Avithegod2010/peek") },
                    )
                    Text(
                        "Peek is unofficial and not affiliated with Instagram or Meta. Public pages are read by default; optional signed-in mode reuses your own session for private accounts you follow. There is no Peek account, no posting and no tracking. Posts, photos and videos belong to their owners.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            item(key = "footer") {
                Text(
                    "Made for quiet following",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text("Delete everything?") },
            text = {
                Text("This removes every local follow, bookmark, download and cached image, and takes you back to the welcome screen. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    viewModel.clearEverything()
                    haptics.error()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            },
        )
    }

    backupAction?.let { action ->
        val importing = action.second
        var passphrase by remember(action) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { backupAction = null },
            title = { Text(if (importing) "Restore this backup" else "Protect this backup") },
            text = {
                Column {
                    Text(
                        if (importing) {
                            "Enter the passphrase used when this backup was exported. Legacy unencrypted JSON backups can be restored with the field blank."
                        } else {
                            "A passphrase encrypts the backup with AES-256-GCM. Leave it blank to write a plain JSON file you can read elsewhere."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The passphrase is never stored. Lose it and the encrypted backup cannot be recovered.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = action.first
                        backupAction = null
                        if (importing) viewModel.importBackup(uri, passphrase)
                        else viewModel.exportBackup(uri, passphrase)
                    },
                ) { Text(if (importing) "Restore" else "Export") }
            },
            dismissButton = {
                TextButton(onClick = { backupAction = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 6.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ThemeModePicker(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = mode == current
            Surface(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    when (mode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.DARK -> "Dark"
                    },
                    modifier = Modifier.padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    val current = manager.applicationLocales
    val currentTag = if (current.isEmpty) "" else current.get(0)?.language.orEmpty()
    val options = listOf(
        "" to "System",
        "en" to "English",
        "hi" to "हिन्दी",
        "es" to "Español",
        "pt" to "Português",
        "ar" to "العربية",
        "ja" to "日本語",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("App language", style = MaterialTheme.typography.titleSmall)
        Text(
            "Translated navigation and disclaimers · Arabic switches the layout to RTL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            options.forEach { (tag, label) ->
                val selected = currentTag == tag
                Surface(
                    onClick = {
                        manager.applicationLocales = if (tag.isBlank()) {
                            LocaleList.getEmptyLocaleList()
                        } else {
                            LocaleList.forLanguageTags(tag)
                        }
                        (context as? Activity)?.recreate()
                    },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PalettePicker(current: PaletteStyle, onSelect: (PaletteStyle) -> Unit) {
    val colors = mapOf(
        PaletteStyle.LAVENDER to Color(0xFF5B4BC4),
        PaletteStyle.OCEAN to Color(0xFF006A6A),
        PaletteStyle.FOREST to Color(0xFF3E6837),
        PaletteStyle.SUNSET to Color(0xFF8B4A2F),
        PaletteStyle.ROSE to Color(0xFF8B4657),
        PaletteStyle.MINT to Color(0xFF006B5B),
        PaletteStyle.COFFEE to Color(0xFF6F4E37),
        PaletteStyle.SAKURA to Color(0xFF9C4059),
        PaletteStyle.CITRUS to Color(0xFF7A5900),
        PaletteStyle.INDIGO to Color(0xFF3F51B5),
        PaletteStyle.SLATE to Color(0xFF4A5C6A),
        PaletteStyle.SAND to Color(0xFF7A5B2E),
        PaletteStyle.AURORA to Color(0xFF7B3FA0),
    )
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PaletteStyle.entries.forEach { style ->
            val selected = style == current
            Surface(
                onClick = { onSelect(style) },
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = if (selected) {
                    androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                    )
                } else {
                    null
                },
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 11.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(colors[style] ?: MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        style.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomAccentPicker(
    current: Long?,
    saturation: Float,
    value: Float,
    onAccent: (Long?) -> Unit,
    onSaturation: (Float) -> Unit,
    onValue: (Float) -> Unit,
) {
    val context = LocalContext.current
    val currentColor = current?.let { Color(it.toInt()) }
    val hsv = remember(current) {
        FloatArray(3).also { array ->
            if (current != null) {
                android.graphics.Color.colorToHSV(current.toInt(), array)
            } else {
                array[0] = 265f
                array[1] = saturation
                array[2] = value
            }
        }
    }
    var hue by remember(current) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(saturation) { mutableFloatStateOf(saturation) }
    var bright by remember(value) { mutableFloatStateOf(value) }
    var hex by remember(current) {
        mutableStateOf(current?.let { String.format("#%08X", it) } ?: "#FF5B4BC4")
    }

    fun apply() {
        onAccent(Color.hsv(hue, sat, bright).toArgb().toLong())
        onSaturation(sat)
        onValue(bright)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(currentColor ?: Color(0xFF5B4BC4)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Custom accent", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (current == null) "Off · using the palette or wallpaper" else "Overrides the palette accent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = current != null,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        apply()
                    } else {
                        onAccent(null)
                    }
                },
            )
        }
        if (current != null) {
            Slider(
                value = hue,
                onValueChange = { hue = it },
                onValueChangeFinished = ::apply,
                valueRange = 0f..360f,
            )
            Text(
                "Hue ${hue.roundToInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sat,
                onValueChange = { sat = it },
                onValueChangeFinished = ::apply,
                valueRange = 0.15f..1f,
            )
            Text(
                "Saturation ${(sat * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = bright,
                onValueChange = { bright = it },
                onValueChangeFinished = ::apply,
                valueRange = 0.25f..1f,
            )
            Text(
                "Brightness ${(bright * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.take(9) },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        val raw = hex.removePrefix("#")
                        val parsed = runCatching {
                            if (raw.length == 6) raw.toLong(16) or 0xFF000000L else raw.toLong(16)
                        }.getOrNull()
                        if (parsed != null) {
                            onAccent(parsed)
                            val fresh = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toInt(), fresh)
                            hue = fresh[0]
                            sat = fresh[1]
                            bright = fresh[2]
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Apply") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val json = """{"accent":${current},"saturation":$saturation,"value":$value,"hex":"$hex"}"""
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("Peek theme", json),
                        )
                    },
                ) { Text("Copy theme") }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        Regex("""#([0-9a-fA-F]{6,8})""").find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                            val parsed = runCatching {
                                if (raw.length == 6) raw.toLong(16) or 0xFF000000L else raw.toLong(16)
                            }.getOrNull() ?: return@let
                            hex = "#$raw"
                            onAccent(parsed)
                            val fresh = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toInt(), fresh)
                            hue = fresh[0]
                            sat = fresh[1]
                            bright = fresh[2]
                        }
                        Regex("\"saturation\"\\s*:\\s*([0-9.]+)").find(text)
                            ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { onSaturation(it) }
                        Regex("\"value\"\\s*:\\s*([0-9.]+)").find(text)
                            ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { onValue(it) }
                    },
                ) { Text("Paste theme") }
            }
        }
    }
}

@Composable
private fun FontScaleSlider(value: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${(local * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0.85f..1.3f,
        )
    }
}

@Composable
private fun SyncDepthSlider(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Details per profile", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                if (local.roundToInt() == 0) "Fast" else "${local.roundToInt()} latest",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = 0f..6f,
            steps = 5,
        )
        Text(
            "0 keeps syncing light. Higher values fetch counts and video details for the newest posts.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntervalPicker(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(1, 3, 6, 12, 24).forEach { hours ->
            val selected = current == hours
            Surface(
                onClick = { onSelect(hours) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    "${hours}h",
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
