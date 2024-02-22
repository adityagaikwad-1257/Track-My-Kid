package com.wdipl.trackmykid.parenthome.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.R
import com.wdipl.trackmykid.databinding.FragmentParentNoConnectBinding
import com.wdipl.trackmykid.firebase.PARENT_CONNECTION_CODE
import com.wdipl.trackmykid.firebase.USERS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ParentNoConnectFragment private constructor(): Fragment() {

    private var _binding: FragmentParentNoConnectBinding? = null
    private val binding: FragmentParentNoConnectBinding
        get() = _binding!!

    private val fireStore = Firebase.firestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentParentNoConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var cCode = prefs?.userData?.parentConnectCode

        if (cCode == null){
            cCode = generateRandomCode()
            lifecycleScope.launch {
                prefs?.userData = prefs?.userData?.copy()?.apply { parentConnectCode = cCode }

                fireStore.collection(USERS)
                    .document(prefs?.userData?.email!!)
                    .update(PARENT_CONNECTION_CODE, cCode)
            }
        }

        binding.connectionCode.text = addSpaces(cCode)
    }

    private fun addSpaces(str: String?):String{
        val chars = str!!.toCharArray()
        return "${chars[0]} ${chars[1]} ${chars[2]} ${chars[3]} ${chars[4]} ${chars[5]}"
    }

    private fun generateRandomCode(): String =
        "${createRandomDigit()}${createRandomDigit()}${createRandomDigit()}${createRandomDigit()}${createRandomDigit()}${createRandomDigit()}"

    private fun createRandomDigit(): Int{
        return Random.nextInt(0, 9)
    }

    companion object{
        fun create(): ParentNoConnectFragment = ParentNoConnectFragment()
    }
}