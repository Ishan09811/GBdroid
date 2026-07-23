
package io.github.gbdroid.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.github.gbdroid.EmulationActivity
import io.github.gbdroid.adapters.GameAdapter
import io.github.gbdroid.core.Core
import io.github.gbdroid.databinding.FragmentGamesBinding
import io.github.gbdroid.model.GameModel
import io.github.gbdroid.utils.GameCacheManager
import io.github.gbdroid.utils.IconMetadataHelper.getIconUrl
import io.github.gbdroid.utils.SearchLocationHelper
import io.github.gbdroid.utils.applySafePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!

    private val gameList = mutableListOf<GameModel>()
    private lateinit var gameAdapter: GameAdapter

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            if (SearchLocationHelper.isFolderExists(it)) {
                Snackbar.make(
                    binding.root,
                    "Folder already added to library",
                    Snackbar.LENGTH_SHORT
                ).setAnchorView(binding.add).show()
                return@let
            }
            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            SearchLocationHelper.saveFolderUri(it)
            loadGames(listOf(it), clearExisting = false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gameAdapter = GameAdapter(gameList) { clickedGame ->
            launchEmulationActivity(clickedGame.uri)
        }

        binding.gamesList.layoutManager = LinearLayoutManager(requireContext())
        binding.gamesList.adapter = gameAdapter
        binding.gamesList.applySafePadding()

        binding.add.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        val gameFolders = SearchLocationHelper.getGameFolders()
        if (gameFolders.isEmpty()) return
        val persistedUris = requireContext().contentResolver.persistedUriPermissions.map { it.uri }
        val validGameFolders = gameFolders.filter { persistedUris.contains(it) }
        if (validGameFolders.isNotEmpty()) {
            loadGames(validGameFolders, clearExisting = true)
        }
    }

    private fun loadGames(gameFolders: List<Uri>, clearExisting: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val newlyFoundGames = mutableListOf<GameModel>()

            for (treeUri in gameFolders) {
                val documentFile = DocumentFile.fromTreeUri(requireContext(), treeUri)

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
                            if (cachedGame != null) {
                                newlyFoundGames.add(cachedGame)
                            } else {
                                newlyFoundGames.add(
                                    GameModel(
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
            }

            withContext(Dispatchers.Main) {
                if (clearExisting) {
                    gameList.clear()
                }

                val currentUris = gameList.map { it.uri.toString() }
                val uniqueNewGames = newlyFoundGames.filter { it.uri.toString() !in currentUris }

                gameList.addAll(uniqueNewGames)
                gameList.sortBy { it.fileName.lowercase() }

                gameAdapter.notifyDataSetChanged()
                loadGamesMetadata()
            }
        }
    }

    private fun loadGamesMetadata() {
        lifecycleScope.launch(Dispatchers.IO) {
            for ((i, element) in gameList.withIndex()) {
                val game = gameList.getOrNull(i) ?: continue
                if (game.title == null) {
                    if (!Core.init()) continue
                    if (!Core.loadRom(game.uri)) continue
                    val gameTitle = Core.gameTitle()
                    val gameCode = Core.gameCode()
                    val iconUrl = getIconUrl(gameTitle)
                    withContext(Dispatchers.Main) {
                        if (i < gameList.size && element.uri == game.uri) {
                            element.title = gameTitle
                            element.code = gameCode
                            element.version = Core.gameVersion
                            element.iconUrl = iconUrl
                            GameCacheManager.saveGame(element)
                            gameAdapter.notifyItemChanged(i)
                        }
                    }
                    Core.reset()
                }
            }
        }
    }

    private fun launchEmulationActivity(gameUri: Uri) {
        val intent = Intent(requireContext(), EmulationActivity::class.java).apply {
            putExtra("gameUri", gameUri.toString())
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}