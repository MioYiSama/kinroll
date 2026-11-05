@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package net.mioyi.kinroll.ios

import androidx.room.Room
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.KinrollDatabase
import net.mioyi.kinroll.platform.LlamaNative
import net.mioyi.kinroll.platform.PlatformServices
import platform.Foundation.*

object KinrollIos {
    lateinit var graph: AppGraph
        private set
    lateinit var photoKit: PhotoKitBridge
        private set

    fun initialize(photoKit: PhotoKitBridge, ort: OrtBridge, llama: LlamaNative) {
        check(!::graph.isInitialized)
        this.photoKit = photoKit
        val manager = NSFileManager.defaultManager
        val documents = manager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as NSURL
        val support = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        check(manager.createDirectoryAtURL(support, withIntermediateDirectories = true, attributes = null, error = null))
        check(support.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null))
        graph = AppGraph(PlatformServices(
            photoLibrary = IosPhotoLibrary(photoKit), inferenceRuntime = IosInferenceRuntime(ort), llamaNative = llama,
            databaseBuilder = Room.databaseBuilder<KinrollDatabase>(name = documents.path + "/kinroll.db"),
            filesDir = requireNotNull(support.path), httpEngine = Darwin,
            cpuThreads = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceIn(2, 4),
            indexSchedulerFactory = { IosIndexScheduler(it) },
        ))
    }

    fun runBackgroundIndexing(onDone: (Boolean) -> Unit): IndexJobHandle {
        val job = graph.appScope.launch { graph.indexer.runOnce() }
        job.invokeOnCompletion { cause ->
            graph.appScope.launch(Dispatchers.Main) { onDone(cause == null) }
        }
        return IndexJobHandle(job)
    }
}
