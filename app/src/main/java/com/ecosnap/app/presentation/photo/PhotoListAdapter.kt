package com.ecosnap.app.presentation.photo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ecosnap.app.databinding.ItemPhotoBinding
import com.ecosnap.app.domain.model.TrashPhoto

class PhotoListAdapter(
    private val onDeleteClick: (TrashPhoto) -> Unit,
    private val onPhotoClick: ((TrashPhoto) -> Unit)? = null,
) : ListAdapter<TrashPhoto, PhotoListAdapter.PhotoViewHolder>(PhotoDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return PhotoViewHolder(binding, onDeleteClick, onPhotoClick)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PhotoViewHolder(
        private val binding: ItemPhotoBinding,
        private val onDeleteClick: (TrashPhoto) -> Unit,
        private val onPhotoClick: ((TrashPhoto) -> Unit)?,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: TrashPhoto) {
            binding.ivTrashPhoto.load(photo.imageUrl) {
                crossfade(true)
            }

            binding.tvCapturedTime.text = photo.capturedAt.ifEmpty { "Vừa xong" }

            binding.btnDeletePhoto.setOnClickListener {
                onDeleteClick(photo)
            }

            binding.root.setOnClickListener {
                onPhotoClick?.invoke(photo)
            }
        }
    }

    private object PhotoDiffCallback : DiffUtil.ItemCallback<TrashPhoto>() {
        override fun areItemsTheSame(oldItem: TrashPhoto, newItem: TrashPhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrashPhoto, newItem: TrashPhoto): Boolean {
            return oldItem == newItem
        }
    }
}
