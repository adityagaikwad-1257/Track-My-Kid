package com.wdipl.trackmykid

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.window.OnBackInvokedDispatcher
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.databinding.ActivityEditNameBinding
import com.wdipl.trackmykid.firebase.PROFILE_IMAGE_URL
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.USER_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditNameActivity : AppCompatActivity() {

    private var _binding: ActivityEditNameBinding? = null
    private val binding: ActivityEditNameBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityEditNameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.view.setOnClickListener {
            finish()
        }

        binding.nameEt.requestFocus()

        binding.nameEt.setOnEditorActionListener { textView, i, keyEvent ->
            if (textView.text.trim().isEmpty()) return@setOnEditorActionListener true

            val userData = prefs?.userData!!
            userData.userName = textView.text.trim().toString()
            prefs?.userData = userData

            Firebase.firestore.collection(USERS)
                .document(prefs?.userData?.email!!)
                .update(USER_NAME, textView.text.toString())

            setResult(RESULT_OK)
            finish()
            return@setOnEditorActionListener true
        }
    }
}