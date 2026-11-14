package com.avi.peek.data.repo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

/**
 * AES-GCM at-rest storage for downloads that belong to private profiles.
 *
 * Files are sealed into filesDir/private_vault/<code>/<name>.vault and the
 * plain files are removed. Opening the vault decrypts them back into the
 * normal download directory for playback. The key never leaves Android
 * Keystore.
 */
class VaultStore(context: Context) {

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
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun decrypt(source: File, target: File) {
        try {
            source.inputStream().buffered().use { input ->
                val iv = ByteArray(IV_BYTES)
                var read = 0
                while (read < IV_BYTES) {
                    val step = input.read(iv, read, IV_BYTES - read)
                    if (step <= 0) error("Incomplete vault file")
                    read += step
                }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                target.parentFile?.mkdirs()
                CipherInputStream(input, cipher).use { decrypted ->
                    target.outputStream().buffered().use { output -> decrypted.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "peek_private_vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
