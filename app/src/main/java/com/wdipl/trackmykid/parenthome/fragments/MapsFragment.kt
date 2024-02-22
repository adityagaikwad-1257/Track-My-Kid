package com.wdipl.trackmykid.parenthome.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.databinding.FragmentMapsBinding
import com.wdipl.trackmykid.firebase.GEOFENCES
import com.wdipl.trackmykid.firebase.LOCATIONS
import com.wdipl.trackmykid.firebase.models.GeofenceData
import com.wdipl.trackmykid.firebase.models.LocationUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapsFragment : Fragment() {
    
    private var _binding: FragmentMapsBinding? = null
    private val binding: FragmentMapsBinding
        get() = _binding!!

    private val MAX_RADIUS: Double = 5000.0

    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private lateinit var mGoogleMap: GoogleMap

    private var locationListener: ListenerRegistration? = null

    private val currentLocMarkers = HashMap<String, Marker>()
    private val locationUpdatesMap = HashMap<String, LocationUpdate>()

    private var currentHomeMarker: Marker? = null
    private var currentCircle: Circle? = null

    private var currentGeofence: GeofenceData? = null

    private lateinit var searchLauncher: ActivityResultLauncher<Intent>
    private lateinit var searchIntent: Intent

    private val callback = OnMapReadyCallback { googleMap ->
        this.mGoogleMap = googleMap

        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        try {
            googleMap.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            // do nothing
        }

        googleMap.setOnMapLongClickListener {
            if (binding.bsGeofence.visibility == GONE) return@setOnMapLongClickListener
            
            updateHomeLocationMarker(it)
            updateCircle(currentCircle?.radius)

            binding.updateGeofenceBtn.backgroundTintList = ColorStateList.valueOf(requireActivity().getColor(R.color.my_color_primary))
            binding.updateGeofenceBtn.isEnabled = true
            binding.radiusSeek.isEnabled = true
        }
        
        if (prefs?.userData?.email != null){
            Firebase.firestore.collection(LOCATIONS)
                .document(prefs?.userData?.email!!)
                .collection(LOCATIONS).addSnapshotListener { locations_snap, error -> 
                    if (error != null){
                        Toast.makeText(context, "${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    locations_snap?.documentChanges?.forEach {
                        val locationUpdate = it.document.toObject(LocationUpdate::class.java)

                        if (prefs?.noTrackingUsers?.contains(it.document.id) == false) {
                            updateCurrentLocationMarker(it.document.id,
                                locationUpdate.name?:"No name",
                                LatLng(locationUpdate.geoPoint.latitude, locationUpdate.geoPoint.longitude))
                        }

                        locationUpdatesMap[it.document.id] = locationUpdate
                    }
                }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)

        launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            try {
                mGoogleMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                // do nothing
            }
        }

        searchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            if (it.resultCode == Activity.RESULT_OK) {
                val intent = it.data
                if (intent != null) {
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    if (place.latLng != null){
                        updateHomeLocationMarker(place.latLng)
                        updateCircle(currentCircle?.radius)

                        binding.updateGeofenceBtn.backgroundTintList = ColorStateList.valueOf(requireActivity().getColor(R.color.my_color_primary))
                        binding.updateGeofenceBtn.isEnabled = true
                        binding.radiusSeek.isEnabled = true
                    }
                }
            }
        }

        searchIntent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)).build(requireContext())

        if (prefs?.userData?.email != null){
            lifecycleScope.launch {
                currentGeofence = Firebase.firestore.collection(GEOFENCES)
                    .document(prefs?.userData?.email!!).get().await().toObject(GeofenceData::class.java)

                inflateCurrentGeofence()
            }
        }

        initViews()

        clickEvents()
    }

    private fun initViews() {
        binding.radiusSeek.setOnSeekBarChangeListener(object :OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                
                Log.d(TAG, "onProgressChanged: $progress")
                binding.geofenceRad.text = String.format("%.1f kms", (50 * progress.toFloat()/1000))
                updateCircle(progress.toDouble() * 50)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun clickEvents() {
        binding.geofenceFab.setOnClickListener {
            binding.geofenceFab.visibility = GONE
            binding.bsGeofence.visibility = VISIBLE
            binding.searchTxt.visibility = VISIBLE

            if (currentHomeMarker == null){
                binding.updateGeofenceBtn.backgroundTintList = ColorStateList.valueOf(requireActivity().getColor(android.R.color.darker_gray))
                binding.updateGeofenceBtn.isEnabled = false
                binding.radiusSeek.isEnabled = false
                binding.radiusSeek.progressBackgroundTintList = ColorStateList.valueOf(requireActivity().getColor(android.R.color.darker_gray))
            }else{
                binding.updateGeofenceBtn.backgroundTintList = ColorStateList.valueOf(requireActivity().getColor(R.color.my_color_primary))
                binding.updateGeofenceBtn.isEnabled = true
                binding.radiusSeek.isEnabled = true
            }
        }

        binding.closeBsGeofence.setOnClickListener {
            binding.geofenceFab.visibility = VISIBLE
            binding.bsGeofence.visibility = GONE
            binding.searchTxt.visibility = GONE

            inflateCurrentGeofence()
        }
        
        binding.updateGeofenceBtn.setOnClickListener { 
            if (currentCircle == null || currentCircle!!.radius == 0.toDouble()){
                Toast.makeText(context, "Select radius for geofence", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (currentHomeMarker == null){
                Toast.makeText(context, "Please select a geofence center", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (prefs?.userData?.email == null) return@setOnClickListener

            lifecycleScope.launch {
                binding.progress.root.visibility = VISIBLE

                val geofenceData = GeofenceData(currentHomeMarker?.position?.latitude!!, currentHomeMarker?.position?.longitude!!, currentCircle?.radius!!)

                try {
                    Firebase.firestore.collection(GEOFENCES)
                        .document(prefs?.userData?.email!!)
                        .set(geofenceData)
                        .await()

                    Toast.makeText(context, "Geofence updated successfully", Toast.LENGTH_SHORT).show()
                    currentGeofence = geofenceData
                } catch (e: Exception) {
                    Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                }

                binding.progress.root.visibility = GONE

                binding.geofenceFab.visibility = VISIBLE
                binding.bsGeofence.visibility = GONE
                binding.searchTxt.visibility = GONE

                inflateCurrentGeofence()
            }
        }

        binding.searchTxt.setOnClickListener {
            searchLauncher.launch(searchIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationListener?.remove()
    }

    private fun inflateCurrentGeofence(){
        if (currentGeofence?.homeLat != null){
            updateHomeLocationMarker(LatLng(currentGeofence?.homeLat!!, currentGeofence?.homeLng!!))
        }else{
            updateHomeLocationMarker(null)
        }

        if (currentGeofence?.radius != null){
            updateCircle(currentGeofence?.radius!!) 
        }else{
            updateCircle(null)
        }

        updateGeofenceBs()
    }

    private fun updateGeofenceBs() {
        if (currentGeofence?.radius == null){
            binding.geofenceRad.text = String.format("%.1f kms", (0).toDouble())
            binding.radiusSeek.progress = 0
            return
        }
        val radius = currentGeofence?.radius!!.toDouble()
        binding.geofenceRad.text = String.format("%.1f kms", (radius / 1000))
        binding.radiusSeek.progress = ((radius / MAX_RADIUS) * 100).toInt()
    }

    private fun updateHomeLocationMarker(latLng: LatLng?){
        currentHomeMarker?.remove()
        currentHomeMarker = null

        if (latLng == null) return

        mGoogleMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(latLng)
                    .zoom(18f)
                    .bearing(0f)
                    .tilt(30f).build()
            )
        )

        currentHomeMarker = mGoogleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_home_loc_marker))
        )

    }

    private fun updateCurrentLocationMarker(email: String, title: String, latLng: LatLng) {
        if (currentLocMarkers.containsKey(email)){
            currentLocMarkers[email]?.remove()
            currentLocMarkers.remove(email)
        }

        mGoogleMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(latLng)
                    .zoom(16f)
                    .bearing(0f)
                    .tilt(30f).build()
            )
        )

        val marker = mGoogleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("$title at New link road")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_cur_loc_marker))
        )

        if (marker != null) currentLocMarkers[email] = marker
    }

    fun updateCircle(radius: Double?){
        if (currentHomeMarker?.position == null || radius == null){
            currentCircle?.remove()
            currentCircle = null
            return
        }

        val circleOptions = CircleOptions()
        circleOptions.center(currentHomeMarker?.position!!)
        circleOptions.radius(radius)
        circleOptions.strokeWidth(3f)
        circleOptions.strokeColor(context?.getColor(R.color.black)!!)
        circleOptions.fillColor(Color.argb(30, 0, 0, 0))

        val lastCircle = currentCircle
        currentCircle = mGoogleMap.addCircle(circleOptions)
        lastCircle?.remove()

        mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentHomeMarker?.position!!, getZoomLevel(currentCircle)))
    }

    private fun getZoomLevel(circle: Circle?): Float {
        var zoomLevel = 18f
        if (circle != null && circle.radius != 0.toDouble()) {
            val radius = circle.radius + circle.radius / 2
            val scale = radius / 500
            zoomLevel = (16 - Math.log(scale) / Math.log(2.0)).toFloat()
        }
        return zoomLevel
    }

    fun updateCurrentMarkerForUser(email: String, startTracking: Boolean){
        if (startTracking){
            if (!currentLocMarkers.containsKey(email)){
                // marker is not marked for this user
                val locationUpdate = locationUpdatesMap[email]
                if (locationUpdate != null)
                    updateCurrentLocationMarker(email, locationUpdate.name?:"No name",
                        LatLng(locationUpdate.geoPoint.latitude, locationUpdate.geoPoint.longitude))
            }
        }else{
            // stop tracking
            if (currentLocMarkers.containsKey(email)){
                currentLocMarkers[email]?.remove()
                currentLocMarkers.remove(email)
            }
        }
    }

    companion object {
        fun create(): MapsFragment = MapsFragment()

        const val TAG = "adityatesting"
    }
}