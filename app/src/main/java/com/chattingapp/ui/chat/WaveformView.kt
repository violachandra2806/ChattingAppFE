package com.chattingapp.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chattingapp.R
import kotlin.math.abs
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    fun setWaveColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private val bars = mutableListOf<Float>()
    private var maxBars = 30
    private var isPlaying = false
    private var animationProgress = 0f

    init {
        // Generate random waveform bars
        for (i in 0 until maxBars) {
            bars.add((10..80).random().toFloat())
        }
    }

    fun setAmplitudes(amplitudes: List<Float>) {
        bars.clear()
        bars.addAll(amplitudes.take(maxBars))
        invalidate()
    }

    fun startAnimation() {
        isPlaying = true
        animationProgress = 0f
        animateWaveform() // ✅ RENAMED
    }

    fun stopAnimation() {
        isPlaying = false
        animationProgress = 0f
        invalidate()
    }

    fun updateProgress(progress: Float) {
        animationProgress = progress
        invalidate()
    }

    private fun animateWaveform() { // ✅ RENAMED dari animate()
        if (!isPlaying) return

        animationProgress += 0.05f
        if (animationProgress > 1f) animationProgress = 0f

        invalidate()
        postDelayed({ animateWaveform() }, 50) // ✅ RECURSIVE CALL UPDATED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        if (bars.isEmpty()) return

        val barWidth = 4f
        val spacing = (width - (bars.size * barWidth)) / (bars.size + 1)

        bars.forEachIndexed { index, amplitude ->
            val x = spacing + index * (barWidth + spacing)

            // Animate based on progress
            val animatedHeight = if (isPlaying) {
                val wave = abs(sin((animationProgress * 10 + index) * 0.5).toFloat())
                amplitude * (0.5f + wave * 0.5f)
            } else {
                amplitude * 0.6f
            }

            val barHeight = (animatedHeight / 100f) * height * 0.8f

            // Draw bar
            canvas.drawLine(
                x + barWidth / 2,
                centerY - barHeight / 2,
                x + barWidth / 2,
                centerY + barHeight / 2,
                paint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 60
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            height
        )
    }
}