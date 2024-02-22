package com.wdipl.trackmykid.childhome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import com.wdipl.trackmykid.App.Companion.notificationHelper
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.childhome.locationutils.LocationService
import com.wdipl.trackmykid.databinding.ActivityChildHomeBinding
import com.wdipl.trackmykid.firebase.CONNECTIONS
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.UserData
import com.wdipl.trackmykid.welcome.WelcomeActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChildHomeActivity : AppCompatActivity() {
    private val TAG = "adityatesting"

    private var _binding: ActivityChildHomeBinding? = null
    private val binding: ActivityChildHomeBinding
        get() = _binding!!

    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private lateinit var intentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityChildHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = "Hi, ${prefs?.userData?.userName}"

        if (0 == prefs?.userData?.connections?.size){
            binding.connectedView.visibility = GONE
            binding.noConnectionView.visibility = VISIBLE
        }else{
            lifecycleScope.launch {
                inflateParentsDetails(prefs?.userData?.connections?.get(0))
            }
        }

        intentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                // permission granted
            }
        }

        launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            if (it.containsKey(Manifest.permission.ACCESS_FINE_LOCATION)){
                // requested fine location
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                    startService(Intent(this, LocationService::class.java))
                }else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){
                    AlertDialog.Builder(this, R.style.AlertDialog).apply {
                        setTitle("Access location")
                        setMessage("We understand your privacy. Location permission is required to share your current location with your parent.")
                        setPositiveButton("Allow"){
                            dialog, which ->
                            dialog.dismiss()
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                        setNegativeButton("No, thanks"){
                            dialog, which -> dialog.dismiss()
                        }
                    }.show()
                }else{
                    AlertDialog.Builder(this, R.style.AlertDialog).apply {
                        setTitle("Access location")
                        setMessage("We understand your privacy. Location permission is required to share your current location with your parent.")
                        setPositiveButton("Settings"){
                                dialog, which ->
                            dialog.dismiss()
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                        }
                        setNegativeButton("No, thanks"){
                                dialog, which -> dialog.dismiss()
                        }
                    }.show()
                }
            }
        }

        if (prefs?.userData != null){
            if (prefs!!.userData!!.connections.size > 0){
                // already connected
                Log.d(TAG, "onCreate: Requesting location permission")
                launcher.launch(arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }

        clickEvents()
    }

    private fun clickEvents() {
        binding.signOut.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
            prefs?.sigOutUser()
            startActivity(Intent(this@ChildHomeActivity, WelcomeActivity::class.java))
            finish()
        }

        binding.connectBtn.setOnClickListener{
            if (allOkay()){
                binding.connectBtn.isEnabled = false
                binding.connectProgress.visibility = VISIBLE
                binding.connectTxt.text = null

                val email = binding.parentEmail.editText?.text.toString().lowercase()
                val cCode = binding.connectionCode.editText?.text.toString()

                lifecycleScope.launch {
                    val remoteUserData = Firebase.firestore.collection(USERS)
                        .document(email)
                        .get().await().toObject(UserData::class.java)

                    if (remoteUserData == null || remoteUserData.parentConnectCode != cCode){
                        Toast.makeText(
                            this@ChildHomeActivity,
                            "Invalid connection details",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (remoteUserData.connections.size >= 5){
                        Toast.makeText(
                            this@ChildHomeActivity,
                            "Parent room is full",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else{
                        remoteUserData.connections.add(prefs?.userData?.email!!)
                        Firebase.firestore.collection(USERS)
                            .document(email).update(CONNECTIONS, remoteUserData.connections)

                        val connectionsList = ArrayList<String>()
                        connectionsList.add(email)
                        Firebase.firestore.collection(USERS)
                            .document(prefs?.userData?.email!!).update(CONNECTIONS, connectionsList)

                        prefs?.userData = prefs?.userData?.copy()?.apply { connections = connectionsList}

                        inflateParentsDetails(email)

                        Log.d(TAG, "onCreate: Requesting location permission")
                        launcher.launch(arrayOf(
                            Manifest.permission.POST_NOTIFICATIONS,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }

                    binding.connectBtn.isEnabled = true
                    binding.connectProgress.visibility = GONE
                    binding.connectTxt.text = getString(R.string.connect)
                }
            }
        }

    }

    private fun inflateParentsDetails(parentEmail: String?) {
        if (parentEmail == null) return

        lifecycleScope.launch {
            val parentData = Firebase.firestore.collection(USERS)
                .document(parentEmail)
                .get()
                .await().toObject(UserData::class.java)

            binding.noConnectionView.visibility = GONE
            binding.connectedView.visibility = VISIBLE

            binding.parentName.text = parentData?.userName
            binding.parentEmailTxt.text = parentData?.email
        }
    }

    private fun allOkay(): Boolean {
        var allOkay = true

        if (binding.parentEmail.editText?.text.isNullOrBlank()) {
            binding.parentEmail.error = "Required"
            allOkay = false
        }else{
            binding.parentEmail.error = null
            binding.parentEmail.isErrorEnabled = false
        }

        if (binding.connectionCode.editText?.text?.length!! == 0) {
            binding.connectionCode.error = "Required"
            allOkay = false
        }else{
            binding.connectionCode.error = null
            binding.connectionCode.isErrorEnabled = false
        }

        return allOkay
    }
}