package com.bankapp.zbk

import android.app.Application
import com.bankapp.zbk.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ZbkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ZbkApplication)
            modules(sharedModule())
        }
    }
}
