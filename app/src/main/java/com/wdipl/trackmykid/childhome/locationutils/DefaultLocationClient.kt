package com.wdipl.trackmykid.childhome.locationutils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task

class DefaultLocationClient(private val context: Context,
                            private val fusedLocationProviderClient: FusedLocationProviderClient):LocationClient {

    companion object{
        private const val LOCATION_UPDATES_INTERVAL:Long = 5 * 1000
        private const val LOCATION_UPDATES_MIN_INTERVAL:Long = 60 * 1000
        private const val LOCATION_UPDATES_MIN_DISPLACEMENT: Float = (100).toFloat()

        private const val TAG = LocationService.TAG;
    }

    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null

    override fun getLocationUpdates(
        defaultLocationUpdates: LocationClient.DefaultLocationUpdates
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            throw LocationClient.LocationException("No location permission")
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) throw LocationClient.LocationException("Gps is off")

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATES_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_UPDATES_MIN_INTERVAL)
            .setMinUpdateDistanceMeters(LOCATION_UPDATES_MIN_DISPLACEMENT)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                defaultLocationUpdates.locationUpdates(if (locationResult.locations.isEmpty()) null else locationResult.locations[0])
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest!!, locationCallback!!, Looper.getMainLooper())
    }

    fun removeLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback!!)
                .addOnCompleteListener { task: Task<Void?> ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "removeLocationUpdates: ")
                    }
                }
        }
    }
}
