package com.chattingapp.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.chattingapp.R

object AvatarUtils {

    private val avatarColorResIds = intArrayOf(
        R.color.lighter_purple,
        R.color.fade_purple,
        R.color.light_blue,
        R.color.green,
        R.color.blue,
        R.color.teal_700,
        R.color.red
    )

    private fun normalizeName(name: String?): String {
        return name?.trim().orEmpty()
    }

    private fun firstInitial(name: String?): String {
        val n = normalizeName(name)
        if (n.isEmpty()) return "?"
        return n.substring(0, 1).uppercase()
    }

    private fun pickStableColor(context: Context, seed: String?): Int {
        val s = normalizeName(seed)
        val idx = if (s.isEmpty()) 0 else kotlin.math.abs(s.hashCode()) % avatarColorResIds.size
        return ContextCompat.getColor(context, avatarColorResIds[idx])
    }

    private fun initialsDrawable(context: Context, name: String?): Drawable {
        val bg = pickStableColor(context, name)
        val text = firstInitial(name)
        return InitialsDrawable(bgColor = bg, text = text)
    }

    /**
     * Loads an avatar URL into an ImageView.
     * - If url is null/blank/"null" or Glide fails, shows a single-letter initial with a random (stable) background.
     */
    fun loadInto(imageView: ImageView, url: String?, nameForFallback: String?) {
        val ctx = imageView.context
        val safeUrl = url?.trim().takeUnless { it.isNullOrEmpty() || it.equals("null", ignoreCase = true) }
        val fallback = initialsDrawable(ctx, nameForFallback)

        if (safeUrl == null) {
            Glide.with(imageView).clear(imageView)
            imageView.setImageDrawable(fallback)
            return
        }

        Glide.with(imageView)
            .load(safeUrl)
            .placeholder(fallback)
            .error(fallback)
            .into(imageView)
    }

    /**
     * Applies an initial-based avatar to a TextView (e.g., profile picture circle with one letter).
     */
    fun applyTo(textView: TextView, name: String?) {
        val ctx = textView.context
        val bgColor = pickStableColor(ctx, name)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }

        textView.text = firstInitial(name)
        textView.background = shape
        textView.setTextColor(ContextCompat.getColor(ctx, R.color.white))
    }
}

private class InitialsDrawable(
    private val bgColor: Int,
    private val text: String
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bgColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rectF = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rectF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())

        val radius = kotlin.math.min(rectF.width(), rectF.height()) / 2f
        canvas.drawCircle(rectF.centerX(), rectF.centerY(), radius, bgPaint)

        // Text size scales with available space
        textPaint.textSize = radius * 0.9f
        val fm = textPaint.fontMetrics
        val textY = rectF.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, rectF.centerX(), textY, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
