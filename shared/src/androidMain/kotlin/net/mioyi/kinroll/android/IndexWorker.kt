package net.mioyi.kinroll.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.mioyi.kinroll.index.IndexerState

class IndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private var foregroundAllowed = true

    override suspend fun doWork(): Result = coroutineScope {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val label = applicationContext.applicationInfo.loadLabel(applicationContext.packageManager)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, label, NotificationManager.IMPORTANCE_LOW))
        updateForeground(done = 0, total = 0)

        val indexer = KinrollGraph.get().indexer
        val progress = launch(start = CoroutineStart.UNDISPATCHED) {
            var lastDone = -1
            indexer.state.collect { state ->
                if (state is IndexerState.Indexing && state.done != lastDone &&
                    (lastDone < 0 || state.done - lastDone >= 20 || state.done == state.total)
                ) {
                    lastDone = state.done
                    setProgress(workDataOf("done" to state.done, "total" to state.total))
                    updateForeground(state.done, state.total)
                }
            }
        }
        try {
            indexer.runOnce()
            val state = indexer.state.value
            if (state is IndexerState.Failed) Result.failure() else Result.success()
        } finally {
            progress.cancelAndJoin()
        }
    }

    private suspend fun updateForeground(done: Int, total: Int) {
        if (!foregroundAllowed) return
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(net.mioyi.kinroll.shared.R.drawable.ic_kinroll_notification)
            .setContentTitle(applicationContext.applicationInfo.loadLabel(applicationContext.packageManager))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .build()
        try {
            setForeground(ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (restricted: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is also an IllegalStateException.
            // The OS may reject a foreground promotion when indexing starts in the background.
            foregroundAllowed = false
            println("kinroll-index foreground unavailable: ${restricted.message}")
        }
    }

    private companion object {
        const val CHANNEL_ID = "indexing"
        const val NOTIFICATION_ID = 1001
    }
}
