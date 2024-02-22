package com.wdipl.trackmykid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.wdipl.trackmykid.notifications.NotificationHelper
import com.wdipl.trackmykid.sharedpreference.UserPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App: Application()
{
    companion object {
        var prefs: UserPreference? = null
        lateinit var instance: App
            private set

        var notificationHelper: NotificationHelper? = null

        const val LOCATION_NOTIFICATION_CHANNEL_ID = "fs_location_updates"
        const val LOCATION_UPDATES_CHANNEL_NAME = "Location updates"
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        prefs = UserPreference(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)

        val importance = NotificationManager.IMPORTANCE_HIGH
        val mChannel = NotificationChannel(LOCATION_NOTIFICATION_CHANNEL_ID, LOCATION_UPDATES_CHANNEL_NAME, importance)
        mChannel.description = getString(R.string.location_channel_description)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)

        // Verbose Logging set to help debug issues, remove before releasing your app.
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // OneSignal Initialization
        OneSignal.initWithContext(this, getString(R.string.ONESIGNAL_APP_ID))

        // requestPermission will show the native Android notification permission prompt.
        // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }
}