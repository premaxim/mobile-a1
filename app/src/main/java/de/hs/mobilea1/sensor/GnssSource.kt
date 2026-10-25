package de.hs.mobilea1.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Standort-Updates ausschließlich über [LocationManager.GPS_PROVIDER]. */
class GnssSource(private val context: Context) {

    enum class StartResult { STARTED, NO_PERMISSION, UNAVAILABLE, DISABLED }

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    val isAvailable: Boolean
        get() = locationManager?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val isEnabled: Boolean
        get() = locationManager?.let {
            LocationManagerCompat.isLocationEnabled(it) &&
                it.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } == true

    fun start(onLocation: (Location) -> Unit, onProviderChanged: (enabled: Boolean) -> Unit): StartResult {
        val manager = locationManager
        if (manager == null || !isAvailable) return StartResult.UNAVAILABLE
        if (!hasPermission) return StartResult.NO_PERMISSION
        stop()
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)
            override fun onProviderEnabled(provider: String) = onProviderChanged(true)
            override fun onProviderDisabled(provider: String) = onProviderChanged(false)
        }
        return try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                newListener,
                Looper.getMainLooper(),
            )
            listener = newListener
            if (isEnabled) StartResult.STARTED else StartResult.DISABLED
        } catch (_: SecurityException) {
            StartResult.NO_PERMISSION
        } catch (_: IllegalArgumentException) {
            StartResult.UNAVAILABLE
        }
    }

    fun stop() {
        listener?.let { locationManager?.removeUpdates(it) }
        listener = null
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
