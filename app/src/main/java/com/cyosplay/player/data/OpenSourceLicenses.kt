package com.cyosplay.player.data

data class OpenSourceLicenseNotice(
    val id: String,
    val libraryName: String,
    val version: String,
    val licenseType: String,
    val copyright: String,
    val summary: String,
    val licenseText: String
)

object OpenSourceLicenses {

    const val APP_COPYRIGHT_NOTICE: String = "© 2026 TKm. All rights reserved."

    const val APP_LICENSE_POSTURE: String = "Closed-Source"

    const val APP_PROPRIETARY_NOTICE: String =
        "CyosPlay is proprietary, closed-source software. Unauthorized copying, cloning, or redistribution of CyosPlay is prohibited."

    const val THIRD_PARTY_OSS_TITLE: String = "Third-Party Open Source Licenses"

    const val THIRD_PARTY_OSS_DISCLAIMER: String =
        "Crediting bundled third-party libraries is legally required and does not make CyosPlay itself open source."

    private const val APACHE_2_NOTICE: String =
        "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. " +
            "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0. " +
            "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, " +
            "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied."

    val thirdPartyNotices: List<OpenSourceLicenseNotice> = listOf(
        OpenSourceLicenseNotice(
            id = "androidx_media3_exoplayer",
            libraryName = "AndroidX Media3 ExoPlayer",
            version = "1.5.1",
            licenseType = "Apache 2.0",
            copyright = "Copyright (C) The Android Open Source Project",
            summary = "Hardware-accelerated and software MediaCodec video/audio playback, container extractors, and MediaSession support.",
            licenseText = APACHE_2_NOTICE
        ),
        OpenSourceLicenseNotice(
            id = "androidx_compose_material3",
            libraryName = "AndroidX Jetpack Compose & Material 3",
            version = "Compose BOM",
            licenseType = "Apache 2.0",
            copyright = "Copyright (C) The Android Open Source Project",
            summary = "Declarative UI toolkit, Material Design 3 components, and adaptive layouts.",
            licenseText = APACHE_2_NOTICE
        ),
        OpenSourceLicenseNotice(
            id = "androidx_security_biometric",
            libraryName = "AndroidX Security Crypto & Biometric",
            version = "AndroidX",
            licenseType = "Apache 2.0",
            copyright = "Copyright (C) The Android Open Source Project",
            summary = "Hardware-backed AndroidKeyStore cryptography and BiometricPrompt authentication.",
            licenseText = APACHE_2_NOTICE
        )
    )
}
