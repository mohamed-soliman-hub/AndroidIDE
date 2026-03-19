package com.androidide

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IDEApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object {
        lateinit var instance: IDEApplication
            private set
    }
}
