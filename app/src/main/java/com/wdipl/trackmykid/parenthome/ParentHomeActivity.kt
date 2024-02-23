package com.wdipl.trackmykid.parenthome

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.gms.tasks.Tasks.await
import com.google.android.libraries.places.api.Places
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.BuildConfig
import com.wdipl.trackmykid.EditNameActivity
import com.wdipl.trackmykid.ProfileDialog
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.databinding.ActivityParentHomeBinding
import com.wdipl.trackmykid.databinding.BsParentSettingsBinding
import com.wdipl.trackmykid.firebase.LOCATIONS
import com.wdipl.trackmykid.firebase.PROFILE_IMAGES
import com.wdipl.trackmykid.firebase.PROFILE_IMAGE_URL
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.LocationUpdate
import com.wdipl.trackmykid.firebase.models.UserData
import com.wdipl.trackmykid.parenthome.fragments.MapsFragment
import com.wdipl.trackmykid.parenthome.fragments.ParentNoConnectFragment
import com.wdipl.trackmykid.welcome.WelcomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ParentHomeActivity : AppCompatActivity() {

    private var _binding: ActivityParentHomeBinding? = null
    private val binding: ActivityParentHomeBinding
        get() = _binding!!

    private var _bsBinding: BsParentSettingsBinding? = null
    private val bsBinding: BsParentSettingsBinding
        get() = _bsBinding!!

    private var _settingsBs: BottomSheetDialog? = null
    private val settingsBs: BottomSheetDialog
        get() = _settingsBs!!

    private val profileDialog: ProfileDialog by lazy { ProfileDialog(this) }

    private val trackingUsersAdapter: TrackingUsersAdapter by lazy { TrackingUsersAdapter() }

    private var imagePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var resultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityParentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = "Hi, ${prefs?.userData?.userName}"

        _bsBinding = BsParentSettingsBinding.inflate(layoutInflater)
        _settingsBs = BottomSheetDialog(this)
        settingsBs.setContentView(bsBinding.root)

        initViews()

        clickEvents()

        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)

        if (prefs?.userData != null) {
            if (prefs!!.userData!!.connections.size > 0) {
                val mapsFragment = MapsFragment.create()
                trackingUsersAdapter.updateTracker = mapsFragment::updateCurrentMarkerForUser
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fcv_parent_home, mapsFragment)
                    .commitAllowingStateLoss()
            } else if (prefs!!.userData!!.email != null) {
                Firebase.firestore.collection(USERS)
                    .document(prefs?.userData?.email!!)
                    .addSnapshotListener(this) { doc, error ->
                        if (error != null) {
                            Toast.makeText(this, "${error.message}", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }

                        if (doc != null && doc.exists()) {
                            val userData = doc.toObject(UserData::class.java)
                            if (userData != null && userData.connections.size > 0) {
                                val mapsFragment = MapsFragment.create()
                                trackingUsersAdapter.updateTracker = mapsFragment::updateCurrentMarkerForUser
                                supportFragmentManager.beginTransaction()
                                    .replace(R.id.fcv_parent_home, mapsFragment)
                                    .commitAllowingStateLoss()
                            } else {
                                supportFragmentManager.beginTransaction()
                                    .replace(R.id.fcv_parent_home, ParentNoConnectFragment.create())
                                    .commitAllowingStateLoss()
                            }
                        }
                    }
            }
        }

        if (prefs?.userData?.email != null) {
            lifecycleScope.launch {
                val userData = Firebase.firestore.collection(USERS)
                    .document(prefs?.userData?.email!!)
                    .get().await().toObject(UserData::class.java)

                prefs?.userData = userData
                inflateUserData(userData)
            }
        }
    }

    private fun initViews() {
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

                        Glide.with(this@ParentHomeActivity)
                            .load(it.data?.data)
                            .fitCenter()
                            .error(R.drawable.ic_no_user)
                            .placeholder(android.R.color.darker_gray)
                            .into(binding.profileImg)

                        Glide.with(this@ParentHomeActivity)
                            .load(it.data?.data)
                            .fitCenter()
                            .error(R.drawable.ic_no_user)
                            .placeholder(android.R.color.darker_gray)
                            .into(profileDialog.binding.userImage)

                        Toast.makeText(
                            this@ParentHomeActivity,
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

        bsBinding.rvChild.layoutManager = LinearLayoutManager(this)
        bsBinding.rvChild.adapter = trackingUsersAdapter

        lifecycleScope.launch {
            try {
                Firebase.firestore.collection(LOCATIONS)
                    .document(prefs?.userData?.email!!)
                    .collection(LOCATIONS)
                    .addSnapshotListener(this@ParentHomeActivity) { snap, error ->
                        if (error != null) return@addSnapshotListener

                        if (snap != null) {
                            val trackingUsers = ArrayList<TrackingUser>()
                            snap.documents.forEach {
                                val locationUpdate = it.toObject(LocationUpdate::class.java)
                                if (locationUpdate != null)
                                    trackingUsers.add(TrackingUser(it.id, locationUpdate))
                            }

                            trackingUsersAdapter.submitList(trackingUsers)
                        }
                    }
            } catch (e: Exception) {
                // do nothing
            }
        }
    }

    private fun clickEvents() {
        binding.settings.setOnClickListener {
            settingsBs.show()
        }

        binding.profile.setOnClickListener {
            profileDialog.show()
        }

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
    }

    private fun inflateUserData(userData: UserData?) {
        if (userData == null) return
        binding.title.text = "Hi, ${userData.userName}"

        if (userData.parentConnectCode != null)
            bsBinding.connectionCode.text = addSpaces(userData.parentConnectCode)

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

    private fun addSpaces(str: String?): String {
        val chars = str!!.toCharArray()
        return "${chars[0]} ${chars[1]} ${chars[2]} ${chars[3]} ${chars[4]} ${chars[5]}"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Toast.makeText(this, "apna", Toast.LENGTH_SHORT).show()
        return super.onOptionsItemSelected(item)
    }

    private fun signOut() {
        prefs?.sigOutUser()
        startActivity(Intent(this@ParentHomeActivity, WelcomeActivity::class.java))
        finish()
    }
}