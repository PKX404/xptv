package com.pkx404.xparty

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

data class IptvChannel(val id: String, val name: String, val url: String, val category: String, val country: String, val logo: String? = null, val radio: Boolean = false, val programme: String? = null, val viewers: Long? = null)

class IptvChannelCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
 init { LayoutInflater.from(context).inflate(R.layout.item_iptv_channel, this, true) }
 fun bind(channel: IptvChannel, playing: Boolean, onClick: (IptvChannel) -> Unit) {
  findViewById<TextView>(R.id.channel_name).text = channel.name
  findViewById<TextView>(R.id.channel_category).text = channel.programme ?: channel.category
  findViewById<TextView>(R.id.channel_flag).text = channel.country.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) }?.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }?.joinToString("") ?: ""
  findViewById<TextView>(R.id.channel_viewers).apply { visibility = if (channel.viewers != null) VISIBLE else GONE; text = channel.viewers?.let { "$it watching" }.orEmpty() }
  val logo = findViewById<ImageView>(R.id.channel_logo); Glide.with(logo).load(channel.logo).placeholder(R.drawable.ic_tv).circleCrop().into(logo)
  findViewById<RadioWaveView>(R.id.radio_wave).apply { visibility = if (channel.radio) VISIBLE else GONE; setPlaying(playing && channel.radio) }
  isSelected = playing; contentDescription = "${channel.name}, ${channel.category}${if (playing) ", playing" else ""}"; setOnClickListener { onClick(channel) }
 }
}

class RadioWaveView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
 private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt() }
 private var phase = 0f; private var playing = false
 private var animator: ValueAnimator? = null
 init { animator = ValueAnimator.ofFloat(0f, 6.283185f).apply { duration = 850; repeatCount = ValueAnimator.INFINITE; addUpdateListener { phase = it.animatedValue as Float; invalidate() } } }
 fun setPlaying(value: Boolean) { playing = value; update() }
 private fun update() { animator?.let { if (playing && isAttachedToWindow && isShown && windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled()) { if (!it.isStarted) it.start() } else it.cancel() } }
 override fun onAttachedToWindow() { super.onAttachedToWindow(); update() }
 override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
 override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); update() }
 override fun onDraw(canvas: Canvas) { repeat(3) { i -> val h = height * (.3f + .7f * kotlin.math.abs(kotlin.math.sin(phase + i))); val x = width * (i + .2f) / 3; canvas.drawRoundRect(x, (height - h) / 2, x + width / 6, (height + h) / 2, 3f, 3f, paint) } }
}
