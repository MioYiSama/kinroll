import Foundation
import OnnxRuntimeBindings
import Shared

final class OrtBridgeImpl: NSObject, OrtBridge {
    private var environment: ORTEnv?
    private let lock = NSLock()
    private var errorMessage: String?

    override init() {
        super.init()
        do { environment = try ORTEnv(loggingLevel: .warning) }
        catch { record(error) }
    }
    func lastError() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return errorMessage
    }
    fileprivate func record(_ error: Error) {
        lock.lock()
        errorMessage = error.localizedDescription
        lock.unlock()
    }
    func openSession(modelPath: String) -> (any OrtBridgeSession)? {
        do {
            guard let environment else { throw BridgeError.unavailable }
            let options = try ORTSessionOptions()
            try options.setIntraOpNumThreads(Int32(max(2, min(4, ProcessInfo.processInfo.activeProcessorCount))))
            try options.setGraphOptimizationLevel(.all)
            let session = try ORTSession(env: environment, modelPath: modelPath, sessionOptions: options)
            return try Session(owner: self, session: session)
        } catch { record(error); return nil }
    }

    private enum BridgeError: Error { case unavailable, closed, invalidTensor, missingOutput }
    private final class Session: NSObject, OrtBridgeSession {
        private let owner: OrtBridgeImpl
        private var session: ORTSession?
        private let inputs: [String]
        private let outputs: [String]
        init(owner: OrtBridgeImpl, session: ORTSession) throws {
            self.owner = owner
            self.session = session
            inputs = try session.inputNames()
            outputs = try session.outputNames()
        }
        func inputNames() -> [String] { inputs }
        func outputNames() -> [String] { outputs }
        func close() { session = nil }
        func run(names: [String], datas: [Data], shapes: [[KotlinLong]], types: [KotlinInt], outputNames: [String]) -> [OrtBridgeOutput]? {
            do {
                guard let session else { throw BridgeError.closed }
                guard names.count == datas.count, names.count == shapes.count, names.count == types.count else {
                    throw BridgeError.invalidTensor
                }
                var tensors = [String: ORTValue]()
                for index in names.indices {
                    let type: ORTTensorElementDataType
                    switch types[index].int32Value {
                    case 1: type = .float
                    case 7: type = .int64
                    default: throw BridgeError.invalidTensor
                    }
                    tensors[names[index]] = try ORTValue(tensorData: NSMutableData(data: datas[index]), elementType: type,
                                                         shape: shapes[index].map { NSNumber(value: $0.int64Value) })
                }
                let result = try session.run(withInputs: tensors, outputNames: Set(outputNames), runOptions: nil)
                return try outputNames.map { name in
                    guard let tensor = result[name] else { throw BridgeError.missingOutput }
                    let shape = try tensor.tensorTypeAndShapeInfo()
                    guard shape.elementType == .float else { throw BridgeError.invalidTensor }
                    // ORT owns the tensor buffer; copy before its ORTValue is released.
                    let buffer = try tensor.tensorData()
                    return OrtBridgeOutput(data: Data(bytes: buffer.bytes, count: buffer.length),
                                           shape: shape.shape.map { KotlinLong(longLong: $0.int64Value) })
                }
            } catch { owner.record(error); return nil }
        }
    }
}
