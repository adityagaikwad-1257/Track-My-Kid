package com.wdipl.trackmykid.welcome.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.childhome.ChildHomeActivity
import com.wdipl.trackmykid.databinding.FragmentChooseRoleBinding
import com.wdipl.trackmykid.firebase.USERS
import com.wdipl.trackmykid.firebase.models.Role
import com.wdipl.trackmykid.parenthome.ParentHomeActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChooseRoleFragment : Fragment() {

    private var _binding: FragmentChooseRoleBinding? = null
    private val binding: FragmentChooseRoleBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChooseRoleBinding.inflate(inflater, container, false)
        binding.rolesTab.addTab(binding.rolesTab.newTab().apply {
            text = "Parent"
        })
        binding.rolesTab.addTab(binding.rolesTab.newTab().apply {
            text = "Child"
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.continueBtn.setOnClickListener{
            binding.continueTxt.visibility = GONE
            binding.progress.visibility = VISIBLE
            binding.continueBtn.isEnabled = false

            lifecycleScope.launch {
                try {
                    Firebase.firestore.collection(USERS)
                        .document(prefs?.userData?.email!!.lowercase())
                        .update("role", getRole()).await()

                    when (binding.rolesTab.selectedTabPosition){
                        0 -> {
                            startActivity(Intent(context, ParentHomeActivity::class.java))
                            activity?.finish()
                        }
                        1 -> {
                            startActivity(Intent(context, ChildHomeActivity::class.java))
                            activity?.finish()
                        }
                    }

                    activity?.finish()
                }catch (e: Exception){
                    Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
    }

    private fun getRole(): String {
        return when (binding.rolesTab.selectedTabPosition){
           0 -> Role.PARENT.name
           1 -> Role.CHILD.name
           else -> Role.NONE.name
        }
    }
}