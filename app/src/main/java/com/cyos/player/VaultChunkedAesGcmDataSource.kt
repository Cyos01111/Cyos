package com.cyos.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.cyosplay.player.util.CryptoSecurityManager
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.math.min

/**
 * Zero-plaintext seekable Media3 [DataSource] for Private Vault V4 Envelope & legacy V3 chunked AES-256-GCM files.
 *
 * Security & Performance guarantees:
 * - Strictly enforces canonical path containment inside `filesDir/private_encrypted_vault/` to
 *   prevent directory traversal or symlink escapes from crafted `cyosvault://` URIs.
 * - Reuses a single [Cipher], single encrypted read buffer, and single 256 KB plaintext buffer
 *   per open stream to eliminate per-chunk heap churn.
 * - Immediately zeroes the plaintext buffer whenever another chunk is loaded or [close] is called.
 * - Zero plaintext ever touches flash storage.
 */
@OptIn(UnstableApi::class)
class VaultChunkedAesGcmDataSource(
    context: Context
) : BaseDataSource(/* isNetwork = */ false) {

    private val appContext: Context = context.applicationContext ?: context

    private var activeUri: Uri? = null
    private var raf: RandomAccessFile? = null
    private var header: CryptoSecurityManager.StreamV3Header? = null
    private var activeFileKey: SecretKey? = null
    private var totalChunks: Long = 0L

    private var currentPlainPosition: Long = 0L
    private var bytesRemainingInSpec: Long = 0L

    private var cachedChunkIndex: Long = -1L
    private var cachedPlainLength: Int = 0
    private var reusableCipher: Cipher? = null
    private var reusableEncryptedBuf: ByteArray? = null
    private var reusablePlainBuf: ByteArray? = null
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (!isVaultStreamUri(uri)) {
            throw IOException("Unsupported URI scheme for VaultChunkedAesGcmDataSource: ${uri.scheme}")
        }
        val encryptedPath = resolveEncryptedFilePath(uri)
            ?: throw IOException("Missing encrypted vault file path in URI")
        val encryptedFile = File(encryptedPath)
        if (!isPathContainedInPrivateVault(appContext, encryptedFile)) {
            throw IOException("Access denied: Vault path is outside private_encrypted_vault directory")
        }
        if (!encryptedFile.exists() || !encryptedFile.isFile) {
            throw IOException("Encrypted Vault file does not exist")
        }

        val streamHeader = CryptoSecurityManager.readStreamHeader(encryptedFile)
            ?: throw IOException("Invalid or unsupported encrypted Vault file header")
        val resolvedFileKey = CryptoSecurityManager.resolveStreamFileKey(appContext, streamHeader)
            ?: throw IOException("Failed to authenticate Vault file encryption key")

        val totalPlainLength = streamHeader.totalPlainLength
        if (dataSpec.position < 0L || dataSpec.position > totalPlainLength) {
            throw IOException("Invalid seek position ${dataSpec.position} for Vault length $totalPlainLength")
        }

        transferInitializing(dataSpec)

        activeUri = uri
        header = streamHeader
        activeFileKey = resolvedFileKey
        totalChunks = ((totalPlainLength + streamHeader.chunkPlainSize - 1L) / streamHeader.chunkPlainSize).coerceAtLeast(1L)
        raf = RandomAccessFile(encryptedFile, "r")
        currentPlainPosition = dataSpec.position
        bytesRemainingInSpec = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, totalPlainLength - currentPlainPosition)
        } else {
            totalPlainLength - currentPlainPosition
        }
        if (reusableCipher == null) {
            reusableCipher = Cipher.getInstance("AES/GCM/NoPadding")
        }
        val requiredEncCap = streamHeader.chunkPlainSize + 16 + 64
        if (reusableEncryptedBuf == null || reusableEncryptedBuf!!.size < requiredEncCap) {
            reusableEncryptedBuf = ByteArray(requiredEncCap)
        }
        val requiredPlainCap = streamHeader.chunkPlainSize + 64
        if (reusablePlainBuf == null || reusablePlainBuf!!.size < requiredPlainCap) {
            reusablePlainBuf = ByteArray(requiredPlainCap)
        }
        cachedChunkIndex = -1L
        cachedPlainLength = 0
        opened = true
        transferStarted(dataSpec)
        return bytesRemainingInSpec
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemainingInSpec <= 0L) return C.RESULT_END_OF_INPUT

        val v3Header = header ?: throw IOException("Vault stream header unavailable")
        val chunkPlainSize = v3Header.chunkPlainSize
        val targetChunkIndex = currentPlainPosition / chunkPlainSize
        val offsetWithinChunk = (currentPlainPosition % chunkPlainSize).toInt()

        val plainLen = loadChunkIntoReusableRam(v3Header, targetChunkIndex)
        val plainBuf = reusablePlainBuf ?: throw IOException("Vault plaintext buffer unavailable")
        if (offsetWithinChunk >= plainLen) {
            return C.RESULT_END_OF_INPUT
        }

        val availableInChunk = plainLen - offsetWithinChunk
        val toCopy = min(
            min(length.toLong(), bytesRemainingInSpec),
            availableInChunk.toLong()
        ).toInt()

        if (toCopy <= 0) return C.RESULT_END_OF_INPUT

        System.arraycopy(plainBuf, offsetWithinChunk, buffer, offset, toCopy)
        currentPlainPosition += toCopy.toLong()
        bytesRemainingInSpec -= toCopy.toLong()
        bytesTransferred(toCopy)
        return toCopy
    }

    private fun loadChunkIntoReusableRam(
        v3Header: CryptoSecurityManager.StreamV3Header,
        chunkIndex: Long
    ): Int {
        if (cachedChunkIndex == chunkIndex && cachedPlainLength > 0) {
            return cachedPlainLength
        }

        val plainBuf = reusablePlainBuf ?: throw IOException("Vault plaintext buffer uninitialized")
        val encBuf = reusableEncryptedBuf ?: throw IOException("Vault encrypted buffer uninitialized")
        val cipher = reusableCipher ?: Cipher.getInstance("AES/GCM/NoPadding").also { reusableCipher = it }

        // Zero previous plaintext bytes immediately before decrypting the next chunk
        if (cachedPlainLength > 0) {
            Arrays.fill(plainBuf, 0, cachedPlainLength, 0.toByte())
            cachedPlainLength = 0
            cachedChunkIndex = -1L
        }

        if (chunkIndex < 0L || chunkIndex >= totalChunks) {
            throw EOFException("Vault chunk index $chunkIndex out of bounds ($totalChunks)")
        }

        val file = raf ?: throw IOException("Vault RandomAccessFile closed")
        val diskOffset = v3Header.headerSizeBytes + chunkIndex * v3Header.fullChunkRecordBytes
        file.seek(diskOffset)

        val encryptedChunkSize = file.readInt()
        val maxExpectedEncSize = v3Header.chunkPlainSize + 16 + 64
        if (encryptedChunkSize <= 16 || encryptedChunkSize > maxExpectedEncSize) {
            throw IOException("Corrupt Vault encrypted chunk length: $encryptedChunkSize")
        }

        file.readFully(encBuf, 0, encryptedChunkSize)

        val chunkKey = activeFileKey ?: throw IOException("Vault stream key unavailable")
        val isFinal = chunkIndex == (totalChunks - 1L)
        val decryptedLen = try {
            CryptoSecurityManager.decryptStreamChunkIntoWithKey(
                chunkKey = chunkKey,
                reusableCipher = cipher,
                baseIv = v3Header.baseIv,
                chunkIndex = chunkIndex,
                isFinal = isFinal,
                encryptedChunk = encBuf,
                encryptedLength = encryptedChunkSize,
                outputPlainBuffer = plainBuf
            )
        } catch (e: Exception) {
            Arrays.fill(plainBuf, 0.toByte())
            throw IOException("Vault chunk $chunkIndex AES-GCM authentication failed", e)
        }

        cachedPlainLength = decryptedLen
        cachedChunkIndex = chunkIndex
        return decryptedLen
    }

    override fun getUri(): Uri? = activeUri

    override fun close() {
        reusablePlainBuf?.let { Arrays.fill(it, 0.toByte()) }
        cachedPlainLength = 0
        cachedChunkIndex = -1L
        header = null
        activeFileKey = null
        totalChunks = 0L
        currentPlainPosition = 0L
        bytesRemainingInSpec = 0L
        activeUri = null

        try {
            raf?.close()
        } catch (_: IOException) {
            // Ignore close errors
        } finally {
            raf = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(context: Context) : DataSource.Factory {
        private val appContext = context.applicationContext ?: context
        override fun createDataSource(): DataSource = VaultChunkedAesGcmDataSource(appContext)
    }

    companion object {
        const val SCHEME = "cyosvault"
        const val PRIVATE_VAULT_DIR_NAME = "private_encrypted_vault"
        private const val HOST_STREAM = "stream"
        private const val QUERY_PATH = "path"

        fun isVaultStreamUri(uri: Uri?): Boolean {
            return uri != null && SCHEME.equals(uri.scheme, ignoreCase = true)
        }

        /**
         * Verifies that [candidateFile] resolves canonically inside the app's private encrypted
         * vault directory (`filesDir/private_encrypted_vault/`), blocking `..` traversal and symlinks.
         */
        fun isPathContainedInPrivateVault(context: Context, candidateFile: File): Boolean {
            return try {
                val appCtx = context.applicationContext ?: context
                val vaultDir = File(appCtx.filesDir, PRIVATE_VAULT_DIR_NAME)
                val vaultCanonical = vaultDir.canonicalPath
                val targetCanonical = candidateFile.canonicalPath
                val prefix = if (vaultCanonical.endsWith(File.separator)) {
                    vaultCanonical
                } else {
                    "$vaultCanonical${File.separator}"
                }
                targetCanonical.startsWith(prefix)
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Builds a `cyosvault://stream/<displayName>?path=<encryptedFilePath>` URI so Media3's
         * container extractor sniffer sees the video file extension while routing bytes strictly
         * through [VaultChunkedAesGcmDataSource].
         */
        fun buildVaultUri(encryptedFilePath: String, displayFileName: String): Uri {
            val safeName = displayFileName.trim().ifBlank { "vault_video.mp4" }
            return Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_STREAM)
                .appendPath(safeName)
                .appendQueryParameter(QUERY_PATH, encryptedFilePath)
                .build()
        }

        fun resolveEncryptedFilePath(uri: Uri?): String? {
            if (!isVaultStreamUri(uri)) return null
            return uri?.getQueryParameter(QUERY_PATH)?.takeIf { it.isNotBlank() }
        }
    }
}
