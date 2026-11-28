import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException

val verifyGitHubPrivacy = tasks.register("verifyGitHubPrivacy") {
    group = "verification"
    description = "Verifies that all keystores, secrets, encrypted blobs, and exported APKs are hidden from GitHub and no passwords leak in publishable files."

    val projectRootDir = rootProject.projectDir
    doLast {
        val gitIgnoreFile = File(projectRootDir, ".gitignore")
        if (!gitIgnoreFile.isFile) {
            throw GradleException("GitHub privacy check failed: .gitignore is missing.")
        }
        val gitIgnoreLines = gitIgnoreFile.readLines(StandardCharsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val requiredIgnoreRules = listOf(
            ".env",
            ".env.*",
            "*.keystore",
            "*.keystore.*",
            "*.jks",
            "*.p12",
            "*.pem",
            "*.key",
            "debug.keystore",
            "debug.keystore.base64",
            "*.enc",
            "*.gpg",
            "*.cyosvault",
            "export/",
            "*.apk",
            "*.zip",
            "*.sha256"
        )
        val missingIgnoreRules = requiredIgnoreRules.filterNot { it in gitIgnoreLines }
        if (missingIgnoreRules.isNotEmpty()) {
            throw GradleException(
                "GitHub privacy check failed: .gitignore is missing mandatory exclusion rules: $missingIgnoreRules"
            )
        }
        val forbiddenUnignoreRules = listOf("!debug.keystore", "!debug.keystore.base64", "!export/")
        val exposedUnignores = forbiddenUnignoreRules.filter { it in gitIgnoreLines }
        if (exposedUnignores.isNotEmpty()) {
            throw GradleException(
                "GitHub privacy check failed: .gitignore must not un-ignore sensitive/binary artifacts: $exposedUnignores"
            )
        }

        val envExampleFile = File(projectRootDir, ".env.example")
        if (envExampleFile.isFile) {
            envExampleFile.readLines(StandardCharsets.UTF_8).forEachIndexed { idx, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val eqIndex = line.indexOf('=')
                if (eqIndex <= 0) {
                    throw GradleException("GitHub privacy check failed: malformed line ${idx + 1} in .env.example.")
                }
                val key = line.substring(0, eqIndex).trim()
                val value = line.substring(eqIndex + 1).trim()
                if (key == "CYOSPLAY_BUILD_CODE_PASSWORD") {
                    throw GradleException("GitHub privacy check failed: retired encrypted envelope password variable found in .env.example.")
                }
                if (value.isNotEmpty()) {
                    throw GradleException(
                        "GitHub privacy check failed: .env.example line ${idx + 1} ($key) must keep an empty value."
                    )
                }
            }
        }

        val skippedDirs = setOf(
            ".git",
            ".gradle",
            "build",
            ".build-outputs",
            "export",
            ".aistudio",
            ".kotlin",
            ".idea"
        )
        val forbiddenArtifactExtensions = setOf("enc", "gpg", "asc", "cyosvault", "p12", "pfx", "pk8", "jks")
        val textExtensions = setOf(
            "kt", "kts", "java", "xml", "json", "properties", "sh", "md", "yml", "yaml", "txt", "pro", "toml", "example"
        )

        val pemPrivateKeyMarker = "-----BEGIN " + "PRIVATE KEY-----"
        val rsaPrivateKeyMarker = "-----BEGIN RSA " + "PRIVATE KEY-----"
        val ecPrivateKeyMarker = "-----BEGIN EC " + "PRIVATE KEY-----"
        val encEnvelopeMarker = "CYOS" + "ENC1:"
        val retiredEnvVar = "CYOSPLAY_BUILD_CODE_" + "PASSWORD"
        val nonEmptyReleaseSecretRegex = Regex(
            """(?m)^[ \t]*CYOSPLAY_RELEASE_(?:STORE|KEY)_PASSWORD[ \t]*=[ \t]*[^\r\n\s#]+"""
        )
        val leakedTokenRegex = Regex(
            """(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z\-_]{35}|sk_live_[0-9A-Za-z]{20,})"""
        )

        val violations = mutableListOf<String>()

        fun scanDirectory(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name in skippedDirs) continue
                    scanDirectory(child)
                } else if (child.isFile) {
                    val relPath = child.relativeTo(projectRootDir).path
                    val ext = child.extension.lowercase()
                    if (ext in forbiddenArtifactExtensions) {
                        violations += "Encrypted/private artifact present in publishable tree: $relPath"
                        continue
                    }
                    if (child.name == ".env" || (child.name.startsWith(".env.") && child.name != ".env.example")) {
                        continue // Already ignored by .gitignore
                    }
                    if (child.name == "debug.keystore" || child.name == "debug.keystore.base64" || ext == "keystore") {
                        continue // Explicitly ignored by .gitignore
                    }
                    if (relPath == "gradle/verify-release-security.gradle.kts" || relPath == ".githooks/pre-commit") {
                        continue // Guard definitions themselves
                    }
                    if (ext !in textExtensions && child.name != ".gitignore" && child.name != ".env.example") {
                        continue
                    }
                    if (child.length() > 2 * 1024 * 1024L) continue

                    val content = child.readText(StandardCharsets.UTF_8)
                    if (content.contains(pemPrivateKeyMarker) ||
                        content.contains(rsaPrivateKeyMarker) ||
                        content.contains(ecPrivateKeyMarker)
                    ) {
                        violations += "Private key material detected in $relPath"
                    }
                    if (content.contains(encEnvelopeMarker)) {
                        violations += "Encrypted password envelope detected in $relPath"
                    }
                    if (content.contains(retiredEnvVar)) {
                        violations += "Retired encrypted envelope password variable detected in $relPath"
                    }
                    if (nonEmptyReleaseSecretRegex.containsMatchIn(content)) {
                        violations += "Non-empty release keystore password assignment detected in $relPath"
                    }
                    if (leakedTokenRegex.containsMatchIn(content)) {
                        violations += "Potential hardcoded credential token detected in $relPath"
                    }
                }
            }
        }

        scanDirectory(projectRootDir)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "GitHub privacy check FAILED with ${violations.size} violation(s):\n - " +
                    violations.joinToString("\n - ")
            )
        }
        logger.lifecycle("CyosPlay GitHub privacy check PASSED: zero leaked passwords, keystores, or encrypted blobs in publishable files.")
    }
}

val verifyReleaseSignatureAndEncryption = tasks.register("verifyReleaseSignatureAndEncryption") {
    group = "verification"
    description = "Verifies release APK JAR/APK signatures without emitting encrypted password envelopes, and exports CyosPlay release APK locally to export/."
    dependsOn(verifyGitHubPrivacy)

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

        val releaseKeystorePath = resolveSecret("CYOSPLAY_RELEASE_KEYSTORE_PATH") ?: "cyosplay-release.keystore"
        val releaseKeyAlias = resolveSecret("CYOSPLAY_RELEASE_KEY_ALIAS") ?: "cyosplay_release"
        val releaseKeystorePassword = resolveSecret("CYOSPLAY_RELEASE_STORE_PASSWORD")
        val releaseKeyPassword = resolveSecret("CYOSPLAY_RELEASE_KEY_PASSWORD") ?: releaseKeystorePassword

        val builtApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        val legacyEnvelopeFile = layout.buildDirectory.file("outputs/apk/release/release-integrity.enc").get().asFile
        if (legacyEnvelopeFile.exists()) {
            legacyEnvelopeFile.delete()
        }

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
            listOf(exportedApk, exportedSha256File, exportedZip, legacyEnvelopeFile).forEach { file ->
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

                    jarFile.getInputStream(entry).use { input ->
                        while (input.read(buffer) > 0) {
                            // Stream entire entry so JarFile verifies digest signatures
                        }
                    }

                    if (name in requiredSignedEntries) {
                        val certs = entry.certificates
                        if (certs.isNullOrEmpty()) {
                            throw GradleException("APK entry '$name' has no verified JAR signature certificates.")
                        }
                        val entryFingerprints = certs.map { sha256Hex(it.encoded) }
                        if (expectedCertSha256 !in entryFingerprints) {
                            throw GradleException("APK entry '$name' certificate fingerprint does not match '$activeKeyAlias' keystore.")
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

            // 3. Export verified release APK, SHA-256 digest, and local ZIP archive into GitHub-ignored export/
            exportDir.mkdirs()
            builtApk.copyTo(exportedApk, overwrite = true)
            if (sha256HexFile(exportedApk) != apkSha256) {
                throw GradleException("Exported APK at ${exportedApk.path} failed SHA-256 integrity verification.")
            }
            exportedSha256File.writeText("$apkSha256  ${exportedApk.name}\n", StandardCharsets.UTF_8)

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
                    "exported locally to ${exportedApk.relativeTo(rootProject.projectDir).path} (${exportedApk.length()} bytes) " +
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
    tasks.findByName("check")?.dependsOn(verifyGitHubPrivacy)
    tasks.findByName("assembleRelease")?.finalizedBy(verifyReleaseSignatureAndEncryption)
}
