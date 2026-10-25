package de.hs.mobilea1

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hs.mobilea1.ui.ChartProducers
import de.hs.mobilea1.ui.SensorScreen
import de.hs.mobilea1.ui.SensorScreenActions
import de.hs.mobilea1.ui.SensorViewModel
import de.hs.mobilea1.ui.theme.MobileA1Theme

class MainActivity : ComponentActivity() {

    private val viewModel: SensorViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onPermissionsChanged()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestMissingPermissions()

        val charts = ChartProducers(viewModel.accelChart, viewModel.barometerChart, viewModel.gnssChart)
        val actions = SensorScreenActions(
            onModeChange = viewModel::setMode,
            onSamplingRateChange = viewModel::setAccelSamplingRate,
            onDismissBanner = viewModel::dismissBanner,
            onStartPressureMeasurement = viewModel::startPressureMeasurement,
            onOpenAppSettings = ::openAppSettings,
            onOpenLocationSettings = ::openLocationSettings,
        )
        setContent {
            MobileA1Theme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SensorScreen(state, remember { charts }, remember { actions })
            }
        }
    }

    /** Alle Sensor-Listener und GNSS-Updates werden hier (wieder) angemeldet. */
    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    /** Im Hintergrund wird nicht gemessen: alle Listener abmelden. */
    override fun onStop() {
        viewModel.onStop()
        super.onStop()
    }

    private fun requestMissingPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun openAppSettings() {
        startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun openLocationSettings() {
        startSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
