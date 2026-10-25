package com.pkx404.xparty

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

class VerticalVolumeSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
 private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
 var value: Int = 70
  set(next) { field = next.coerceIn(0, 100); invalidate() }
 var onChanged: ((Int) -> Unit)? = null
 init { isFocusable = true; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
 private val density get() = resources.displayMetrics.density
 override fun onDraw(canvas: Canvas) { val top = 12 * density; val bottom = height - top; val y = bottom - (bottom - top) * value / 100; paint.strokeWidth = 2 * density; paint.strokeCap = Paint.Cap.ROUND; paint.color = 0xFF3A2A4A.toInt(); canvas.drawLine(width / 2f, top, width / 2f, bottom, paint); paint.color = 0xFFC9B6FF.toInt(); canvas.drawLine(width / 2f, y, width / 2f, bottom, paint); paint.color = -1; canvas.drawCircle(width / 2f, y, 3 * density, paint) }
 private fun change(next: Int) { value = next; onChanged?.invoke(value); sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED) }
 override fun onTouchEvent(event: MotionEvent): Boolean { if (!isEnabled) return false; when (event.actionMasked) { MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true); MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true } }; change(((height - 12 * density - event.y) / (height - 24 * density).coerceAtLeast(1f) * 100).toInt()); if (event.actionMasked == MotionEvent.ACTION_UP) { parent?.requestDisallowInterceptTouchEvent(false); performClick() }; return true }
 override fun performClick(): Boolean { super.performClick(); return true }
 override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> { change(value + 5); true }; KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> { change(value - 5); true }; else -> super.onKeyDown(keyCode, event) }
 override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(info); info.className = "android.widget.SeekBar"; info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, value.toFloat()); info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS) }
 override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean { if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id && arguments != null) { change(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).toInt()); return true }; return super.performAccessibilityAction(action, arguments) }
}
