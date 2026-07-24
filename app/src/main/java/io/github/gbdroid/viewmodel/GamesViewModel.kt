
package io.github.gbdroid.viewmodel

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.gbdroid.GBdroidApplication
import io.github.gbdroid.core.Core
import io.github.gbdroid.model.GameModel
import io.github.gbdroid.utils.GameCacheManager
import io.github.gbdroid.utils.IconMetadataHelper.getIconUrl
import io.github.gbdroid.utils.SearchLocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GamesViewModel : ViewModel() {
    private val _gameList = MutableStateFlow<List<GameModel>>(emptyList())
    val gameList: StateFlow<List<GameModel>> = _gameList
    var isLoading: Boolean = false

    init {
        loadGamesFromDisk()
    }

    fun checkUpdatedList() {
        loadGamesFromDisk(_gameList.value.isNotEmpty())
    }

    fun loadGamesFromDisk(passive: Boolean = false) {
        val gameFolders = SearchLocationHelper.getGameFolders()
        if (gameFolders.isNotEmpty()) {
            val persistedUris = GBdroidApplication.context.contentResolver.persistedUriPermissions.map { it.uri }
            val validGameFolders = gameFolders.filter { persistedUris.contains(it) }
            if (validGameFolders.isNotEmpty()) {
                loadGames(validGameFolders, clearExisting = true, passive)
            } else {
                _gameList.value = emptyList()
            }
        } else {
            _gameList.value = emptyList()
        }
    }

    fun loadGames(gameFolders: List<Uri>, clearExisting: Boolean, passive: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isLoading) return@launch
            isLoading = true
            val newlyFoundGames = mutableListOf<GameModel>()

            for (treeUri in gameFolders) {
                val documentFile = DocumentFile.fromTreeUri(GBdroidApplication.context, treeUri)

                if (documentFile != null && documentFile.isDirectory) {
                    documentFile.listFiles().forEach { file ->
                        val fileName = file.name

                        if (fileName != null && (
                                    fileName.endsWith(".gba", true) ||
                                            fileName.endsWith(".gbc", true) ||
                                            fileName.endsWith(".gb", true) ||
                                            fileName.endsWith(".zip", true))
                        ) {
                            val cachedGame = GameCacheManager.getGame(file.uri, fileName)
                            newlyFoundGames.add(
                                cachedGame ?: GameModel(
                                    uri = file.uri,
                                    fileName = fileName,
                                    title = null,
                                    version = null
                                )
                            )
                        }
                    }
                }
            }

            val sortedList = newlyFoundGames.sortedBy { it.fileName.lowercase() }.toMutableList()

            if (passive) {
                val metadataChanged = processMetadata(sortedList)
                val currentUris = _gameList.value.map { it.uri.toString() }
                val newUris = sortedList.map { it.uri.toString() }

                if (metadataChanged || currentUris != newUris) {
                    withContext(Dispatchers.Main) {
                        _gameList.value = sortedList
                    }
                }

                withContext(Dispatchers.Main) {
                    isLoading = false
                }
                return@launch
            } else {
                withContext(Dispatchers.Main) {
                    if (clearExisting) {
                        _gameList.value = emptyList()
                    }
                    _gameList.value = sortedList
                }
            }

            loadGamesMetadata()
        }
    }

    private fun processMetadata(workingList: MutableList<GameModel>): Boolean {
        var listStructureChanged = false
        val iterator = workingList.iterator()

        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element.title == null) {
                if (!Core.init()) continue
                if (!Core.loadRom(element.uri)) {
                    iterator.remove()
                    listStructureChanged = true
                    continue
                }

                element.title = Core.gameTitle()
                element.code = Core.gameCode()
                element.version = Core.gameVersion
                element.iconUrl = getIconUrl(element.title ?: "")
                GameCacheManager.saveGame(element)
                listStructureChanged = true
                Core.reset()
            }
        }
        return listStructureChanged
    }

    private suspend fun loadGamesMetadata() {
        val currentList = _gameList.value.toList()

        for (element in currentList) {
            if (element.title == null) {
                if (!Core.init()) continue
                if (!Core.loadRom(element.uri)) {
                    withContext(Dispatchers.Main) {
                        _gameList.value = _gameList.value.filter { it.uri != element.uri }
                    }
                    continue
                }

                val gameTitle = Core.gameTitle()
                val gameCode = Core.gameCode()
                val iconUrl = getIconUrl(gameTitle)

                val updatedGame = element.copy(
                    title = gameTitle,
                    code = gameCode,
                    version = Core.gameVersion,
                    iconUrl = iconUrl
                )

                GameCacheManager.saveGame(updatedGame)
                Core.reset()

                withContext(Dispatchers.Main) {
                    _gameList.value = _gameList.value.map {
                        if (it.uri == updatedGame.uri) updatedGame else it
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            isLoading = false
        }
    }
}
