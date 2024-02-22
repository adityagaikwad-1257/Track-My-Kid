package com.wdipl.trackmykid.childhome.locationutils

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App
import com.wdipl.trackmykid.App.Companion.LOCATION_NOTIFICATION_CHANNEL_ID
import com.wdipl.trackmykid.App.Companion.notificationHelper
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.BuildConfig
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.firebase.GEOFENCES
import com.wdipl.trackmykid.firebase.LOCATIONS
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.GeofenceData
import com.wdipl.trackmykid.firebase.models.LocationUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationService: Service(), LocationClient.DefaultLocationUpdates {

    companion object{
        const val TAG = "aditya_LocationService"

        const val LOCATION_UPDATES_NOTIFICATION_ID = 12
    }

    private lateinit var locationClient:DefaultLocationClient

    override fun onCreate() {
        super.onCreate()
        locationClient = DefaultLocationClient(this, LocationServices.getFusedLocationProviderClient(this))
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val notification: Notification =
            NotificationCompat.Builder(this, LOCATION_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Sharing location with your parent")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(Notification.PRIORITY_HIGH)
                .setOngoing(true)
                .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            Log.d(TAG, "startLocationUpdates: ")
            locationClient.getLocationUpdates(this)
        } catch (e: LocationClient.LocationException) {
            // do nothing
            Log.e(TAG, "startLocationUpdates: ", e)
        }

        startForeground(LOCATION_UPDATES_NOTIFICATION_ID, notification)
        notificationManager.notify(LocationService.LOCATION_UPDATES_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient.removeLocationUpdates()
    }

    override fun locationUpdates(location: Location?) {
        Log.d(TAG, "locationUpdates: {${location?.latitude}, ${location?.longitude})")
        if (location != null && prefs?.userData != null) {
            if (prefs!!.userData!!.connections.size == 0) return

            // sharing location updates
            CoroutineScope(Dispatchers.IO).launch {
                Firebase.firestore.collection(LOCATIONS)
                    .document(prefs!!.userData!!.connections[0])
                    .collection(LOCATIONS)
                    .document(prefs!!.userData!!.email!!)
                    .set(LocationUpdate(prefs!!.userData!!.userName, GeoPoint(location.latitude, location.longitude)))

            }

            // checking geofence
            CoroutineScope(Dispatchers.IO).launch {

                val geofence = try {
                    Firebase.firestore.collection(GEOFENCES)
                        .document(prefs!!.userData!!.connections[0])
                        .get().await().toObject(GeofenceData::class.java)
                }catch (e: Exception){
                    null
                } ?: return@launch

                val homeLocation = Location("homeLocation")
                homeLocation.latitude = geofence.homeLat
                homeLocation.longitude = geofence.homeLng

                val distance = homeLocation.distanceTo(location)

                Log.d(TAG, "locationUpdates: Geofence = $geofence")
                Log.d(TAG, "locationUpdates: Distance = $distance")

                if (distance > geofence.radius){
                    Log.d(TAG, "locationUpdates: OUT OF GEOFENCE")
                    // out of geofence
                    if (!prefs?.isUserOutOfGeofence!!){
                        Log.d(TAG, "locationUpdates: SENDING NOTIFICATION")
                        notificationHelper?.sendNotification(
                            prefs?.userData?.connections?.get(0)!!,
                            "${prefs?.userData?.userName} is out of geofence",
                            "${String.format("%.2f", (distance/1000))} kms away from home",
                            BuildConfig.ONE_SIGNAL_APP_ID
                        )
                    }

                    prefs?.isUserOutOfGeofence = true
                }else{
                    Log.d(TAG, "locationUpdates: INSIDE GEOFENCE")
                    prefs?.isUserOutOfGeofence = false
                }
            }

        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}