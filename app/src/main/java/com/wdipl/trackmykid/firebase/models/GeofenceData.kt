package com.wdipl.trackmykid.firebase.models

data class GeofenceData(val homeLat: Double, val homeLng: Double, val radius: Double){
    constructor():this(0.0, 0.0, 0.0)
}
