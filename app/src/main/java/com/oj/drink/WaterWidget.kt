package com.oj.drink

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.widget.RemoteViews
import java.util.Locale
import kotlin.math.sin
import kotlin.math.PI

class WaterWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_ADD_250 = "com.oj.drink.ADD_250"
        private const val BITMAP_SIZE = 120
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ADD_250) {
            WaterRepository.addWater(context, 250)
            updateAllWidgets(context)
        }
    }

    private fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidget::class.java))
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val currentMl = WaterRepository.getCurrentMl(context)
        val views = RemoteViews(context.packageName, R.layout.widget_water)

        val bitmap = createCircleBitmap(currentMl, WaterRepository.DAILY_GOAL_ML)
        views.setImageViewBitmap(R.id.widget_circle, bitmap)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piLaunch = PendingIntent.getActivity(
            context, 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_circle, piLaunch)

        val intent250 = Intent(context, WaterWidget::class.java).apply {
            action = ACTION_ADD_250
        }
        val pi250 = PendingIntent.getBroadcast(
            context, 0, intent250,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_250, pi250)

        manager.updateAppWidget(widgetId, views)
    }

    private fun createCircleBitmap(currentMl: Int, goalMl: Int): Bitmap {
        val size = BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val progress = (currentMl.toFloat() / goalMl).coerceIn(0f, 1f)
        val cx = size / 2f
        val cy = size / 2f
        val radius = (size / 2f - 6f) * 0.9f
        val left = cx - radius
        val right = cx + radius
        val bottom = cy + radius
        val waterTopY = bottom - 2f * radius * progress

        // Dark navy background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D1B2A")
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Clip everything to circle
        val clipPath = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        if (progress > 0f) {
            // Helper: build filled wave path at given baseline y and phase offset
            fun wavePath(baseY: Float, phase: Float): Path {
                val waveAmp = radius * 0.07f
                val waveLen = radius * 1.5f
                val steps = 60
                return Path().apply {
                    moveTo(left, bottom)
                    for (step in 0..steps) {
                        val x = left + (right - left) * step / steps
                        val y = baseY + waveAmp * sin(2.0 * PI * ((x - left) / waveLen + phase)).toFloat()
                        lineTo(x, y)
                    }
                    lineTo(right, bottom)
                    close()
                }
            }

            // Back wave: lighter blue, 40% opacity
            val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#42A5F5")
                alpha = (0.40f * 255).toInt()
            }
            canvas.drawPath(wavePath(waterTopY, 0.0f), backPaint)

            // Front wave: deeper blue, 70% opacity, half-wavelength offset + slightly higher
            val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1565C0")
                alpha = (0.70f * 255).toInt()
            }
            canvas.drawPath(wavePath(waterTopY - radius * 0.06f, 0.5f), frontPaint)
        }

        canvas.restore()

        // Centered text showing amount in liters
        val text = String.format(Locale.US, "%.1fL", currentMl / 1000f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, textY, textPaint)

        return bitmap
    }
}
