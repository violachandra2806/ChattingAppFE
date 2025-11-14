package com.chattingapp.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    // FIX: Use the same preference file as LoginActivity
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("UserData", Context.MODE_PRIVATE)

    fun getUserId(): String? {
        return sharedPreferences.getString("user_id", null)
    }

    fun setUserId(userId: String) {
        sharedPreferences.edit().putString("user_id", userId).apply()
    }

    fun getUsername(): String? {
        return sharedPreferences.getString("username", null)
    }

    fun setUsername(username: String) {
        sharedPreferences.edit().putString("username", username).apply()
    }

    fun getEmail(): String? {
        return sharedPreferences.getString("email", null)
    }

    fun setEmail(email: String) {
        sharedPreferences.edit().putString("email", email).apply()
    }

    // Add other user data methods as needed
}