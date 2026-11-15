package com.avi.peek.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level encryption for metadata written to Room.
 *
 * Sensitive strings (captions, media URLs, location, audio, profile text and
 * local file paths) are encrypted with an AES-256-GCM key that is generated in
 * and never leaves Android Keystore. Ciphertexts carry an `enc:v1:` prefix so
 * plaintext rows written by older versions can still be read and are sealed on
 * their next write. This key is intentionally not biometric-bound: it protects
 * data at rest, while private content visibility and the vault use their own
 * unlock flow.
 */
object FieldCipher {
    private const val ALIAS = "peek_metadata_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty() || value.startsWith(PREFIX)) return value
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Never silently write plaintext metadata. If the Keystore is
            // unavailable the write must fail and the caller must surface it.
            throw IllegalStateException("Peek could not encrypt local metadata", e)
        }
    }

    fun decrypt(value: String?): String? {
        if (value.isNullOrEmpty() || !value.startsWith(PREFIX)) return value
        return try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            if (payload.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES),
            )
            String(
                cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES),
                Charsets.UTF_8,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
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
