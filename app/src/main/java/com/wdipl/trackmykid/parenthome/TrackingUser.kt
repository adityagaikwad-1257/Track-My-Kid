package com.wdipl.trackmykid.parenthome

import com.wdipl.trackmykid.firebase.models.LocationUpdate

data class TrackingUser(val email: String, val locationUpdate: LocationUpdate)
