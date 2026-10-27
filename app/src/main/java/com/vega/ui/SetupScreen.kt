package com.vega.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vega.actions.CarSimActions
import com.vega.brain.Assistant
import com.vega.brain.PrivacyMode
import com.vega.data.TraceLog
import com.vega.service.VegaService
import com.vega.ui.theme.VegaBackground
import com.vega.ui.theme.VegaNight
import com.vega.ui.theme.VegaShape
import com.vega.ui.theme.VegaSize
import com.vega.ui.theme.VegaType

private val SAMPLES = listOf(
    "play hanuman chalisa on youtube",
    "pause",
    "take me to pune railway station",
    "find the nearest petrol pump",
    "call rohan",
    "message rohan I'm coming late",
    "add that I'll reach by 7",
    "send it",
    "it's too hot",
    "open my window halfway and switch off the AC",
    "how are you",
)

@Composable
fun SetupScreen(
    assistant: Assistant,
    missingPermissions: List<String>,
    canDrawOverlays: Boolean,
    onGrantPermissions: () -> Unit,
    onGrantOverlay: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val p = VegaNight
    val state by assistant.state.collectAsStateWithLifecycle()
    val caption by assistant.caption.collectAsStateWithLifecycle()
    val draft by assistant.draft.collectAsStateWithLifecycle()
    val car by assistant.car.state.collectAsStateWithLifecycle()
    val traces by TraceLog.entries.collectAsStateWithLifecycle()
    val running by VegaService.running.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (input.isNotBlank()) {
            assistant.submitText(input)
            input = ""
        }
    }

    Row(
        Modifier.fillMaxSize().background(VegaBackground).padding(VegaSize.EdgeMargin),
        horizontalArrangement = Arrangement.spacedBy(VegaSize.EdgeMargin),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VegaSize.Gap),
        ) {
            Text("Vega", style = VegaType.Greeting, color = p.text)
            Text(
                "State: ${state.name.lowercase()} · service ${if (running) "running" else "stopped"}",
                style = MaterialTheme.typography.bodyLarge,
                color = p.textSecondary,
            )

            Panel("Setup") {
                if (missingPermissions.isNotEmpty()) {
                    Text(
                        "Missing: " + missingPermissions.joinToString { it.substringAfterLast('.') },
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.warning,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VegaSize.Gap)) {
                    Button(onClick = onGrantPermissions, enabled = missingPermissions.isNotEmpty()) { Text("Permissions") }
                    Button(onClick = onGrantOverlay, enabled = !canDrawOverlays) { Text(if (canDrawOverlays) "Overlay ✓" else "Allow overlay") }
                    if (running) OutlinedButton(onClick = onStopService) { Text("Stop Vega") }
                    else Button(onClick = onStartService) { Text("Start Vega") }
                }
            }

            Panel("Command") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VegaSize.Gap)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Type what you would say") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                    )
                    Button(onClick = { submit() }) { Text("Run") }
                    Button(onClick = { assistant.toggleListening() }) { Text("Talk") }
                }
                SAMPLES.forEach { sample ->
                    Text(
                        "“$sample”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.listening,
                        modifier = Modifier.fillMaxWidth().clickable { assistant.submitText(sample) }.padding(vertical = 6.dp),
                    )
                }
            }

            SettingsPanel(assistant)
        }

        // Scrolls on its own so the reply stays in view on short screens.
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VegaSize.Gap),
        ) {
            Panel("Conversation") {
                Text("You: ${caption.user.ifEmpty { "…" }}", style = MaterialTheme.typography.bodyLarge, color = if (caption.userFinal) p.text else p.textSecondary)
                Text("Vega: ${caption.vega.ifEmpty { "…" }}", style = MaterialTheme.typography.bodyLarge, color = p.speaking)
                draft?.let { d ->
                    Text("${d.channel.uppercase()} to ${d.contactName}: ${d.body.ifEmpty { "(empty)" }}", style = MaterialTheme.typography.bodyLarge, color = p.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(VegaSize.Gap)) {
                        Button(onClick = { assistant.submitText("send it") }) { Text("Send") }
                        OutlinedButton(onClick = { assistant.submitText("cancel") }) { Text("Cancel") }
                    }
                }
            }
            CarPanel(car)
            Panel("Trace log") {
                if (traces.isEmpty()) Text("No commands yet", color = p.textSecondary)
                traces.forEach { e ->
                    Column {
                        Text("#${e.id} “${e.text}” · ${e.source} · ${e.totalMs} ms", style = MaterialTheme.typography.bodyMedium, color = p.text)
                        Text(e.stages, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(VegaNight.surface, VegaShape.Card)
            .border(1.dp, VegaNight.outline, VegaShape.Card)
            .padding(VegaSize.CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = VegaNight.text)
        content()
    }
}

@Composable
private fun CarPanel(car: CarSimActions.CarState) {
    Panel("Car (simulated)") {
        Text(
            "AC ${if (car.acOn) "on · ${car.tempC}°" else "off"} · fan ${car.fan} · ambient ${car.ambient}",
            style = MaterialTheme.typography.bodyLarge,
            color = VegaNight.text,
        )
        car.windows.forEach { (pos, pct) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pos.replace('_', ' ').replaceFirstChar { it.uppercase() }, Modifier.width(140.dp), color = VegaNight.textSecondary)
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.weight(1f))
                Text("  $pct%", Modifier.width(72.dp), color = VegaNight.text)
            }
        }
    }
}

@Composable
private fun SettingsPanel(assistant: Assistant) {
    val s = assistant.settings
    Panel("Settings") {
        SettingField("Driver name", s.driverName) { s.driverName = it }
        SettingField("Home address", s.homeAddress) { s.homeAddress = it }
        SettingField("Office address", s.officeAddress) { s.officeAddress = it }
        ChoiceRow("Default media app", listOf("youtube", "spotify"), s.defaultMediaApp) { s.defaultMediaApp = it }
        ChoiceRow("Voice", listOf("female", "male"), s.voice) { assistant.setVoice(it) }
        ChoiceRow("Message privacy", PrivacyMode.entries.map { it.key }, s.privacy.key) { key ->
            s.privacy = PrivacyMode.entries.first { it.key == key }
        }
    }
}

@Composable
private fun SettingField(label: String, initial: String, onChange: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(label: String, options: List<String>, initial: String, onPick: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(220.dp))
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = {
                    selected = option
                    onPick(option)
                },
                label = { Text(option) },
            )
        }
    }
}
