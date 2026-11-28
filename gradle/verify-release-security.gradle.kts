import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.jar.JarFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.GradleException

val verifyReleaseSignatureAndEncryption = tasks.register("verifyReleaseSignatureAndEncryption") {
    group = "verification"
    description = "Verifies release APK JAR/APK signatures, optionally seals AES-256-GCM integrity metadata when Secrets are configured, and exports CyosPlay-1.08rv9-release.apk to export/."

    doLast {
        fun resolveSecret(name: String): String? {
            val envValue = System.getenv(name)?.trim()
            if (!envValue.isNullOrEmpty() && !envValue.startsWith("CHANGE_ME_")) {
                return envValue
            }
            val propValue = (project.findProperty(name) as? String)?.trim()
            if (!propValue.isNullOrEmpty() && !propValue.startsWith("CHANGE_ME_")) {
                return propValue
            }
            val dotEnvFile = rootProject.file(".env")
            if (dotEnvFile.isFile) {
                val line = dotEnvFile.readLines()
                    .asSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it.startsWith("$name=") }
                if (line != null) {
                    val raw = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                    if (raw.isNotEmpty() && !raw.startsWith("CHANGE_ME_")) {
                        return raw
                    }
                }
            }
            return null
        }

        val androidExt = project.extensions.findByName("android")
        val defaultConfigObj = androidExt?.let { ext ->
            runCatching { ext.javaClass.getMethod("getDefaultConfig").invoke(ext) }.getOrNull()
        }
        val releaseVersionName = defaultConfigObj?.let { cfg ->
            runCatching { cfg.javaClass.getMethod("getVersionName").invoke(cfg) as? String }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: "1.08rv9"
        val releaseVersionCode = defaultConfigObj?.let { cfg ->
            runCatching { (cfg.javaClass.getMethod("getVersionCode").invoke(cfg) as? Number)?.toInt() }.getOrNull()
        } ?: 51
        val releaseKeystorePath = resolveSecret("CYOSPLAY_RELEASE_KEYSTORE_PATH") ?: "cyosplay-release.keystore"
        val releaseKeyAlias = resolveSecret("CYOSPLAY_RELEASE_KEY_ALIAS") ?: "cyosplay_release"
        val releaseKeystorePassword = resolveSecret("CYOSPLAY_RELEASE_STORE_PASSWORD")
        val releaseKeyPassword = resolveSecret("CYOSPLAY_RELEASE_KEY_PASSWORD") ?: releaseKeystorePassword
        val buildCodeEncryptionPassword = resolveSecret("CYOSPLAY_BUILD_CODE_PASSWORD")

        val envelopeHeader = "CYOSENC1"
        val envelopeAad = "CYOSPLAY_RELEASE_ENVELOPE_V1".toByteArray(StandardCharsets.UTF_8)

        val builtApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        val integrityEnvelopeFile = layout.buildDirectory.file("outputs/apk/release/release-integrity.enc").get().asFile
        val exportDir = rootProject.file("export")
        val exportedApk = File(exportDir, "CyosPlay-$releaseVersionName-release.apk")
        val exportedSha256File = File(exportDir, "CyosPlay-$releaseVersionName-release.apk.sha256")
        val exportedZip = File(exportDir, "CyosPlay-$releaseVersionName-release-apk.zip")
        val releaseKeystoreFile = rootProject.file(releaseKeystorePath)

        val useCustomReleaseKeystore = releaseKeystoreFile.isFile &&
            releaseKeystoreFile.length() > 0L &&
            !releaseKeystorePassword.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

        fun purgeUnverifiedArtifacts() {
            listOf(exportedApk, exportedSha256File, exportedZip, integrityEnvelopeFile).forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun sha256HexFile(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val encoded = factory.generateSecret(spec).encoded
            spec.clearPassword()
            return SecretKeySpec(encoded, "AES")
        }

        fun encryptEnvelope(plaintext: String, password: String): String {
            val rng = SecureRandom()
            val salt = ByteArray(16).also { rng.nextBytes(it) }
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val key = deriveAesKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(envelopeAad)
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val b64 = Base64.getEncoder()
            return "$envelopeHeader:${b64.encodeToString(salt)}:${b64.encodeToString(iv)}:${b64.encodeToString(cipherBytes)}"
        }

        fun decryptEnvelope(encodedEnvelope: String, password: String): String {
            val parts = encodedEnvelope.trim().split(":")
            if (parts.size != 4 || parts[0] != envelopeHeader) {
                throw GradleException("Malformed encrypted release integrity envelope header.")
            }
            val b64 = Base64.getDecoder()
            val salt = b64.decode(parts[1])
            val iv = b64.decode(parts[2])
            val cipherBytes = b64.decode(parts[3])
            val key = deriveAesKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(envelopeAad)
            val plainBytes = cipher.doFinal(cipherBytes)
            return String(plainBytes, StandardCharsets.UTF_8)
        }

        try {
            if (!builtApk.exists() || builtApk.length() == 0L) {
                throw GradleException("Release APK missing at ${builtApk.absolutePath}.")
            }

            // 1. Strict release keystore guardrail: by default NEVER allow a debug-signed APK to pass release verification.
            //    An explicit -PallowDebugSignedReleaseExport=true opt-in allows verified debug-signed local testing exports.
            val allowDebugSignedTestingExport =
                (project.findProperty("allowDebugSignedReleaseExport") as? String)?.equals("true", ignoreCase = true) == true
            if (!useCustomReleaseKeystore && !allowDebugSignedTestingExport) {
                throw GradleException(
                    "Release verification blocked: custom release keystore '${releaseKeystoreFile.name}' and " +
                        "CYOSPLAY_RELEASE_STORE_PASSWORD Secret must be configured before exporting a release APK. " +
                        "(For local testing exports only, pass -PallowDebugSignedReleaseExport=true.)"
                )
            }
            val activeKeystoreFile: File
            val activeKeyAlias: String
            val ksPassChars: CharArray
            val keyPassChars: CharArray
            if (useCustomReleaseKeystore) {
                activeKeystoreFile = releaseKeystoreFile
                activeKeyAlias = releaseKeyAlias
                ksPassChars = releaseKeystorePassword!!.toCharArray()
                keyPassChars = releaseKeyPassword!!.toCharArray()
            } else {
                activeKeystoreFile = rootProject.file("debug.keystore")
                activeKeyAlias = "androiddebugkey"
                ksPassChars = "android".toCharArray()
                keyPassChars = "android".toCharArray()
                if (!activeKeystoreFile.isFile) {
                    throw GradleException("Debug-signed testing export requires workspace debug.keystore at ${activeKeystoreFile.absolutePath}.")
                }
            }
            val expectedCertSha256: String
            try {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                activeKeystoreFile.inputStream().use { keyStore.load(it, ksPassChars) }
                if (!keyStore.containsAlias(activeKeyAlias)) {
                    throw GradleException("Expected signing key alias '$activeKeyAlias' not found in keystore.")
                }
                val privateKey = keyStore.getKey(activeKeyAlias, keyPassChars)
                    ?: throw GradleException("Signing private key '$activeKeyAlias' could not be unlocked.")
                if (privateKey.encoded == null && privateKey.algorithm.isNullOrBlank()) {
                    throw GradleException("Signing private key '$activeKeyAlias' is invalid.")
                }
                val signingCert = keyStore.getCertificate(activeKeyAlias)
                    ?: throw GradleException("Signing X.509 certificate missing for alias '$activeKeyAlias'.")
                expectedCertSha256 = sha256Hex(signingCert.encoded)
            } finally {
                ksPassChars.fill('\u0000')
                keyPassChars.fill('\u0000')
            }

            // 2a. Validate JAR signature via jarsigner -verify
            val jarSignerProcess = ProcessBuilder("jarsigner", "-verify", builtApk.absolutePath)
                .redirectErrorStream(true)
                .start()
            val jarSignerOutput = jarSignerProcess.inputStream.bufferedReader().readText()
            val jarSignerExit = jarSignerProcess.waitFor()
            if (jarSignerExit != 0 || !jarSignerOutput.contains("jar verified", ignoreCase = true)) {
                throw GradleException("JAR signature verification failed (exit=$jarSignerExit): ${jarSignerOutput.trim()}")
            }

            // 2b. Validate signed JarFile entries, META-INF signature files, and certificate consistency/pinning
            var hasManifest = false
            var hasSfFile = false
            var hasSigBlock = false
            val requiredSignedEntries = setOf("classes.dex", "AndroidManifest.xml", "resources.arsc")
            val verifiedRequiredEntries = mutableSetOf<String>()
            var classesDexSha256: String? = null
            var observedCertSha256: String? = null

            JarFile(builtApk, true).use { jarFile ->
                val entries = jarFile.entries()
                val buffer = ByteArray(64 * 1024)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (entry.isDirectory) continue

                    if (name == "META-INF/MANIFEST.MF") hasManifest = true
                    if (name.startsWith("META-INF/") && name.endsWith(".SF")) hasSfFile = true
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".EC") || name.endsWith(".DSA"))) {
                        hasSigBlock = true
                    }

                    val dexDigest = if (name == "classes.dex") MessageDigest.getInstance("SHA-256") else null
                    jarFile.getInputStream(entry).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            dexDigest?.update(buffer, 0, read)
                        }
                    }
                    if (dexDigest != null) {
                        classesDexSha256 = dexDigest.digest().joinToString("") { "%02x".format(it) }
                    }

                    if (name in requiredSignedEntries) {
                        val certs = entry.certificates
                        if (certs.isNullOrEmpty()) {
                            throw GradleException("APK entry '$name' has no verified JAR signature certificates.")
                        }
                        val entryFingerprints = certs.map { sha256Hex(it.encoded) }
                        if (expectedCertSha256 !in entryFingerprints) {
                            throw GradleException("APK entry '$name' certificate fingerprint does not match '$releaseKeyAlias' release keystore.")
                        }
                        observedCertSha256 = expectedCertSha256
                        verifiedRequiredEntries += name
                    }
                }
            }

            if (!hasManifest || !hasSfFile || !hasSigBlock) {
                throw GradleException("APK is missing required JAR signature metadata (MANIFEST.MF=$hasManifest, .SF=$hasSfFile, .RSA/.EC=$hasSigBlock).")
            }
            if (verifiedRequiredEntries != requiredSignedEntries) {
                val missing = requiredSignedEntries - verifiedRequiredEntries
                throw GradleException("Required signed APK entries missing or unverified: $missing")
            }
            val verifiedDexSha256 = classesDexSha256
                ?: throw GradleException("Unable to compute SHA-256 digest for classes.dex.")
            val verifiedCertSha256 = observedCertSha256
                ?: throw GradleException("Unable to determine verified APK signing certificate fingerprint.")

            // 2c. Cross-platform Android SDK apksigner verification (v1 + v2 APK Signature Schemes)
            val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
                ?: System.getenv("ANDROID_HOME")
                ?: "/opt/android/sdk"
            val buildToolsDir = File(sdkRoot, "build-tools")
            val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
            val apkSignerName = if (isWindows) "apksigner.bat" else "apksigner"
            val apkSignerBin = if (buildToolsDir.isDirectory) {
                buildToolsDir.listFiles()
                    ?.sortedBy { it.name }
                    ?.map { File(it, apkSignerName) }
                    ?.lastOrNull { it.isFile && (isWindows || it.canExecute()) }
            } else {
                null
            }
            if (apkSignerBin == null) {
                throw GradleException(
                    "apksigner binary not found under '$buildToolsDir' (ANDROID_SDK_ROOT='$sdkRoot'); " +
                        "refusing to skip APK v2/v3 signature verification."
                )
            }
            val apkSignerProc = ProcessBuilder(apkSignerBin.absolutePath, "verify", builtApk.absolutePath)
                .redirectErrorStream(true)
                .start()
            val apkSignerOut = apkSignerProc.inputStream.bufferedReader().readText()
            val apkSignerExit = apkSignerProc.waitFor()
            if (apkSignerExit != 0) {
                throw GradleException("apksigner verification failed (exit=$apkSignerExit): ${apkSignerOut.trim()}")
            }

            val apkSha256 = sha256HexFile(builtApk)
            val signingMode = if (useCustomReleaseKeystore) "custom-release-keystore" else "standard-signed-export"

            // 3. When CYOSPLAY_BUILD_CODE_PASSWORD secret is configured, seal & round-trip verify AES-256-GCM integrity envelope
            if (!buildCodeEncryptionPassword.isNullOrBlank()) {
                val buildKeyDigest = sha256Hex(buildCodeEncryptionPassword.toByteArray(StandardCharsets.UTF_8))
                val integrityPlaintext = listOf(
                    "VERSION_NAME=$releaseVersionName",
                    "VERSION_CODE=$releaseVersionCode",
                    "SIGNING_MODE=$signingMode",
                    "APK_FILE=${exportedApk.name}",
                    "APK_BYTES=${builtApk.length()}",
                    "APK_SHA256=$apkSha256",
                    "DEX_SHA256=$verifiedDexSha256",
                    "SIGNER_CERT_SHA256=$verifiedCertSha256",
                    "BUILD_KEY_DIGEST=$buildKeyDigest"
                ).joinToString("\n")

                val encryptedEnvelope = encryptEnvelope(integrityPlaintext, buildCodeEncryptionPassword)
                integrityEnvelopeFile.parentFile?.mkdirs()
                integrityEnvelopeFile.writeText(encryptedEnvelope, StandardCharsets.UTF_8)

                val persistedEnvelopeText = integrityEnvelopeFile.readText(StandardCharsets.UTF_8)
                if (persistedEnvelopeText.contains(buildCodeEncryptionPassword) ||
                    (!releaseKeystorePassword.isNullOrBlank() && persistedEnvelopeText.contains(releaseKeystorePassword)) ||
                    persistedEnvelopeText.contains("APK_SHA256=") ||
                    persistedEnvelopeText.contains("SIGNER_CERT_SHA256=")
                ) {
                    throw GradleException("Plaintext secret or unencrypted metadata leak detected in release integrity envelope.")
                }

                val decryptedPlaintext = decryptEnvelope(persistedEnvelopeText, buildCodeEncryptionPassword)
                val decryptedMap = decryptedPlaintext.lineSequence()
                    .mapNotNull { line ->
                        val idx = line.indexOf('=')
                        if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else null
                    }
                    .toMap()

                if (decryptedMap["VERSION_NAME"] != releaseVersionName ||
                    decryptedMap["VERSION_CODE"] != releaseVersionCode.toString() ||
                    decryptedMap["APK_BYTES"] != builtApk.length().toString() ||
                    decryptedMap["APK_SHA256"] != apkSha256 ||
                    decryptedMap["DEX_SHA256"] != verifiedDexSha256 ||
                    decryptedMap["SIGNER_CERT_SHA256"] != verifiedCertSha256 ||
                    decryptedMap["BUILD_KEY_DIGEST"] != buildKeyDigest
                ) {
                    throw GradleException("Decrypted release integrity envelope failed field-by-field digest verification.")
                }

                val wrongKeyRejected = try {
                    decryptEnvelope(persistedEnvelopeText, "${buildCodeEncryptionPassword}_WRONG_KEY")
                    false
                } catch (_: AEADBadTagException) {
                    true
                } catch (_: java.security.GeneralSecurityException) {
                    true
                }
                if (!wrongKeyRejected) {
                    throw GradleException("AES-256-GCM tamper/wrong-key rejection check failed.")
                }
            }

            // 4. Export verified release APK, SHA-256 digest, and explorer-visible ZIP archive into export/
            exportDir.mkdirs()
            builtApk.copyTo(exportedApk, overwrite = true)
            if (sha256HexFile(exportedApk) != apkSha256) {
                throw GradleException("Exported APK at ${exportedApk.path} failed SHA-256 integrity verification.")
            }
            exportedSha256File.writeText("$apkSha256  ${exportedApk.name}\n", StandardCharsets.UTF_8)

            // Package verified Release APK (STORED for zero double-compression) + SHA-256 digest into ZIP
            fun computeCrc32(file: File): Long {
                val crc = CRC32()
                file.inputStream().buffered().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        crc.update(buf, 0, read)
                    }
                }
                return crc.value
            }

            ZipOutputStream(exportedZip.outputStream().buffered()).use { zos ->
                val apkEntry = ZipEntry(exportedApk.name).apply {
                    method = ZipEntry.STORED
                    size = exportedApk.length()
                    compressedSize = exportedApk.length()
                    crc = computeCrc32(exportedApk)
                }
                zos.putNextEntry(apkEntry)
                exportedApk.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()

                val shaEntry = ZipEntry(exportedSha256File.name)
                zos.putNextEntry(shaEntry)
                exportedSha256File.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            // Round-trip verify archived APK bytes inside exportedZip match apkSha256
            val zippedApkSha256 = ZipFile(exportedZip).use { zf ->
                val entry = zf.getEntry(exportedApk.name)
                    ?: throw GradleException("Exported ZIP missing expected entry '${exportedApk.name}'.")
                val md = MessageDigest.getInstance("SHA-256")
                zf.getInputStream(entry).buffered().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        md.update(buf, 0, read)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (zippedApkSha256 != apkSha256) {
                throw GradleException("Exported ZIP entry '${exportedApk.name}' failed SHA-256 integrity verification.")
            }

            logger.lifecycle(
                "CyosPlay $releaseVersionName release export verification PASSED [$signingMode]: " +
                    "JAR & APK signatures verified (cert ${verifiedCertSha256.take(16)}...), " +
                    "exported to ${exportedApk.relativeTo(rootProject.projectDir).path} (${exportedApk.length()} bytes) " +
                    "and ${exportedZip.relativeTo(rootProject.projectDir).path} (${exportedZip.length()} bytes)."
            )
        } catch (failure: Throwable) {
            purgeUnverifiedArtifacts()
            if (failure is GradleException) {
                throw failure
            }
            throw GradleException(
                "CyosPlay release export signature verification FAILED: ${failure.message}",
                failure
            )
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy(verifyReleaseSignatureAndEncryption)
}
