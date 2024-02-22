package com.wdipl.trackmykid.notifications

import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.wdipl.trackmykid.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

class NotificationHelper(context: Context) {

    companion object{
        private const val TAG = "adityaNotificationHelper"
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://onesignal.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val apiService: NotificationApiService by lazy {
        retrofit.create(NotificationApiService::class.java)
    }

    fun sendNotification(toEmail: String, title: String, message: String, oneSignalAppId: String){

        CoroutineScope(Dispatchers.IO).launch {
            val response = apiService.sendNotification("Bearer ${BuildConfig.ONE_SIGNL_API_KEY}",
                RequestData().apply {
                    toExternalIds = arrayListOf(toEmail)
                    appId = oneSignalAppId
                    contents = Contents(en = message)
                    headings = Headings(en = title)
                    channelId = "c47fbd8f-adab-4020-8613-9efcdcbf4822"
                }
            )

            Log.d(TAG, "sendNotification: $response")
        }

    }

    interface NotificationApiService{
        @Headers(
            "Accept:application/json",
            "Content-type:application/json"
        )
        @POST("api/v1/notifications")
        suspend fun sendNotification(@Header("Authorization") authToken: String, @Body requestData: RequestData): Response<JSONObject>
    }

    data class RequestData (
        @SerializedName("include_external_user_ids" ) var toExternalIds : ArrayList<String>? = null,
        @SerializedName("app_id"                    ) var appId            : String?           = null,
        @SerializedName("contents"                  ) var contents         : Contents?         = null,
        @SerializedName("headings"                  ) var headings         : Headings?         = null,
        @SerializedName("android_channel_id"        ) var channelId        : String?           = null
    )

    data class Contents (
        @SerializedName("en" ) var en : String? = null
    )

    data class Headings (
        @SerializedName("en" ) var en : String? = null
    )
}