package com.pkx404.xparty

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class LikeAnimationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
 private val content = LayoutInflater.from(context).inflate(R.layout.item_like_button, this, false)
 private val count: TextView
 private var liked = false
 private var likes = 0
 private var pulse: AnimatorSet? = null
 var onLikeChanged: ((Boolean) -> Unit)? = null
 init { clipChildren = false; clipToPadding = false; addView(content, LayoutParams(-2, dp(36), Gravity.CENTER)); count = content.findViewById(R.id.like_count); content.setOnClickListener { setLiked(!liked, true); onLikeChanged?.invoke(liked) }; setLiked(false, false) }
 fun bind(count: Int, selected: Boolean) { likes = count.coerceAtLeast(0); setLiked(selected, false) }
 fun setLiked(value: Boolean, animate: Boolean) {
  if (animate && value != liked) likes = (likes + if (value) 1 else -1).coerceAtLeast(0)
  liked = value; count.text = likes.toString(); content.isSelected = liked; content.contentDescription = "${if (liked) "Unlike" else "Like"}, $likes likes"
  val shape = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, if (liked) intArrayOf(0xFF7C5CFF.toInt(), 0xFFFF3B7F.toInt()) else intArrayOf(0xFF2A2A3A.toInt(), 0xFF2A2A3A.toInt())).apply { cornerRadius = dp(18).toFloat() }
  content.background = RippleDrawable(ColorStateList.valueOf(0x1AFFFFFF), shape, null)
  if (!animate || !android.animation.ValueAnimator.areAnimatorsEnabled()) return
  pulse?.cancel(); pulse = AnimatorSet().apply { playTogether(ObjectAnimator.ofFloat(content, "scaleX", 1f, 1.2f, 1f), ObjectAnimator.ofFloat(content, "scaleY", 1f, 1.2f, 1f)); duration = 200; start() }
  if (liked) repeat(6) { index ->
   val heart = ImageView(context).apply { setImageResource(R.drawable.ic_heart); imageTintList = ColorStateList.valueOf(if (index % 2 == 0) 0xFFFF3B7F.toInt() else 0xFFB6A2FF.toInt()); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
   addView(heart, LayoutParams(dp(12), dp(12), Gravity.CENTER))
   heart.animate().translationX(dp((index - 2.5f) * 12).toFloat()).translationY(-dp(42 + (index % 3) * 10).toFloat()).alpha(0f).setDuration(400).setListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(animation: Animator) { removeView(heart) } }).start()
  }
 }
 override fun onDetachedFromWindow() { pulse?.cancel(); for (i in childCount - 1 downTo 1) { getChildAt(i).animate().cancel(); removeViewAt(i) }; super.onDetachedFromWindow() }
 private fun dp(value: Number) = (value.toFloat() * resources.displayMetrics.density).toInt()
}
