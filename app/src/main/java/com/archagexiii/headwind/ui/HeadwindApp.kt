package com.archagexiii.headwind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archagexiii.headwind.fan.Connection
import com.archagexiii.headwind.fan.FanStatus
import com.archagexiii.headwind.fan.FanUiState
import com.archagexiii.headwind.fan.HeadwindProtocol

@Composable
fun HeadwindApp(
    state: FanUiState,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onOff: () -> Unit,
    modifier: Modifier = Modifier,
    fanName: String = "HEADWIND",
    onSettings: () -> Unit = {},
) {
    val speed = state.displayedSpeed
    val connected = state.canControl
    val setting = when {
        !connected -> "—"
        speed == 0 -> "STANDBY"
        else -> "$speed%"
    }
    val detail = when {
        !connected -> state.message
        state.requestedSpeed != null -> state.message
        state.status?.isStopped == true -> "Ready when you are"
        state.status?.mode != 4 -> "Sensor mode · tap + for manual"
        else -> "Level ${HeadwindProtocol.levels.indexOf(speed).takeIf { it >= 0 }?.plus(1) ?: "—"} of 6"
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color(0xFF091313))) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF76E5C3))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Increase fan speed"
                        stateDescription = if (connected) setting else state.message
                    }
                    .clickable(role = Role.Button, onClick = onIncrease),
            ) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 60.dp)
                        .padding(top = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding() + 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Box(Modifier.size(7.dp).background(if (connected) Color(0xFF115C49) else Color(0xFF667B73), CircleShape))
                    Text(fanName, color = Color(0xFF163A30), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("+", Modifier.align(Alignment.Center), color = Color(0xFF092F24), fontSize = 124.sp, fontWeight = FontWeight.Light)
                Text(
                    if (connected) "MORE" else state.message,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 24.dp),
                    color = Color(0xFF163A30), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF091313)))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF203A37))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Decrease fan speed"
                        stateDescription = "$setting. $detail"
                    }
                    .clickable(role = Role.Button, onClick = onDecrease).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("−", color = Color(0xFFE4F5EE), fontSize = 104.sp, fontWeight = FontWeight.Light, lineHeight = 108.sp)
                Text(setting, color = Color(0xFFB6F2DC), fontSize = if (speed == 0) 28.sp else 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text(detail, Modifier.padding(top = 8.dp), color = Color(0xFFC1D2CC), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF091313)))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF151E1D))
                    .semantics(mergeDescendants = true) { contentDescription = "Stop fan and leave it in standby" }
                    .clickable(role = Role.Button, onClick = onOff).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("OFF", color = Color(0xFFFFB4A1), fontSize = 64.sp, fontWeight = FontWeight.Medium, letterSpacing = 5.sp)
                Text("STOP · STAY READY", Modifier.padding(top = 16.dp), color = Color(0xFFB8C3BF), fontSize = 12.sp, letterSpacing = 2.sp)
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
