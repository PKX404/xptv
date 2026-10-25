package com.pkx404.xparty

import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.sign

class MiniProfileDialog : DialogFragment() {
    enum class Action { MESSAGE, ADD_FRIEND, TOGGLE_MUTE, REPORT }
    interface Host {
        fun bindProfileAvatar(userId: String, target: ImageView)
        fun openFullProfile(userId: String)
        fun onProfileAction(userId: String, action: Action)
    }

    private val host: Host get() = (parentFragment as? Host) ?: (activity as? Host)
        ?: error("MiniProfileDialog requires a Host activity or parent fragment")

    override fun onCreateDialog(state: Bundle?): Dialog {
        val context = activity
        val args = requireNotNull(arguments)
        val userId = requireNotNull(args.getString("id"))
        val content = context.layoutInflater.inflate(R.layout.dialog_user_mini, null)
        content.findViewById<TextView>(R.id.mini_name).text = args.getString("name", "")
        content.findViewById<TextView>(R.id.mini_handle).apply {
            text = args.getString("handle", "")
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        val full = View.OnClickListener { host.openFullProfile(userId); dismiss() }
        content.findViewById<ImageView>(R.id.mini_avatar).apply {
            host.bindProfileAvatar(userId, this)
            setOnClickListener(full)
        }
        content.findViewById<Button>(R.id.mini_profile).setOnClickListener(full)
        val actions = mapOf(R.id.mini_message to Action.MESSAGE, R.id.mini_friend to Action.ADD_FRIEND,
            R.id.mini_mute to Action.TOGGLE_MUTE, R.id.mini_report to Action.REPORT)
        actions.forEach { (id, action) -> content.findViewById<Button>(id).setOnClickListener {
            host.onProfileAction(userId, action); dismiss()
        } }
        content.findViewById<Button>(R.id.mini_mute).setText(
            if (args.getBoolean("muted")) R.string.mini_unmute else R.string.mini_mute)
        return Dialog(context, R.style.RaveDialogTheme).apply {
            setContentView(content)
            setCanceledOnTouchOutside(true)
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        dialog.window?.apply {
            setGravity(Gravity.CENTER)
            setLayout(min((300 * metrics.density).roundToInt(), metrics.widthPixels - (16 * metrics.density).roundToInt()),
                WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.6f)
        }
    }

    companion object {
        fun bindAvatar(view: View, manager: FragmentManager, userId: String, name: String,
                       handle: String, muted: Boolean = false) {
            view.setOnClickListener {
                if (manager.isStateSaved || manager.findFragmentByTag("mini-profile") != null) return@setOnClickListener
                MiniProfileDialog().apply { arguments = Bundle().apply {
                    putString("id", userId); putString("name", name); putString("handle", handle); putBoolean("muted", muted)
                } }.show(manager, "mini-profile")
            }
        }
    }
}

class SquircleImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : ImageView(context, attrs) {
    private val shape = Path()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rx = w / 2f; val ry = h / 2f
        shape.reset()
        for (i in 0..128) {
            val angle = i * Math.PI * 2 / 128
            val c = cos(angle); val s = sin(angle)
            val x = rx + rx * (sign(c) * sqrt(abs(c))).toFloat()
            val y = ry + ry * (sign(s) * sqrt(abs(s))).toFloat()
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
    }
    override fun draw(canvas: Canvas) { val save = canvas.save(); canvas.clipPath(shape); super.draw(canvas); canvas.restoreToCount(save) }
}

class RaveBottomSheetDialog(context: Context) : Dialog(context, R.style.RaveBottomSheetTheme) {
    init { setContentView(R.layout.panel_bottom_sheet); setCanceledOnTouchOutside(true) }
    override fun onStart() {
        super.onStart()
        window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.6f)
            if (Build.VERSION.SDK_INT >= 31) {
                val radius = (20 * context.resources.displayMetrics.density).roundToInt()
                setBackgroundBlurRadius(radius)
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.apply { blurBehindRadius = radius }
            }
        }
    }
}
