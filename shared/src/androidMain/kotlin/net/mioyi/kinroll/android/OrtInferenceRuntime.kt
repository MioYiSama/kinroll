package net.mioyi.kinroll.android

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import net.mioyi.kinroll.platform.InferenceRuntime
import net.mioyi.kinroll.platform.InferenceSession
import net.mioyi.kinroll.platform.TensorInput
import net.mioyi.kinroll.platform.TensorOutput

class OrtInferenceRuntime(private val cpuThreads: Int) : InferenceRuntime {
    override fun open(modelPath: String): InferenceSession {
        val session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(cpuThreads)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(modelPath, options)
        }
        return Session(session)
    }

    private class Session(private val session: OrtSession) : InferenceSession {
        override val inputNames: List<String> = session.inputNames.toList()
        override val outputNames: List<String> = session.outputNames.toList()

        override fun run(inputs: List<TensorInput>, outputs: List<String>): List<TensorOutput> {
            val tensors = LinkedHashMap<String, OnnxTensor>(inputs.size)
            try {
                for (input in inputs) {
                    require(input.name !in tensors) { "Duplicate tensor input: ${input.name}" }
                    require((input.floats != null) != (input.longs != null)) {
                        "Tensor input must contain exactly one of floats or longs"
                    }
                    tensors[input.name] = if (input.floats != null) {
                        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.floats), input.shape)
                    } else {
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(requireNotNull(input.longs)), input.shape)
                    }
                }
                return session.run(tensors, outputs.toSet()).use { result ->
                    outputs.map { name ->
                        val tensor = result.get(name).get() as OnnxTensor
                        val buffer = tensor.floatBuffer
                        val data = FloatArray(buffer.remaining())
                        buffer.get(data)
                        TensorOutput(tensor.info.shape, data)
                    }
                }
            } finally {
                tensors.values.forEach { it.close() }
            }
        }

        override fun close() = session.close()
    }

    private companion object {
        val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    }
}
