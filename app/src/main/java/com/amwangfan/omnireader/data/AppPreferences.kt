package com.amwangfan.omnireader.data

import android.content.Context

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

    var defaultDownloadTreeUri: String
        get() = prefs.getString(KEY_DEFAULT_DOWNLOAD_TREE_URI, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_DOWNLOAD_TREE_URI, value).apply()
        }

    var autoUploadImports: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPLOAD_IMPORTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_UPLOAD_IMPORTS, value).apply()
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

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_DEFAULT_DOWNLOAD_TREE_URI = "default_download_tree_uri"
        const val KEY_AUTO_UPLOAD_IMPORTS = "auto_upload_imports"
    }
}
