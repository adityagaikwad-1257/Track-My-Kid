package com.wdipl.trackmykid.firebase.models

data class UserData(
    val userId: String?,
    var userName: String?,
    val email: String?,
    var profilePictureUrl: String?,
    var role: String? = Role.NONE.name
){
    constructor():this(null, null, null, null)

    var connections: ArrayList<String> = ArrayList()
    var parentConnectCode: String? = null
}

enum class Role(role: Int){
    PARENT(0),
    CHILD(1),
    NONE(2);

    companion object{
        fun create(role: Any?): Role{
            return when(role){
                PARENT.name -> PARENT
                CHILD.name -> CHILD
                else -> NONE
            }
        }
    }
}