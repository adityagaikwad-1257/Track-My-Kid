package com.wdipl.trackmykid.childhome.locationutils

import android.location.Location

interface LocationClient {

    @Throws(LocationException::class)
    fun getLocationUpdates(defaultLocationUpdates: DefaultLocationUpdates)

    class LocationException(message: String) : Exception(message)

    interface DefaultLocationUpdates {
        fun locationUpdates(location: Location?)
    }
}