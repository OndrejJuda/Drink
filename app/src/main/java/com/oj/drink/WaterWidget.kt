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

        // Dark circle background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C1E")
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Clip to circle, draw water fill from the bottom
        val clipPath = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        val fillHeight = size * progress
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
        }
        canvas.drawRect(0f, size - fillHeight, size.toFloat(), size.toFloat(), fillPaint)
        canvas.restore()

        // Circle outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy, radius, outlinePaint)

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
