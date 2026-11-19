plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cyosplay.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyosplay.player"
        minSdk = 24
        targetSdk = 36
        versionCode = 38
        versionName = "1.07rv6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAJOR_CAPABILITY_ID", "\"cyos play\"")
    }

    signingConfigs {
        create("release") {
            val releaseKeystore = rootProject.file("cyosplay-release.keystore")
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = "wdwewefeSDFWEFFFR#@#@&|?><@"
                keyAlias = "cyosplay_release"
                keyPassword = "wdwewefeSDFWEFFFR#@#@&|?><@"
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
    implementation(libs.coil.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)

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

afterEvaluate {
    listOf(
        "CyosPlay-v1.07rv5-release-signed.apk",
        "cyosplay-1.07rv5-release.apk",
        "CyosPlay-v1.07rv4-release-signed.apk",
        "cyosplay-1.07rv4-release.apk",
        "CyosPlay-v1.07rv3-release-signed.apk",
        "cyosplay-1.07rv3-release.apk",
        "CyosPlay-v1.07rv2-release-signed.apk",
        "cyosplay-1.07rv2-release.apk",
        "CyosPlay-v1.07rv-release-signed.apk",
        "cyosplay-1.07rv-release.apk",
        "CyosPlay-v1.06rv18-release-signed.apk",
        "cyosplay-1.06rv18-release.apk",
        "CyosPlay-v1.06rv17-release-signed.apk",
        "cyosplay-1.06rv17-release.apk",
        "CyosPlay-v1.06rv16-release-signed.apk",
        "cyosplay-1.06rv16-release.apk",
        "CyosPlay-v1.06rv15-release-signed.apk",
        "cyosplay-1.06rv15-release.apk",
        "CyosPlay-v1.06rv14-release-signed.apk",
        "cyosplay-1.06rv14-release.apk",
        "app/cyosplay_release.jks",
        "CyosPlay-v1.06rv13-release-signed.apk",
        "cyosplay-1.06rv13-release.apk",
        "CyosPlay-v1.06rv12-release-signed.apk",
        "cyosplay-1.06rv12-release.apk",
        "CyosPlay-v1.06rv11-release-signed.apk",
        "cyosplay-1.06rv11-release.apk",
        "CyosPlay-v1.06rv10-release-signed.apk",
        "cyosplay-1.06rv10-release.apk",
        "CyosPlay-v1.06rv9-release-signed.apk",
        "cyosplay-1.06rv9-release.apk",
        "CyosPlay-v1.06rv8-release-signed.apk",
        "cyosplay-1.06rv8-release.apk",
        "CyosPlay-v1.06rv7-release-signed.apk",
        "cyosplay-1.06rv7-release.apk",
        "CyosPlay-v1.06rv6-release-signed.apk",
        "cyosplay-1.06rv6-release.apk",
        "CyosPlay-v1.06rv5-release-signed.apk",
        "cyosplay-1.06rv5-release.apk",
        "CyosPlay-v1.06rv4-release-signed.apk",
        "cyosplay-1.06rv4-release.apk",
        "CyosPlay-v1.06rv3-release-signed.apk",
        "cyosplay-1.06rv3-release.apk",
        "CyosPlay-v1.06rv2-release-signed.apk",
        "cyosplay-1.06rv2-release.apk",
        "CyosPlay-v1.06rv-release-signed.apk",
        "cyosplay-1.06rv-release.apk",
        "CyosPlay-v1.05rv4-release-signed.apk",
        "cyosplay-1.05rv4-release.apk",
        "CyosPlay-v1.05rv3-release-signed.apk",
        "cyosplay-1.05rv3-release.apk",
        "CyosPlay-v1.05rv2-release-signed.apk",
        "cyosplay-1.05rv2-release.apk",
        "CyosPlay-v1.05rv-release-signed.apk",
        "cyosplay-1.05rv-release.apk",
        "CyosPlay-v1.04rv3-release-signed.apk",
        "cyosplay-1.04rv3-release.apk",
        "CyosPlay-v1.04rv2-release-signed.apk",
        "cyosplay-1.04rv2-release.apk",
        "CyosPlay-v1.04rv-release-signed.apk",
        "cyosplay-1.04rv-release.apk",
        "CyosPlay-v1.04mfr-release-signed.apk",
        "cyosplay-1.04mfr-release.apk",
        "CyosPlay-v1.03-release-signed.apk",
        "cyosplay-1.03-release.apk",
        "cyosplay-1.02c-release.apk"
    ).forEach { oldApk ->
        val file = rootProject.file(oldApk)
        if (file.exists()) {
            file.delete()
        }
    }
    tasks.findByName("assembleRelease")?.doLast {
        val builtApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (builtApk.exists()) {
            builtApk.copyTo(rootProject.file("CyosPlay-v1.07rv6-release-signed.apk"), overwrite = true)
            builtApk.copyTo(rootProject.file("cyosplay-1.07rv6-release.apk"), overwrite = true)
        }
    }
}

