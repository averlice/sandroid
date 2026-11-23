package com.quinton.sandroid

import android.content.Context
import android.content.SharedPreferences

/**
 * Everything the engine touches before the user's first unlock has to come from the
 * device-protected context. The manifest's defaultToDeviceProtectedStorage flag does not cover
 * the context a boot receiver or a service is handed while the device is still locked, so the
 * engine always asks for the device-protected context by name.
 */
internal object DeviceStorage {
    fun context(context: Context): Context = context.createDeviceProtectedStorageContext()

    fun prefs(context: Context, name: String): SharedPreferences =
        context(context).getSharedPreferences(name, Context.MODE_PRIVATE)
}
