package com.botwmap.app

import android.app.Application
import com.botwmap.app.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BotwMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@BotwMapApplication)
            modules(appModules)
        }
    }
}
