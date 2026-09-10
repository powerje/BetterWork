package dev.betterwork.phone

import dev.betterwork.platform.BetterApp

class PhoneApplication : BetterApp() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdates.observe(this)
    }
}
