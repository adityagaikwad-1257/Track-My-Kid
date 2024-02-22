package com.wdipl.trackmykid.welcome.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.childhome.ChildHomeActivity
import com.wdipl.trackmykid.databinding.FragmentSplashBinding
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.Role
import com.wdipl.trackmykid.firebase.models.UserData
import com.wdipl.trackmykid.parenthome.ParentHomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding: FragmentSplashBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)

        if (prefs?.userData?.userId == null){
            findNavController().navigate(R.id.action_splashFragment_to_signInFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.splashFragment, true).build())
        }else{
            lifecycleScope.launch {
               prefs?.userData = Firebase.firestore.collection(USERS)
                   .document(prefs?.userData?.email!!)
                   .get().await().toObject(UserData::class.java)

                when (prefs?.userData?.role){
                    Role.PARENT.name -> {
                        startActivity(Intent(context, ParentHomeActivity::class.java))
                        activity?.finish()
                    }
                    Role.CHILD.name -> {
                        startActivity(Intent(context, ChildHomeActivity::class.java))
                        activity?.finish()
                    }
                    else -> findNavController().navigate(R.id.action_splashFragment_to_chooseRoleFragment,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.splashFragment, true).build())
                }
            }
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}