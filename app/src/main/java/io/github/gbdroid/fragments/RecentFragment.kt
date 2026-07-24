
package io.github.gbdroid.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.gbdroid.EmulationActivity
import io.github.gbdroid.adapters.GameAdapter
import io.github.gbdroid.databinding.FragmentRecentBinding
import io.github.gbdroid.model.GameModel
import io.github.gbdroid.utils.GameCacheManager
import io.github.gbdroid.utils.applySafePadding
import io.github.gbdroid.viewmodel.GamesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecentFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var recentAdapter: GameAdapter
    private val viewModel: GamesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (_binding == null) _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recentAdapter = GameAdapter { game ->
            launchEmulationActivity(game)
        }

        binding.root.applySafePadding()
        binding.recentRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recentRecyclerView.adapter = recentAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gameList.collect { gamesList ->
                recentAdapter.submitList(
                    gamesList.filter { it.lastPlayed > 0L }.sortedByDescending { it.lastPlayed }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadGamesFromDisk()
    }

    private fun launchEmulationActivity(game: GameModel) {
        game.lastPlayed = System.currentTimeMillis()

        lifecycleScope.launch(Dispatchers.IO) {
            GameCacheManager.saveGame(game)
        }

        val intent = Intent(requireContext(), EmulationActivity::class.java).apply {
            putExtra("gameUri", game.uri.toString())
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}