package net.mioyi.kinroll.android

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.mioyi.kinroll.platform.IndexScheduler

class AndroidIndexScheduler(context: Context) : IndexScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun request() {
        workManager.enqueueUniqueWork(
            "kinroll-index",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>().build(),
        )
    }
}
