package net.mioyi.kinroll.android

import android.content.Context
import androidx.room.Room
import io.ktor.client.engine.okhttp.OkHttp
import net.mioyi.kinroll.data.KinrollDatabase
import net.mioyi.kinroll.platform.PlatformServices

object AndroidPlatform {
    fun create(context: Context): PlatformServices {
        val appContext = context.applicationContext
        val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        return PlatformServices(
            photoLibrary = AndroidPhotoLibrary(appContext),
            inferenceRuntime = OrtInferenceRuntime(cpuThreads),
            llamaNative = AndroidLlamaNative(),
            databaseBuilder = Room.databaseBuilder<KinrollDatabase>(
                appContext,
                appContext.getDatabasePath("kinroll.db").absolutePath,
            ),
            filesDir = appContext.filesDir.absolutePath,
            httpEngine = OkHttp,
            cpuThreads = cpuThreads,
            indexSchedulerFactory = { AndroidIndexScheduler(appContext) },
        )
    }
}
