package net.mioyi.kinroll.ios

import net.mioyi.kinroll.platform.InferenceRuntime
import net.mioyi.kinroll.platform.InferenceSession
import net.mioyi.kinroll.platform.TensorInput
import net.mioyi.kinroll.platform.TensorOutput

class IosInferenceRuntime(private val bridge: OrtBridge) : InferenceRuntime {
    override fun open(modelPath: String): InferenceSession {
        val session = bridge.openSession(modelPath) ?: error(bridge.lastError() ?: "ONNX session creation failed")
        return object : InferenceSession {
            override val inputNames = session.inputNames()
            override val outputNames = session.outputNames()
            override fun run(inputs: List<TensorInput>, outputs: List<String>): List<TensorOutput> {
                val data = inputs.map { input ->
                    require((input.floats == null) != (input.longs == null))
                    input.floats?.toNSData() ?: input.longs!!.toNSData()
                }
                val result = session.run(inputs.map { it.name }, data, inputs.map { it.shape.toList() },
                    inputs.map { if (it.floats != null) 1 else 7 }, outputs)
                    ?: error(bridge.lastError() ?: "ONNX inference failed")
                return result.map { TensorOutput(it.shape.toLongArray(), it.data.toFloatArray()) }
            }
            override fun close() = session.close()
        }
    }
}
