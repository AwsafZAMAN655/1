package com.example.fastcontacts

import android.app.Application

class FastContactsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.applyTheme(this)
    }
}
