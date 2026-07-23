
package io.github.gbdroid

import android.app.Application
import android.content.Context

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
    }
}
