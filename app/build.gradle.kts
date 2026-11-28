plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "com.cyosplay.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyosplay.player"
        minSdk = 24
        targetSdk = 36
        versionCode = 51
        versionName = "1.08rv9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAJOR_CAPABILITY_ID", "\"cyos play\"")
    }

    signingConfigs {
        create("release") {
            fun resolveReleaseSecret(name: String): String? {
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

            val releaseKeystorePath = resolveReleaseSecret("CYOSPLAY_RELEASE_KEYSTORE_PATH")
                ?: "cyosplay-release.keystore"
            val releaseKeyAlias = resolveReleaseSecret("CYOSPLAY_RELEASE_KEY_ALIAS")
                ?: "cyosplay_release"
            val releaseStorePass = resolveReleaseSecret("CYOSPLAY_RELEASE_STORE_PASSWORD")
            val releaseKeyPass = resolveReleaseSecret("CYOSPLAY_RELEASE_KEY_PASSWORD")
                ?: releaseStorePass
            val releaseKeystore = rootProject.file(releaseKeystorePath)

            if (releaseKeystore.exists() && !releaseStorePass.isNullOrBlank() && !releaseKeyPass.isNullOrBlank()) {
                storeFile = releaseKeystore
                storePassword = releaseStorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
                enableV1Signing = true
                enableV2Signing = true
            } else {
                storeFile = rootProject.file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.effect)
    // implementation(libs.coil.compose) // Unused: thumbnail decoding uses ContentResolver.loadThumbnail + Semaphore(2)
    implementation(libs.androidx.biometric)
    // implementation(libs.androidx.security.crypto) // Unused: app uses native AndroidKeyStore AES-256-GCM in CryptoSecurityManager
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.oss.licenses)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register("cleanLegacyRootApks") {
    group = "cleanup"
    description = "Deletes legacy root-directory release APK/ZIP artifacts from previous versions."
    val rootDirRef = rootProject.projectDir
    doLast {
        rootDirRef.listFiles()
            ?.filter { file ->
                file.isFile && (
                    file.name.endsWith(".apk", ignoreCase = true) ||
                        file.name.endsWith("-release-signed-apk.zip", ignoreCase = true)
                    )
            }
            ?.forEach { it.delete() }
    }
}

apply(from = rootProject.file("gradle/verify-release-security.gradle.kts"))

