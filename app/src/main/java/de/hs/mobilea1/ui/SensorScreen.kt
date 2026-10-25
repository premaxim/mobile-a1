package de.hs.mobilea1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SensorScreenActions(
    val onModeChange: (DisplayMode) -> Unit,
    val onSamplingRateChange: (AccelSamplingRate) -> Unit,
    val onDismissBanner: () -> Unit,
    val onStartPressureMeasurement: () -> Unit,
    val onToggleGnssMeasurement: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenLocationSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(state: UiState, charts: ChartProducers, actions: SensorScreenActions) {
    Scaffold(topBar = { TopAppBar(title = { Text("Sensor-Monitor") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = state.mode.ordinal) {
                DisplayMode.entries.forEach { mode ->
                    Tab(
                        selected = state.mode == mode,
                        onClick = { actions.onModeChange(mode) },
                        text = { Text(mode.label) },
                    )
                }
            }
            AnimatedVisibility(visible = state.banner != null) {
                WarningBanner(state.banner.orEmpty(), actions.onDismissBanner)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.mode) {
                    DisplayMode.VALUES -> ValueCards(state, actions)
                    DisplayMode.CHART -> ChartCards(state, charts, actions)
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
fun SensorCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SensorUnavailable() {
    Text(
        "Sensor nicht verfügbar",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SamplingRateSelector(selected: AccelSamplingRate, onSelect: (AccelSamplingRate) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AccelSamplingRate.entries.forEachIndexed { index, rate ->
            SegmentedButton(
                selected = rate == selected,
                onClick = { onSelect(rate) },
                shape = SegmentedButtonDefaults.itemShape(index, AccelSamplingRate.entries.size),
            ) { Text(rate.label) }
        }
    }
}

@Composable
fun GnssStatusContent(gnss: GnssUi, actions: SensorScreenActions, data: @Composable (GnssUi.Data) -> Unit) {
    when (gnss) {
        GnssUi.Unavailable -> SensorUnavailable()
        GnssUi.NoPermission -> {
            Text(
                "Standortberechtigung verweigert. Für GNSS-Werte wird der genaue Standort benötigt.",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.Button(onClick = actions.onOpenAppSettings) {
                Text("App-Einstellungen öffnen")
            }
        }
        GnssUi.Disabled -> {
            Text("GPS ist deaktiviert.", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.OutlinedButton(onClick = actions.onOpenLocationSettings) {
                Text("Standort-Einstellungen öffnen")
            }
        }
        GnssUi.Waiting -> Text("Warte auf GPS-Fix …", style = MaterialTheme.typography.bodyMedium)
        is GnssUi.Data -> data(gnss)
    }
}
