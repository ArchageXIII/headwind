package com.archagexiii.headwind.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archagexiii.headwind.fan.FanDevice

@Composable
fun FanSettings(
    selectedFan: FanDevice?,
    nearbyFans: List<FanDevice>,
    scanning: Boolean,
    message: String,
    connectionMessage: String,
    busy: Boolean,
    onScan: () -> Unit,
    onSelect: (String) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fan settings") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (selectedFan == null) "Choose your fan once. We’ll reconnect automatically next time." else "REMEMBERED FAN",
                    style = MaterialTheme.typography.labelMedium)
                if (selectedFan != null) {
                    Text(selectedFan.name, fontWeight = FontWeight.SemiBold)
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onForget, enabled = !busy) { Text("Forget fan") }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nearby fans", Modifier.padding(top = 14.dp), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onScan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Scan again") }
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
                nearbyFans.forEach { fan ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button) { onSelect(fan.address) }.padding(vertical = 14.dp),
                    ) {
                        Text(fan.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${fan.address.takeLast(8)}${if (fan.address == selectedFan?.address) " · Saved" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) Text("Finishing the current speed change…", style = MaterialTheme.typography.bodySmall)
                Text("Switching or forgetting a fan leaves its speed unchanged.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
