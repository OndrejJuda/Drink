package com.oj.drink

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oj.drink.ui.theme.DrinkTheme
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var currentMl by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentMl = WaterRepository.getCurrentMl(this)
        setContent {
            DrinkTheme {
                val goal = WaterRepository.DAILY_GOAL_ML
                val blue = Color(0xFF2196F3)
                val dark = Color(0xFF1C1C1E)
                val progress = (currentMl.toFloat() / goal).coerceIn(0f, 1f)

                var devTapCount by remember { mutableStateOf(0) }
                var devLastTapMs by remember { mutableStateOf(0L) }
                var showDevDialog by remember { mutableStateOf(false) }

                if (showDevDialog) {
                    AlertDialog(
                        onDismissRequest = { showDevDialog = false },
                        title = { Text("Developer Mode") },
                        text = { Text("Fill the last 36 days with random test data?") },
                        confirmButton = {
                            TextButton(onClick = {
                                WaterRepository.generateTestData(this@MainActivity)
                                showDevDialog = false
                            }) { Text("Generate") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDevDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dark)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Liquid fill circle
                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = minOf(size.width, size.height) / 2f - 6f
                            val cx = size.width / 2f
                            val cy = size.height / 2f

                            drawCircle(color = dark, radius = radius, center = Offset(cx, cy))

                            val circlePath = Path().apply {
                                addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                            }
                            clipPath(circlePath) {
                                val fillHeight = size.height * progress
                                drawRect(
                                    color = blue,
                                    topLeft = Offset(0f, size.height - fillHeight),
                                    size = Size(size.width, fillHeight)
                                )
                            }

                            drawCircle(
                                color = blue,
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 6f)
                            )
                        }

                        // Large value exactly at circle center
                        Text(
                            text = String.format(Locale.US, "%.2fL", currentMl / 1000f),
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // Subtitle below center, not part of the centered group
                        Text(
                            text = "/ ${goal / 1000f}L",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = 34.dp)
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if (now - devLastTapMs > 2000) devTapCount = 0
                                    devTapCount++
                                    devLastTapMs = now
                                    if (devTapCount >= 7) {
                                        showDevDialog = true
                                        devTapCount = 0
                                    }
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            currentMl = WaterRepository.addWater(this@MainActivity, 100)
                            updateWidget()
                        }) {
                            Text("+100ml")
                        }
                        Button(onClick = {
                            currentMl = WaterRepository.addWater(this@MainActivity, 250)
                            updateWidget()
                        }) {
                            Text("+250ml")
                        }
                        Button(
                            onClick = {
                                currentMl = WaterRepository.resetToday(this@MainActivity)
                                updateWidget()
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A3A3C)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Reset today",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    WaterChart(
                        currentMl = currentMl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentMl = WaterRepository.getCurrentMl(this)
    }

    private fun updateWidget() {
        val intent = Intent(this, WaterWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(this@MainActivity)
                .getAppWidgetIds(ComponentName(this@MainActivity, WaterWidget::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }
}

private val DAY_LABELS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

@Composable
private fun WaterChart(currentMl: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val blue = Color(0xFF2196F3)

    // Last 7 days: index 0 = oldest, index 6 = today
    // Also compute the 30-day rolling average *before* each visible day for the trend line
    val (chartData, rollingAverages) = remember(currentMl) {
        val historyMap = WaterRepository.getHistory(context).toMap()
        val today = LocalDate.now().toEpochDay()
        val data = (6 downTo 0).map { daysAgo ->
            if (daysAgo == 0) currentMl else historyMap[today - daysAgo] ?: 0
        }
        val averages = (6 downTo 0).map { daysAgo ->
            val dayEpoch = today - daysAgo
            val values = (1..30).mapNotNull { historyMap[dayEpoch - it] }
            if (values.isEmpty()) 0f else values.average().toFloat()
        }
        Pair(data, averages)
    }

    // X-axis day labels for the same 7 days
    val dayLabels: List<String> = remember(currentMl) {
        val today = LocalDate.now()
        (6 downTo 0).map { daysAgo ->
            DAY_LABELS[today.minusDays(daysAgo.toLong()).dayOfWeek.value - 1]
        }
    }

    val labelStyle = TextStyle(color = Color.Gray, fontSize = 10.sp)

    Canvas(modifier = modifier) {
        val padLeft = 36.dp.toPx()
        val padBottom = 24.dp.toPx()
        val padTop = 8.dp.toPx()
        val padRight = 8.dp.toPx()

        val chartLeft = padLeft
        val chartRight = size.width - padRight
        val chartBottom = size.height - padBottom
        val chartTop = padTop
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val maxValue = ((chartData.maxOrNull() ?: 0) + 250).toFloat()
        val slotWidth = chartWidth / 7f
        val barWidth = slotWidth * 0.5f

        // Axes
        drawLine(
            color = Color.DarkGray,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartBottom),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.DarkGray,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.dp.toPx()
        )

        // Goal reference line (dashed, faint blue)
        drawLine(
            color = blue.copy(alpha = 0.25f),
            start = Offset(chartLeft, chartTop),
            end = Offset(chartRight, chartTop),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        // Bars
        chartData.forEachIndexed { i, ml ->
            val slotCenter = chartLeft + slotWidth * i + slotWidth / 2f
            val barHeight = (ml.toFloat() / maxValue) * chartHeight
            val barTop = chartBottom - barHeight
            drawRect(
                color = blue,
                topLeft = Offset(slotCenter - barWidth / 2f, barTop),
                size = Size(barWidth, barHeight.coerceAtLeast(0f))
            )
        }

        // 30-day rolling average line (one point per visible day)
        val avgPoints = rollingAverages.mapIndexed { i, avg ->
            val slotCenter = chartLeft + slotWidth * i + slotWidth / 2f
            val avgY = chartBottom - (avg / maxValue) * chartHeight
            Offset(slotCenter, avgY)
        }
        if (avgPoints.size >= 2) {
            drawPath(
                path = smoothPath(avgPoints),
                color = Color.White.copy(alpha = 0.55f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // X-axis day labels
        dayLabels.forEachIndexed { i, label ->
            val slotCenter = chartLeft + slotWidth * i + slotWidth / 2f
            val measured = textMeasurer.measure(label, style = labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    slotCenter - measured.size.width / 2f,
                    chartBottom + 4.dp.toPx()
                ),
                style = labelStyle
            )
        }

        // Y-axis labels: 0 at bottom, goal at top
        val zeroLayout = textMeasurer.measure("0", style = labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = "0",
            topLeft = Offset(
                chartLeft - zeroLayout.size.width - 4.dp.toPx(),
                chartBottom - zeroLayout.size.height / 2f
            ),
            style = labelStyle
        )
        val maxLabel = String.format(Locale.US, "%.1fL", maxValue / 1000f)
        val goalLayout = textMeasurer.measure(maxLabel, style = labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = maxLabel,
            topLeft = Offset(
                chartLeft - goalLayout.size.width - 4.dp.toPx(),
                chartTop - goalLayout.size.height / 2f
            ),
            style = labelStyle
        )
    }
}
