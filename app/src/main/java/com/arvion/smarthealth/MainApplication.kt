package com.arvion.smarthealth

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.arvion.smarthealth.utils.NotificationHelper

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Setup Global Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the full stack trace to Logcat immediately
            Log.e("SmartHealthCrash", "FATAL EXCEPTION: ${thread.name}", throwable)
            
            // Show a Toast on the Main Looper
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "CRITICAL ERROR: ${throwable.localizedMessage}\nCheck Logcat for 'SmartHealthCrash'",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            // Allow the default handler to finish (will kill the process)
            // But sleep a bit to give the Toast a chance to at least start
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        NotificationHelper.createNotificationChannel(this)
    }
}
