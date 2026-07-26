
package io.github.gbdroid.utils

import io.github.gbdroid.core.PLATFORM
import java.net.URLEncoder

object IconMetadataHelper {
    fun getIconUrl(gameTitle: String?, platform: PLATFORM): String? {
        if (gameTitle.isNullOrBlank()) return null

        val systemFolder = when (platform) {
            PLATFORM.GB -> "Nintendo - Game Boy"
            PLATFORM.GBA -> "Nintendo - Game Boy Advance"
            else -> "Nintendo - Game Boy Color" // I assume it to be gbc as mgba does not give us any specific value for gbc
        }

        val encodedSystemFolder = URLEncoder.encode(systemFolder, "UTF-8").replace("+", "%20")
        val sanitizedTitle = gameTitle.replace(Regex("[&*/:`<>?|\\\\\"]"), "_")
        val encodedTitle = URLEncoder.encode(sanitizedTitle, "UTF-8").replace("+", "%20")
        return "https://thumbnails.libretro.com/$encodedSystemFolder/Named_Boxarts/$encodedTitle.png"
    }
}