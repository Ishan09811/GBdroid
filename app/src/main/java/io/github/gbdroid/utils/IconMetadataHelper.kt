
package io.github.gbdroid.utils

import android.util.Log
import org.jsoup.Jsoup

object IconMetadataHelper {
    fun getIconUrl(gameTitle: String?): String? {
        if (gameTitle == null) return null

        return try {
            val searchUrl = "https://thegamesdb.net/search.php?name=${gameTitle.replace(" ", "+")}"
            val document = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get()

            val imageElement = document.selectFirst("div.card img.card-img-top")
            imageElement?.attr("src")
        } catch (e: Exception) {
            Log.e("IconMetadataHelper", "Failed to scrape HTML", e)
            null
        }
    }
}