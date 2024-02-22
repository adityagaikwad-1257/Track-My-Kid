package com.wdipl.trackmykid.firebase.models

import com.google.firebase.firestore.GeoPoint

data class LocationUpdate(val name: String?, val geoPoint: GeoPoint){
    constructor():this("No name", GeoPoint(0.0, 0.0))
}
