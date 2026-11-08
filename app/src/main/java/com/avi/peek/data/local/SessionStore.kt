package com.avi.peek.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.peekSessionData: DataStore<Preferences> by preferencesDataStore(name = "peek_session")

@Serializable
data class SessionCookie(
    val name: String,
    val value: String,
)

@Serializable
data class StoredSession(
    val cookies: List<SessionCookie> = emptyList(),
    val username: String? = null,
    val userId: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)

data class AccountState(
    val signedIn: Boolean = false,
    val username: String? = null,
    val userId: String? = null,
    val savedAt: Long = 0L,
)

/**
 * Optional Instagram web session. Only the cookies Instagram hands to the
 * WebView are kept, encrypted with an Android Keystore key. Passwords never
 * touch this class.
 */
class SessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cookieSerializer = ListSerializer(SessionCookie.serializer())
    private val sessionKey = stringPreferencesKey("encrypted_session")

    private val prefs = context.peekSessionData.data
        .catch { emit(emptyPreferences()) }

    val state: Flow<AccountState> = prefs.map { preferences ->
        readSession(preferences[sessionKey])?.let { session ->
            AccountState(
                signedIn = true,
                username = session.username,
                userId = session.userId,
                savedAt = session.savedAt,
            )
        } ?: AccountState()
    }

    suspend fun snapshot(): AccountState = state.first()

    suspend fun load(): StoredSession? = readSession(
        context.peekSessionData.data
            .catch { emit(emptyPreferences()) }
            .first()[sessionKey],
    )

    suspend fun save(session: StoredSession) {
        val payload = json.encodeToString(
            SessionPayload.serializer(),
            SessionPayload(
                cookiesJson = json.encodeToString(cookieSerializer, session.cookies),
                username = session.username,
                userId = session.userId,
                savedAt = session.savedAt,
            ),
        )
        val encrypted = SessionCrypto.encrypt(payload)
        context.peekSessionData.edit { it[sessionKey] = encrypted }
    }

    suspend fun clear() {
        context.peekSessionData.edit { it.remove(sessionKey) }
    }

    private fun readSession(encrypted: String?): StoredSession? {
        if (encrypted.isNullOrBlank()) return null
        val payload = runCatching {
            val text = SessionCrypto.decrypt(encrypted)
            json.decodeFromString(SessionPayload.serializer(), text)
        }.getOrNull() ?: return null
        val cookies = runCatching {
            json.decodeFromString(cookieSerializer, payload.cookiesJson)
        }.getOrDefault(emptyList())
        return StoredSession(
            cookies = cookies,
            username = payload.username,
            userId = payload.userId,
            savedAt = payload.savedAt,
        )
    }

    @Serializable
    private data class SessionPayload(
        val cookiesJson: String = "[]",
        val username: String? = null,
        val userId: String? = null,
        val savedAt: Long = 0L,
    )
}

private object SessionCrypto {
    private const val KEY_ALIAS = "peek_instagram_session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Invalid session payload" }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, iv),
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
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
}
