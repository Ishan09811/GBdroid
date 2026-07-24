
package io.github.gbdroid.utils

import android.content.Context
import android.content.SharedPreferences

object GlobalConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    private const val PREFS_NAME = "app_prefs"

    @Volatile var autoSave: Boolean = true
    @Volatile var fastForward: Boolean = false

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        //autoSave = prefs.getBoolean("pref_auto_save", true)
        //fastForward = prefs.getBoolean("pref_fast_forward", false)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            //"pref_auto_save" -> autoSave = sharedPreferences.getBoolean(key, true)
            //"pref_fast_forward" -> fastForward = sharedPreferences.getBoolean(key, false)
        }
    }
}