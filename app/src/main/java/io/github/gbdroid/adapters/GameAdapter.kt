package io.github.gbdroid.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.fallback
import coil3.request.error
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import io.github.gbdroid.model.GameModel
import io.github.gbdroid.databinding.ItemGameBinding

class GameAdapter(
    private val games: List<GameModel>,
    private val onGameClick: (GameModel) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    inner class GameViewHolder(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onGameClick(games[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]

        holder.binding.title.text = game.title ?: game.fileName
        holder.binding.version.text = game.version ?: "Version: --"
        holder.binding.icon.load(game.iconUrl ?: "") {
            crossfade(true)
            fallback(android.R.drawable.ic_media_play)
            error(android.R.drawable.ic_media_play)
            transformations(RoundedCornersTransformation(16f))
        }
    }

    override fun getItemCount(): Int = games.size
}