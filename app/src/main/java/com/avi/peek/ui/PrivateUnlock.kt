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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.avi.peek.ui.components.findActivity

private const val ERROR_HW_UNAVAILABLE = 1
private const val ERROR_NONE_ENROLLED = 11
private const val ERROR_NO_HARDWARE = 12
private const val ERROR_UNSUPPORTED = 18

/**
 * Reusable biometric prompt for revealing private content or opening the
 * private vault. Returns a function that takes the success callback.
 */
@Composable
fun rememberPrivateUnlock(
    onError: (String) -> Unit = {},
): ((onSuccess: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cancellation by remember { mutableStateOf<CancellationSignal?>(null) }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pending?.invoke()
        } else {
            onError("Peek stayed hidden.")
        }
        pending = null
    }

    fun openDeviceCredential() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock private content",
            "Confirm it is really you",
        )
        if (intent == null) {
            onError("Add a screen lock or biometric to this device.")
            pending = null
        } else {
            runCatching { credentialLauncher.launch(intent) }
                .onFailure {
                    onError("This device can't open the lock screen.")
                    pending = null
                }
        }
    }

    fun openBiometric() {
        val host = activity
        if (host == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            openDeviceCredential()
            return
        }
        try {
            val builder = BiometricPrompt.Builder(host)
                .setTitle("Unlock private content")
                .setSubtitle("Biometric or screen lock")
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
                        pending?.invoke()
                        pending = null
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val canFallBack = errorCode == ERROR_NONE_ENROLLED ||
                            errorCode == ERROR_NO_HARDWARE ||
                            errorCode == ERROR_HW_UNAVAILABLE ||
                            errorCode == ERROR_UNSUPPORTED
                        if (canFallBack) {
                            openDeviceCredential()
                        } else {
                            onError(errString.toString())
                            pending = null
                        }
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

    return { onSuccess ->
        pending = onSuccess
        openBiometric()
    }
}
