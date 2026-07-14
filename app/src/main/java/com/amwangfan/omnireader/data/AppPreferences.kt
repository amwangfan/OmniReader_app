package com.amwangfan.omnireader.data

import android.content.Context
import java.util.UUID

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("omnireader", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value).apply()
        }

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
        }

    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, "").orEmpty()
            if (existing.isNotBlank()) {
                return existing
            }
            val created = "android_${UUID.randomUUID()}"
            prefs.edit().putString(KEY_DEVICE_ID, created).commit()
            return created
        }

    fun saveSession(login: LoginResponse) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, login.accessToken)
            .putString(KEY_REFRESH_TOKEN, login.refreshToken)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    fun updateAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).commit()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_DEVICE_ID = "device_id"
    }
}
