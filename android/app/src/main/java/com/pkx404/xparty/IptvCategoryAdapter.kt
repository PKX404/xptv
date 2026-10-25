package com.pkx404.xparty

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class IptvCategoryAdapter(private val play: (IptvChannel) -> Unit) : ListAdapter<IptvChannel, IptvCategoryAdapter.Holder>(object : DiffUtil.ItemCallback<IptvChannel>() {
 override fun areItemsTheSame(a: IptvChannel, b: IptvChannel) = a.id == b.id
 override fun areContentsTheSame(a: IptvChannel, b: IptvChannel) = a == b
}) {
 class Holder(val card: IptvChannelCard) : RecyclerView.ViewHolder(card)
 private var channels = emptyList<IptvChannel>(); private var category = "All"; private var query = ""
 var playingId: String? = null
  set(value) { val previous = field; field = value; currentList.forEachIndexed { index, channel -> if (channel.id == value || channel.id == previous) notifyItemChanged(index) } }
 fun replace(items: List<IptvChannel>) { channels = items; filter(category, query) }
 fun filter(category: String, query: String) { this.category = category; this.query = query; submitList(channels.filter { (category == "All" || category == "Radio" && it.radio || it.category.equals(category, true)) && (it.name + " " + it.category).contains(query, true) }) }
 override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(IptvChannelCard(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2).apply { val gap = (6 * resources.displayMetrics.density).toInt(); setMargins(gap, gap, gap, gap) } })
 override fun onBindViewHolder(holder: Holder, position: Int) { holder.card.bind(getItem(position), getItem(position).id == playingId, play) }
}
