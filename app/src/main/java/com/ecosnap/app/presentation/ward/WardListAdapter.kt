package com.ecosnap.app.presentation.ward

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosnap.app.databinding.ItemWardBinding
import com.ecosnap.app.domain.model.WardCategory

class WardListAdapter(
    private val onItemClick: (WardCategory) -> Unit,
) : ListAdapter<WardCategory, WardListAdapter.WardViewHolder>(WardDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WardViewHolder {
        val binding = ItemWardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return WardViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: WardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WardViewHolder(
        private val binding: ItemWardBinding,
        private val onItemClick: (WardCategory) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ward: WardCategory) {
            binding.tvWardName.text = ward.wardName
            binding.tvPhotoCount.text = "${ward.totalPhotos} ảnh báo cáo"

            binding.root.setOnClickListener {
                onItemClick(ward)
            }
        }
    }

    private object WardDiffCallback : DiffUtil.ItemCallback<WardCategory>() {
        override fun areItemsTheSame(oldItem: WardCategory, newItem: WardCategory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WardCategory, newItem: WardCategory): Boolean {
            return oldItem == newItem
        }
    }
}
