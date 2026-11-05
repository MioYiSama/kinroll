package net.mioyi.kinroll

import androidx.compose.ui.window.ComposeUIViewController
import net.mioyi.kinroll.ios.KinrollIos
import net.mioyi.kinroll.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App(KinrollIos.graph) }
