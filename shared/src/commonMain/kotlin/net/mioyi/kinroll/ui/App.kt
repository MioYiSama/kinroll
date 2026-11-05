package net.mioyi.kinroll.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.platform.PermissionState
import net.mioyi.kinroll.ui.cleanup.CleanupScreen
import net.mioyi.kinroll.ui.components.LocalThumbnailCache
import net.mioyi.kinroll.ui.credits.CreditsScreen
import net.mioyi.kinroll.ui.credits.LicenseScreen
import net.mioyi.kinroll.ui.home.HomeScreen
import net.mioyi.kinroll.ui.onboarding.OnboardingScreen
import net.mioyi.kinroll.ui.overview.OverviewScreen
import net.mioyi.kinroll.ui.overview.PhotoDetailScreen
import net.mioyi.kinroll.ui.post.PostScreen
import net.mioyi.kinroll.ui.theme.KinrollTheme

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object CleanupRoute
@Serializable data object OverviewRoute
@Serializable data class PhotoDetailRoute(val id: String)
@Serializable data object PostRoute
@Serializable data object CreditsRoute
@Serializable data class LicenseRoute(val name: String, val licenseFile: String)

@Composable
fun App(graph: AppGraph) {
    KinrollTheme {
        CompositionLocalProvider(LocalThumbnailCache provides graph.thumbnails) {
            val nav = rememberNavController()
            val start: Any = remember {
                val permission = graph.platform.photoLibrary.permissionState()
                val granted = permission == PermissionState.FULL || permission == PermissionState.LIMITED
                if (granted && graph.modelManager.allReady()) HomeRoute else OnboardingRoute
            }
            NavHost(navController = nav, startDestination = start) {
                composable<OnboardingRoute> {
                    OnboardingScreen(graph) {
                        nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                    }
                }
                composable<HomeRoute> {
                    HomeScreen(
                        graph = graph,
                        onCleanup = { nav.navigate(CleanupRoute) },
                        onOverview = { nav.navigate(OverviewRoute) },
                        onPost = { nav.navigate(PostRoute) },
                        onCredits = { nav.navigate(CreditsRoute) },
                    )
                }
                composable<CleanupRoute> {
                    CleanupScreen(
                        graph = graph,
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(PhotoDetailRoute(it)) },
                    )
                }
                composable<OverviewRoute> {
                    OverviewScreen(
                        graph = graph,
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(PhotoDetailRoute(it)) },
                    )
                }
                composable<PhotoDetailRoute> { entry ->
                    PhotoDetailScreen(graph, entry.toRoute<PhotoDetailRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<PostRoute> {
                    PostScreen(graph, onBack = { nav.popBackStack() })
                }
                composable<CreditsRoute> {
                    CreditsScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { name, file -> nav.navigate(LicenseRoute(name, file)) },
                    )
                }
                composable<LicenseRoute> { entry ->
                    val route = entry.toRoute<LicenseRoute>()
                    LicenseScreen(route.name, route.licenseFile, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
