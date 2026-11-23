package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class InventoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized()
        container = AppContainer(applicationContext)
    }

    private fun ensureFirebaseInitialized() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:100000000000:android:abcdef0123456789")
                    .setApiKey("AIzaSyFakeKeyForLocalInitialization123456")
                    .setProjectId("ai-studio-android-inventar")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
