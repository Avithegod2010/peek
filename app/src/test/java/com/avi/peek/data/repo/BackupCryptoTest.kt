package com.avi.peek.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failure paths of [BackupCrypto]: every malformed or unauthenticated payload
 * must be rejected, and the rejection must not echo the plaintext or the
 * passphrase back into the message (that text is shown in the UI and lands in
 * logcat/crash reports).
 */
class BackupCryptoTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val plaintext = "PEEK-BACKUP-MARKER: private saved posts".toByteArray()
    private val marker = "PEEK-BACKUP-MARKER"

    private fun rejectsWithoutLeaking(block: () -> Unit) {
        val failure = assertThrows(Throwable::class.java) { block() }
        assertNotNull(failure)
        val message = buildString {
            append(failure.message.orEmpty())
            append(' ')
            append(failure.cause?.message.orEmpty())
        }
        assertFalse("exception message leaked the plaintext: $message", message.contains(marker))
        assertFalse(
            "exception message leaked the passphrase: $message",
            message.contains("correct horse"),
        )
    }

    @Test
    fun wrongPassphraseFailsClosedWithoutLeakingAnything() {
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        rejectsWithoutLeaking { BackupCrypto.decrypt(encrypted, "not the passphrase".toCharArray()) }
    }

    @Test
    fun truncatedPayloadIsRejectedBeforeAnyCipherWork() {
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        // Anything shorter than the 5+16+12 byte header cannot even be sliced.
        rejectsWithoutLeaking { BackupCrypto.decrypt(encrypted.copyOfRange(0, 20), passphrase) }
        // Header only, no ciphertext/tag at all.
        rejectsWithoutLeaking { BackupCrypto.decrypt(encrypted.copyOfRange(0, 33), passphrase) }
        // Empty buffer is not an encrypted backup.
        rejectsWithoutLeaking { BackupCrypto.decrypt(ByteArray(0), passphrase) }
    }

    @Test
    fun bitFlipInsideTheCiphertextBodyFailsAuthentication() {
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        val bodyStart = 5 + 16 + 12
        for (offset in listOf(bodyStart, bodyStart + 4, encrypted.lastIndex)) {
            val tampered = encrypted.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 0x10).toByte()
            rejectsWithoutLeaking { BackupCrypto.decrypt(tampered, passphrase) }
        }
        // The authentic copy still opens, so the failures above came from the
        // flipped bit and not from a broken fixture.
        assertTrue(plaintext.contentEquals(BackupCrypto.decrypt(encrypted, passphrase)))
    }

    @Test
    fun corruptSaltOrIvFailsClosed() {
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        // Salt lives at 5..21: a different salt derives a different key.
        val saltTampered = encrypted.copyOf()
        saltTampered[7] = (saltTampered[7].toInt() xor 0x01).toByte()
        rejectsWithoutLeaking { BackupCrypto.decrypt(saltTampered, passphrase) }
        // IV lives at 21..33: a different IV breaks the tag as well.
        val ivTampered = encrypted.copyOf()
        ivTampered[24] = (ivTampered[24].toInt() xor 0x01).toByte()
        rejectsWithoutLeaking { BackupCrypto.decrypt(ivTampered, passphrase) }
    }

    @Test
    fun emptyInputsAreRejected() {
        rejectsWithoutLeaking { BackupCrypto.encrypt(plaintext, CharArray(0)) }
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase)
        rejectsWithoutLeaking { BackupCrypto.decrypt(encrypted, CharArray(0)) }
        rejectsWithoutLeaking { BackupCrypto.decrypt("not an encrypted backup".toByteArray(), passphrase) }
    }

    @Test
    fun isEncryptedRequiresTheFullMagicPrefix() {
        assertFalse(BackupCrypto.isEncrypted("PEEK".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("PEEK1".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("XEEK1payload".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
        assertTrue(BackupCrypto.isEncrypted(BackupCrypto.encrypt(ByteArray(0), passphrase)))
    }

    @Test
    fun identicalPlaintextNeverProducesIdenticalFiles() {
        val first = BackupCrypto.encrypt(plaintext, passphrase)
        val second = BackupCrypto.encrypt(plaintext, passphrase)
        assertFalse("salt/IV reuse: the two files share a header", first.copyOfRange(5, 33).contentEquals(second.copyOfRange(5, 33)))
        assertTrue(plaintext.contentEquals(BackupCrypto.decrypt(first, passphrase)))
        assertTrue(plaintext.contentEquals(BackupCrypto.decrypt(second, passphrase)))
    }
}
