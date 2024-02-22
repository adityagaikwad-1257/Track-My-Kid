package com.wdipl.trackmykid.welcome.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.onesignal.OneSignal
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.databinding.FragmentSignUpBinding
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.UserData
import com.wdipl.trackmykid.welcome.signIn.SignInResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignUpFragment : Fragment() {

    private val TAG = "adityatesting"

    private var _binding: FragmentSignUpBinding? = null
    private val binding: FragmentSignUpBinding
        get() = _binding!!

    private val auth = Firebase.auth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clickEvents()
    }

    private fun clickEvents() {
        binding.signIn.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.signUp.setOnClickListener {
            if (allOkay()) {
                signUp(
                    binding.email.editText?.text.toString().trim(),
                    binding.password.editText?.text.toString(),
                    binding.firstName.editText?.text.toString().trim(),
                    binding.lastNameEt.text.toString().trim()
                )
            }
        }
    }

    private fun signUp(
        email: String?, password: String?,
        firstName: String?, lastName: String?
    ) {
        if (email == null || password == null) return

        binding.progress.root.visibility = VISIBLE

        val signInResult = CoroutineScope(Dispatchers.IO).async {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()

                if (authResult.user == null) {
                    return@async SignInResult(null, "Something went wrong")
                }

                val userData = authResult.user?.run {
                    UserData(uid, "$firstName ${lastName ?: ""}", email.lowercase(), null)}!!

                Firebase.firestore.collection(USERS)
                    .document(email.lowercase())
                    .set(userData).await()

                prefs?.userData = userData

                SignInResult(
                    authResult.user?.run {
                        UserData(uid, displayName, email, photoUrl?.toString())
                    },
                    "User registered successfully"
                )
            } catch (e: Exception) {
                SignInResult(null, e.message)
            }
        }

        lifecycleScope.launch {
            val result = signInResult.await()
            Toast.makeText(context, "${result.errorMessage}", Toast.LENGTH_SHORT).show()
            binding.progress.root.visibility = GONE

            if (result.data != null){
                OneSignal.login(result.data.email!!)
                findNavController().navigate(R.id.action_signUpFragment2_to_chooseRoleFragment)
            }
        }

    }

    private fun allOkay(): Boolean {
        var allOkay = true
        if (binding.firstName.editText?.text.isNullOrBlank()) {
            binding.firstName.error = "Required"
            allOkay = false
        } else {
            binding.firstName.error = null
            binding.firstName.isErrorEnabled = false
        }

        if (binding.email.editText?.text.isNullOrBlank()) {
            binding.email.error = "Required"
            allOkay = false
        } else {
            binding.email.error = null
            binding.email.isErrorEnabled = false
        }

        if (binding.password.editText?.text?.length!! < 6) {
            binding.password.error = "Must be at least 8 characters"
            allOkay = false
        } else if (!binding.confirmPassword.editText?.text?.contentEquals(binding.password.editText?.text)!!) {
            binding.password.error = null
            binding.password.isErrorEnabled = false

            binding.confirmPassword.error = "Password doesn't match"
            allOkay = false
        } else {
            binding.password.error = null
            binding.confirmPassword.error = null

            binding.password.isErrorEnabled = false
            binding.confirmPassword.isErrorEnabled = false
        }

        return allOkay
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}