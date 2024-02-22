package com.wdipl.trackmykid.welcome.signIn

import com.wdipl.trackmykid.firebase.models.UserData

data class SignInResult(
    val data: UserData?,
    val errorMessage: String?
)
