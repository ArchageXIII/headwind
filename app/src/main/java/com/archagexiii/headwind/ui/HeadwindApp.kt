package com.archagexiii.headwind.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archagexiii.headwind.fan.Connection
import com.archagexiii.headwind.fan.FanStatus
import com.archagexiii.headwind.fan.FanUiState

@Composable
fun HeadwindApp(
    state: FanUiState,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier,
    fanName: String = "HEADWIND",
    onSettings: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val connected = state.canControl
    val setting = if (connected) state.displayedSpeed.toString() + "%" else "—"
    val poweredOn = state.isPoweredOn
    val topInset = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(0xFF091313))) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF76E5C3))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Increase fan speed"
                        stateDescription = if (connected) setting else state.message
                    }
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onIncrease()
                    },
            ) {
                Text("+", Modifier.align(Alignment.Center), color = Color(0xFF092F24), fontSize = 124.sp, fontWeight = FontWeight.Light)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF091313)))
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF203A37))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Decrease fan speed"
                        stateDescription = if (connected) setting else state.message
                    }
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDecrease()
                    },
            ) {
                Text("−", Modifier.align(Alignment.Center), color = Color(0xFFE4F5EE), fontSize = 104.sp, fontWeight = FontWeight.Light)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF091313)))
            // Deliberately no click, toggle or pointer handler: safe space for the Home gesture.
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF151E1D))
                    .semantics(mergeDescendants = true) { contentDescription = "Fan status" }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(setting, color = Color(0xFFB6F2DC), fontSize = 88.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Text(
                    if (connected) "Connected · $fanName" else fanName,
                    Modifier.padding(top = 12.dp), color = Color(0xFFC1D2CC), fontSize = 14.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (!connected || state.requestedSpeed != null) {
                    Text(state.message, Modifier.padding(top = 8.dp), color = Color(0xFFC1D2CC),
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
        IconToggleButton(
            checked = poweredOn,
            onCheckedChange = { onTogglePower() },
            modifier = Modifier.align(Alignment.TopStart)
                .padding(top = topInset + 4.dp, start = 8.dp)
                .size(48.dp)
                .background(if (poweredOn && connected) Color(0xFF163A30) else Color(0x33163A30), CircleShape)
                .semantics {
                    contentDescription = "Fan power"
                    stateDescription = when {
                        !connected -> state.message
                        poweredOn -> "On"
                        else -> "Off"
                    }
                },
        ) {
            Canvas(Modifier.size(24.dp)) {
                val ink = if (poweredOn && connected) Color(0xFFB6F2DC) else Color(0xFF163A30)
                val stroke = 2.dp.toPx()
                drawArc(ink, -45f, 270f, false,
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                    size = Size(size.width * 0.7f, size.height * 0.7f),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(ink, Offset(center.x, size.height * 0.08f),
                    Offset(center.x, size.height * 0.48f), stroke, StrokeCap.Round)
            }
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding() + 4.dp, end = 4.dp)
                .semantics { contentDescription = "Fan settings" },
        ) {
            Canvas(Modifier.size(22.dp)) {
                val ink = Color(0xFF163A30).copy(alpha = 0.65f)
                val gear = Path()
                repeat(32) { point ->
                    val radius = size.minDimension * if (point % 4 == 1 || point % 4 == 2) 0.47f else 0.36f
                    val angle = point * Math.PI / 16
                    val x = center.x + radius * kotlin.math.cos(angle).toFloat()
                    val y = center.y + radius * kotlin.math.sin(angle).toFloat()
                    if (point == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                }
                gear.close()
                drawPath(gear, ink, style = Stroke(width = 1.5.dp.toPx()))
                drawCircle(ink, radius = size.minDimension * 0.14f, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

@Preview(widthDp = 393, heightDp = 850)
@Composable
private fun RemotePreview() {
    HeadwindApp(FanUiState(Connection.READY, FanStatus(4, 40)), {}, {}, {})
}
