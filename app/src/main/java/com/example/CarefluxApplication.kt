package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class CarefluxApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Eagerly and safely initialize FirebaseApp on startup so background workers, receivers, and services can access Firebase without crashing
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.e("CarefluxApplication", "FirebaseApp initialization error: ${e.localizedMessage}", e)
        }

        // 2. Pre-create notification channels safely
        try {
            com.example.util.SmartNotificationDispatcher.setupNotificationChannels(this)
        } catch (e: Exception) {
            Log.e("CarefluxApplication", "Notification channels setup error: ${e.localizedMessage}", e)
        }
    }
}
