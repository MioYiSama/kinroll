package net.mioyi.kinroll

import android.app.Application
import net.mioyi.kinroll.android.AndroidPlatform
import net.mioyi.kinroll.android.KinrollGraph

class KinrollApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KinrollGraph.init(AndroidPlatform.create(this))
    }
}
