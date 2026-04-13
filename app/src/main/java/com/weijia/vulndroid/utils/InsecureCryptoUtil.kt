package com.weijia.vulndroid.utils

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * InsecureCryptoUtil — VulnDroid
 * ================================
 * SECURITY FINDING: M10 Insufficient Cryptography
 *
 * OWASP Mobile Top 10 2024: M10
 * MASVS:  MSTG-CRYPTO-1, MSTG-CRYPTO-2, MSTG-CRYPTO-3, MSTG-CRYPTO-4
 * CWE:    CWE-327 (Use of Broken Algorithm), CWE-329 (Hardcoded IV),
 *         CWE-916 (Insufficient Password Hashing)
 * Risk:   HIGH
 *
 * This utility demonstrates six distinct cryptography vulnerabilities
 * commonly found in production Android applications:
 *
 * 1. MD5 for password hashing — cryptographically broken, rainbow tables exist
 * 2. AES/ECB mode — deterministic, reveals patterns in ciphertext
 * 3. Hardcoded encryption key — key is in source code / decompiled APK
 * 4. Hardcoded IV (Initialization Vector) — ECB has no IV, CBC with fixed IV is broken
 * 5. SHA-1 for data integrity — broken since 2017 (SHAttered attack)
 * 6. Weak key derivation — direct string-to-bytes, no PBKDF2/bcrypt/Argon2
 */
object InsecureCryptoUtil {

    private const val TAG = "VulnDroid_Crypto"

    // ── M10 VULNERABILITY 1: Hardcoded encryption key ──────────────────────
    // This key is embedded in the APK and visible in decompiled source.
    // Should be: generated at runtime via Android Keystore, never hardcoded.
    private const val HARDCODED_KEY = "vulndroid2024key"    // 16 bytes = AES-128
    private const val HARDCODED_IV  = "vulndroidiv123456"   // 16 bytes — never reuse IV

    /**
     * M10 VULNERABILITY: MD5 password hashing.
     *
     * MD5 is NOT a password hashing algorithm. It is a message digest function.
     * Problems:
     * - No salt → identical passwords produce identical hashes
     * - Rainbow tables for MD5 are freely available online (crackstation.net)
     * - GPU can compute ~10 BILLION MD5 hashes per second
     * - "password123" → 482c811da5d5b4bc6d497ffa98491e38 (cracked in milliseconds)
     *
     * REMEDIATION: Use bcrypt, scrypt, or Argon2 with a per-user random salt.
     * Android: Use BCrypt library or delegate hashing to the secure backend server.
     */
    fun hashPasswordMD5(password: String): String {
        Log.d(TAG, "Hashing password with MD5 (INSECURE): $password")  // [M6] logged too!
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.toHex()
    }

    /**
     * M10 VULNERABILITY: SHA-1 for data integrity checking.
     *
     * SHA-1 was broken in 2017 (Google's SHAttered attack demonstrated collision).
     * It is deprecated for all security uses.
     * REMEDIATION: Use SHA-256 or SHA-3 minimum.
     */
    fun checksumSHA1(data: String): String {
        val md = MessageDigest.getInstance("SHA-1")  // VULNERABILITY: SHA-1 is broken
        return md.digest(data.toByteArray()).toHex()
    }

    /**
     * M10 VULNERABILITY: AES/ECB mode encryption.
     *
     * ECB (Electronic Code Book) mode is the worst AES mode:
     * - Each 16-byte block is encrypted independently with the same key
     * - Identical plaintext blocks → identical ciphertext blocks
     * - Patterns in plaintext are visible in ciphertext (see "ECB penguin" demonstration)
     * - No semantic security — deterministic encryption leaks information
     * - Trivially vulnerable to block rearrangement attacks
     *
     * For a visual demonstration: encrypt a bitmap image in ECB mode vs CBC mode.
     * ECB preserves the image structure; CBC looks random.
     *
     * REMEDIATION: Use AES/GCM/NoPadding — provides both confidentiality AND integrity.
     */
    fun encryptECB(plaintext: String): String {
        val keySpec = SecretKeySpec(
            HARDCODED_KEY.toByteArray(Charsets.UTF_8),  // [M10] Hardcoded key
            "AES"
        )
        // M10 VULNERABILITY: ECB mode — DO NOT USE
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptECB(ciphertext: String): String {
        val keySpec = SecretKeySpec(
            HARDCODED_KEY.toByteArray(Charsets.UTF_8),
            "AES"
        )
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decrypted = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * M10 VULNERABILITY: AES/CBC with hardcoded static IV.
     *
     * CBC mode is better than ECB but this implementation has a critical flaw:
     * the IV (Initialization Vector) is hardcoded and reused for every encryption.
     *
     * The IV MUST be:
     * - Random (cryptographically secure random — SecureRandom)
     * - Unique per encryption operation
     * - Transmitted alongside the ciphertext (it is not secret, just must be random)
     *
     * With a fixed IV, CBC mode degrades toward ECB for the first block,
     * and enables chosen-plaintext attacks.
     *
     * REMEDIATION:
     *   val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
     *   val ivSpec = IvParameterSpec(iv)
     *   // Prepend iv to ciphertext for storage/transmission
     */
    fun encryptCBCFixedIV(plaintext: String): String {
        val keySpec = SecretKeySpec(HARDCODED_KEY.toByteArray(), "AES")
        // M10 VULNERABILITY: Hardcoded static IV — never do this
        val ivSpec = IvParameterSpec(HARDCODED_IV.toByteArray())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * M10 VULNERABILITY: Weak key derivation — direct string to bytes.
     *
     * Converting a password string directly to AES key bytes is not key derivation.
     * The key space is limited to printable ASCII characters.
     * No work factor — brute force is trivial.
     *
     * REMEDIATION: Use PBKDF2WithHmacSHA256 with 600,000+ iterations and a random salt.
     *   val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
     *   val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 256)
     *   val key = factory.generateSecret(spec).encoded
     */
    fun deriveKeyInsecure(password: String): ByteArray {
        // M10 VULNERABILITY: Password directly truncated/padded to 16 bytes — not key derivation
        return password.padEnd(16, '0').take(16).toByteArray(Charsets.UTF_8)
    }

    // Extension: convert ByteArray to hex string
    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}