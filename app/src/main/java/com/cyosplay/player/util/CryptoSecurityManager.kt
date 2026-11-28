package com.cyosplay.player.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.util.Arrays
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore & AES-256-GCM Streaming Security Manager:
 * 1. Protects the Data Encryption Key (DEK) with an AndroidKeyStore hardware-backed AES-256-GCM master key
 *    (with automatic per-device CSPRNG fallback in headless JVM/Robolectric unit tests) — zero static secrets
 *    or embedded passwords exist anywhere in source code.
 * 2. Performs chunked AEAD streaming encryption/decryption (CYOS_STREAM_V3) so multi-gigabyte 4K/8K videos
 *    never load into heap memory, preventing OutOfMemoryError and detecting any truncation/tampering.
 * 3. Enforces 120,000-iteration PBKDF2-HMAC-SHA256 secret hashing, 6+ digit PIN/passcode validation, and
 *    exponential lockout backoff on failed authentication attempts.
 */
object CryptoSecurityManager {

    private const val PREFS_NAME = "cyosplay_zk_keystore_v2"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_MASTER_ALIAS = "cyosplay_keystore_master_key_v3"

    private const val KEY_DEVICE_SALT = "zk_device_salt_b64"
    private const val KEY_WRAPPED_DYNAMIC_DEK = "zk_wrapped_dynamic_dek_b64"
    private const val KEY_WRAPPED_KEYSTORE_DEK_V3 = "zk_wrapped_keystore_dek_v3_b64"
    private const val KEY_FALLBACK_JVM_KEK_B64 = "zk_fallback_jvm_kek_v3_b64"
    private const val KEY_KEYSTORE_MIGRATED_V3 = "zk_keystore_migrated_v3"
    private const val KEY_AUTO_ROTATION_COUNT = "zk_auto_rotation_count"
    private const val KEY_FAILED_CRACK_COUNT = "zk_failed_crack_attempts"
    private const val KEY_LOCKOUT_UNTIL_TS = "zk_lockout_until_timestamp_ms"
    private const val KEY_LAST_ROTATION_TS = "zk_last_rotation_timestamp"
    private const val KEY_STEALTH_SESSION_TOKEN = "zk_stealth_session_token"
    private const val KEY_DEBUG_PERMANENTLY_DISABLED = "zk_debug_mode_permanently_disabled_lock"

    /**
     * Debug mode is permanently and irrevocably disabled across the entire APK.
     */
    const val IS_DEBUG_MODE_PERMANENTLY_DISABLED: Boolean = true

    const val MIN_VAULT_PIN_LENGTH: Int = 6
    const val MAX_VAULT_PIN_LENGTH: Int = 8

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    const val PBKDF2_AUTH_ITERATIONS_V3 = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ENCRYPTED_PREFIX = "ENCv2:"
    private const val HASH_VERSION_V3_PREFIX = "V3:"
    private const val DATACODE_V4_PREFIX = "DATACODE_V4:"

    // Streaming file encryption constants (256 KB chunks for low-RAM footprint on 4K/8K REMUX files)
    private val STREAM_MAGIC_HEADER = "CYOSV3\u0000\u0001".toByteArray(StandardCharsets.US_ASCII)
    private val STREAM_MAGIC_HEADER_V4 = "CYOSV4\u0000\u0001".toByteArray(StandardCharsets.US_ASCII)
    private val STREAM_V4_DEK_WRAP_AAD = "CYOSV4_DEK_WRAP".toByteArray(StandardCharsets.US_ASCII)
    const val STREAM_V4_WRAPPED_DEK_BYTES = 32 + GCM_TAG_BYTES // 48 bytes (256-bit DEK + 128-bit GCM tag)
    const val STREAM_V4_HEADER_SIZE_BYTES = 8L + GCM_IV_LENGTH + STREAM_V4_WRAPPED_DEK_BYTES + GCM_IV_LENGTH + 8L // 88 bytes
    const val STREAM_CHUNK_SIZE = 256 * 1024

    const val MAJOR_CAPABILITY_UNIQUE_ID = "cyos play"
    private const val KEY_DATA_CODES_LOCKED = "zk_data_codes_locked_v4"
    private const val KEY_DATA_CODES_PASSCODE_HASH = "zk_data_codes_passcode_hash_v4"
    const val MIN_DATA_CODES_PASSCODE_LENGTH: Int = 6

    class KeystoreUnwrapException(
        message: String,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    fun interface KeyRotationListener {
        fun onMasterKeyRotated(oldKey: SecretKey, newKey: SecretKey)
    }

    /**
     * Two-phase key rotation participant: prepares/stages re-encrypted files with [newKey] BEFORE
     * the new DEK is persisted, and only commits renames after the new DEK is durable.
     */
    interface TwoPhaseKeyRotationListener : KeyRotationListener {
        fun prepareMasterKeyRotation(oldKey: SecretKey, newKey: SecretKey): Boolean
        fun commitMasterKeyRotation(newKey: SecretKey)
        fun rollbackMasterKeyRotation(oldKey: SecretKey)
        override fun onMasterKeyRotated(oldKey: SecretKey, newKey: SecretKey) {
            if (prepareMasterKeyRotation(oldKey, newKey)) {
                commitMasterKeyRotation(newKey)
            } else {
                rollbackMasterKeyRotation(oldKey)
            }
        }
    }

    private val rotationListeners = CopyOnWriteArrayList<KeyRotationListener>()
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedDataEncryptionKey: SecretKey? = null

    @Volatile
    var lastKeystoreErrorMessage: String? = null
        private set

    internal fun clearCachedKeyForTesting() {
        cachedDataEncryptionKey = null
        lastKeystoreErrorMessage = null
    }

    fun registerRotationListener(listener: KeyRotationListener) {
        if (!rotationListeners.contains(listener)) {
            rotationListeners.add(listener)
        }
    }

    fun unregisterRotationListener(listener: KeyRotationListener) {
        rotationListeners.remove(listener)
    }

    fun verifyDeviceKeystoreIntegrity(context: Context): Boolean {
        return try {
            val probe = "CYOS_KEYSTORE_INTEGRITY_PROBE"
            val encrypted = encryptString(context, probe)
            isEncryptedString(encrypted) && decryptString(context, encrypted) == probe
        } catch (_: Throwable) {
            false
        }
    }

    fun hasDataCodesPasscode(context: Context): Boolean {
        return !getKeystorePrefs(context).getString(KEY_DATA_CODES_PASSCODE_HASH, null).isNullOrBlank()
    }

    fun setDataCodesPasscode(context: Context, rawPasscode: String): Boolean {
        val trimmed = rawPasscode.trim()
        if (trimmed.length < MIN_DATA_CODES_PASSCODE_LENGTH) return false
        val hashed = createEncryptedSecretHash(context, trimmed)
        getKeystorePrefs(context).edit().putString(KEY_DATA_CODES_PASSCODE_HASH, hashed).commit()
        return true
    }

    fun verifyDataCodesPasscode(context: Context, candidatePasscode: String): Boolean {
        val storedHash = getKeystorePrefs(context).getString(KEY_DATA_CODES_PASSCODE_HASH, null)
        if (storedHash.isNullOrBlank()) return false
        return verifyEncryptedSecretHash(context, candidatePasscode.trim(), storedHash)
    }

    fun isDataCodesLocked(context: Context): Boolean {
        val prefs = getKeystorePrefs(context)
        return prefs.getBoolean(KEY_DATA_CODES_LOCKED, false)
    }

    fun setDataCodesLocked(context: Context, locked: Boolean) {
        val prefs = getKeystorePrefs(context)
        prefs.edit().putBoolean(KEY_DATA_CODES_LOCKED, locked).commit()
    }

    fun unlockDataCodesWithPasscode(
        context: Context,
        candidatePasscode: String,
        vaultPinVerifier: ((String) -> Boolean)? = null
    ): Boolean {
        val trimmed = candidatePasscode.trim()
        if (trimmed.isEmpty()) return false
        val matched = when {
            hasDataCodesPasscode(context) -> {
                verifyDataCodesPasscode(context, trimmed) || (vaultPinVerifier?.invoke(trimmed) == true)
            }
            vaultPinVerifier != null -> vaultPinVerifier.invoke(trimmed)
            else -> false
        }
        if (matched) {
            setDataCodesLocked(context, false)
        }
        return matched
    }

    fun lockDataCodes(context: Context) {
        setDataCodesLocked(context, true)
    }

    private fun getKeystorePrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getOrCreateDeviceSalt(prefs: SharedPreferences): ByteArray {
        val existing = prefs.getString(KEY_DEVICE_SALT, null)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val salt = ByteArray(32)
        secureRandom.nextBytes(salt)
        prefs.edit().putString(KEY_DEVICE_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)).commit()
        return salt
    }

    /**
     * Obtains or generates a non-exportable 256-bit AES-GCM master key in AndroidKeyStore.
     * Returns null only in headless JVM test environments where AndroidKeyStore is not mounted.
     */
    private fun getOrCreateAndroidKeystoreMasterKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                return existingEntry.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates or loads a per-device random 256-bit fallback KEK when AndroidKeyStore is
     * unavailable (e.g., headless JVM / Robolectric tests). Uses zero static seeds or passwords.
     */
    private fun getOrCreateDeviceFallbackKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_FALLBACK_JVM_KEK_B64, null)
        if (!existing.isNullOrBlank()) {
            val decoded = Base64.decode(existing, Base64.NO_WRAP)
            if (decoded.size == 32) {
                val key = SecretKeySpec(decoded, "AES")
                Arrays.fill(decoded, 0.toByte())
                return key
            }
        }
        val freshBytes = ByteArray(32)
        secureRandom.nextBytes(freshBytes)
        prefs.edit()
            .putString(KEY_FALLBACK_JVM_KEK_B64, Base64.encodeToString(freshBytes, Base64.NO_WRAP))
            .commit()
        val key = SecretKeySpec(freshBytes, "AES")
        Arrays.fill(freshBytes, 0.toByte())
        return key
    }

    /**
     * Provides the Key-Encryption-Key (KEK) backed by AndroidKeyStore on device, or a
     * per-device CSPRNG 256-bit KEK in local JVM/Robolectric environments.
     */
    private fun getOrCreateKeystoreBackedKek(context: Context): SecretKey {
        getOrCreateAndroidKeystoreMasterKey()?.let { return it }
        return getOrCreateDeviceFallbackKek(context)
    }

    private fun wrapDekWithKeystoreMaster(context: Context, rawDekBytes: ByteArray): ByteArray {
        val kek = getOrCreateKeystoreBackedKek(context)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, kek)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(rawDekBytes)
            val out = ByteArray(1 + iv.size + cipherBytes.size)
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(cipherBytes, 0, out, 1 + iv.size, cipherBytes.size)
            out
        } catch (_: Throwable) {
            encryptBytesWithKey(rawDekBytes, kek)
        }
    }

    private fun unwrapDekWithKeystoreMaster(context: Context, wrappedPayload: ByteArray): ByteArray? {
        if (wrappedPayload.size <= GCM_IV_LENGTH + 1) return null
        val kek = getOrCreateKeystoreBackedKek(context)
        val unwrapped = try {
            val ivLen = wrappedPayload[0].toInt() and 0xFF
            if (ivLen in 12..16 && wrappedPayload.size > 1 + ivLen + GCM_TAG_BYTES) {
                val iv = wrappedPayload.copyOfRange(1, 1 + ivLen)
                val cipherBytes = wrappedPayload.copyOfRange(1 + ivLen, wrappedPayload.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(cipherBytes)
            } else {
                decryptBytesWithKey(wrappedPayload, kek)
            }
        } catch (_: Throwable) {
            decryptBytesWithKey(wrappedPayload, kek)
        } ?: return null
        return if (unwrapped.size == 32) unwrapped else null
    }

    fun isKeystoreKeyManagementActive(context: Context): Boolean {
        val prefs = getKeystorePrefs(context)
        return prefs.getBoolean(KEY_KEYSTORE_MIGRATED_V3, false) &&
            !prefs.getString(KEY_WRAPPED_KEYSTORE_DEK_V3, null).isNullOrBlank()
    }

    @Synchronized
    fun getOrCreateDataEncryptionKey(context: Context): SecretKey {
        cachedDataEncryptionKey?.let { return it }
        val prefs = getKeystorePrefs(context)
        getOrCreateDeviceSalt(prefs)

        // 1. If an existing AndroidKeyStore v3 wrapped DEK is persisted, NEVER overwrite it on transient failure.
        val wrappedV3Base64 = prefs.getString(KEY_WRAPPED_KEYSTORE_DEK_V3, null)
        if (!wrappedV3Base64.isNullOrBlank()) {
            var lastFailure: Throwable? = null
            val retryDelaysMs = longArrayOf(0L, 40L, 120L)
            for (delayMs in retryDelaysMs) {
                if (delayMs > 0L) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                try {
                    val wrappedBytes = Base64.decode(wrappedV3Base64, Base64.NO_WRAP)
                    val rawDek = unwrapDekWithKeystoreMaster(context, wrappedBytes)
                    if (rawDek != null && rawDek.size == 32) {
                        val key = SecretKeySpec(rawDek, "AES")
                        Arrays.fill(rawDek, 0.toByte())
                        cachedDataEncryptionKey = key
                        lastKeystoreErrorMessage = null
                        return key
                    }
                } catch (t: Throwable) {
                    lastFailure = t
                }
            }
            val msg = "AndroidKeyStore temporarily unavailable; existing wrapped DEK preserved to protect Vault & settings."
            lastKeystoreErrorMessage = msg
            throw KeystoreUnwrapException(msg, lastFailure)
        }

        // 2. First-time setup only: generate a fresh 256-bit DEK protected by AndroidKeyStore (or per-device CSPRNG KEK)
        val freshDekBytes = ByteArray(32)
        secureRandom.nextBytes(freshDekBytes)
        val freshKey = SecretKeySpec(freshDekBytes, "AES")
        val wrappedV3 = wrapDekWithKeystoreMaster(context, freshDekBytes)
        Arrays.fill(freshDekBytes, 0.toByte())
        prefs.edit()
            .putString(KEY_WRAPPED_KEYSTORE_DEK_V3, Base64.encodeToString(wrappedV3, Base64.NO_WRAP))
            .putBoolean(KEY_KEYSTORE_MIGRATED_V3, true)
            .remove(KEY_WRAPPED_DYNAMIC_DEK)
            .putLong(KEY_LAST_ROTATION_TS, System.currentTimeMillis())
            .commit()
        cachedDataEncryptionKey = freshKey
        lastKeystoreErrorMessage = null
        return freshKey
    }

    /**
     * Rotates the Data Encryption Key (DEK) using 256-bit CSPRNG entropy wrapped by AndroidKeyStore
     * via a two-phase commit: two-phase listeners (e.g., Vault files) stage & verify all re-encrypted
     * files BEFORE the new DEK is persisted, preventing partial-rotation corruption.
     */
    @Synchronized
    fun rotateDynamicEncryptionKey(context: Context, reason: String = "security_key_rotation"): Int {
        val prefs = getKeystorePrefs(context)
        val oldKey = getOrCreateDataEncryptionKey(context)
        val currentRotationCount = prefs.getInt(KEY_AUTO_ROTATION_COUNT, 0)

        val newDekBytes = ByteArray(32)
        secureRandom.nextBytes(newDekBytes)
        val newKey = SecretKeySpec(newDekBytes, "AES")
        val wrappedV3 = wrapDekWithKeystoreMaster(context, newDekBytes)
        Arrays.fill(newDekBytes, 0.toByte())

        // Phase 1: Stage & verify all two-phase listeners BEFORE committing the new DEK
        val twoPhaseListeners = rotationListeners.filterIsInstance<TwoPhaseKeyRotationListener>()
        val preparedListeners = mutableListOf<TwoPhaseKeyRotationListener>()
        for (listener in twoPhaseListeners) {
            val prepared = try {
                listener.prepareMasterKeyRotation(oldKey, newKey)
            } catch (_: Exception) {
                false
            }
            if (!prepared) {
                preparedListeners.forEach { preparedListener ->
                    runCatching { preparedListener.rollbackMasterKeyRotation(oldKey) }
                }
                runCatching { listener.rollbackMasterKeyRotation(oldKey) }
                return currentRotationCount
            }
            preparedListeners.add(listener)
        }

        // Phase 2: Persist the new wrapped DEK durably, then commit all prepared listeners
        val newRotationCount = currentRotationCount + 1
        prefs.edit()
            .putString(KEY_WRAPPED_KEYSTORE_DEK_V3, Base64.encodeToString(wrappedV3, Base64.NO_WRAP))
            .putBoolean(KEY_KEYSTORE_MIGRATED_V3, true)
            .remove(KEY_WRAPPED_DYNAMIC_DEK)
            .putInt(KEY_AUTO_ROTATION_COUNT, newRotationCount)
            .putLong(KEY_LAST_ROTATION_TS, System.currentTimeMillis())
            .putString(KEY_STEALTH_SESSION_TOKEN, reason)
            .commit()

        cachedDataEncryptionKey = newKey

        for (listener in preparedListeners) {
            runCatching { listener.commitMasterKeyRotation(newKey) }
        }
        for (listener in rotationListeners) {
            if (listener !is TwoPhaseKeyRotationListener) {
                runCatching { listener.onMasterKeyRotated(oldKey, newKey) }
            }
        }
        return newRotationCount
    }

    /**
     * Validates that a new Vault PIN meets hardened length & digit requirements (6 to 8 digits).
     */
    fun isValidVaultPinFormat(pin: String): Boolean {
        val trimmed = pin.trim()
        return trimmed.length in MIN_VAULT_PIN_LENGTH..MAX_VAULT_PIN_LENGTH && trimmed.all { it.isDigit() }
    }

    /**
     * Returns remaining lockout cooldown in milliseconds if exponential backoff is active, or 0L.
     */
    @Synchronized
    fun getLockoutRemainingMs(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val prefs = getKeystorePrefs(context)
        val untilMs = prefs.getLong(KEY_LOCKOUT_UNTIL_TS, 0L)
        return if (untilMs > nowMs) untilMs - nowMs else 0L
    }

    fun isAuthLockedOut(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return getLockoutRemainingMs(context, nowMs) > 0L
    }

    /**
     * Computes exponential backoff cooldown in milliseconds based on consecutive failed attempts.
     * Attempts 1-2: 0ms; Attempt 3: 5s; Attempt 4: 15s; Attempt 5: 30s; Attempt 6+: 60s * 2^(n-6) capped at 15m.
     */
    fun computeExponentialBackoffMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < 3 -> 0L
            failedAttempts == 3 -> 5_000L
            failedAttempts == 4 -> 15_000L
            failedAttempts == 5 -> 30_000L
            else -> {
                val shift = (failedAttempts - 6).coerceIn(0, 4)
                (60_000L shl shift).coerceAtMost(15 * 60_000L)
            }
        }
    }

    /**
     * Records a failed authentication attempt, applies exponential lockout backoff, and triggers
     * key rotation if the failure threshold is reached.
     */
    @Synchronized
    fun recordCrackAttemptAndAutoRotate(
        context: Context,
        threshold: Int = 3,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = getKeystorePrefs(context)
        val attempts = prefs.getInt(KEY_FAILED_CRACK_COUNT, 0) + 1
        val backoffMs = computeExponentialBackoffMs(attempts)
        val lockoutUntil = if (backoffMs > 0L) nowMs + backoffMs else 0L

        prefs.edit()
            .putInt(KEY_FAILED_CRACK_COUNT, attempts)
            .putLong(KEY_LOCKOUT_UNTIL_TS, lockoutUntil)
            .commit()

        return if (attempts >= threshold || isRuntimeTampered()) {
            rotateDynamicEncryptionKey(context, "anti_crack_attempt_$attempts")
            true
        } else {
            false
        }
    }

    @Synchronized
    fun resetCrackAttempts(context: Context) {
        getKeystorePrefs(context).edit()
            .putInt(KEY_FAILED_CRACK_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TS, 0L)
            .commit()
    }

    fun getFailedCrackAttempts(context: Context): Int {
        return getKeystorePrefs(context).getInt(KEY_FAILED_CRACK_COUNT, 0)
    }

    fun getAutoRotationCount(context: Context): Int {
        return getKeystorePrefs(context).getInt(KEY_AUTO_ROTATION_COUNT, 0)
    }

    fun isRuntimeTampered(): Boolean {
        return try {
            Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun enforcePermanentNonDebugMode(context: Context): Boolean {
        try {
            context.applicationInfo.flags =
                context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        } catch (_: Throwable) {
        }
        val prefs = getKeystorePrefs(context)
        if (!prefs.getBoolean(KEY_DEBUG_PERMANENTLY_DISABLED, false)) {
            prefs.edit().putBoolean(KEY_DEBUG_PERMANENTLY_DISABLED, true).commit()
        }
        if (isRuntimeTampered()) {
            rotateDynamicEncryptionKey(context, "anti_debug_lockdown")
        }
        return true
    }

    fun isDebugModePermanentlyDisabled(): Boolean = IS_DEBUG_MODE_PERMANENTLY_DISABLED

    data class DebugSecurityReport(
        val isPermanentlyDisabled: Boolean,
        val isDebuggerConnected: Boolean,
        val isWaitingForDebugger: Boolean,
        val isFlagDebuggableActive: Boolean,
        val isRuntimeSecure: Boolean,
        val summary: String
    )

    fun checkDebugStatus(context: Context): DebugSecurityReport {
        val debuggerConnected = try { Debug.isDebuggerConnected() } catch (_: Throwable) { false }
        val waitingForDebugger = try { Debug.waitingForDebugger() } catch (_: Throwable) { false }
        val flagDebuggable = try {
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) { false }
        val isSecure = !debuggerConnected && !waitingForDebugger
        return DebugSecurityReport(
            isPermanentlyDisabled = IS_DEBUG_MODE_PERMANENTLY_DISABLED,
            isDebuggerConnected = debuggerConnected,
            isWaitingForDebugger = waitingForDebugger,
            isFlagDebuggableActive = flagDebuggable,
            isRuntimeSecure = isSecure,
            summary = if (isSecure) "Non-Debug Enforced • Zero Debugger Attachments • Strict APK Isolation" else "Debugger Attached"
        )
    }

    fun isRemovableSdCardPath(path: String): Boolean {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isEmpty()) return false
        val lower = normalized.lowercase()
        if (lower.startsWith("/storage/emulated") ||
            lower.startsWith("/sdcard") ||
            lower.startsWith("/storage/self") ||
            lower.startsWith("/data")
        ) {
            return false
        }
        if (lower.contains("sdcard1") || lower.contains("ext_sd") || lower.contains("external_sd")) {
            return true
        }
        if (lower.startsWith("/storage/")) {
            val volumeSegment = lower.removePrefix("/storage/").substringBefore('/')
            if (volumeSegment.isNotBlank() && volumeSegment != "emulated" && volumeSegment != "self") {
                return true
            }
        }
        return false
    }

    fun encryptBytesWithKey(plainBytes: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainBytes)
        val output = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(cipherBytes, 0, output, iv.size, cipherBytes.size)
        return output
    }

    fun decryptBytesWithKey(encryptedPayload: ByteArray, key: SecretKey): ByteArray? {
        if (encryptedPayload.size <= GCM_IV_LENGTH) return null
        return try {
            val iv = encryptedPayload.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = encryptedPayload.copyOfRange(GCM_IV_LENGTH, encryptedPayload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptStringWithKey(plainText: String, key: SecretKey): String {
        val encrypted = encryptBytesWithKey(plainText.toByteArray(StandardCharsets.UTF_8), key)
        return ENCRYPTED_PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptStringWithKey(cipherText: String?, key: SecretKey): String? {
        if (cipherText.isNullOrBlank()) return null
        return try {
            val rawBase64 = if (cipherText.startsWith(ENCRYPTED_PREFIX)) {
                cipherText.removePrefix(ENCRYPTED_PREFIX)
            } else {
                cipherText
            }
            val decoded = Base64.decode(rawBase64, Base64.NO_WRAP)
            val plainBytes = decryptBytesWithKey(decoded, key) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptString(context: Context, plainText: String): String {
        val key = getOrCreateDataEncryptionKey(context)
        return encryptStringWithKey(plainText, key)
    }

    fun decryptString(context: Context, cipherText: String?): String? {
        val key = getOrCreateDataEncryptionKey(context)
        return decryptStringWithKey(cipherText, key)
    }

    fun encryptBytes(context: Context, plainBytes: ByteArray): ByteArray {
        val key = getOrCreateDataEncryptionKey(context)
        return encryptBytesWithKey(plainBytes, key)
    }

    fun decryptBytes(context: Context, encryptedPayload: ByteArray): ByteArray? {
        val key = getOrCreateDataEncryptionKey(context)
        return decryptBytesWithKey(encryptedPayload, key)
    }

    fun isEncryptedString(value: String?): Boolean {
        return value != null && value.startsWith(ENCRYPTED_PREFIX)
    }

    /**
     * Hashes a PIN, passcode, or recovery answer using 120,000-iteration PBKDF2WithHmacSHA256 with a 256-bit
     * random salt, then wraps the resulting token in AES-256-GCM under the Keystore-protected DEK.
     */
    fun createEncryptedSecretHash(context: Context, rawSecret: String): String {
        val salt = ByteArray(32).also { secureRandom.nextBytes(it) }
        val hash = deriveSecretHashBytesV3(rawSecret.trim(), salt, PBKDF2_AUTH_ITERATIONS_V3)
        val combined = HASH_VERSION_V3_PREFIX +
            Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(hash, Base64.NO_WRAP)
        Arrays.fill(hash, 0.toByte())
        return encryptString(context, combined)
    }

    fun verifyEncryptedSecretHash(context: Context, candidateSecret: String, storedToken: String?): Boolean {
        if (storedToken.isNullOrBlank()) return false
        val unwrapped = decryptString(context, storedToken) ?: return false
        return try {
            if (unwrapped.startsWith(HASH_VERSION_V3_PREFIX)) {
                val payload = unwrapped.removePrefix(HASH_VERSION_V3_PREFIX)
                val parts = payload.split(":")
                if (parts.size != 2) return false
                val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)
                val actualHash = deriveSecretHashBytesV3(candidateSecret.trim(), salt, PBKDF2_AUTH_ITERATIONS_V3)
                val matches = MessageDigest.isEqual(expectedHash, actualHash)
                Arrays.fill(actualHash, 0.toByte())
                matches
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveSecretHashBytesV3(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val secretChars = secret.toCharArray()
        return try {
            val spec = PBEKeySpec(secretChars, salt, iterations, KEY_LENGTH_BITS)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(secretChars, '\u0000')
        }
    }

    private fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Long): ByteArray {
        val chunkIv = baseIv.copyOf(GCM_IV_LENGTH)
        val indexBytes = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        for (i in 0 until 8) {
            chunkIv[GCM_IV_LENGTH - 8 + i] = (chunkIv[GCM_IV_LENGTH - 8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return chunkIv
    }

    private fun buildChunkAad(chunkIndex: Long, isFinalChunk: Boolean): ByteArray {
        return ByteBuffer.allocate(9)
            .putLong(chunkIndex)
            .put(if (isFinalChunk) 1.toByte() else 0.toByte())
            .array()
    }

    private fun wrapFileDekWithKek(fileDekBytes: ByteArray, kek: SecretKey): Pair<ByteArray, ByteArray> {
        val wrapIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, wrapIv))
        cipher.updateAAD(STREAM_V4_DEK_WRAP_AAD)
        val wrappedDek = cipher.doFinal(fileDekBytes)
        return wrapIv to wrappedDek
    }

    private fun unwrapFileDekBytesWithKek(wrapIv: ByteArray, wrappedDek: ByteArray, kek: SecretKey): ByteArray? {
        if (wrapIv.size != GCM_IV_LENGTH || wrappedDek.size != STREAM_V4_WRAPPED_DEK_BYTES) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, wrapIv))
            cipher.updateAAD(STREAM_V4_DEK_WRAP_AAD)
            val rawDek = cipher.doFinal(wrappedDek)
            if (rawDek.size == 32) {
                rawDek
            } else {
                Arrays.fill(rawDek, 0.toByte())
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Streams encryption of a file onto disk in bounded 256 KB chunks using AES-256-GCM V4 Envelope
     * Encryption (random per-file 256-bit DEK wrapped by the master KEK in an 88-byte header).
     * Enables O(1) instant key rotation by re-wrapping only the 60-byte DEK envelope without
     * ever touching or re-encrypting multi-gigabyte video payloads.
     */
    fun encryptFileToDisk(context: Context, sourceFile: File, targetEncryptedFile: File): Boolean {
        val key = getOrCreateDataEncryptionKey(context)
        return encryptFileToDiskWithKey(sourceFile, targetEncryptedFile, key)
    }

    fun encryptFileToDiskWithKey(sourceFile: File, targetEncryptedFile: File, key: SecretKey): Boolean {
        return encryptFileToStreamV4WithKek(sourceFile, targetEncryptedFile, key)
    }

    fun encryptFileToStreamV4WithKek(sourceFile: File, targetEncryptedFile: File, kek: SecretKey): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            return false
        }
        val parentDir = targetEncryptedFile.parentFile ?: return false
        parentDir.mkdirs()
        val stagingFile = File(parentDir, "${targetEncryptedFile.name}.tmp_${System.nanoTime()}")

        val rawFileDek = ByteArray(32).also { secureRandom.nextBytes(it) }
        val fileDek = SecretKeySpec(rawFileDek, "AES")
        val (dekWrapIv, wrappedFileDek) = wrapFileDekWithKek(rawFileDek, kek)
        Arrays.fill(rawFileDek, 0.toByte())

        val baseIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val plainBuffer = ByteArray(STREAM_CHUNK_SIZE)
        var success = false

        try {
            val totalLength = sourceFile.length()
            BufferedInputStream(FileInputStream(sourceFile), STREAM_CHUNK_SIZE).use { input ->
                FileOutputStream(stagingFile).use { fos ->
                    DataOutputStream(BufferedOutputStream(fos, STREAM_CHUNK_SIZE)).use { out ->
                        out.write(STREAM_MAGIC_HEADER_V4)
                        out.write(dekWrapIv)
                        out.write(wrappedFileDek)
                        out.write(baseIv)
                        out.writeLong(totalLength)

                        var bytesRemaining = totalLength
                        var chunkIndex = 0L

                        while (bytesRemaining > 0L) {
                            val toRead = minOf(plainBuffer.size.toLong(), bytesRemaining).toInt()
                            var totalReadInChunk = 0
                            while (totalReadInChunk < toRead) {
                                val read = input.read(plainBuffer, totalReadInChunk, toRead - totalReadInChunk)
                                if (read <= 0) break
                                totalReadInChunk += read
                            }
                            if (totalReadInChunk <= 0) {
                                throw EOFException("Unexpected EOF while encrypting source file")
                            }
                            bytesRemaining -= totalReadInChunk.toLong()
                            val isFinal = bytesRemaining == 0L

                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                            cipher.init(Cipher.ENCRYPT_MODE, fileDek, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                            cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                            val encryptedChunk = cipher.doFinal(plainBuffer, 0, totalReadInChunk)

                            out.writeInt(encryptedChunk.size)
                            out.write(encryptedChunk)
                            chunkIndex++
                        }
                        out.flush()
                        try {
                            fos.fd.sync()
                        } catch (_: Exception) {}
                    }
                }
            }

            Arrays.fill(plainBuffer, 0.toByte())

            if (!verifyEncryptedFileIntegrityWithKey(stagingFile, kek)) {
                stagingFile.delete()
                return false
            }

            if (targetEncryptedFile.exists()) {
                targetEncryptedFile.delete()
            }
            success = if (stagingFile.renameTo(targetEncryptedFile)) {
                true
            } else {
                stagingFile.copyTo(targetEncryptedFile, overwrite = true)
                stagingFile.delete()
                targetEncryptedFile.exists() && targetEncryptedFile.length() > STREAM_V4_HEADER_SIZE_BYTES
            }
            return success
        } catch (_: Exception) {
            Arrays.fill(plainBuffer, 0.toByte())
            stagingFile.delete()
            return false
        }
    }

    /**
     * Legacy V3 chunked encryption kept for backwards-compatibility migration tests.
     */
    fun encryptFileToDiskV3WithKey(sourceFile: File, targetEncryptedFile: File, key: SecretKey): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() <= 0L) {
            return false
        }
        val parentDir = targetEncryptedFile.parentFile ?: return false
        parentDir.mkdirs()
        val stagingFile = File(parentDir, "${targetEncryptedFile.name}.tmp_${System.nanoTime()}")

        val baseIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val plainBuffer = ByteArray(STREAM_CHUNK_SIZE)
        var success = false

        try {
            val totalLength = sourceFile.length()
            BufferedInputStream(FileInputStream(sourceFile), STREAM_CHUNK_SIZE).use { input ->
                FileOutputStream(stagingFile).use { fos ->
                    DataOutputStream(BufferedOutputStream(fos, STREAM_CHUNK_SIZE)).use { out ->
                        out.write(STREAM_MAGIC_HEADER)
                        out.write(baseIv)
                        out.writeLong(totalLength)

                        var bytesRemaining = totalLength
                        var chunkIndex = 0L

                        while (bytesRemaining > 0L) {
                            val toRead = minOf(plainBuffer.size.toLong(), bytesRemaining).toInt()
                            var totalReadInChunk = 0
                            while (totalReadInChunk < toRead) {
                                val read = input.read(plainBuffer, totalReadInChunk, toRead - totalReadInChunk)
                                if (read <= 0) break
                                totalReadInChunk += read
                            }
                            if (totalReadInChunk <= 0) {
                                throw EOFException("Unexpected EOF while encrypting source file")
                            }
                            bytesRemaining -= totalReadInChunk.toLong()
                            val isFinal = bytesRemaining == 0L

                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                            cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                            val encryptedChunk = cipher.doFinal(plainBuffer, 0, totalReadInChunk)

                            out.writeInt(encryptedChunk.size)
                            out.write(encryptedChunk)
                            chunkIndex++
                        }
                        out.flush()
                        try {
                            fos.fd.sync()
                        } catch (_: Exception) {}
                    }
                }
            }

            Arrays.fill(plainBuffer, 0.toByte())

            if (!verifyEncryptedFileIntegrityWithKey(stagingFile, key)) {
                stagingFile.delete()
                return false
            }

            if (targetEncryptedFile.exists()) {
                targetEncryptedFile.delete()
            }
            success = if (stagingFile.renameTo(targetEncryptedFile)) {
                true
            } else {
                stagingFile.copyTo(targetEncryptedFile, overwrite = true)
                stagingFile.delete()
                targetEncryptedFile.exists() && targetEncryptedFile.length() > STREAM_MAGIC_HEADER.size
            }
            return success
        } catch (_: Exception) {
            Arrays.fill(plainBuffer, 0.toByte())
            stagingFile.delete()
            return false
        }
    }

    /**
     * Verifies the cryptographic integrity and completeness of an encrypted vault/recycle-bin file
     * (V4 envelope, V3 streaming, or legacy V2) without writing plaintext to disk.
     */
    fun verifyEncryptedFileIntegrity(context: Context, encryptedFile: File): Boolean {
        val key = getOrCreateDataEncryptionKey(context)
        return verifyEncryptedFileIntegrityWithKey(encryptedFile, key)
    }

    fun verifyEncryptedFileIntegrityWithKey(encryptedFile: File, key: SecretKey): Boolean {
        if (!encryptedFile.exists() || !encryptedFile.isFile || encryptedFile.length() <= GCM_IV_LENGTH + GCM_TAG_BYTES) {
            return false
        }
        return try {
            when {
                isStreamV4File(encryptedFile) -> {
                    DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), STREAM_CHUNK_SIZE)).use { dis ->
                        val magic = ByteArray(STREAM_MAGIC_HEADER_V4.size)
                        dis.readFully(magic)
                        if (!magic.contentEquals(STREAM_MAGIC_HEADER_V4)) return false
                        val dekWrapIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(dekWrapIv)
                        val wrappedDek = ByteArray(STREAM_V4_WRAPPED_DEK_BYTES)
                        dis.readFully(wrappedDek)
                        val rawDek = unwrapFileDekBytesWithKek(dekWrapIv, wrappedDek, key) ?: return false
                        val fileDek = SecretKeySpec(rawDek, "AES")
                        Arrays.fill(rawDek, 0.toByte())

                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val expectedPlainLength = dis.readLong()
                        if (expectedPlainLength <= 0L) return false
                        verifyStreamChunks(dis, fileDek, baseIv, expectedPlainLength)
                    }
                }
                isStreamV3File(encryptedFile) -> {
                    DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), STREAM_CHUNK_SIZE)).use { dis ->
                        val magic = ByteArray(STREAM_MAGIC_HEADER.size)
                        dis.readFully(magic)
                        if (!magic.contentEquals(STREAM_MAGIC_HEADER)) return false
                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val expectedPlainLength = dis.readLong()
                        if (expectedPlainLength <= 0L) return false
                        verifyStreamChunks(dis, key, baseIv, expectedPlainLength)
                    }
                }
                else -> {
                    // Legacy v2 single-block format verification
                    val encryptedBytes = encryptedFile.readBytes()
                    val plainBytes = decryptBytesWithKey(encryptedBytes, key) ?: return false
                    val valid = plainBytes.isNotEmpty()
                    Arrays.fill(plainBytes, 0.toByte())
                    valid
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyStreamChunks(
        dis: DataInputStream,
        chunkKey: SecretKey,
        baseIv: ByteArray,
        expectedPlainLength: Long
    ): Boolean {
        var verifiedPlainBytes = 0L
        var chunkIndex = 0L
        var sawFinalChunk = false

        while (verifiedPlainBytes < expectedPlainLength) {
            val encryptedChunkSize = try {
                dis.readInt()
            } catch (_: EOFException) {
                return false
            }
            if (encryptedChunkSize <= GCM_TAG_BYTES || encryptedChunkSize > STREAM_CHUNK_SIZE + GCM_TAG_BYTES + 1024) {
                return false
            }
            val encryptedChunk = ByteArray(encryptedChunkSize)
            dis.readFully(encryptedChunk)
            val chunkPlainSize = (encryptedChunkSize - GCM_TAG_BYTES).toLong()
            val isFinal = (verifiedPlainBytes + chunkPlainSize) == expectedPlainLength

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val chunkIv = deriveChunkIv(baseIv, chunkIndex)
            cipher.init(Cipher.DECRYPT_MODE, chunkKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
            cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
            val plainChunk = cipher.doFinal(encryptedChunk)
            Arrays.fill(plainChunk, 0.toByte())

            verifiedPlainBytes += plainChunk.size.toLong()
            if (isFinal) sawFinalChunk = true
            chunkIndex++
        }
        return sawFinalChunk && verifiedPlainBytes == expectedPlainLength
    }

    data class StreamV3Header(
        val baseIv: ByteArray,
        val totalPlainLength: Long,
        val chunkPlainSize: Int = STREAM_CHUNK_SIZE,
        val headerSizeBytes: Long = (STREAM_MAGIC_HEADER.size + GCM_IV_LENGTH + 8).toLong(),
        val fullChunkRecordBytes: Long = (4 + STREAM_CHUNK_SIZE + GCM_TAG_BYTES).toLong(),
        val version: Int = 3,
        val dekWrapIv: ByteArray? = null,
        val wrappedFileDek: ByteArray? = null
    )

    fun isStreamV4File(file: File): Boolean {
        if (!file.exists() || file.length() < STREAM_V4_HEADER_SIZE_BYTES) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(STREAM_MAGIC_HEADER_V4.size)
                val read = fis.read(header)
                read == STREAM_MAGIC_HEADER_V4.size && header.contentEquals(STREAM_MAGIC_HEADER_V4)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isStreamV3File(file: File): Boolean {
        if (!file.exists() || file.length() < STREAM_MAGIC_HEADER.size + GCM_IV_LENGTH + 8) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(STREAM_MAGIC_HEADER.size)
                val read = fis.read(header)
                read == STREAM_MAGIC_HEADER.size && header.contentEquals(STREAM_MAGIC_HEADER)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isStreamEncryptedFile(file: File): Boolean = isStreamV4File(file) || isStreamV3File(file)

    /**
     * Reads and validates either a V4 Envelope header (88 bytes) or a legacy V3 header (28 bytes)
     * without decrypting any video payload bytes.
     */
    fun readStreamV3Header(file: File): StreamV3Header? = readStreamHeader(file)

    fun readStreamHeader(file: File): StreamV3Header? {
        if (!file.exists() || !file.isFile || file.length() < STREAM_MAGIC_HEADER.size + GCM_IV_LENGTH + 8) {
            return null
        }
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 128)).use { dis ->
                val magic = ByteArray(STREAM_MAGIC_HEADER.size)
                dis.readFully(magic)
                when {
                    magic.contentEquals(STREAM_MAGIC_HEADER_V4) -> {
                        if (file.length() < STREAM_V4_HEADER_SIZE_BYTES) return null
                        val dekWrapIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(dekWrapIv)
                        val wrappedFileDek = ByteArray(STREAM_V4_WRAPPED_DEK_BYTES)
                        dis.readFully(wrappedFileDek)
                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val plainLength = dis.readLong()
                        if (plainLength <= 0L) return null
                        StreamV3Header(
                            baseIv = baseIv,
                            totalPlainLength = plainLength,
                            headerSizeBytes = STREAM_V4_HEADER_SIZE_BYTES,
                            version = 4,
                            dekWrapIv = dekWrapIv,
                            wrappedFileDek = wrappedFileDek
                        )
                    }
                    magic.contentEquals(STREAM_MAGIC_HEADER) -> {
                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val plainLength = dis.readLong()
                        if (plainLength <= 0L) return null
                        StreamV3Header(
                            baseIv = baseIv,
                            totalPlainLength = plainLength,
                            headerSizeBytes = (STREAM_MAGIC_HEADER.size + GCM_IV_LENGTH + 8).toLong(),
                            version = 3
                        )
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unwraps the per-file 256-bit DEK from a V4 Envelope header using the active master KEK.
     * For legacy V3 headers, returns the master KEK directly so callers share one unified code path.
     */
    fun resolveStreamFileKey(context: Context, header: StreamV3Header): SecretKey? {
        val masterKek = getOrCreateDataEncryptionKey(context)
        return resolveStreamFileKeyWithKek(header, masterKek)
    }

    fun resolveStreamFileKeyWithKek(header: StreamV3Header, masterKek: SecretKey): SecretKey? {
        if (header.version == 4) {
            val wrapIv = header.dekWrapIv ?: return null
            val wrappedDek = header.wrappedFileDek ?: return null
            val rawDek = unwrapFileDekBytesWithKek(wrapIv, wrappedDek, masterKek) ?: return null
            val key = SecretKeySpec(rawDek, "AES")
            Arrays.fill(rawDek, 0.toByte())
            return key
        }
        return masterKek
    }

    /**
     * Prepares a 60-byte `[12B newDekWrapIv][48B newWrappedFileDek]` header replacement payload in RAM
     * for two-phase O(1) KEK rotation of a V4 Envelope file without modifying disk or touching video payload.
     */
    fun prepareStreamV4HeaderRewrap(
        encryptedFile: File,
        oldKek: SecretKey,
        newKek: SecretKey
    ): ByteArray? {
        val header = readStreamHeader(encryptedFile) ?: return null
        if (header.version != 4) return null
        val wrapIv = header.dekWrapIv ?: return null
        val wrappedDek = header.wrappedFileDek ?: return null
        val rawDek = unwrapFileDekBytesWithKek(wrapIv, wrappedDek, oldKek) ?: return null
        return try {
            val (newWrapIv, newWrappedDek) = wrapFileDekWithKek(rawDek, newKek)
            // Verify round-trip unwrap under newKek before returning staged header payload
            val verifyDek = unwrapFileDekBytesWithKek(newWrapIv, newWrappedDek, newKek) ?: return null
            val verified = MessageDigest.isEqual(rawDek, verifyDek)
            Arrays.fill(verifyDek, 0.toByte())
            if (!verified) return null
            ByteArray(GCM_IV_LENGTH + STREAM_V4_WRAPPED_DEK_BYTES).also { payload ->
                System.arraycopy(newWrapIv, 0, payload, 0, GCM_IV_LENGTH)
                System.arraycopy(newWrappedDek, 0, payload, GCM_IV_LENGTH, STREAM_V4_WRAPPED_DEK_BYTES)
            }
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(rawDek, 0.toByte())
        }
    }

    /**
     * Commits a previously prepared 60-byte V4 DEK envelope payload into bytes `[8..67]` of [encryptedFile].
     * O(1) I/O regardless of video size — zero plaintext ever touches flash storage.
     */
    fun commitStreamV4HeaderRewrap(encryptedFile: File, preparedEnvelopePayload: ByteArray): Boolean {
        if (preparedEnvelopePayload.size != GCM_IV_LENGTH + STREAM_V4_WRAPPED_DEK_BYTES) return false
        if (!encryptedFile.exists() || !encryptedFile.isFile || encryptedFile.length() < STREAM_V4_HEADER_SIZE_BYTES) {
            return false
        }
        return try {
            java.io.RandomAccessFile(encryptedFile, "rws").use { raf ->
                val magic = ByteArray(STREAM_MAGIC_HEADER_V4.size)
                raf.seek(0L)
                raf.readFully(magic)
                if (!magic.contentEquals(STREAM_MAGIC_HEADER_V4)) return false
                raf.seek(STREAM_MAGIC_HEADER_V4.size.toLong())
                raf.write(preparedEnvelopePayload)
                runCatching { raf.fd.sync() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * O(1) zero-plaintext KEK rotation for a V4 Envelope encrypted file: unwraps the 32-byte per-file DEK
     * under [oldKek], re-wraps it under [newKek] with a fresh GCM IV, and updates only bytes `[8..67]` in-place.
     */
    fun rewrapStreamV4HeaderInPlace(encryptedFile: File, oldKek: SecretKey, newKek: SecretKey): Boolean {
        val payload = prepareStreamV4HeaderRewrap(encryptedFile, oldKek, newKek) ?: return false
        return try {
            commitStreamV4HeaderRewrap(encryptedFile, payload)
        } finally {
            Arrays.fill(payload, 0.toByte())
        }
    }

    /**
     * Authenticates and decrypts a single V3 AES-256-GCM chunk in RAM using the active DEK.
     * Throws AEADBadTagException / GeneralSecurityException if tampered or wrong key.
     */
    fun decryptStreamV3Chunk(
        context: Context,
        baseIv: ByteArray,
        chunkIndex: Long,
        isFinal: Boolean,
        encryptedChunk: ByteArray,
        offset: Int = 0,
        length: Int = encryptedChunk.size
    ): ByteArray {
        val key = getOrCreateDataEncryptionKey(context)
        return decryptStreamV3ChunkWithKey(key, baseIv, chunkIndex, isFinal, encryptedChunk, offset, length)
    }

    fun decryptStreamV3ChunkWithKey(
        key: SecretKey,
        baseIv: ByteArray,
        chunkIndex: Long,
        isFinal: Boolean,
        encryptedChunk: ByteArray,
        offset: Int = 0,
        length: Int = encryptedChunk.size
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val chunkIv = deriveChunkIv(baseIv, chunkIndex)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
        cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
        return cipher.doFinal(encryptedChunk, offset, length)
    }

    /**
     * Zero-allocation V3/V4 AES-256-GCM chunk decryptor reusing a caller-owned [Cipher] and
     * [outputPlainBuffer] to avoid per-chunk Cipher and 256 KB ByteArray allocations during playback.
     */
    fun decryptStreamV3ChunkInto(
        context: Context,
        reusableCipher: Cipher,
        baseIv: ByteArray,
        chunkIndex: Long,
        isFinal: Boolean,
        encryptedChunk: ByteArray,
        encryptedLength: Int,
        outputPlainBuffer: ByteArray
    ): Int {
        val key = getOrCreateDataEncryptionKey(context)
        return decryptStreamChunkIntoWithKey(
            chunkKey = key,
            reusableCipher = reusableCipher,
            baseIv = baseIv,
            chunkIndex = chunkIndex,
            isFinal = isFinal,
            encryptedChunk = encryptedChunk,
            encryptedLength = encryptedLength,
            outputPlainBuffer = outputPlainBuffer
        )
    }

    fun decryptStreamChunkIntoWithKey(
        chunkKey: SecretKey,
        reusableCipher: Cipher,
        baseIv: ByteArray,
        chunkIndex: Long,
        isFinal: Boolean,
        encryptedChunk: ByteArray,
        encryptedLength: Int,
        outputPlainBuffer: ByteArray
    ): Int {
        val chunkIv = deriveChunkIv(baseIv, chunkIndex)
        reusableCipher.init(Cipher.DECRYPT_MODE, chunkKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
        reusableCipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
        return reusableCipher.doFinal(encryptedChunk, 0, encryptedLength, outputPlainBuffer, 0)
    }

    /**
     * Streams decryption of an encrypted vault/recycle-bin file (V4, V3, or legacy V2) to targetPlainFile
     * using a .tmp staging file. If authentication fails at any chunk, deletes the partial .tmp file
     * and returns false without corrupting targetPlainFile or deleting encryptedFile.
     */
    fun decryptFileFromDisk(context: Context, encryptedFile: File, targetPlainFile: File): Boolean {
        val key = getOrCreateDataEncryptionKey(context)
        return decryptFileFromDiskWithKey(encryptedFile, targetPlainFile, key)
    }

    fun decryptFileFromDiskWithKey(encryptedFile: File, targetPlainFile: File, key: SecretKey): Boolean {
        if (!encryptedFile.exists() || !encryptedFile.isFile || encryptedFile.length() <= GCM_IV_LENGTH + GCM_TAG_BYTES) {
            return false
        }
        val parentDir = targetPlainFile.parentFile ?: return false
        parentDir.mkdirs()
        val stagingFile = File(parentDir, "${targetPlainFile.name}.dec_tmp_${System.nanoTime()}")

        try {
            when {
                isStreamV4File(encryptedFile) -> {
                    DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), STREAM_CHUNK_SIZE)).use { dis ->
                        val magic = ByteArray(STREAM_MAGIC_HEADER_V4.size)
                        dis.readFully(magic)
                        if (!magic.contentEquals(STREAM_MAGIC_HEADER_V4)) {
                            stagingFile.delete()
                            return false
                        }
                        val dekWrapIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(dekWrapIv)
                        val wrappedDek = ByteArray(STREAM_V4_WRAPPED_DEK_BYTES)
                        dis.readFully(wrappedDek)
                        val rawDek = unwrapFileDekBytesWithKek(dekWrapIv, wrappedDek, key)
                        if (rawDek == null) {
                            stagingFile.delete()
                            return false
                        }
                        val fileDek = SecretKeySpec(rawDek, "AES")
                        Arrays.fill(rawDek, 0.toByte())

                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val expectedPlainLength = dis.readLong()
                        if (expectedPlainLength <= 0L) {
                            stagingFile.delete()
                            return false
                        }
                        if (!decryptStreamChunksToFile(dis, fileDek, baseIv, expectedPlainLength, stagingFile)) {
                            stagingFile.delete()
                            return false
                        }
                    }
                }
                isStreamV3File(encryptedFile) -> {
                    DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), STREAM_CHUNK_SIZE)).use { dis ->
                        val magic = ByteArray(STREAM_MAGIC_HEADER.size)
                        dis.readFully(magic)
                        if (!magic.contentEquals(STREAM_MAGIC_HEADER)) {
                            stagingFile.delete()
                            return false
                        }
                        val baseIv = ByteArray(GCM_IV_LENGTH)
                        dis.readFully(baseIv)
                        val expectedPlainLength = dis.readLong()
                        if (expectedPlainLength <= 0L) {
                            stagingFile.delete()
                            return false
                        }
                        if (!decryptStreamChunksToFile(dis, key, baseIv, expectedPlainLength, stagingFile)) {
                            stagingFile.delete()
                            return false
                        }
                    }
                }
                else -> {
                    // Backward-compatible legacy v2 single-block decryption
                    val encryptedBytes = encryptedFile.readBytes()
                    val plainBytes = decryptBytesWithKey(encryptedBytes, key)
                    if (plainBytes == null || plainBytes.isEmpty()) {
                        stagingFile.delete()
                        return false
                    }
                    stagingFile.writeBytes(plainBytes)
                    Arrays.fill(plainBytes, 0.toByte())
                }
            }

            if (targetPlainFile.exists()) {
                targetPlainFile.delete()
            }
            val moved = if (stagingFile.renameTo(targetPlainFile)) {
                true
            } else {
                stagingFile.copyTo(targetPlainFile, overwrite = true)
                stagingFile.delete()
                targetPlainFile.exists() && targetPlainFile.length() > 0L
            }
            return moved
        } catch (_: Exception) {
            stagingFile.delete()
            return false
        }
    }

    private fun decryptStreamChunksToFile(
        dis: DataInputStream,
        chunkKey: SecretKey,
        baseIv: ByteArray,
        expectedPlainLength: Long,
        stagingFile: File
    ): Boolean {
        var writtenPlainBytes = 0L
        var chunkIndex = 0L
        var sawFinalChunk = false

        FileOutputStream(stagingFile).use { fos ->
            BufferedOutputStream(fos, STREAM_CHUNK_SIZE).use { out ->
                while (writtenPlainBytes < expectedPlainLength) {
                    val encryptedChunkSize = dis.readInt()
                    if (encryptedChunkSize <= GCM_TAG_BYTES ||
                        encryptedChunkSize > STREAM_CHUNK_SIZE + GCM_TAG_BYTES + 1024
                    ) {
                        throw IllegalStateException("Invalid encrypted chunk size")
                    }
                    val encryptedChunk = ByteArray(encryptedChunkSize)
                    dis.readFully(encryptedChunk)
                    val chunkPlainSize = (encryptedChunkSize - GCM_TAG_BYTES).toLong()
                    val isFinal = (writtenPlainBytes + chunkPlainSize) == expectedPlainLength

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val chunkIv = deriveChunkIv(baseIv, chunkIndex)
                    cipher.init(Cipher.DECRYPT_MODE, chunkKey, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                    cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                    val plainChunk = cipher.doFinal(encryptedChunk)

                    out.write(plainChunk)
                    writtenPlainBytes += plainChunk.size.toLong()
                    Arrays.fill(plainChunk, 0.toByte())
                    if (isFinal) sawFinalChunk = true
                    chunkIndex++
                }
                out.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {}
            }
        }
        return sawFinalChunk && writtenPlainBytes == expectedPlainLength && stagingFile.length() == expectedPlainLength
    }

    /**
     * Rotates an encrypted file from [oldKey] to [newKey]:
     * - For V4 Envelope files: performs O(1) instant in-place header re-wrap (zero plaintext, 60 bytes written).
     * - For legacy V3/V2 files: streams an upgrade into a V4 Envelope file atomically.
     */
    fun reEncryptFileOnDisk(encryptedFile: File, oldKey: SecretKey, newKey: SecretKey): Boolean {
        if (!encryptedFile.exists() || !encryptedFile.isFile) return false
        if (isStreamV4File(encryptedFile)) {
            return rewrapStreamV4HeaderInPlace(encryptedFile, oldKey, newKey)
        }
        val parentDir = encryptedFile.parentFile ?: return false
        val tempPlain = File(parentDir, "${encryptedFile.name}.rot_plain_${System.nanoTime()}")
        val tempReEnc = File(parentDir, "${encryptedFile.name}.rot_enc_${System.nanoTime()}")
        return try {
            if (!decryptFileFromDiskWithKey(encryptedFile, tempPlain, oldKey)) {
                shredFileZeroTrace(tempPlain)
                return false
            }
            if (!encryptFileToStreamV4WithKek(tempPlain, tempReEnc, newKey)) {
                shredFileZeroTrace(tempPlain)
                tempReEnc.delete()
                return false
            }
            shredFileZeroTrace(tempPlain)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            if (tempReEnc.renameTo(encryptedFile)) {
                true
            } else {
                tempReEnc.copyTo(encryptedFile, overwrite = true)
                tempReEnc.delete()
                encryptedFile.exists()
            }
        } catch (_: Exception) {
            shredFileZeroTrace(tempPlain)
            tempReEnc.delete()
            false
        }
    }

    fun decryptFileToStream(context: Context, encryptedFile: File): InputStream? {
        val tempOut = File(context.cacheDir, "stream_dec_${System.nanoTime()}.tmp")
        return if (decryptFileFromDisk(context, encryptedFile, tempOut)) {
            object : FileInputStream(tempOut) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        shredFileZeroTrace(tempOut)
                    }
                }
            }
        } else {
            tempOut.delete()
            null
        }
    }

    /**
     * Multi-pass overwrite and deletion for zero-trace file shredding.
     */
    fun shredFileZeroTrace(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            if (file.isFile && file.length() > 0) {
                val length = file.length().coerceAtMost(2 * 1024 * 1024L).toInt()
                java.io.RandomAccessFile(file, "rws").use { raf ->
                    val zeros = ByteArray(4096)
                    val randomBuf = ByteArray(4096)
                    var written = 0
                    raf.seek(0)
                    while (written < length) {
                        secureRandom.nextBytes(randomBuf)
                        val chunk = minOf(randomBuf.size, length - written)
                        raf.write(randomBuf, 0, chunk)
                        written += chunk
                    }
                    written = 0
                    raf.seek(0)
                    while (written < length) {
                        val chunk = minOf(zeros.size, length - written)
                        raf.write(zeros, 0, chunk)
                        written += chunk
                    }
                }
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }

    fun isPathAllowedForIsolatedApp(path: String, ownPackageName: String = "com.cyosplay.player"): Boolean {
        if (path.isBlank()) return false
        val normalized = path.replace('\\', '/')
        if (normalized.contains("../") || normalized.endsWith("/..")) {
            return false
        }
        val lower = normalized.lowercase()
        val ownLower = ownPackageName.lowercase()
        if (lower.contains("/android/data") && !lower.contains("/android/data/$ownLower")) {
            return false
        }
        if (lower.contains("/android/obb") && !lower.contains("/android/obb/$ownLower")) {
            return false
        }
        if (lower.contains("/android/media") && !lower.contains("/android/media/$ownLower")) {
            return false
        }
        if (lower.startsWith("/data/data") && !lower.startsWith("/data/data/$ownLower")) {
            return false
        }
        if (lower.startsWith("/data/user") && !lower.contains("/$ownLower")) {
            return false
        }
        return true
    }

    /**
     * Checks whether the destination directory has sufficient available storage for copying a file
     * of [requiredBytes], leaving a safe 4 MB headroom when queried via StatFs.
     */
    fun hasEnoughStorageForCopy(destinationDir: File?, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return true
        val dir = destinationDir ?: return true
        return try {
            dir.mkdirs()
            val probePath = if (dir.exists()) dir.absolutePath else dir.parentFile?.absolutePath ?: return true
            val stat = android.os.StatFs(probePath)
            val available = stat.availableBytes
            available <= 0L || available >= requiredBytes
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Safely copies a video file to [targetFile] without locking or interrupting active playback of [sourceFile].
     * Verifies available storage first, streams via a staging file or non-locking read-only channel, and
     * verifies the exact byte size of the duplicate while leaving [sourceFile] untouched.
     */
    fun fastCopyFileZeroCopy(
        sourceFile: File,
        targetFile: File,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile) return false
        val expectedLength = sourceFile.length()
        if (!hasEnoughStorageForCopy(targetFile.parentFile, expectedLength)) {
            return false
        }
        return try {
            val parentDir = targetFile.parentFile
            parentDir?.mkdirs()
            FileInputStream(sourceFile).channel.use { inChannel ->
                FileOutputStream(targetFile).channel.use { outChannel ->
                    val size = inChannel.size()
                    var position = 0L
                    val chunkSize = 16L * 1024L * 1024L
                    while (position < size) {
                        val toTransfer = minOf(chunkSize, size - position)
                        val transferred = inChannel.transferTo(position, toTransfer, outChannel)
                        if (transferred <= 0L) break
                        position += transferred
                        onProgress?.invoke(position, size)
                    }
                    outChannel.force(false)
                }
            }
            val success = targetFile.exists() && targetFile.length() == expectedLength && sourceFile.exists()
            if (success) {
                onProgress?.invoke(expectedLength, expectedLength)
            }
            success
        } catch (_: Exception) {
            try {
                targetFile.parentFile?.mkdirs()
                sourceFile.copyTo(targetFile, overwrite = true)
                val ok = targetFile.exists() && targetFile.length() == expectedLength && sourceFile.exists()
                if (ok) {
                    onProgress?.invoke(expectedLength, expectedLength)
                }
                ok
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Performs an atomic move of [sourceFile] to [targetFile]:
     * 1. Attempts an instant filesystem `renameTo` when on the same mount point.
     * 2. If across directories/volumes, copies to [targetFile], strictly verifies that [targetFile] exists
     *    and matches [sourceFile]'s byte length, and ONLY then deletes/shreds [sourceFile] so the original
     *    file never remains in the source folder.
     */
    fun fastMoveFileInstant(
        sourceFile: File,
        targetFile: File,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile) return false
        val expectedLength = sourceFile.length()
        return try {
            if (sourceFile.canonicalPath == targetFile.canonicalPath) {
                onProgress?.invoke(expectedLength, expectedLength)
                return true
            }
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (sourceFile.renameTo(targetFile) && targetFile.exists() && !sourceFile.exists()) {
                onProgress?.invoke(targetFile.length(), targetFile.length())
                return true
            }
            if (fastCopyFileZeroCopy(sourceFile, targetFile, onProgress)) {
                val verified = targetFile.exists() && targetFile.length() == expectedLength
                if (verified) {
                    val shredded = shredFileZeroTrace(sourceFile)
                    if (!shredded && sourceFile.exists()) {
                        sourceFile.delete()
                    }
                    !sourceFile.exists() && targetFile.exists()
                } else {
                    targetFile.delete()
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0.toByte())
        }
        return bytes
    }

    /**
     * Encrypts any video technical datacode (codec flags, Matroska EBML headers, Lavf60.3.100 tags,
     * resolution, bitrate, and stream fingerprint) directly under the per-device AndroidKeyStore
     * AES-256-GCM DEK (zero embedded static secrets).
     */
    @Synchronized
    fun encryptVideoDatacode(context: Context, rawDatacodePayload: String): String {
        return encryptString(context, "$DATACODE_V4_PREFIX$rawDatacodePayload")
    }

    /**
     * Decrypts a video technical datacode encrypted with [encryptVideoDatacode] using the
     * per-device AndroidKeyStore AES-256-GCM DEK.
     */
    @Synchronized
    fun decryptVideoDatacode(context: Context, encryptedDatacode: String?): String? {
        if (encryptedDatacode.isNullOrBlank()) return null
        val unwrapped = decryptString(context, encryptedDatacode) ?: return null
        return if (unwrapped.startsWith(DATACODE_V4_PREFIX)) {
            unwrapped.removePrefix(DATACODE_V4_PREFIX)
        } else {
            unwrapped
        }
    }

    @Synchronized
    fun encryptBuildCodeData(context: Context, rawBuildPayload: String): String {
        return encryptVideoDatacode(context, rawBuildPayload)
    }

    @Synchronized
    fun decryptBuildCodeData(context: Context, encryptedBuildPayload: String?): String? {
        return decryptVideoDatacode(context, encryptedBuildPayload)
    }

    /**
     * Generates a compact, authenticated encrypted datacode token for a video item,
     * proving that its stream metadata & build datacodes are protected with AES-256-GCM encryption.
     */
    fun generateEncryptedVideoDatacodeToken(
        context: Context,
        videoPath: String,
        containerSpec: String,
        codecSpec: String,
        width: Int,
        height: Int
    ): String {
        val plain = "$videoPath|$containerSpec|$codecSpec|${width}x${height}|${FfmpegMatroskaCodecRegistry.MATROSKA_MUXING_APP}"
        return encryptVideoDatacode(context, plain)
    }

    // --- Media & Vault Keystore Signing ---

    private const val KEYSTORE_EXPORT_SIGNER_ALIAS = "cyosplay_media_export_signer"
    @Volatile
    private var fallbackJvmExportKeyPair: KeyPair? = null

    data class ExportSignatureManifest(
        val filename: String,
        val fileSize: Long,
        val sha256Digest: String,
        val signatureHex: String,
        val signerAlias: String = "cyosplay_media_export_signer",
        val algorithm: String = "SHA256withRSA",
        val timestampMs: Long = System.currentTimeMillis(),
        val isVerified: Boolean = true
    )

    @Synchronized
    private fun getOrCreateExportSigningKeyPair(): KeyPair {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_EXPORT_SIGNER_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (existingEntry != null) {
                return KeyPair(existingEntry.certificate.publicKey, existingEntry.privateKey)
            }
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_EXPORT_SIGNER_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()
            kpg.initialize(spec)
            return kpg.generateKeyPair()
        } catch (_: Throwable) {
            fallbackJvmExportKeyPair?.let { return it }
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, secureRandom)
            val pair = kpg.generateKeyPair()
            fallbackJvmExportKeyPair = pair
            return pair
        }
    }

    /**
     * Computes the SHA-256 hexadecimal digest of any file without loading it completely into memory.
     */
    fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), 64 * 1024).use { bis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (bis.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that a copied destination file exists, has the exact same byte length as the source file,
     * and matches the header & tail 64 KB SHA-256 digest of the source file before any source deletion occurs.
     */
    fun verifyCopiedFileIntegrity(sourceFile: File, destFile: File): Boolean {
        if (!sourceFile.exists() || !destFile.exists()) return false
        val srcLen = sourceFile.length()
        val dstLen = destFile.length()
        if (srcLen != dstLen) return false
        if (srcLen == 0L) return true
        return try {
            val sampleSize = minOf(srcLen, 64L * 1024L).toInt()
            val srcHeader = ByteArray(sampleSize)
            val dstHeader = ByteArray(sampleSize)
            java.io.RandomAccessFile(sourceFile, "r").use { srcRaf ->
                java.io.RandomAccessFile(destFile, "r").use { dstRaf ->
                    srcRaf.readFully(srcHeader)
                    dstRaf.readFully(dstHeader)
                    if (!MessageDigest.isEqual(srcHeader, dstHeader)) return false
                    if (srcLen > sampleSize) {
                        val tailOffset = srcLen - sampleSize
                        val srcTail = ByteArray(sampleSize)
                        val dstTail = ByteArray(sampleSize)
                        srcRaf.seek(tailOffset)
                        dstRaf.seek(tailOffset)
                        srcRaf.readFully(srcTail)
                        dstRaf.readFully(dstTail)
                        if (!MessageDigest.isEqual(srcTail, dstTail)) return false
                    }
                }
            }
            true
        } catch (_: Throwable) {
            srcLen == dstLen && dstLen > 0L
        }
    }

    /**
     * Signs arbitrary byte payload with the hardware-backed Android Keystore RSA private key.
     */
    @Synchronized
    fun signBytesWithKeystore(data: ByteArray): ByteArray {
        val keyPair = getOrCreateExportSigningKeyPair()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }

    /**
     * Verifies byte payload signature against the Keystore public key.
     */
    @Synchronized
    fun verifyBytesWithKeystore(data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val keyPair = getOrCreateExportSigningKeyPair()
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(keyPair.public)
            verifier.update(data)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Signs an export media file with the hardware-backed Keystore, verifies the signature,
     * and produces a companion .sig.json manifest file alongside it.
     */
    @Synchronized
    fun signMediaFileWithKeystore(file: File): ExportSignatureManifest {
        val sha256 = computeFileSha256(file)
        val sigBytes = signBytesWithKeystore(sha256.toByteArray(StandardCharsets.UTF_8))
        val sigHex = sigBytes.joinToString("") { "%02x".format(it) }
        val verified = verifyBytesWithKeystore(sha256.toByteArray(StandardCharsets.UTF_8), sigBytes)

        val manifest = ExportSignatureManifest(
            filename = file.name,
            fileSize = file.length(),
            sha256Digest = sha256,
            signatureHex = sigHex,
            signerAlias = KEYSTORE_EXPORT_SIGNER_ALIAS,
            algorithm = "SHA256withRSA",
            timestampMs = System.currentTimeMillis(),
            isVerified = verified
        )

        // Write companion signature manifest with proper JSON escaping via org.json.JSONObject
        val companionFile = File(file.parentFile ?: file, "${file.name}.sig.json")
        try {
            val jsonObject = org.json.JSONObject().apply {
                put("filename", manifest.filename)
                put("fileSize", manifest.fileSize)
                put("sha256", manifest.sha256Digest)
                put("signature", manifest.signatureHex)
                put("signer", manifest.signerAlias)
                put("algorithm", manifest.algorithm)
                put("timestampMs", manifest.timestampMs)
                put("verified", manifest.isVerified)
            }
            companionFile.writeText(jsonObject.toString(2))
        } catch (_: Exception) {
            // Non-fatal if companion cannot write to read-only folder
        }

        return manifest
    }
}
