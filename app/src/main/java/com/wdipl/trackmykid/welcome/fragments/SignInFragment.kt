package com.wdipl.trackmykid.welcome.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.onesignal.OneSignal
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.childhome.ChildHomeActivity
import com.wdipl.trackmykid.databinding.FragmentSignInBinding
import com.wdipl.trackmykid.firebase.ROLE
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.Role
import com.wdipl.trackmykid.firebase.models.UserData
import com.wdipl.trackmykid.parenthome.ParentHomeActivity
import com.wdipl.trackmykid.welcome.signIn.GoogleAuthUiClient
import com.wdipl.trackmykid.welcome.signIn.SignInResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignInFragment() : Fragment() {

    private val TAG = "adityatesting"

    private var _binding: FragmentSignInBinding? = null
    private val binding: FragmentSignInBinding
        get() = _binding!!

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            requireContext(),
            Identity.getSignInClient(requireContext())
        )
    }

    private var auth = Firebase.auth

    private lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        launcher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ){

            if (it.resultCode == RESULT_OK){
                lifecycleScope.launch {
                    val signInResult = googleAuthUiClient.signInWithIntent(intent = it.data?: return@launch)

                    if (signInResult.data?.email != null){
                        var userData = Firebase.firestore
                            .collection(USERS)
                            .document(signInResult.data.email)
                            .get().await().toObject(UserData::class.java)

                        if (userData == null){
                            // new user with google
                            userData = signInResult.data
                        }else{
                            // already a user
                            // updating details
                            signInResult.data.run {
                                userData.userName = userName
                                userData.profilePictureUrl = profilePictureUrl
                            }
                        }

                        Firebase.firestore
                            .collection(USERS)
                            .document(signInResult.data.email.lowercase())
                            .set(userData).await()

                        prefs?.userData = userData

                        Toast.makeText(context, "Sign in successful", Toast.LENGTH_SHORT).show()

                        OneSignal.login(signInResult.data.email.lowercase())

                        when (prefs?.userData?.role){
                            Role.PARENT.name -> {
                                startActivity(Intent(context, ParentHomeActivity::class.java))
                                activity?.finish()
                            }
                            Role.CHILD.name -> {
                                startActivity(Intent(context, ChildHomeActivity::class.java))
                                activity?.finish()
                            }
                            else -> findNavController().navigate(R.id.action_signInFragment_to_chooseRoleFragment)
                        }
                    }else{
                        Toast.makeText(context, "${signInResult.errorMessage}", Toast.LENGTH_SHORT)
                            .show()
                    }

                    binding.progress.root.visibility = GONE
                }
            }else{
                binding.progress.root.visibility = GONE
                Toast.makeText(context, "Sign in cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        clickEvents()
    }

    private fun clickEvents() {
        binding.register.setOnClickListener{
            findNavController().navigate(R.id.action_signInFragment_to_signUpFragment2)
        }

        binding.signIn.setOnClickListener{
            if (allOkay())
                signInWithEmail(binding.email.editText?.text.toString(), binding.password.editText?.text.toString())
        }

        binding.forgotPassword.setOnClickListener {

        }

        binding.googleSignIn.setOnClickListener {
            lifecycleScope.launch {
                binding.progress.root.visibility = VISIBLE
                val intentSender = googleAuthUiClient.signIn()

                if (intentSender != null){
                    launcher.launch(IntentSenderRequest.Builder(
                        intentSender).build())
                }else{
                    binding.progress.root.visibility = GONE
                }
            }
        }
    }

    private fun allOkay(): Boolean {
        var allOkay = true

        if (binding.email.editText?.text.isNullOrBlank()) {
            binding.email.error = "Required"
            allOkay = false
        }else{
            binding.email.error = null
            binding.email.isErrorEnabled = false
        }

        if (binding.password.editText?.text?.length!! == 0) {
            binding.password.error = "Required"
            allOkay = false
        }else{
            binding.password.error = null
            binding.password.isErrorEnabled = false
        }

        return allOkay
    }

    private fun signInWithEmail(email: String, password: String) {
        binding.progress.root.visibility = VISIBLE

        val signInResult = CoroutineScope(Dispatchers.IO).async {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                if (authResult.user == null){
                    return@async SignInResult(null, "User not found")
                }

                prefs?.userData = Firebase.firestore.collection(USERS).document(email.lowercase()).get().await().toObject(UserData::class.java)

                SignInResult(prefs?.userData, "Sign in successful")
            }catch (e: Exception){
                var message = e.message

                if (e is FirebaseAuthInvalidCredentialsException)
                    message = "Invalid credentials"

                SignInResult(null, message)
            }
        }

        lifecycleScope.launch {
            val result = signInResult.await()
            Toast.makeText(context, "${result.errorMessage}", Toast.LENGTH_SHORT).show()
            binding.progress.root.visibility = GONE

            if (result.data != null){
                OneSignal.login(result.data.email!!)
                when (result.data.role){
                    Role.PARENT.name -> {
                        startActivity(Intent(context, ParentHomeActivity::class.java))
                        activity?.finish()
                    }
                    Role.CHILD.name -> {
                        startActivity(Intent(context, ChildHomeActivity::class.java))
                        activity?.finish()
                    }
                    else -> findNavController().navigate(R.id.action_signInFragment_to_chooseRoleFragment)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = SignInFragment()
    }
}