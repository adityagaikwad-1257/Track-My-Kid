package com.wdipl.trackmykid

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.WindowManager
import com.wdipl.trackmykid.databinding.DailogUserProfileBinding

class ProfileDialog(context: Context) {

    private val dialog: Dialog
    val binding: DailogUserProfileBinding

    init {
        dialog = Dialog(context)
        binding = DailogUserProfileBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        try {
            val back = ColorDrawable(Color.TRANSPARENT)
            val inset = InsetDrawable(back, 30)
            dialog.window!!.setBackgroundDrawable(inset)
        } catch (e: Exception) {
            // do nothing
        }

        try {
            val layoutParams = dialog.window!!.attributes
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            dialog.window!!.attributes = layoutParams
        } catch (e: Exception) {
            // do nothing
        }
    }

    fun show() = dialog.show()
}