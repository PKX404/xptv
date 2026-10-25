package com.pkx404.xparty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class QuickSearchAdapter(private val open: (QuickVideo) -> Unit, private val prefetch: (QuickVideo) -> Unit = {}) : ListAdapter<QuickVideo, QuickSearchAdapter.Holder>(object : DiffUtil.ItemCallback<QuickVideo>() {
 override fun areItemsTheSame(a: QuickVideo, b: QuickVideo) = a.url == b.url
 override fun areContentsTheSame(a: QuickVideo, b: QuickVideo) = a == b
}) {
 class Holder(view: View) : RecyclerView.ViewHolder(view) { val image: ImageView = view.findViewById(R.id.search_thumbnail); val title: TextView = view.findViewById(R.id.search_title); val duration: TextView = view.findViewById(R.id.search_duration) }
 override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_quick_video, parent, false))
 override fun onBindViewHolder(holder: Holder, position: Int) {
  val item = getItem(position); holder.title.text = item.title; holder.duration.text = if (item.live) "LIVE" else if (item.durationSeconds < 0) "" else "%d:%02d".format(item.durationSeconds / 60, item.durationSeconds % 60)
  Glide.with(holder.image).load(item.thumbnail).centerCrop().into(holder.image)
  holder.itemView.contentDescription = "${item.title}, ${holder.duration.text}"
  holder.itemView.setOnClickListener { open(item) }; holder.itemView.setOnFocusChangeListener { _, focused -> if (focused) prefetch(item) }
 }
 override fun onViewRecycled(holder: Holder) { Glide.with(holder.image).clear(holder.image); holder.itemView.setOnClickListener(null); super.onViewRecycled(holder) }
}
