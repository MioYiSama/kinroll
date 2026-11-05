package net.mioyi.kinroll.android

import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.platform.PlatformServices

object KinrollGraph {
    @Volatile
    private var graph: AppGraph? = null

    @Synchronized
    fun init(platform: PlatformServices) {
        check(graph == null) { "KinrollGraph has already been initialized" }
        graph = AppGraph(platform)
    }

    fun get(): AppGraph = checkNotNull(graph) { "KinrollGraph must be initialized in Application.onCreate" }
}
