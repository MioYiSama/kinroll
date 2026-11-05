package net.mioyi.kinroll.ios

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.platform.IndexScheduler

class IosIndexScheduler(private val graph: AppGraph) : IndexScheduler {
    override fun request() { graph.appScope.launch { graph.indexer.runOnce() } }
}
class IndexJobHandle internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}
