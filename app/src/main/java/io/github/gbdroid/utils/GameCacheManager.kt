
package io.github.gbdroid.utils

import android.content.Context
import android.net.Uri
import io.github.gbdroid.model.GameModel
import org.json.JSONObject
import androidx.core.content.edit
import io.github.gbdroid.GBdroidApplication

object GameCacheManager {
    private val prefs = GBdroidApplication.context.getSharedPreferences("games_cache", Context.MODE_PRIVATE)

    fun getGame(uri: Uri, fileName: String): GameModel? {
        val jsonString = prefs.getString(uri.toString(), null) ?: return null

        return try {
            val json = JSONObject(jsonString)
            GameModel(
                uri = uri,
                fileName = fileName,
                title = json.optString("title").takeIf { it.isNotEmpty() },
                version = json.optString("version").takeIf { it.isNotEmpty() },
                iconUrl = json.optString("iconUrl").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveGame(game: GameModel) {
        val json = JSONObject().apply {
            put("title", game.title ?: "")
            put("version", game.version ?: "")
            put("iconUrl", game.iconUrl ?: "")
        }

        prefs.edit { putString(game.uri.toString(), json.toString()) }
    }
}
