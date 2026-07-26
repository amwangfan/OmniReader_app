package com.amwangfan.omnireader.data

import android.content.Context
import android.os.Build
import android.provider.Settings

class DeviceIdentityProvider(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    fun current(): DeviceRegistrationRequest {
        val configuredName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull().orEmpty().trim()
        val systemName = defaultDeviceName(configuredName, Build.MANUFACTURER, Build.MODEL)
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return DeviceRegistrationRequest(
            id = preferences.getOrCreateDeviceId(),
            displayName = systemName,
            systemName = systemName,
            manufacturer = Build.MANUFACTURER.orEmpty().trim(),
            model = Build.MODEL.orEmpty().trim(),
            appVersion = versionName,
        )
    }
}

internal fun defaultDeviceName(systemName: String?, manufacturer: String?, model: String?): String {
    systemName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val make = manufacturer.orEmpty().trim().replaceFirstChar { it.uppercase() }
    val product = model.orEmpty().trim()
    if (product.startsWith(make, ignoreCase = true)) return product.ifBlank { "Android device" }
    return listOf(make, product).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android device" }
}
