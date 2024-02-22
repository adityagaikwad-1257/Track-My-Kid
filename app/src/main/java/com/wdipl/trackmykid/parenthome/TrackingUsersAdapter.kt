package com.wdipl.trackmykid.parenthome

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.wdipl.trackmykid.App.Companion.prefs
import com.wdipl.trackmykid.databinding.TrackedUserViewholderBinding

class TrackingUsersAdapter: ListAdapter<TrackingUser, TrackingUsersAdapter.TrackingUserViewHolder>(DIFF_UTILS) {

    companion object{
        private val DIFF_UTILS = object : DiffUtil.ItemCallback<TrackingUser>(){
            override fun areItemsTheSame(oldItem: TrackingUser, newItem: TrackingUser): Boolean {
                return oldItem.email == newItem.email
            }

            override fun areContentsTheSame(oldItem: TrackingUser, newItem: TrackingUser): Boolean {
                return oldItem == newItem
            }

        }
    }

    var updateTracker:((email: String, startTracking: Boolean) -> Unit)? = null

    inner class TrackingUserViewHolder(val binding: TrackedUserViewholderBinding): ViewHolder(binding.root){
        fun setData(trackingUser: TrackingUser){
            binding.userSwitch.text = trackingUser.locationUpdate.name
            binding.userSwitch.isChecked = prefs?.noTrackingUsers?.contains(trackingUser.email) == false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackingUserViewHolder {
        val binding = TrackedUserViewholderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackingUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackingUserViewHolder, position: Int) {
        holder.setData(getItem(position))
        holder.binding.userSwitch.setOnCheckedChangeListener{
            button, isChecked ->
            val noTrackingUsers = prefs?.noTrackingUsers?.toMutableSet() ?: return@setOnCheckedChangeListener

            if (!isChecked){
                noTrackingUsers.add(getItem(position).email)
            }else if (noTrackingUsers.contains(getItem(position).email)){
                noTrackingUsers.remove(getItem(position).email)
            }
            prefs?.noTrackingUsers = noTrackingUsers

            updateTracker?.invoke(getItem(position).email, isChecked)
        }
    }
}