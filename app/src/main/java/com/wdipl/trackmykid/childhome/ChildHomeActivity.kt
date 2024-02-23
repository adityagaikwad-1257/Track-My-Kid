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
import com.bumptech.glide.Glide
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.storage.ktx.storage
import com.wdipl.trackmykid.App.Companion.notificationHelper
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.EditNameActivity
import com.wdipl.trackmykid.ProfileDialog
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.childhome.locationutils.LocationService
import com.wdipl.trackmykid.databinding.ActivityChildHomeBinding
import com.wdipl.trackmykid.firebase.CONNECTIONS
import com.wdipl.trackmykid.firebase.PROFILE_IMAGES
import com.wdipl.trackmykid.firebase.PROFILE_IMAGE_URL
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

    private val profileDialog: ProfileDialog by lazy { ProfileDialog(this) }

    private var imagePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityChildHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inflateUserData(prefs?.userData)

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

        initView()

        clickEvents()
    }

    private fun initView(){
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (prefs?.userData?.email != null && it?.data?.data != null){

                    lifecycleScope.launch {
                        profileDialog.binding.progress.visibility = VISIBLE

                        val imageUri = Firebase.storage.reference
                            .child(PROFILE_IMAGES)
                            .child(prefs?.userData?.email!!)
                            .putFile(it.data?.data!!).continueWithTask {task ->
                                if (!task.isSuccessful) return@continueWithTask null

                                Firebase.storage.reference
                                    .child(PROFILE_IMAGES)
                                    .child(prefs?.userData?.email!!)
                                    .downloadUrl
                            }.await()

                        Firebase.firestore.collection(USERS)
                            .document(prefs?.userData?.email!!)
                            .update(PROFILE_IMAGE_URL, imageUri.toString()).await()

                        profileDialog.binding.progress.visibility = GONE

                        Glide.with(this@ChildHomeActivity)
                            .load(it.data?.data)
                            .fitCenter()
                            .error(R.drawable.ic_no_user)
                            .placeholder(android.R.color.darker_gray)
                            .into(binding.profileImg)

                        Glide.with(this@ChildHomeActivity)
                            .load(it.data?.data)
                            .fitCenter()
                            .error(R.drawable.ic_no_user)
                            .placeholder(android.R.color.darker_gray)
                            .into(profileDialog.binding.userImage)

                        Toast.makeText(
                            this@ChildHomeActivity,
                            "Profile image updated.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else if (it.resultCode == ImagePicker.RESULT_ERROR) {
                Toast.makeText(this, ImagePicker.getError(it.data), Toast.LENGTH_SHORT).show()
            }
        }

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == RESULT_OK){
                binding.title.text = "Hi, ${prefs?.userData?.userName}"
                profileDialog.binding.userName.text = "${prefs?.userData?.userName}"
            }
        }
    }

    private fun clickEvents() {
        profileDialog.binding.signOutBtn.setOnClickListener {
            AlertDialog.Builder(this, R.style.AlertDialog).apply {
                setTitle("Are you sure?")
                setMessage("Do you want to sign out?")
                setPositiveButton("No") { dialog, which -> dialog.dismiss() }
                setNegativeButton("Yes") { dialog, which ->
                    dialog.dismiss()
                    signOut()
                }
            }.show()
        }

        binding.profile.setOnClickListener {
            profileDialog.show()
        }

        profileDialog.binding.userImage.setOnClickListener {
            if (profileDialog.binding.progress.visibility == VISIBLE) return@setOnClickListener

            ImagePicker.with(this)
                .cropSquare()
                .compress(512)
                .galleryOnly()
                .createIntent {
                    imagePickerLauncher?.launch(it)
                }
        }

        profileDialog.binding.userName.setOnClickListener {
            resultLauncher?.launch(Intent(this, EditNameActivity::class.java))
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

    private fun inflateUserData(userData: UserData?) {
        if (userData == null) return
        binding.title.text = "Hi, ${userData.userName}"

        profileDialog.binding.userName.setText("${userData.userName}")
        profileDialog.binding.userEmail.text = "${userData.email}"

        if (userData.profilePictureUrl != null) {
            Glide.with(this)
                .load(userData.profilePictureUrl)
                .placeholder(android.R.color.darker_gray)
                .error(R.drawable.ic_no_user)
                .fitCenter()
                .into(profileDialog.binding.userImage)

            Glide.with(this)
                .load(userData.profilePictureUrl)
                .placeholder(android.R.color.darker_gray)
                .error(R.drawable.ic_no_user)
                .fitCenter()
                .into(binding.profileImg)
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

    fun signOut(){
        stopService(Intent(this, LocationService::class.java))
        prefs?.sigOutUser()
        startActivity(Intent(this@ChildHomeActivity, WelcomeActivity::class.java))
        finish()
    }
}