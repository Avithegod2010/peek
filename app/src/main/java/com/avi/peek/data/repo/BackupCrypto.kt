package com.avi.peek.data.repo

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted Peek backups.
 *
 * File layout: "PEEK1" (5 bytes) + salt (16) + iv (12) + AES-GCM ciphertext.
 * AES-256 with a 128-bit tag and PBKDF2-HMAC-SHA256 key derivation.
 * The passphrase never leaves the device and is not stored.
 */
object BackupCrypto {
    private val MAGIC = "PEEK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun isEncrypted(data: ByteArray): Boolean =
        data.size > MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = derive(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val ciphertext = cipher.doFinal(plain)
            MAGIC + salt + iv + ciphertext
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        require(isEncrypted(data)) { "That file is not an encrypted Peek backup" }
        require(passphrase.isNotEmpty()) { "Enter the backup passphrase" }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val payloadStart = ivStart + IV_BYTES
        val salt = data.copyOfRange(saltStart, ivStart)
        val iv = data.copyOfRange(ivStart, payloadStart)
        val payload = data.copyOfRange(payloadStart, data.size)
        val key = derive(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            cipher.doFinal(payload)
        } finally {
            key.fill(0)
        }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
