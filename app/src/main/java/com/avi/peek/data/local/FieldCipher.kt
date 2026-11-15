package com.avi.peek.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level encryption for metadata written to Room.
 *
 * Sensitive strings (captions, media URLs, location, audio, profile text and
 * local file paths) are encrypted with an AES-256-GCM key that is generated in
 * and never leaves Android Keystore. Ciphertexts carry an `enc:vN:` prefix so
 * plaintext rows written by older versions can still be read and are sealed on
 * their next write. The prefix also allows key rotation without a destructive
 * migration. This key is intentionally not biometric-bound: it protects data
 * at rest, while private content visibility and the vault use their own unlock
 * flow.
 */
object FieldCipher {
    private const val BASE_ALIAS = "peek_metadata_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_VERSION = 9
    private val sealedPrefix = Regex("^enc:v([1-9][0-9]*):")

    @Volatile
    private var preferredVersion: Int? = null

    // Keystore calls are IPC-heavy. Keep recently used keys and decrypted
    // field values in memory so scrolling a cached feed does not re-decrypt
    // the same captions and URLs for every flow emission.
    private val keys = ConcurrentHashMap<Int, SecretKey>()
    private val plaintext = object : LruCache<String, String>(2_048) {}

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty() || sealedPrefix.containsMatchIn(value)) return value
        val version = writeVersion()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(version))
            val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            "enc:v$version:" + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Never silently write plaintext metadata. If the Keystore is
            // unavailable the write must fail and the caller must surface it.
            throw IllegalStateException("Peek could not encrypt local metadata", e)
        }
    }

    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val match = sealedPrefix.find(value) ?: return value
        val version = match.groupValues[1].toIntOrNull() ?: return null
        if (version !in 1..MAX_VERSION) return null
        plaintext.get(value)?.let { return it }
        return try {
            val payload = Base64.decode(value.substring(match.value.length), Base64.NO_WRAP)
            if (payload.size <= IV_BYTES) return null
            val existing = existingKey(version) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existing,
                GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES),
            )
            String(
                cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES),
                Charsets.UTF_8,
            ).also { plaintext.put(value, it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Clears decrypted and key caches, e.g. when private content locks. */
    fun clearCache() {
        plaintext.evictAll()
        keys.clear()
    }

    /**
     * Creates the next metadata key version and makes it the write key.
     * Existing ciphertexts remain readable through their version prefix.
     */
    fun rotateToNextVersion(): Int {
        val current = writeVersion()
        if (current >= MAX_VERSION) return current
        val next = current + 1
        key(next)
        preferredVersion = next
        plaintext.evictAll()
        return next
    }

    fun currentVersion(): Int = writeVersion()

    private fun writeVersion(): Int {
        preferredVersion?.let { return it }
        val keyStore = keystore()
        val detected = (MAX_VERSION downTo 1).firstOrNull { keyStore.containsAlias(alias(it)) } ?: 1
        preferredVersion = detected
        return detected
    }

    private fun existingKey(version: Int): SecretKey? {
        keys[version]?.let { return it }
        val keyStore = keystore()
        if (!keyStore.containsAlias(alias(version))) return null
        return (keyStore.getEntry(alias(version), null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.also { keys[version] = it }
    }

    private fun key(version: Int): SecretKey {
        existingKey(version)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias(version),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey().also { keys[version] = it }
    }

    private fun alias(version: Int): String =
        if (version == 1) BASE_ALIAS else "${BASE_ALIAS}_v$version"

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

fun MediaEntity.sealMetadata(): MediaEntity = copy(
    userAvatar = FieldCipher.encrypt(userAvatar),
    userFullName = FieldCipher.encrypt(userFullName) ?: userFullName,
    caption = FieldCipher.encrypt(caption),
    displayUrl = FieldCipher.encrypt(displayUrl),
    videoUrl = FieldCipher.encrypt(videoUrl),
    childrenJson = FieldCipher.encrypt(childrenJson),
    accessibilityCaption = FieldCipher.encrypt(accessibilityCaption),
    locationName = FieldCipher.encrypt(locationName),
    audioTitle = FieldCipher.encrypt(audioTitle),
    audioArtist = FieldCipher.encrypt(audioArtist),
    localImagePathsJson = FieldCipher.encrypt(localImagePathsJson),
    localVideoPath = FieldCipher.encrypt(localVideoPath),
)

fun MediaEntity.unsealMetadata(): MediaEntity = copy(
    userAvatar = FieldCipher.decrypt(userAvatar),
    userFullName = FieldCipher.decrypt(userFullName) ?: userFullName,
    caption = FieldCipher.decrypt(caption),
    displayUrl = FieldCipher.decrypt(displayUrl),
    videoUrl = FieldCipher.decrypt(videoUrl),
    childrenJson = FieldCipher.decrypt(childrenJson),
    accessibilityCaption = FieldCipher.decrypt(accessibilityCaption),
    locationName = FieldCipher.decrypt(locationName),
    audioTitle = FieldCipher.decrypt(audioTitle),
    audioArtist = FieldCipher.decrypt(audioArtist),
    localImagePathsJson = FieldCipher.decrypt(localImagePathsJson),
    localVideoPath = FieldCipher.decrypt(localVideoPath),
)

fun ProfileEntity.sealMetadata(): ProfileEntity = copy(
    fullName = FieldCipher.encrypt(fullName) ?: fullName,
    biography = FieldCipher.encrypt(biography) ?: biography,
    profilePicUrl = FieldCipher.encrypt(profilePicUrl),
    bioLinksJson = FieldCipher.encrypt(bioLinksJson) ?: bioLinksJson,
    highlightsJson = FieldCipher.encrypt(highlightsJson) ?: highlightsJson,
)

fun ProfileEntity.unsealMetadata(): ProfileEntity = copy(
    fullName = FieldCipher.decrypt(fullName) ?: fullName,
    biography = FieldCipher.decrypt(biography) ?: biography,
    profilePicUrl = FieldCipher.decrypt(profilePicUrl),
    bioLinksJson = FieldCipher.decrypt(bioLinksJson) ?: bioLinksJson,
    highlightsJson = FieldCipher.decrypt(highlightsJson) ?: highlightsJson,
)

fun CommentEntity.sealMetadata(): CommentEntity = copy(
    username = FieldCipher.encrypt(username) ?: username,
    avatarUrl = FieldCipher.encrypt(avatarUrl),
    text = FieldCipher.encrypt(text) ?: text,
)

fun CommentEntity.unsealMetadata(): CommentEntity = copy(
    username = FieldCipher.decrypt(username) ?: username,
    avatarUrl = FieldCipher.decrypt(avatarUrl),
    text = FieldCipher.decrypt(text) ?: text,
)

fun SearchHistoryEntity.sealMetadata(): SearchHistoryEntity = copy(
    query = FieldCipher.encrypt(query) ?: query,
)

fun SearchHistoryEntity.unsealMetadata(): SearchHistoryEntity = copy(
    query = FieldCipher.decrypt(query) ?: query,
)
