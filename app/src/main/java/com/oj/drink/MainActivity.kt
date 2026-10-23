package com.oj.drink

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    /** Day currently being logged into. Always reset to today when the app comes forward. */
    private var selectedDay by mutableLongStateOf(WaterRepository.today())
    private var selectedMl by mutableIntStateOf(0)
    private var currentStreak by mutableIntStateOf(0)
    private var maxStreak by mutableIntStateOf(0)

    /** Bumped after every write so the chart's cached data recomputes. */
    private var dataVersion by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.scheduleNext(this)
        refresh()
        setContent {
            DrinkTheme {
                val goal = WaterRepository.DAILY_GOAL_ML
                val dark = Color(0xFF1C1C1E)
                val progress = (selectedMl.toFloat() / goal).coerceIn(0f, 1f)

                val today = WaterRepository.today()
                val lastIndex = WaterRepository.VISIBLE_DAYS - 1
                val selectedIndex = (lastIndex - (today - selectedDay)).toInt().coerceIn(0, lastIndex)

                var devTapCount by remember { mutableStateOf(0) }
                var devLastTapMs by remember { mutableStateOf(0L) }
                var showDevDialog by remember { mutableStateOf(false) }

                val waveTransition = rememberInfiniteTransition(label = "wave")
                val wave1Phase by waveTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "w1"
                )
                val wave2Phase by waveTransition.animateFloat(
                    initialValue = 1f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "w2"
                )

                if (showDevDialog) {
                    AlertDialog(
                        onDismissRequest = { showDevDialog = false },
                        title = { Text("Developer Mode") },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        WaterRepository.generateTestData(this@MainActivity)
                                        refresh()
                                        showDevDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Generate test data (skip real days)") }
                                TextButton(
                                    onClick = {
                                        WaterRepository.clearFakeData(this@MainActivity)
                                        refresh()
                                        showDevDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Clear fake data only") }
                                TextButton(
                                    onClick = {
                                        Reminders.runCheck(
                                            this@MainActivity,
                                            java.time.LocalTime.now().toSecondOfDay()
                                        )
                                        showDevDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Run reminder check now") }
                                TextButton(
                                    onClick = {
                                        WaterRepository.clearAllData(this@MainActivity)
                                        selectedDay = WaterRepository.today()
                                        refresh()
                                        updateWidget()
                                        showDevDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("⚠ Clear ALL data") }
                            }
                        },
                        confirmButton = {},
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
                            val left = cx - radius
                            val right = cx + radius
                            val bottom = cy + radius
                            // Water surface baseline (y decreases upward on screen)
                            val waterTopY = bottom - 2f * radius * progress
                            val waveAmp = 6.dp.toPx()
                            val waveLen = radius * 1.5f

                            // Dark background
                            drawCircle(
                                color = Color(0xFF0D1B2A),
                                radius = radius,
                                center = Offset(cx, cy)
                            )

                            val circlePath = Path().apply {
                                addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                            }
                            clipPath(circlePath) {
                                if (progress > 0f) {
                                    // Helper: build a filled wave path
                                    // baseY = equilibrium surface, phase = 0..1 horizontal shift
                                    fun waveFill(baseY: Float, phase: Float): Path = Path().apply {
                                        moveTo(left, bottom)
                                        for (step in 0..80) {
                                            val x = left + (right - left) * step / 80f
                                            val y = baseY + waveAmp * sin(
                                                2.0 * PI * ((x - left) / waveLen + phase)
                                            ).toFloat()
                                            lineTo(x, y)
                                        }
                                        lineTo(right, bottom)
                                        close()
                                    }

                                    // Layer 1 — back wave: lighter blue, 40% alpha, moving right
                                    drawPath(
                                        path = waveFill(waterTopY, wave1Phase),
                                        color = Color(0xFF42A5F5).copy(alpha = 0.40f)
                                    )

                                    // Layer 2 — front wave: deeper blue, 70% alpha,
                                    // half-wavelength offset + 5dp higher, moving left
                                    drawPath(
                                        path = waveFill(waterTopY - 5.dp.toPx(), wave2Phase + 0.5f),
                                        color = Color(0xFF1565C0).copy(alpha = 0.70f)
                                    )
                                }

                                // Specular highlight — top-left
                                val hlCx = cx - radius * 0.30f
                                val hlCy = cy - radius * 0.38f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                                        center = Offset(hlCx, hlCy),
                                        radius = radius * 0.40f
                                    ),
                                    radius = radius * 0.40f,
                                    center = Offset(hlCx, hlCy)
                                )
                            }
                        }

                        // Large value exactly at circle center
                        Text(
                            text = String.format(Locale.US, "%.2fL", selectedMl / 1000f),
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

                        // Streak badge — overlaps top-right edge of circle
                        StreakBadge(
                            currentStreak = currentStreak,
                            maxStreak = maxStreak,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-12).dp, y = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DayIndicator(
                        selectedDay = selectedDay,
                        today = today,
                        onBackToToday = {
                            selectedDay = today
                            refresh()
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { add(100) }) {
                            Text("+100ml")
                        }
                        Button(onClick = { add(250) }) {
                            Text("+250ml")
                        }
                        Button(
                            onClick = {
                                WaterRepository.resetDay(this@MainActivity, selectedDay)
                                refresh()
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
                                contentDescription = "Reset selected day",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    WaterChart(
                        dataVersion = dataVersion,
                        selectedIndex = selectedIndex,
                        onDaySelected = { index ->
                            selectedDay = today - (lastIndex - index)
                            refresh()
                        },
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
        // Coming back to the app always drops you into today, so a forgotten
        // day selection can never silently swallow later entries.
        selectedDay = WaterRepository.today()
        refresh()
    }

    private fun add(ml: Int) {
        WaterRepository.addWaterForDay(this, selectedDay, ml)
        refresh()
        updateWidget()
        Reminders.onWaterAdded(this)
    }

    private fun refresh() {
        selectedMl = WaterRepository.getMlForDay(this, selectedDay)
        currentStreak = WaterRepository.getStreak(this)
        maxStreak = WaterRepository.getMaxStreak(this)
        dataVersion++
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
private val MONTH_LABELS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatDay(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val weekday = DAY_LABELS[date.dayOfWeek.value - 1]
    return "$weekday, ${MONTH_LABELS[date.monthValue - 1]} ${date.dayOfMonth}"
}

private val BADGE_SIZE = 48.dp

/**
 * Streak badge that flips like a coin when tapped: 🔥 current streak on the front,
 * 🏆 all-time best on the back. Useful exactly on days like the one where the goal
 * is already out of reach — the record is still there to look at.
 *
 * Only one face is composed at a time; the back is counter-rotated so its text isn't
 * mirrored. [flipped] is local state, so the badge is always face-up after onResume.
 */
@Composable
private fun StreakBadge(currentStreak: Int, maxStreak: Int, modifier: Modifier = Modifier) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "flip"
    )
    val showingBack = rotation > 90f
    val accent = when {
        showingBack -> Color(0xFFFFC400)
        currentStreak > 0 -> Color(0xFFFF6D00)
        else -> Color.DarkGray
    }

    Box(
        modifier = modifier
            .size(BADGE_SIZE)
            .graphicsLayer {
                rotationY = rotation
                // Without this the 48dp badge swings through an extreme perspective
                // and visibly balloons mid-flip.
                cameraDistance = 12f * density
            }
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
            .border(width = 2.dp, color = accent, shape = CircleShape)
            .clickable { flipped = !flipped },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.graphicsLayer { if (showingBack) rotationY = 180f },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (showingBack) "🏆" else "🔥", fontSize = 13.sp, lineHeight = 14.sp)
            Text(
                text = "${if (showingBack) maxStreak else currentStreak}",
                color = if (showingBack) accent else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Fixed-height strip under the circle telling you which day the buttons write into.
 * Height is constant so switching days doesn't shift the layout.
 */
@Composable
private fun DayIndicator(selectedDay: Long, today: Long, onBackToToday: () -> Unit) {
    Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.Center) {
        if (selectedDay == today) {
            Text(
                text = "Today",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 13.sp
            )
        } else {
            Row(
                modifier = Modifier
                    .background(Color(0xFF3A2A12), RoundedCornerShape(15.dp))
                    .clickable { onBackToToday() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDay(selectedDay),
                    color = Color(0xFFFFB74D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✕",
                    color = Color(0xFFFFB74D).copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

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

private val CHART_PAD_LEFT = 36.dp
private val CHART_PAD_RIGHT = 8.dp

@Composable
private fun WaterChart(
    dataVersion: Int,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val blue = Color(0xFF2196F3)
    val days = WaterRepository.VISIBLE_DAYS
    val lastIndex = days - 1

    // Index 0 = oldest visible day, last index = today.
    // Also compute the 30-day rolling average *before* each visible day for the trend line.
    // dataVersion is the cache key — it changes after every write.
    val (chartData, rollingAverages) = remember(dataVersion) {
        val dayMap = WaterRepository.getAllDays(context)
        val today = WaterRepository.today()
        val data = (lastIndex downTo 0).map { daysAgo -> dayMap[today - daysAgo] ?: 0 }
        val averages = (lastIndex downTo 0).map { daysAgo ->
            val dayEpoch = today - daysAgo
            val values = (1..30).mapNotNull { dayMap[dayEpoch - it] }
            if (values.isEmpty()) 0f else values.average().toFloat()
        }
        Pair(data, averages)
    }

    // X-axis day labels — only depend on the current date, not history
    val dayLabels: List<String> = remember {
        val today = LocalDate.now()
        (lastIndex downTo 0).map { daysAgo ->
            DAY_LABELS[today.minusDays(daysAgo.toLong()).dayOfWeek.value - 1]
        }
    }

    val labelStyle = TextStyle(color = Color.Gray, fontSize = 10.sp)
    val selectedLabelStyle = TextStyle(
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
    )

    Canvas(
        modifier = modifier.pointerInput(days) {
            // The whole vertical slot is the tap target, not just the small label.
            detectTapGestures { tap ->
                val chartLeft = CHART_PAD_LEFT.toPx()
                val chartWidth = size.width - CHART_PAD_RIGHT.toPx() - chartLeft
                if (chartWidth <= 0f) return@detectTapGestures
                val index = ((tap.x - chartLeft) / (chartWidth / days)).toInt()
                if (index in 0..lastIndex) onDaySelected(index)
            }
        }
    ) {
        val chartLeft = CHART_PAD_LEFT.toPx()
        val chartRight = size.width - CHART_PAD_RIGHT.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val maxValue = ((chartData.maxOrNull() ?: 0) + 250).toFloat()
        val slotWidth = chartWidth / days
        val barWidth = slotWidth * 0.5f

        // Selection band behind the selected day
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            topLeft = Offset(chartLeft + slotWidth * selectedIndex, chartTop),
            size = Size(slotWidth, chartHeight),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

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

        // Horizontal grid lines and Y-axis labels at every 1L interval
        val maxLiters = (maxValue / 1000f).toInt()
        for (liters in 0..maxLiters) {
            val y = chartBottom - (liters * 1000f / maxValue) * chartHeight
            // Grid line (skip 0 — the baseline already covers it)
            if (liters > 0) {
                drawLine(
                    color = Color.DarkGray.copy(alpha = 0.7f),
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Label
            val label = if (liters == 0) "0" else "${liters}L"
            val measured = textMeasurer.measure(label, style = labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    chartLeft - measured.size.width - 4.dp.toPx(),
                    y - measured.size.height / 2f
                ),
                style = labelStyle
            )
        }

        // Bars with rounded top corners
        val cornerR = 6.dp.toPx()
        chartData.forEachIndexed { i, ml ->
            if (ml <= 0) return@forEachIndexed
            val slotCenter = chartLeft + slotWidth * i + slotWidth / 2f
            val barLeft = slotCenter - barWidth / 2f
            val barRight = slotCenter + barWidth / 2f
            val barHeight = (ml.toFloat() / maxValue) * chartHeight
            val barTop = chartBottom - barHeight
            val r = cornerR.coerceAtMost(barHeight / 2f).coerceAtMost(barWidth / 2f)
            val barPath = Path().apply {
                moveTo(barLeft, chartBottom)
                lineTo(barLeft, barTop + r)
                quadraticBezierTo(barLeft, barTop, barLeft + r, barTop)
                lineTo(barRight - r, barTop)
                quadraticBezierTo(barRight, barTop, barRight, barTop + r)
                lineTo(barRight, chartBottom)
                close()
            }
            drawPath(barPath, color = blue)
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

        // X-axis day labels — the selected one is brighter and bold
        dayLabels.forEachIndexed { i, label ->
            val style = if (i == selectedIndex) selectedLabelStyle else labelStyle
            val slotCenter = chartLeft + slotWidth * i + slotWidth / 2f
            val measured = textMeasurer.measure(label, style = style)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(
                    slotCenter - measured.size.width / 2f,
                    chartBottom + 4.dp.toPx()
                ),
                style = style
            )
        }
    }
}
