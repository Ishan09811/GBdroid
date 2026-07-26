
package io.github.gbdroid

import android.app.Application
import android.content.Context
import io.github.gbdroid.utils.GlobalConfig

class GBdroidApplication : Application() {
    init {
        instance = this
    }

    companion object {
        lateinit var instance : GBdroidApplication
            private set

        val context : Context get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        GlobalConfig.initialize(context)
    }
}
