package com.exemplo.fermata_demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class VideoEntry(val title: String, val uri: String)

class VideoLibraryAdapter(
    private val items: MutableList<VideoEntry>,
    private val onPlay: (VideoEntry) -> Unit,
    private val onDelete: (VideoEntry, Int) -> Unit
) : RecyclerView.Adapter<VideoLibraryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_video_title)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_video)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.itemView.setOnClickListener { onPlay(item) }
        holder.btnDelete.setOnClickListener { onDelete(item, holder.adapterPosition) }
    }

    override fun getItemCount() = items.size
}
