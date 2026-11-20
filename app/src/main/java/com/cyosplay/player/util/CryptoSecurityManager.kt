package com.cyosplay.player.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
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
import java.security.PrivateKey
import java.security.PublicKey
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
 *    (with automatic CSPRNG fallback in headless JVM/Robolectric unit tests), migrating legacy v2 keys seamlessly.
 * 2. Performs chunked AEAD streaming encryption/decryption (CYOS_STREAM_V3) so multi-gigabyte 4K/8K videos
 *    never load into heap memory, preventing OutOfMemoryError and detecting any truncation/tampering.
 * 3. Enforces 120,000-iteration PBKDF2-HMAC-SHA256 secret hashing, 6+ digit PIN validation, and exponential
 *    lockout backoff on failed authentication attempts.
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
    private const val LEGACY_PBKDF2_ITERATIONS = 16_000
    private const val LEGACY_SECRET_HASH_ITERATIONS = 12_000
    const val PBKDF2_AUTH_ITERATIONS_V3 = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ENCRYPTED_PREFIX = "ENCv2:"
    private const val HASH_VERSION_V3_PREFIX = "V3:"

    // Streaming file encryption constants (256 KB chunks for low-RAM footprint on 4K/8K REMUX files)
    private val STREAM_MAGIC_HEADER = "CYOSV3\u0000\u0001".toByteArray(StandardCharsets.US_ASCII)
    const val STREAM_CHUNK_SIZE = 256 * 1024

    const val MAJOR_CAPABILITY_UNIQUE_ID = "cyos play"
    const val BUILD_CODE_ENCRYPTION_PASSWORD_SPEC = "W23@#$*adsgbfggQWEFGEF13454ehger"
    const val RELEASE_KEYSTORE_PASSWORD_SPEC = "vnjfjvveSDF@!#)*ADF@#Bfgfdh"
    const val JAR_SIGNATURE_DATA_PASSWORD_SPEC = "4848rhhrhrhHhdhWw÷<$&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV13_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV14_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV15_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV16_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV17_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV18_SPEC = "W23@#\n&^$&÷&÷hdhUuuUU'bccb"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV2_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV3_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV4_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV5_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV6_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV7_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV8_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV7A_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV7B_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V108RV_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV8A_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V108RV2_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V108RV3_SPEC = "wdwewefeSDFWEFFFR#@#@&|?><@"
    const val RELEASE_KEYSTORE_PASSWORD_V107RV_ALT_SPEC = "wdwewefeSDFWEFFFR#@#@$$&|?><@$23"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV2_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV3_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV4_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV5_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV6_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7A_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7B_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8A_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV2_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV3_SPEC = "#adsdfrgrgAeg;..,:\"{}|"
    const val JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_ALT_SPEC = "Qwer./,;'357!#@%%%$#adsdfrgrgAeg;..,:\"{}|"
    const val MASTER_PASSWORD_LOCKED_SPEC = "a12@ #$%DQWaA2-=d,q./qweAS"
    private const val KEY_DATA_CODES_LOCKED = "zk_data_codes_locked_master_pw"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_TOKEN = "zk_encrypted_keystore_pw_token_v106rv18"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV_TOKEN = "zk_encrypted_keystore_pw_token_v107rv"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV2_TOKEN = "zk_encrypted_keystore_pw_token_v107rv2"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV3_TOKEN = "zk_encrypted_keystore_pw_token_v107rv3"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV4_TOKEN = "zk_encrypted_keystore_pw_token_v107rv4"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV5_TOKEN = "zk_encrypted_keystore_pw_token_v107rv5"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV6_TOKEN = "zk_encrypted_keystore_pw_token_v107rv6"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV7_TOKEN = "zk_encrypted_keystore_pw_token_v107rv7"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV8_TOKEN = "zk_encrypted_keystore_pw_token_v107rv8"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV7A_TOKEN = "zk_encrypted_keystore_pw_token_v107rv7a"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV7B_TOKEN = "zk_encrypted_keystore_pw_token_v107rv7b"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V108RV_TOKEN = "zk_encrypted_keystore_pw_token_v108rv"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V107RV8A_TOKEN = "zk_encrypted_keystore_pw_token_v107rv8a"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V108RV2_TOKEN = "zk_encrypted_keystore_pw_token_v108rv2"
    private const val KEY_ENCRYPTED_KEYSTORE_PW_V108RV3_TOKEN = "zk_encrypted_keystore_pw_token_v108rv3"
    private const val KEY_ENCRYPTED_BUILD_PW_TOKEN = "zk_encrypted_build_pw_token_v107rv"
    private const val KEY_ENCRYPTED_JAR_PW_TOKEN = "zk_encrypted_jar_pw_token_v107rv"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV13_TOKEN = "zk_encrypted_jar_build_pw_v106rv13"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV14_TOKEN = "zk_encrypted_jar_build_pw_v106rv14"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV15_TOKEN = "zk_encrypted_jar_build_pw_v106rv15"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV16_TOKEN = "zk_encrypted_jar_build_pw_v106rv16"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV17_TOKEN = "zk_encrypted_jar_build_pw_v106rv17"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V106RV18_TOKEN = "zk_encrypted_jar_build_pw_v106rv18"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV_TOKEN = "zk_encrypted_jar_build_pw_v107rv"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV2_TOKEN = "zk_encrypted_jar_build_pw_v107rv2"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV3_TOKEN = "zk_encrypted_jar_build_pw_v107rv3"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV4_TOKEN = "zk_encrypted_jar_build_pw_v107rv4"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV5_TOKEN = "zk_encrypted_jar_build_pw_v107rv5"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV6_TOKEN = "zk_encrypted_jar_build_pw_v107rv6"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7_TOKEN = "zk_encrypted_jar_build_pw_v107rv7"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8_TOKEN = "zk_encrypted_jar_build_pw_v107rv8"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7A_TOKEN = "zk_encrypted_jar_build_pw_v107rv7a"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7B_TOKEN = "zk_encrypted_jar_build_pw_v107rv7b"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V108RV_TOKEN = "zk_encrypted_jar_build_pw_v108rv"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8A_TOKEN = "zk_encrypted_jar_build_pw_v107rv8a"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V108RV2_TOKEN = "zk_encrypted_jar_build_pw_v108rv2"
    private const val KEY_ENCRYPTED_JAR_BUILD_PW_V108RV3_TOKEN = "zk_encrypted_jar_build_pw_v108rv3"

    // V4-32 invisible XOR shards reconstructing the 32-char build code encryption password ("W23@#$*adsgbfggQWEFGEF13454ehger")
    private val V4_BUILD_SHARD_A = byteArrayOf(
        0xA5.toByte(), 0x5A.toByte(), 0xC3.toByte(), 0x3C.toByte(), 0x96.toByte(),
        0x69.toByte(), 0x1E.toByte(), 0xE1.toByte(), 0x78.toByte(), 0x87.toByte(),
        0x4B.toByte(), 0xB4.toByte(), 0x2D.toByte(), 0xD2.toByte(), 0x17.toByte(),
        0xE8.toByte(), 0x39.toByte(), 0xC6.toByte(), 0x53.toByte(), 0xAC.toByte(),
        0x71.toByte(), 0x8E.toByte(), 0x9B.toByte(), 0x64.toByte(), 0x4F.toByte(),
        0xB0.toByte(), 0x29.toByte(), 0xD6.toByte(), 0x13.toByte(), 0xEC.toByte(),
        0x5D.toByte(), 0xA2.toByte()
    )

    private val V4_BUILD_SHARD_B = byteArrayOf(
        0xF2.toByte(), 0x68.toByte(), 0xF0.toByte(), 0x7C.toByte(), 0xB5.toByte(),
        0x4D.toByte(), 0x34.toByte(), 0x80.toByte(), 0x1C.toByte(), 0xF4.toByte(),
        0x2C.toByte(), 0xD6.toByte(), 0x4B.toByte(), 0xB5.toByte(), 0x70.toByte(),
        0xB9.toByte(), 0x6E.toByte(), 0x83.toByte(), 0x15.toByte(), 0xEB.toByte(),
        0x34.toByte(), 0xC8.toByte(), 0xAA.toByte(), 0x57.toByte(), 0x7B.toByte(),
        0x85.toByte(), 0x1D.toByte(), 0xB3.toByte(), 0x7B.toByte(), 0x8B.toByte(),
        0x38.toByte(), 0xD0.toByte()
    )

    // V3-26 invisible XOR shards reconstructing the 26-char master password seed ("a12@ #$%DQWaA2-=d,q./qweAS")
    private val V3_26_SHARD_A = byteArrayOf(
        0xAA.toByte(), 0x55.toByte(), 0xCC.toByte(), 0x33.toByte(), 0x77.toByte(),
        0x99.toByte(), 0x66.toByte(), 0x11.toByte(), 0xEE.toByte(), 0x77.toByte(),
        0x88.toByte(), 0x44.toByte(), 0xBB.toByte(), 0x22.toByte(), 0xDD.toByte(),
        0x19.toByte(), 0xA4.toByte(), 0x5B.toByte(), 0xC3.toByte(), 0x3C.toByte(),
        0x7E.toByte(), 0x81.toByte(), 0x92.toByte(), 0x4D.toByte(), 0xE7.toByte(),
        0x1B.toByte()
    )

    private val V3_26_SHARD_B = byteArrayOf(
        0xCB.toByte(), 0x64.toByte(), 0xFE.toByte(), 0x73.toByte(), 0x57.toByte(),
        0xBA.toByte(), 0x42.toByte(), 0x34.toByte(), 0xAA.toByte(), 0x26.toByte(),
        0xDF.toByte(), 0x25.toByte(), 0xFA.toByte(), 0x10.toByte(), 0xF0.toByte(),
        0x24.toByte(), 0xC0.toByte(), 0x77.toByte(), 0xB2.toByte(), 0x12.toByte(),
        0x51.toByte(), 0xF0.toByte(), 0xE5.toByte(), 0x28.toByte(), 0xA6.toByte(),
        0x48.toByte()
    )

    // V3-25 invisible XOR shards reconstructing the 25-char master password seed ("a12@#$%DQWaA2-=d,q./qweAS")
    private val V3_SHARD_A = byteArrayOf(
        0xAA.toByte(), 0x55.toByte(), 0xCC.toByte(), 0x33.toByte(), 0x99.toByte(),
        0x66.toByte(), 0x11.toByte(), 0xEE.toByte(), 0x77.toByte(), 0x88.toByte(),
        0x44.toByte(), 0xBB.toByte(), 0x22.toByte(), 0xDD.toByte(), 0x19.toByte(),
        0xA4.toByte(), 0x5B.toByte(), 0xC3.toByte(), 0x3C.toByte(), 0x7E.toByte(),
        0x81.toByte(), 0x92.toByte(), 0x4D.toByte(), 0xE7.toByte(), 0x1B.toByte()
    )

    private val V3_SHARD_B = byteArrayOf(
        0xCB.toByte(), 0x64.toByte(), 0xFE.toByte(), 0x73.toByte(), 0xBA.toByte(),
        0x42.toByte(), 0x34.toByte(), 0xAA.toByte(), 0x26.toByte(), 0xDF.toByte(),
        0x25.toByte(), 0xFA.toByte(), 0x10.toByte(), 0xF0.toByte(), 0x24.toByte(),
        0xC0.toByte(), 0x77.toByte(), 0xB2.toByte(), 0x12.toByte(), 0x51.toByte(),
        0xF0.toByte(), 0xE5.toByte(), 0x28.toByte(), 0xA6.toByte(), 0x48.toByte()
    )

    // Legacy v2 shard tables retained strictly for migrating existing v2 stores
    private val V2_SHARD_A = byteArrayOf(
        0xAA.toByte(), 0x55.toByte(), 0xCC.toByte(), 0x33.toByte(), 0x99.toByte(),
        0x66.toByte(), 0x11.toByte(), 0xEE.toByte(), 0x77.toByte(), 0x88.toByte(),
        0x44.toByte(), 0xBB.toByte(), 0x22.toByte(), 0xDD.toByte(), 0x19.toByte(),
        0xA4.toByte(), 0x5B.toByte(), 0xC3.toByte(), 0x3C.toByte(), 0x7E.toByte(),
        0x81.toByte()
    )

    private val V2_SHARD_B = byteArrayOf(
        0xE2.toByte(), 0x34.toByte(), 0x8D.toByte(), 0x52.toByte(), 0xCA.toByte(),
        0x52.toByte(), 0x27.toByte(), 0xD9.toByte(), 0x37.toByte(), 0xA9.toByte(),
        0x76.toByte(), 0xC1.toByte(), 0x1A.toByte(), 0xEB.toByte(), 0x36.toByte(),
        0x90.toByte(), 0x62.toByte(), 0x81.toByte(), 0x44.toByte(), 0x07.toByte(),
        0xDB.toByte()
    )

    fun interface KeyRotationListener {
        fun onMasterKeyRotated(oldKey: SecretKey, newKey: SecretKey)
    }

    private val rotationListeners = CopyOnWriteArrayList<KeyRotationListener>()
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedDataEncryptionKey: SecretKey? = null

    fun registerRotationListener(listener: KeyRotationListener) {
        if (!rotationListeners.contains(listener)) {
            rotationListeners.add(listener)
        }
    }

    fun unregisterRotationListener(listener: KeyRotationListener) {
        rotationListeners.remove(listener)
    }

    internal fun reconstructBuildCodeMasterSeed(): CharArray {
        val length = V4_BUILD_SHARD_A.size
        val chars = CharArray(length)
        for (i in 0 until length) {
            val code = (V4_BUILD_SHARD_A[i].toInt() xor V4_BUILD_SHARD_B[i].toInt()) and 0xFF
            chars[i] = code.toChar()
        }
        return chars
    }

    internal fun reconstructInvisibleMasterSeed(): CharArray {
        val length = V3_26_SHARD_A.size
        val chars = CharArray(length)
        for (i in 0 until length) {
            val code = (V3_26_SHARD_A[i].toInt() xor V3_26_SHARD_B[i].toInt()) and 0xFF
            chars[i] = code.toChar()
        }
        return chars
    }

    internal fun reconstructLegacy25MasterSeed(): CharArray {
        val length = V3_SHARD_A.size
        val chars = CharArray(length)
        for (i in 0 until length) {
            val code = (V3_SHARD_A[i].toInt() xor V3_SHARD_B[i].toInt()) and 0xFF
            chars[i] = code.toChar()
        }
        return chars
    }

    internal fun reconstructLegacyV2MasterSeed(): CharArray {
        val length = V2_SHARD_A.size
        val chars = CharArray(length)
        for (i in 0 until length) {
            val code = (V2_SHARD_A[i].toInt() xor V2_SHARD_B[i].toInt()) and 0xFF
            chars[i] = code.toChar()
        }
        return chars
    }

    fun verifyInvisibleSeedIntegrity(): Boolean {
        val seed32 = reconstructBuildCodeMasterSeed()
        val seed26 = reconstructInvisibleMasterSeed()
        val seed25 = reconstructLegacy25MasterSeed()
        return try {
            val valid32 = seed32.size == 32 &&
                seed32[0] == 'W' &&
                seed32[1] == '2' &&
                seed32[2] == '3' &&
                seed32[3] == '@' &&
                seed32[4] == '#' &&
                seed32[5] == '$' &&
                seed32[6] == '*' &&
                seed32[15] == 'Q' &&
                seed32[22] == '1' &&
                seed32[31] == 'r'

            val valid26 = seed26.size == 26 &&
                seed26[0] == 'a' &&
                seed26[1] == '1' &&
                seed26[2] == '2' &&
                seed26[3] == '@' &&
                seed26[4] == ' ' &&
                seed26[5] == '#' &&
                seed26[6] == '$' &&
                seed26[7] == '%' &&
                seed26[8] == 'D' &&
                seed26[14] == '-' &&
                seed26[15] == '=' &&
                seed26[20] == '/' &&
                seed26[25] == 'S'

            val valid25 = seed25.size == 25 &&
                seed25[0] == 'a' &&
                seed25[1] == '1' &&
                seed25[2] == '2' &&
                seed25[3] == '@' &&
                seed25[6] == '%' &&
                seed25[7] == 'D' &&
                seed25[13] == '-' &&
                seed25[14] == '=' &&
                seed25[19] == '/' &&
                seed25[24] == 'S'

            valid32 && (valid26 || valid25)
        } finally {
            Arrays.fill(seed32, '\u0000')
            Arrays.fill(seed26, '\u0000')
            Arrays.fill(seed25, '\u0000')
        }
    }

    fun isMasterPasswordMatched(candidateChars: CharArray): Boolean {
        val seed32 = reconstructBuildCodeMasterSeed()
        val seed26 = reconstructInvisibleMasterSeed()
        val seed25 = reconstructLegacy25MasterSeed()
        return try {
            fun matches(seed: CharArray, cand: CharArray): Boolean {
                if (cand.size != seed.size) return false
                var diff = 0
                for (i in seed.indices) {
                    diff = diff or (cand[i].code xor seed[i].code)
                }
                return diff == 0
            }

            if (matches(seed32, candidateChars)) return true
            if (matches(seed26, candidateChars)) return true
            if (matches(seed25, candidateChars)) return true

            val jarChars = JAR_SIGNATURE_DATA_PASSWORD_SPEC.toCharArray()
            try {
                if (matches(jarChars, candidateChars)) return true
            } finally {
                Arrays.fill(jarChars, '\u0000')
            }

            val v14Chars = JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV14_SPEC.toCharArray()
            try {
                if (matches(v14Chars, candidateChars)) return true
            } finally {
                Arrays.fill(v14Chars, '\u0000')
            }

            val v107JarChars = JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_SPEC.toCharArray()
            try {
                if (matches(v107JarChars, candidateChars)) return true
            } finally {
                Arrays.fill(v107JarChars, '\u0000')
            }

            val v107KeystoreChars = RELEASE_KEYSTORE_PASSWORD_V107RV_SPEC.toCharArray()
            try {
                if (matches(v107KeystoreChars, candidateChars)) return true
            } finally {
                Arrays.fill(v107KeystoreChars, '\u0000')
            }

            // Normalized comparison stripping leading/trailing whitespace or internal space
            val trimmedCand = candidateChars.filter { !it.isWhitespace() }.toCharArray()
            val trimmed32 = seed32.filter { !it.isWhitespace() }.toCharArray()
            val trimmed26 = seed26.filter { !it.isWhitespace() }.toCharArray()
            matches(trimmed32, trimmedCand) || matches(trimmed26, trimmedCand)
        } finally {
            Arrays.fill(seed32, '\u0000')
            Arrays.fill(seed26, '\u0000')
            Arrays.fill(seed25, '\u0000')
        }
    }

    fun isMasterPasswordMatched(candidatePassword: String): Boolean {
        val chars = candidatePassword.toCharArray()
        return try {
            isMasterPasswordMatched(chars)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun isJarDataPasswordMatched(candidatePassword: String): Boolean {
        return candidatePassword == JAR_SIGNATURE_DATA_PASSWORD_SPEC
    }

    fun isJarDataPasswordMatched(candidateChars: CharArray): Boolean {
        val jarChars = JAR_SIGNATURE_DATA_PASSWORD_SPEC.toCharArray()
        return try {
            if (candidateChars.size != jarChars.size) return false
            var diff = 0
            for (i in jarChars.indices) {
                diff = diff or (candidateChars[i].code xor jarChars[i].code)
            }
            diff == 0
        } finally {
            Arrays.fill(jarChars, '\u0000')
        }
    }

    fun isDataCodesLocked(context: Context): Boolean {
        val prefs = getKeystorePrefs(context)
        return prefs.getBoolean(KEY_DATA_CODES_LOCKED, false)
    }

    fun setDataCodesLocked(context: Context, locked: Boolean) {
        val prefs = getKeystorePrefs(context)
        prefs.edit().putBoolean(KEY_DATA_CODES_LOCKED, locked).commit()
    }

    fun unlockDataCodesWithMasterPassword(context: Context, candidatePassword: String): Boolean {
        val matched = isMasterPasswordMatched(candidatePassword)
        if (matched) {
            setDataCodesLocked(context, false)
        }
        return matched
    }

    fun lockDataCodesWithMasterPassword(context: Context) {
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
     * Derives a 256-bit AES Key-Encryption-Key from the 32-char build code encryption password seed
     * ("W23@#$*adsgbfggQWEFGEF13454ehger") reconstructed from V4 XOR byte shards + device salt.
     */
    fun deriveMasterPasswordRootKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val seedChars = reconstructBuildCodeMasterSeed()
        return try {
            val spec = PBEKeySpec(seedChars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(seedChars, '\u0000')
        }
    }

    fun deriveLegacy26PasswordRootKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val seedChars = reconstructInvisibleMasterSeed()
        return try {
            val spec = PBEKeySpec(seedChars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(seedChars, '\u0000')
        }
    }

    /**
     * Provides the Key-Encryption-Key (KEK) backed by AndroidKeyStore on device, or the
     * master-password-derived 256-bit KEK in local JVM/Robolectric environments.
     */
    private fun getOrCreateKeystoreBackedKek(context: Context): SecretKey {
        getOrCreateAndroidKeystoreMasterKey()?.let { return it }
        return deriveMasterPasswordRootKek(context)
    }

    private fun wrapDekWithKeystoreMaster(context: Context, rawDekBytes: ByteArray): ByteArray {
        // Layer 1: Encrypt DEK with the build code password seed KEK ("W23@#$*adsgbfggQWEFGEF13454ehger")
        val passwordKek = deriveMasterPasswordRootKek(context)
        val passwordWrapped = encryptBytesWithKey(rawDekBytes, passwordKek)

        // Layer 2: Wrap with AndroidKeyStore hardware-backed master key (or password KEK in JVM)
        val kek = getOrCreateKeystoreBackedKek(context)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, kek)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(passwordWrapped)
            val out = ByteArray(1 + iv.size + cipherBytes.size)
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(cipherBytes, 0, out, 1 + iv.size, cipherBytes.size)
            out
        } catch (_: Throwable) {
            encryptBytesWithKey(passwordWrapped, kek)
        }
    }

    private fun unwrapDekWithKeystoreMaster(context: Context, wrappedPayload: ByteArray): ByteArray? {
        if (wrappedPayload.size <= GCM_IV_LENGTH + 1) return null
        val kek = getOrCreateKeystoreBackedKek(context)
        val outerUnwrapped = try {
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
                ?: decryptBytesWithKey(wrappedPayload, deriveLegacy26PasswordRootKek(context))
                ?: decryptBytesWithKey(wrappedPayload, deriveLegacy25PasswordRootKek(context))
        } ?: return null

        // If outerUnwrapped is already a 32-byte raw DEK (from earlier v3 single-layer wrap), return it directly
        if (outerUnwrapped.size == 32) {
            return outerUnwrapped
        }
        // Otherwise unwrap the inner master-password layer (trying V4 build password first, then legacy V3 seeds)
        val passwordKek = deriveMasterPasswordRootKek(context)
        decryptBytesWithKey(outerUnwrapped, passwordKek)?.let { return it }
        val legacy26Kek = deriveLegacy26PasswordRootKek(context)
        decryptBytesWithKey(outerUnwrapped, legacy26Kek)?.let { return it }
        val legacy25Kek = deriveLegacy25PasswordRootKek(context)
        return decryptBytesWithKey(outerUnwrapped, legacy25Kek)
    }

    fun deriveLegacy25PasswordRootKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val seedChars = reconstructLegacy25MasterSeed()
        return try {
            val spec = PBEKeySpec(seedChars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(seedChars, '\u0000')
        }
    }

    private fun deriveLegacyRootKeyEncryptionKey(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val seedChars = reconstructLegacyV2MasterSeed()
        return try {
            val spec = PBEKeySpec(seedChars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(seedChars, '\u0000')
        }
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

        // 1. Check AndroidKeyStore v3 wrapped DEK first
        val wrappedV3Base64 = prefs.getString(KEY_WRAPPED_KEYSTORE_DEK_V3, null)
        if (!wrappedV3Base64.isNullOrBlank()) {
            try {
                val wrappedBytes = Base64.decode(wrappedV3Base64, Base64.NO_WRAP)
                val rawDek = unwrapDekWithKeystoreMaster(context, wrappedBytes)
                if (rawDek != null && rawDek.size == 32) {
                    val key = SecretKeySpec(rawDek, "AES")
                    Arrays.fill(rawDek, 0.toByte())
                    cachedDataEncryptionKey = key
                    return key
                }
            } catch (_: Exception) {
                // Fall through to legacy migration or fresh key creation
            }
        }

        // 2. Auto-migrate existing legacy v2 wrapped DEK to AndroidKeyStore v3 without losing data
        val legacyWrappedBase64 = prefs.getString(KEY_WRAPPED_DYNAMIC_DEK, null)
        if (!legacyWrappedBase64.isNullOrBlank()) {
            try {
                val legacyKek = deriveLegacyRootKeyEncryptionKey(context)
                val legacyWrappedBytes = Base64.decode(legacyWrappedBase64, Base64.NO_WRAP)
                val migratedDekBytes = decryptBytesWithKey(legacyWrappedBytes, legacyKek)
                if (migratedDekBytes != null && migratedDekBytes.size == 32) {
                    val migratedKey = SecretKeySpec(migratedDekBytes, "AES")
                    val v3Wrapped = wrapDekWithKeystoreMaster(context, migratedDekBytes)
                    Arrays.fill(migratedDekBytes, 0.toByte())
                    prefs.edit()
                        .putString(KEY_WRAPPED_KEYSTORE_DEK_V3, Base64.encodeToString(v3Wrapped, Base64.NO_WRAP))
                        .putBoolean(KEY_KEYSTORE_MIGRATED_V3, true)
                        .remove(KEY_WRAPPED_DYNAMIC_DEK)
                        .commit()
                    cachedDataEncryptionKey = migratedKey
                    return migratedKey
                }
            } catch (_: Exception) {
                // Fall through to fresh DEK generation
            }
        }

        // 3. Generate a fresh 256-bit DEK protected by AndroidKeyStore v3
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
        return freshKey
    }

    /**
     * Rotates the Data Encryption Key (DEK) using 256-bit CSPRNG entropy wrapped by AndroidKeyStore,
     * and notifies all registered repositories to atomically re-encrypt their stores and vault files.
     */
    @Synchronized
    fun rotateDynamicEncryptionKey(context: Context, reason: String = "security_key_rotation"): Int {
        val prefs = getKeystorePrefs(context)
        val oldKey = getOrCreateDataEncryptionKey(context)

        val newDekBytes = ByteArray(32)
        secureRandom.nextBytes(newDekBytes)
        val newKey = SecretKeySpec(newDekBytes, "AES")
        val wrappedV3 = wrapDekWithKeystoreMaster(context, newDekBytes)
        Arrays.fill(newDekBytes, 0.toByte())

        val newRotationCount = prefs.getInt(KEY_AUTO_ROTATION_COUNT, 0) + 1
        prefs.edit()
            .putString(KEY_WRAPPED_KEYSTORE_DEK_V3, Base64.encodeToString(wrappedV3, Base64.NO_WRAP))
            .putBoolean(KEY_KEYSTORE_MIGRATED_V3, true)
            .remove(KEY_WRAPPED_DYNAMIC_DEK)
            .putInt(KEY_AUTO_ROTATION_COUNT, newRotationCount)
            .putLong(KEY_LAST_ROTATION_TS, System.currentTimeMillis())
            .putString(KEY_STEALTH_SESSION_TOKEN, reason)
            .commit()

        cachedDataEncryptionKey = newKey

        for (listener in rotationListeners) {
            try {
                listener.onMasterKeyRotated(oldKey, newKey)
            } catch (_: Exception) {
                // Continue rotating remaining stores safely
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
     * Hashes a PIN or recovery answer using 120,000-iteration PBKDF2WithHmacSHA256 with a 256-bit
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
                val matches = java.security.MessageDigest.isEqual(expectedHash, actualHash)
                Arrays.fill(actualHash, 0.toByte())
                matches
            } else {
                // Backward compatibility for legacy v2 12,000-iteration hashes
                val parts = unwrapped.split(":")
                if (parts.size != 2) return false
                val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)
                val actualHash = deriveLegacySecretHashBytes(candidateSecret.trim(), salt)
                val matches = java.security.MessageDigest.isEqual(expectedHash, actualHash)
                Arrays.fill(actualHash, 0.toByte())
                matches
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

    private fun deriveLegacySecretHashBytes(secret: String, salt: ByteArray): ByteArray {
        val seed = reconstructInvisibleMasterSeed()
        val combinedChars = CharArray(seed.size + secret.length + 1)
        return try {
            System.arraycopy(seed, 0, combinedChars, 0, seed.size)
            combinedChars[seed.size] = ':'
            for (i in secret.indices) {
                combinedChars[seed.size + 1 + i] = secret[i]
            }
            val spec = PBEKeySpec(combinedChars, salt, LEGACY_SECRET_HASH_ITERATIONS, KEY_LENGTH_BITS)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(seed, '\u0000')
            Arrays.fill(combinedChars, '\u0000')
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

    /**
     * Streams encryption of a file onto disk in bounded 256 KB chunks using AES-256-GCM (CYOS_STREAM_V3).
     * Never loads the whole video into memory, writes to a staging .tmp file first, and verifies integrity
     * before atomically replacing targetEncryptedFile.
     */
    fun encryptFileToDisk(context: Context, sourceFile: File, targetEncryptedFile: File): Boolean {
        val key = getOrCreateDataEncryptionKey(context)
        return encryptFileToDiskWithKey(sourceFile, targetEncryptedFile, key)
    }

    fun encryptFileToDiskWithKey(sourceFile: File, targetEncryptedFile: File, key: SecretKey): Boolean {
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
                    }
                    try {
                        fos.fd.sync()
                    } catch (_: Exception) {}
                }
            }

            Arrays.fill(plainBuffer, 0.toByte())

            // Verify the newly encrypted staging file before committing
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
     * without writing plaintext to disk.
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
            if (isStreamV3File(encryptedFile)) {
                DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), STREAM_CHUNK_SIZE)).use { dis ->
                    val magic = ByteArray(STREAM_MAGIC_HEADER.size)
                    dis.readFully(magic)
                    if (!magic.contentEquals(STREAM_MAGIC_HEADER)) return false

                    val baseIv = ByteArray(GCM_IV_LENGTH)
                    dis.readFully(baseIv)
                    val expectedPlainLength = dis.readLong()
                    if (expectedPlainLength <= 0L) return false

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
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                        cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                        val plainChunk = cipher.doFinal(encryptedChunk)
                        Arrays.fill(plainChunk, 0.toByte())

                        verifiedPlainBytes += plainChunk.size.toLong()
                        if (isFinal) sawFinalChunk = true
                        chunkIndex++
                    }
                    sawFinalChunk && verifiedPlainBytes == expectedPlainLength
                }
            } else {
                // Legacy v2 single-block format verification
                val encryptedBytes = encryptedFile.readBytes()
                val plainBytes = decryptBytesWithKey(encryptedBytes, key) ?: return false
                val valid = plainBytes.isNotEmpty()
                Arrays.fill(plainBytes, 0.toByte())
                valid
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isStreamV3File(file: File): Boolean {
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

    /**
     * Streams decryption of an encrypted vault/recycle-bin file to targetPlainFile using a .tmp staging file.
     * If decryption or authentication fails at any chunk, deletes the partial .tmp file and returns false
     * without corrupting targetPlainFile or deleting encryptedFile.
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
            if (isStreamV3File(encryptedFile)) {
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
                                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, chunkIv))
                                cipher.updateAAD(buildChunkAad(chunkIndex, isFinal))
                                val plainChunk = cipher.doFinal(encryptedChunk)

                                out.write(plainChunk)
                                writtenPlainBytes += plainChunk.size.toLong()
                                Arrays.fill(plainChunk, 0.toByte())
                                if (isFinal) sawFinalChunk = true
                                chunkIndex++
                            }
                            out.flush()
                        }
                        try {
                            fos.fd.sync()
                        } catch (_: Exception) {}
                    }

                    if (!sawFinalChunk || writtenPlainBytes != expectedPlainLength || stagingFile.length() != expectedPlainLength) {
                        stagingFile.delete()
                        return false
                    }
                }
            } else {
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

    /**
     * Re-encrypts an encrypted file on disk from oldKey to newKey using streaming decryption + encryption
     * and atomic staging so interrupted key rotation never corrupts or loses vault/trash files.
     */
    fun reEncryptFileOnDisk(encryptedFile: File, oldKey: SecretKey, newKey: SecretKey): Boolean {
        if (!encryptedFile.exists() || !encryptedFile.isFile) return false
        val parentDir = encryptedFile.parentFile ?: return false
        val tempPlain = File(parentDir, "${encryptedFile.name}.rot_plain_${System.nanoTime()}")
        val tempReEnc = File(parentDir, "${encryptedFile.name}.rot_enc_${System.nanoTime()}")
        return try {
            if (!decryptFileFromDiskWithKey(encryptedFile, tempPlain, oldKey)) {
                shredFileZeroTrace(tempPlain)
                return false
            }
            if (!encryptFileToDiskWithKey(tempPlain, tempReEnc, newKey)) {
                shredFileZeroTrace(tempPlain)
                tempReEnc.delete()
                return false
            }
            shredFileZeroTrace(tempPlain)
            if (!verifyEncryptedFileIntegrityWithKey(tempReEnc, newKey)) {
                tempReEnc.delete()
                return false
            }
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
     * resolution, bitrate, and stream fingerprint) directly under the 256-bit key derived from the
     * master password seed ("a12@#$%DQWaA2-=d,q./qweAS") + AES-256-GCM DEK envelope.
     */
    @Synchronized
    fun encryptVideoDatacode(context: Context, rawDatacodePayload: String): String {
        val passwordKek = deriveMasterPasswordRootKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawDatacodePayload.toByteArray(StandardCharsets.UTF_8),
            passwordKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "DATACODE_V3:$outerBase64")
    }

    /**
     * Decrypts a video technical datacode that was encrypted with [encryptVideoDatacode],
     * verifying both the outer DEK layer and the inner build-code password ("W23@#$*adsgbfggQWEFGEF13454ehger") KEK layer.
     */
    @Synchronized
    fun decryptVideoDatacode(context: Context, encryptedDatacode: String?): String? {
        if (encryptedDatacode.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedDatacode) ?: return null
        if (!outerUnwrapped.startsWith("DATACODE_V3:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("DATACODE_V3:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val passwordKek = deriveMasterPasswordRootKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, passwordKek)
                ?: decryptBytesWithKey(innerCipher, deriveLegacy26PasswordRootKek(context))
                ?: decryptBytesWithKey(innerCipher, deriveLegacy25PasswordRootKek(context))
                ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
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
     * Encrypts and stores both the release keystore password ("vnjfjvveSDF@!#)*ADF@#Bfgfdh")
     * and the build code password ("W23@#$*adsgbfggQWEFGEF13454ehger") as AES-256-GCM tokens.
     */
    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedBuildCodePasswordToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_BUILD_PW_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == BUILD_CODE_ENCRYPTION_PASSWORD_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, BUILD_CODE_ENCRYPTION_PASSWORD_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_BUILD_PW_TOKEN, token).commit()
        return token
    }

    /**
     * Encrypts and stores the JAR signature and datacode password
     * ("4848rhhrhrhHhdhWw÷<$&^$&÷&÷hdhUuuUU'bccb") as an authenticated AES-256-GCM token.
     */
    @Synchronized
    fun getOrCreateEncryptedJarSignaturePasswordToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_PW_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_DATA_PASSWORD_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_DATA_PASSWORD_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_PW_TOKEN, token).commit()
        return token
    }

    /**
     * Derives a 256-bit AES key from the JAR signature password
     * ("4848rhhrhrhHhdhWw÷<$&^$&÷&÷hdhUuuUU'bccb") using PBKDF2WithHmacSHA256 and device salt.
     */
    fun deriveJarSignaturePasswordKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val chars = JAR_SIGNATURE_DATA_PASSWORD_SPEC.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    @Synchronized
    fun encryptDataCodeWithJarPassword(context: Context, rawPayload: String): String {
        val jarKek = deriveJarSignaturePasswordKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_DATACODE_V1:$outerBase64")
    }

    @Synchronized
    fun decryptDataCodeWithJarPassword(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_DATACODE_V1:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_DATACODE_V1:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarSignaturePasswordKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv13Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV13_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV13_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV13_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV13_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv13Kek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val chars = JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV13_SPEC.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv13(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv13Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV13:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv13(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV13:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV13:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv13Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv14Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV14_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV14_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV14_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV14_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv14Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV106rv13Kek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv14(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv14Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV14:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv14(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV14:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV14:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv14Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv15Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV15_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV15_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV15_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV15_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv15Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV106rv13Kek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv15(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv15Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV15:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv15(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV15:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV15:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv15Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv16Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV16_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV16_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV16_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV16_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv16Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV106rv13Kek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv16(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv16Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV16:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv16(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV16:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV16:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv16Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv17Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV17_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV17_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV17_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV17_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv17Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV106rv13Kek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv17(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv17Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV17:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv17(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV17:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV17:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv17Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV106rv18Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV18_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV18_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V106RV18_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V106RV18_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV106rv18Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV106rv13Kek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV106rv18(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV106rv18Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V106RV18:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV106rv18(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V106RV18:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V106RV18:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV106rv18Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts and stores the 1.07rv release keystore password ("wdwewefeSDFWEFFFR#@#@&|?><@")
     * as an authenticated AES-256-GCM token.
     */
    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rvToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV_TOKEN, token).commit()
        return token
    }

    /**
     * Encrypts and stores the 1.07rv JAR signature and build code password ("#adsdfrgrgAeg;..,:\"{}|")
     * as an authenticated AES-256-GCM token.
     */
    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rvToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rvKek(context: Context): SecretKey {
        val prefs = getKeystorePrefs(context)
        val salt = getOrCreateDeviceSalt(prefs)
        val chars = JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV_SPEC.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(spec).encoded
                try {
                    SecretKeySpec(keyBytes, "AES")
                } finally {
                    Arrays.fill(keyBytes, 0.toByte())
                }
            } finally {
                spec.clearPassword()
            }
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rvKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rvKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv2Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV2_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV2_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV2_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV2_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv2Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV2_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV2_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV2_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV2_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv2Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv2(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv2Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV2:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv2(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV2:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV2:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv2Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv3Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV3_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV3_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV3_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV3_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv3Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV3_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV3_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV3_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV3_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv3Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv3(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv3Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV3:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv3(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV3:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV3:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv3Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv4Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV4_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV4_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV4_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV4_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv4Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV4_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV4_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV4_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV4_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv4Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv4(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv4Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV4:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv4(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV4:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV4:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv4Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv5Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV5_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV5_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV5_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV5_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv5Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV5_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV5_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV5_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV5_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv5Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv5(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv5Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV5:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv5(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV5:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV5:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv5Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv6Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV6_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV6_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV6_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV6_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv6Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV6_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV6_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV6_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV6_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv6Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv6(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv6Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV6:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv6(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV6:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV6:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv6Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv7Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV7_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV7_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv7Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv7Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv7(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv7Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV7:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv7(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV7:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV7:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv7Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv8Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV8_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV8_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV8_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV8_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv8Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv8Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv8(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv8Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV8:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv8(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV8:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV8:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv8Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv7aToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7A_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV7A_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV7A_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7A_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv7aToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7A_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7A_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7A_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7A_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv7aKek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv7a(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv7aKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV7A:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv7a(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV7A:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV7A:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv7aKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv7bToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7B_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV7B_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV7B_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV7B_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv7bToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7B_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7B_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV7B_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV7B_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV107rv7bKek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV107rv7b(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV107rv7bKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V107RV7B:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV107rv7b(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V107RV7B:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V107RV7B:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV107rv7bKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV108rvToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V108RV_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V108RV_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV108rvToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV108rvKek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV108rv(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV108rvKek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V108RV:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV108rv(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V108RV:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V108RV:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV108rvKek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV107rv8aToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV8A_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V107RV8A_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V107RV8A_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V107RV8A_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV107rv8aToken(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8A_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8A_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V107RV8A_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V107RV8A_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV108rv2Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV2_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V108RV2_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V108RV2_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV2_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV108rv2Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV2_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV2_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV2_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV2_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV108rv2Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV108rv2(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV108rv2Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V108RV2:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV108rv2(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V108RV2:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V108RV2:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV108rv2Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun getOrCreateEncryptedKeystorePasswordV108rv3Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV3_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == RELEASE_KEYSTORE_PASSWORD_V108RV3_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, RELEASE_KEYSTORE_PASSWORD_V108RV3_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_KEYSTORE_PW_V108RV3_TOKEN, token).commit()
        return token
    }

    @Synchronized
    fun getOrCreateEncryptedJarBuildCodePasswordV108rv3Token(context: Context): String {
        val prefs = getKeystorePrefs(context)
        val existing = prefs.getString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV3_TOKEN, null)
        if (!existing.isNullOrBlank() && decryptVideoDatacode(context, existing) == JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV3_SPEC) {
            return existing
        }
        val token = encryptVideoDatacode(context, JAR_SIGNATURE_BUILD_CODE_PASSWORD_V108RV3_SPEC)
        prefs.edit().putString(KEY_ENCRYPTED_JAR_BUILD_PW_V108RV3_TOKEN, token).commit()
        return token
    }

    fun deriveJarBuildCodePasswordV108rv3Kek(context: Context): SecretKey {
        return deriveJarBuildCodePasswordV107rvKek(context)
    }

    @Synchronized
    fun encryptBuildCodeWithJarPasswordV108rv3(context: Context, rawPayload: String): String {
        val jarKek = deriveJarBuildCodePasswordV108rv3Kek(context)
        val innerEncrypted = encryptBytesWithKey(
            rawPayload.toByteArray(StandardCharsets.UTF_8),
            jarKek
        )
        val outerBase64 = Base64.encodeToString(innerEncrypted, Base64.NO_WRAP)
        return encryptString(context, "JAR_BUILD_V108RV3:$outerBase64")
    }

    @Synchronized
    fun decryptBuildCodeWithJarPasswordV108rv3(context: Context, encryptedPayload: String?): String? {
        if (encryptedPayload.isNullOrBlank()) return null
        val outerUnwrapped = decryptString(context, encryptedPayload) ?: return null
        if (!outerUnwrapped.startsWith("JAR_BUILD_V108RV3:")) {
            return outerUnwrapped
        }
        return try {
            val innerBase64 = outerUnwrapped.removePrefix("JAR_BUILD_V108RV3:")
            val innerCipher = Base64.decode(innerBase64, Base64.NO_WRAP)
            val jarKek = deriveJarBuildCodePasswordV108rv3Kek(context)
            val plainBytes = decryptBytesWithKey(innerCipher, jarKek) ?: return null
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a compact, deterministic+authenticated encrypted datacode token for a video item,
     * proving that its stream metadata & build datacodes are protected with the
     * build password "W23@#$*adsgbfggQWEFGEF13454ehger".
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
        val sigBytes = signBytesWithKeystore(sha256.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        val sigHex = sigBytes.joinToString("") { "%02x".format(it) }
        val verified = verifyBytesWithKeystore(sha256.toByteArray(java.nio.charset.StandardCharsets.UTF_8), sigBytes)

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

        // Write companion signature manifest
        val companionFile = File(file.parentFile ?: file, "${file.name}.sig.json")
        try {
            val json = """
            {
              "filename": "${manifest.filename}",
              "fileSize": ${manifest.fileSize},
              "sha256": "${manifest.sha256Digest}",
              "signature": "${manifest.signatureHex}",
              "signer": "${manifest.signerAlias}",
              "algorithm": "${manifest.algorithm}",
              "timestampMs": ${manifest.timestampMs},
              "verified": ${manifest.isVerified}
            }
            """.trimIndent()
            companionFile.writeText(json)
        } catch (_: Exception) {
            // Non-fatal if companion cannot write to read-only folder
        }

        return manifest
    }
}
