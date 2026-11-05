import BackgroundTasks
import Shared
import SwiftUI

@main
struct KinrollApp: App {
    @Environment(\.scenePhase) private var scenePhase
    private static let indexTask = "net.mioyi.kinroll.index"

    init() {
        KinrollIos.shared.initialize(photoKit: PhotoKitBridgeImpl(), ort: OrtBridgeImpl(), llama: LlamaBridgeImpl())
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.indexTask, using: .main) { task in
            let handle = KinrollIos.shared.runBackgroundIndexing { success in
                task.setTaskCompleted(success: success.boolValue)
            }
            task.expirationHandler = { handle.cancel() }
        }
    }
    var body: some Scene {
        WindowGroup { ComposeView().ignoresSafeArea() }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .background else { return }
                let request = BGProcessingTaskRequest(identifier: Self.indexTask)
                request.requiresExternalPower = true
                request.requiresNetworkConnectivity = false
                do { try BGTaskScheduler.shared.submit(request) }
                catch { print("Could not schedule indexing: \(error)") }
            }
    }
}
