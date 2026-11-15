package com.avi.peek.data.repo

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultLockedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AES-GCM at-rest storage for downloads that belong to private profiles.
 *
 * Files are sealed into filesDir/private_vault/<code>/<name>.vault and the
 * plain files are removed. Opening the vault decrypts them back into the
 * normal download directory for playback.
 *
 * The data key lives in Android Keystore and is bound to user authentication:
 * on Android 11+ it can only be used after a biometric or device-credential
 * check, and it is invalidated when biometrics are re-enrolled. The app keeps
 * the unlock window short and re-locks private content on a timer.
 */
class VaultStore(
    context: Context,
    private val requireUserAuth: Boolean = true,
) {

    private val root = File(context.filesDir, "private_vault").apply { mkdirs() }

    fun hasSealed(code: String): Boolean = File(root, code).listFiles()?.isNotEmpty() == true

    fun sizeFor(code: String): Long =
        File(root, code).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun sizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    suspend fun seal(codes: List<String>, downloadRoot: File) = withContext(Dispatchers.IO) {
        codes.forEach { code ->
            val source = File(downloadRoot, code)
            val files = source.listFiles()?.filter { it.isFile }.orEmpty()
            if (files.isEmpty()) return@forEach
            val destination = File(root, code).apply { mkdirs() }
            files.forEach { file ->
                val target = File(destination, "${file.name}.vault")
                encrypt(file, target)
                file.delete()
            }
            if (source.listFiles()?.isEmpty() == true) source.delete()
        }
    }

    suspend fun open(codes: List<String>, downloadRoot: File) = withContext(Dispatchers.IO) {
        codes.forEach { code ->
            val source = File(root, code)
            val files = source.listFiles()?.filter { it.isFile && it.name.endsWith(".vault") }.orEmpty()
            if (files.isEmpty()) return@forEach
            val destination = File(downloadRoot, code).apply { mkdirs() }
            files.forEach { file ->
                val target = File(destination, file.name.removeSuffix(".vault"))
                decrypt(file, target)
                file.delete()
            }
            if (source.listFiles()?.isEmpty() == true) source.delete()
        }
    }

    fun purge() {
        root.deleteRecursively()
        root.mkdirs()
    }

    fun purge(code: String) {
        File(root, code).deleteRecursively()
    }

    private fun encrypt(source: File, target: File) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            target.parentFile?.mkdirs()
            target.outputStream().buffered().use { output ->
                output.write(cipher.iv)
                CipherOutputStream(output, cipher).use { encrypted ->
                    source.inputStream().buffered().use { input -> input.copyTo(encrypted) }
                }
            }
        } catch (e: UserNotAuthenticatedException) {
            target.delete()
            throw VaultLockedException("Unlock private content with your biometric before using the vault.", e)
        } catch (e: KeyPermanentlyInvalidatedException) {
            target.delete()
            throw VaultLockedException("Biometrics changed, so the old vault key was invalidated.", e)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun decrypt(source: File, target: File) {
        try {
            decryptWith(source, target, key())
        } catch (e: UserNotAuthenticatedException) {
            target.delete()
            throw VaultLockedException("Unlock private content with your biometric before opening the vault.", e)
        } catch (e: KeyPermanentlyInvalidatedException) {
            target.delete()
            throw VaultLockedException("Biometrics changed, so the old vault key was invalidated.", e)
        } catch (e: Exception) {
            target.delete()
            // Files sealed by older Peek builds used a key without user
            // authentication. Try that key once so upgrades don't lose access.
            runCatching { decryptWith(source, target, legacyKey()) }
                .onFailure {
                    target.delete()
                    throw e
                }
        }
    }

    private fun decryptWith(source: File, target: File, secretKey: SecretKey) {
        source.inputStream().buffered().use { input ->
            val iv = ByteArray(IV_BYTES)
            var read = 0
            while (read < IV_BYTES) {
                val step = input.read(iv, read, IV_BYTES - read)
                if (step <= 0) error("Incomplete vault file")
                read += step
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            target.parentFile?.mkdirs()
            CipherInputStream(input, cipher).use { decrypted ->
                target.outputStream().buffered().use { output -> decrypted.copyTo(output) }
            }
        }
    }

    private fun key(): SecretKey {
        val alias = if (requireUserAuth) KEY_ALIAS_AUTH else KEY_ALIAS_TEST
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (requireUserAuth) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
            }
            builder.setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The vault key is unusable while the device is locked, even if
                // the five-minute authentication window has not expired.
                builder.setUnlockedDeviceRequired(true)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun legacyKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS_LEGACY, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?.let { return it }
        throw IllegalStateException("No legacy vault key on this device")
    }

    private companion object {
        const val KEY_ALIAS_AUTH = "peek_private_vault_v2"
        const val KEY_ALIAS_LEGACY = "peek_private_vault"
        const val KEY_ALIAS_TEST = "peek_private_vault_test"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val AUTH_WINDOW_SECONDS = 300
    }
}
