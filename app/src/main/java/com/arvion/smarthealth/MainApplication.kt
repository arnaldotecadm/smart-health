package com.arvion.smarthealth

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val mainLooper = Looper.getMainLooper()
        Thread.setDefaultUncaughtExceptionHandler {
            _, throwable ->
            Handler(mainLooper).post { Toast.makeText(applicationContext, "Unhandled exception: ${throwable.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
