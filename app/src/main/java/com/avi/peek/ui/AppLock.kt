package com.avi.peek.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.ui.components.findActivity
import com.avi.peek.util.AppLockState
import com.avi.peek.ui.theme.PeekTheme

// Stable framework error codes; the constants are hidden from the public SDK stubs.
private const val ERROR_HW_UNAVAILABLE = 1
private const val ERROR_NONE_ENROLLED = 11
private const val ERROR_NO_HARDWARE = 12
private const val ERROR_UNSUPPORTED = 18

@Composable
fun AppLockGate(settings: PeekSettings, content: @Composable () -> Unit) {
    // The unlock is owned by AppLockState so the process lifecycle can revoke it
    // (PeekProcessObserver.onStop) and this gate re-renders locked by itself.
    val unlockedByUser by AppLockState.unlocked.collectAsState()
    val unlocked = !settings.appLock || unlockedByUser
    var error by remember { mutableStateOf<String?>(null) }
    var triedBiometric by remember { mutableStateOf(false) }
    var cancellation by remember { mutableStateOf<CancellationSignal?>(null) }
    val context = LocalContext.current
    val activity = context.findActivity()

    fun unlock() {
        AppLockState.unlock()
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            error = null
            unlock()
        } else {
            error = "Peek stayed locked."
        }
    }

    fun openDeviceCredential() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock Peek",
            "Your follows, saves and downloads are private",
        )
        if (intent == null) {
            error = "Add a screen lock or biometric to this device, then reopen Peek."
        } else {
            runCatching { credentialLauncher.launch(intent) }
                .onFailure { error = "This device can't open the lock screen." }
        }
    }

    fun openBiometric() {
        val host = activity
        if (host == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            openDeviceCredential()
            return
        }
        triedBiometric = true
        try {
            val builder = BiometricPrompt.Builder(host)
                .setTitle("Unlock Peek")
                .setSubtitle("Your follows, saves and downloads are private")
                .setNegativeButton("Use screen lock", host.mainExecutor) { _, _ ->
                    openDeviceCredential()
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            val signal = CancellationSignal()
            cancellation = signal
            builder.build().authenticate(
                signal,
                host.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        error = null
                        unlock()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = errString.toString()
                        val canFallBack = errorCode == ERROR_NONE_ENROLLED ||
                            errorCode == ERROR_NO_HARDWARE ||
                            errorCode == ERROR_HW_UNAVAILABLE ||
                            errorCode == ERROR_UNSUPPORTED
                        if (canFallBack) openDeviceCredential()
                    }
                },
            )
        } catch (e: Exception) {
            openDeviceCredential()
        }
    }

    DisposableEffect(Unit) {
        onDispose { cancellation?.cancel() }
    }

    LaunchedEffect(settings.appLock) {
        // Prompt when the lock is switched on, and never re-latch "unlocked" here:
        // a disabled lock is already open via the derived `unlocked` above.
        if (settings.appLock && !unlocked && !triedBiometric) {
            openBiometric()
        }
    }

    if (!settings.appLock || unlocked) {
        content()
        return
    }

    PeekTheme(settings) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "Peek is locked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Unlock with your device biometric or screen lock to see your circle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { openBiometric() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text("Unlock Peek")
                }
                TextButton(onClick = { openDeviceCredential() }) {
                    Text("Use screen lock")
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "App lock is a local convenience. It does not encrypt the database.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
