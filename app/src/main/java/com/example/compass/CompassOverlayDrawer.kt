package com.example.compass

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

object CompassOverlayDrawer {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC001100.toInt() // Dark translucent green background (80% black)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x6600FF00.toInt() // Semi-transparent green border
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Bright neon green
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val tickSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00CC00.toInt() // Green
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val tickMediumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00CC00.toInt() // Green
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val tickLargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00CC00.toInt() // Green
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }

    private val degreeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF66FF66.toInt() // Light green
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private val cardinalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Bright neon green
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val currentAzimuthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Bright neon green
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3300FF00.toInt() // Translucent green background
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x9900FF00.toInt() // Translucent green border
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt()
        style = Paint.Style.FILL
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Bright neon green
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    /**
     * Renders the HUD overlay.
     *
     * In Capture Mode (isCapture == true):
     * - The ruler box, ticks, degree numbers and cardinal letters are REMOVED.
     * - Only the current azimuth degree is drawn, at HALF size and positioned higher up.
     * - The central green vertical line and reticle are drawn.
     */
    fun drawOverlay(
        canvas: Canvas,
        width: Float,
        height: Float,
        azimuth: Float,
        density: Float,
        topPaddingPx: Float,
        isCapture: Boolean = false
    ) {
        val centerX = width / 2f
        val centerY = height / 2f
        val scale = if (isCapture) (width / 1080f).coerceAtLeast(1f) else (density / 3f).coerceAtLeast(0.8f)

        // 1. Central vertical green line
        centerLinePaint.strokeWidth = 2.5f * scale
        val startY = if (isCapture) 0f else topPaddingPx
        canvas.drawLine(centerX, startY, centerX, height, centerLinePaint)

        // 2. Reticle crosshair in the center
        val reticleGap = 12f * density * scale
        val reticleLen = 30f * density * scale
        crosshairPaint.strokeWidth = 2f * scale
        canvas.drawLine(centerX - reticleGap - reticleLen, centerY, centerX - reticleGap, centerY, crosshairPaint)
        canvas.drawLine(centerX + reticleGap, centerY, centerX + reticleGap + reticleLen, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - reticleGap - reticleLen, centerX, centerY - reticleGap, crosshairPaint)
        canvas.drawLine(centerX, centerY + reticleGap, centerX, centerY + reticleGap + reticleLen, crosshairPaint)
        canvas.drawCircle(centerX, centerY, reticleGap * 1.5f, crosshairPaint)

        // 3. Horizontal edge lines pegged to the left and right borders (same height centerY, same length reticleLen)
        canvas.drawLine(0f, centerY, reticleLen, centerY, crosshairPaint)
        canvas.drawLine(width - reticleLen, centerY, width, centerY, crosshairPaint)

        val roundedAzimuth = (azimuth.roundToInt() + 360) % 360
        val azimuthText = "%03d°".format(roundedAzimuth)

        // ==============================================================
        // CAPTURE MODE: NO RULER, ONLY HALF-SIZE DEGREE HIGHER UP
        // ==============================================================
        if (isCapture) {
            // Half size degree text (14sp instead of 28sp)
            currentAzimuthPaint.textSize = 14f * density * scale

            // Positioned higher up (near the top margin)
            val badgeTop = topPaddingPx * 0.45f
            val badgeW = 76f * density * scale
            val badgeH = 28f * density * scale
            val badgeLeft = centerX - badgeW / 2f
            val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
            val badgeRadius = 6f * density * scale

            // Compact translucent badge background & border
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBgPaint)
            badgeBorderPaint.strokeWidth = 1.2f * scale
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBorderPaint)

            // Small green triangle pointing down
            val downTriSize = 4.5f * density * scale
            val downTriPath = Path().apply {
                moveTo(centerX, badgeTop + 6.5f * density * scale)
                lineTo(centerX - downTriSize, badgeTop + 2.5f * density * scale)
                lineTo(centerX + downTriSize, badgeTop + 2.5f * density * scale)
                close()
            }
            canvas.drawPath(downTriPath, trianglePaint)

            // Half-size current degree text
            val textY = badgeTop + badgeH - 6.5f * density * scale
            canvas.drawText(azimuthText, centerX, textY, currentAzimuthPaint)
            return
        }

        // ==============================================================
        // VIEWFINDER MODE: FULL INTERACTIVE GRADUATED RULER
        // ==============================================================

        // Indicator triangle at the top of the line
        val triPath = Path().apply {
            val triSize = 10f * scale * density
            moveTo(centerX, topPaddingPx + triSize)
            lineTo(centerX - triSize * 0.7f, topPaddingPx)
            lineTo(centerX + triSize * 0.7f, topPaddingPx)
            close()
        }
        canvas.drawPath(triPath, trianglePaint)

        // Graduated Compass Ruler Strip
        val rulerTop = topPaddingPx
        val rulerHeight = 110f * density * scale
        val rulerBottom = rulerTop + rulerHeight
        val rulerRect = RectF(16f * density * scale, rulerTop, width - 16f * density * scale, rulerBottom)
        val cornerRadius = 14f * density * scale

        // Draw ruler strip background
        canvas.drawRoundRect(rulerRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rulerRect, cornerRadius, cornerRadius, borderPaint)

        // Highlighted current degree badge in center top of ruler
        val badgeW = 120f * density * scale
        val badgeH = 46f * density * scale
        val badgeLeft = centerX - badgeW / 2f
        val badgeTop = rulerTop + 8f * density * scale
        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        val badgeRadius = 8f * density * scale

        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBgPaint)
        badgeBorderPaint.strokeWidth = 1.5f * scale
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBorderPaint)

        // Triangle pointing DOWN right above the number
        val downTriSize = 6f * density * scale
        val downTriPath = Path().apply {
            moveTo(centerX, badgeTop + 10f * density * scale)
            lineTo(centerX - downTriSize, badgeTop + 4f * density * scale)
            lineTo(centerX + downTriSize, badgeTop + 4f * density * scale)
            close()
        }
        canvas.drawPath(downTriPath, trianglePaint)

        // Viewfinder degree text (28sp)
        currentAzimuthPaint.textSize = 28f * density * scale
        val textY = badgeTop + badgeH - 10f * density * scale
        canvas.drawText(azimuthText, centerX, textY, currentAzimuthPaint)

        // Draw Ruler Graduated Scale
        val tickBaselineY = rulerBottom - 10f * density * scale
        val visibleFovDegrees = 150f
        val pixelsPerDegree = rulerRect.width() / visibleFovDegrees

        val tickSmallH = 8f * density * scale
        val tickMedH = 14f * density * scale
        val tickLargeH = 20f * density * scale

        tickSmallPaint.strokeWidth = 2f * scale
        tickMediumPaint.strokeWidth = 3f * scale
        tickLargePaint.strokeWidth = 4f * scale

        degreeTextPaint.textSize = 12f * density * scale
        cardinalTextPaint.textSize = 17f * density * scale

        canvas.save()
        canvas.clipRect(rulerRect)

        for (deg in 0 until 360 step 10) {
            val delta = (deg - azimuth + 540f) % 360f - 180f

            if (delta in -80f..80f) {
                val tickX = centerX + delta * pixelsPerDegree

                when {
                    deg % 90 == 0 -> {
                        canvas.drawLine(tickX, tickBaselineY, tickX, tickBaselineY - tickLargeH, tickLargePaint)
                        val cardinalLetter = when (deg) {
                            0 -> "N"
                            90 -> "E"
                            180 -> "S"
                            270 -> "W"
                            else -> "N"
                        }
                        canvas.drawText(
                            cardinalLetter,
                            tickX,
                            tickBaselineY - tickLargeH - (6f * density * scale),
                            cardinalTextPaint
                        )
                    }

                    deg % 30 == 0 -> {
                        canvas.drawLine(tickX, tickBaselineY, tickX, tickBaselineY - tickMedH, tickMediumPaint)
                        val degLabel = "$deg°"
                        canvas.drawText(
                            degLabel,
                            tickX,
                            tickBaselineY - tickMedH - (5f * density * scale),
                            degreeTextPaint
                        )
                    }

                    else -> {
                        canvas.drawLine(tickX, tickBaselineY, tickX, tickBaselineY - tickSmallH, tickSmallPaint)
                    }
                }
            }
        }

        // 45-degree intermediate ticks (NE, SE, SW, NW)
        for (deg in listOf(45, 135, 225, 315)) {
            val delta = (deg - azimuth + 540f) % 360f - 180f
            if (delta in -80f..80f) {
                val tickX = centerX + delta * pixelsPerDegree
                val interH = 11f * density * scale
                canvas.drawLine(tickX, tickBaselineY, tickX, tickBaselineY - interH, tickSmallPaint)
                val interLabel = when (deg) {
                    45 -> "NE"
                    135 -> "SE"
                    225 -> "SW"
                    315 -> "NW"
                    else -> ""
                }
                degreeTextPaint.textSize = 10f * density * scale
                canvas.drawText(
                    interLabel,
                    tickX,
                    tickBaselineY - interH - (4f * density * scale),
                    degreeTextPaint
                )
            }
        }

        canvas.restore()

        canvas.drawLine(
            centerX,
            rulerBottom,
            centerX,
            rulerBottom - 12f * density * scale,
            centerLinePaint
        )
    }
}
