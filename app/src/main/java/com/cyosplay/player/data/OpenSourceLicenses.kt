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

    private const val FFMPEG_LGPL_2_1_NOTICE: String =
        "This application uses software from the FFmpeg project (via Media3 FFmpeg decoder extensions) licensed under the GNU Lesser General Public License, " +
            "version 2.1 or later (LGPL v2.1+). You may obtain a copy of the LGPL v2.1 license at https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html. " +
            "FFmpeg is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE."

    val thirdPartyNotices: List<OpenSourceLicenseNotice> = listOf(
        OpenSourceLicenseNotice(
            id = "androidx_media3_exoplayer",
            libraryName = "AndroidX Media3 ExoPlayer",
            version = "1.5.1",
            licenseType = "Apache 2.0",
            copyright = "Copyright (C) The Android Open Source Project",
            summary = "Hardware-accelerated video/audio playback, container extractors, caching, preloading, and MediaSession support.",
            licenseText = APACHE_2_NOTICE
        ),
        OpenSourceLicenseNotice(
            id = "ffmpeg",
            libraryName = "FFmpeg",
            version = "LGPL v2.1+",
            licenseType = "LGPL v2.1+",
            copyright = "Copyright (c) The FFmpeg Developers",
            summary = "Audio and video codec decoding support for legacy and high-bitrate media formats.",
            licenseText = FFMPEG_LGPL_2_1_NOTICE
        )
    )
}
