package com.wdipl.trackmykid.sharedpreference

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.onesignal.OneSignal
import com.wdipl.trackmykid.firebase.CONNECTIONS
import com.wdipl.trackmykid.firebase.PARENT_CONNECTION_CODE
import com.wdipl.trackmykid.firebase.models.UserData

class UserPreference(context: Context){

    companion object{
        private const val USER_DETAILS = "user_details"
        private const val USER_EMAIL = "user_data"
        private const val USER_FULL_NAME = "user_full_name"
        private const val USER_UID = "user_uid"
        private const val USER_ROLE = "user_role"
        private const val USER_IMAGE = "user_image"

        private const val LOCATION_DETAILS = "location_details"
        private const val NO_TRACKING_USERS = "no_tracking_users"

        private const val IS_USER_OUT_OF_GEOFENCE = "is_user_out_of_geofence"
    }

    private val userPrefs: SharedPreferences = context.getSharedPreferences(USER_DETAILS, MODE_PRIVATE)

    private var email: String?
        get() = userPrefs.getString(USER_EMAIL, null)
        set(value) = userPrefs.edit().putString(USER_EMAIL, value).apply()

    private var fullName: String?
        get() = userPrefs.getString(USER_FULL_NAME, null)
        set(value) = userPrefs.edit().putString(USER_FULL_NAME, value).apply()

    private var userUid: String?
        get() = userPrefs.getString(USER_UID, null)
        set(value) = userPrefs.edit().putString(USER_UID, value).apply()

    private var userRole: String?
        get() = userPrefs.getString(USER_ROLE, null)
        set(value) = userPrefs.edit().putString(USER_ROLE, value).apply()

    private var userImage: String?
        get() = userPrefs.getString(USER_IMAGE, null)
        set(value) = userPrefs.edit().putString(USER_IMAGE, value).apply()

    private var userConnections: ArrayList<String>
        get() {
            val conList = ArrayList<String>()
            val setToList = userPrefs.getStringSet(CONNECTIONS, HashSet())?.toList()
            if (setToList != null) conList.addAll(setToList)
            return conList
        }
        set(value) = userPrefs.edit().putStringSet(CONNECTIONS, value.toSet()).apply()

    private var parentConnectionCode: String?
        get() = userPrefs.getString(PARENT_CONNECTION_CODE, null)
        set(value) = userPrefs.edit().putString(PARENT_CONNECTION_CODE, value).apply()

    var userData: UserData?
        get() = UserData(
            userUid, fullName, email, userImage, userRole
        ).apply {
            parentConnectCode = parentConnectionCode
            connections = userConnections
        }
        set(value) {
            email = value?.email
            fullName = value?.userName
            userUid = value?.userId
            userRole = value?.role
            userImage = value?.profilePictureUrl
            userConnections = if (value?.connections == null) ArrayList() else value.connections
            parentConnectionCode = value?.parentConnectCode
        }

    fun sigOutUser(){
        userPrefs.edit().clear().apply()
        locationsPrefs.edit().clear().apply()
        OneSignal.logout()
    }

    private val locationsPrefs: SharedPreferences = context.getSharedPreferences(LOCATION_DETAILS, MODE_PRIVATE)

    var isUserOutOfGeofence: Boolean
        get() = locationsPrefs.getBoolean(IS_USER_OUT_OF_GEOFENCE, false)
        set(value) = locationsPrefs.edit().putBoolean(IS_USER_OUT_OF_GEOFENCE, value).apply()

    var noTrackingUsers: Set<String>
        get() = locationsPrefs.getStringSet(NO_TRACKING_USERS, null)?:HashSet()
        set(value) = locationsPrefs.edit().putStringSet(NO_TRACKING_USERS, value).apply()

}