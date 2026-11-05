import Foundation
import Shared
import llama

/// LlmService serializes calls and keeps this model resident after the first load.
final class LlamaBridgeImpl: NSObject, LlamaNative {
    private static let backend: Void = { llama_backend_init() }()
    private var model: OpaquePointer?
    private var context: OpaquePointer?
    private var vocab: OpaquePointer?
    private var sampler: UnsafeMutablePointer<llama_sampler>?
    private var past: Int32 = 0
    private var contextSize: Int32 = 0
    private var generating = false
    private var piece = [CChar](repeating: 0, count: 256)

    func load(modelPath: String, contextSize: Int32, threads: Int32) -> String? {
        unload()
        guard contextSize >= 32, threads > 0 else { return "invalid context size or thread count" }
        _ = Self.backend
        var modelParams = llama_model_default_params()
        #if targetEnvironment(simulator)
        modelParams.n_gpu_layers = 0
        #else
        modelParams.n_gpu_layers = 99
        #endif
        model = modelPath.withCString { llama_model_load_from_file($0, modelParams) }
        guard let model else { return "could not load LLM model" }
        var params = llama_context_default_params()
        params.n_ctx = UInt32(contextSize)
        params.n_batch = 512
        params.n_ubatch = 512
        params.n_threads = threads
        params.n_threads_batch = threads
        context = llama_init_from_model(model, params)
        guard let context else {
            unload()
            return "could not create LLM context"
        }
        vocab = llama_model_get_vocab(model)
        self.contextSize = Int32(llama_n_ctx(context))
        return nil
    }

    func begin(prompt: String, params: SamplingParams) -> String? {
        guard let context, let vocab else { return "LLM model is not loaded" }
        generating = false
        past = 0
        llama_memory_clear(llama_get_memory(context), true)
        if let sampler { llama_sampler_free(sampler) }
        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params())
        guard let sampler else { return "could not create LLM sampler" }
        if params.temperature <= 0 {
            llama_sampler_chain_add(sampler, llama_sampler_init_greedy())
        } else {
            llama_sampler_chain_add(sampler,
                llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1, 0, params.presencePenalty))
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(params.topK))
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(params.topP, 1))
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(params.temperature))
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(UInt32(bitPattern: params.seed)))
        }
        // withCString is standard UTF-8, unlike JNI's modified UTF-8 string accessor.
        guard let byteCount = Int32(exactly: prompt.utf8.count) else { return "prompt tokenization overflow" }
        let count = prompt.withCString { llama_tokenize(vocab, $0, byteCount, nil, 0, true, true) }
        guard count != Int32.min else { return "prompt tokenization overflow" }
        guard count < 0 else { return "prompt tokenization failed" }
        guard -count < contextSize - 16 else { return "prompt too long" }
        var tokens = [llama_token](repeating: 0, count: Int(-count))
        let tokenCapacity = Int32(tokens.count)
        let actual = prompt.withCString { text in
            tokens.withUnsafeMutableBufferPointer {
                llama_tokenize(vocab, text, byteCount, $0.baseAddress, tokenCapacity, true, true)
            }
        }
        guard actual > 0 else { return "prompt tokenization failed" }
        let error: String? = tokens.withUnsafeMutableBufferPointer { buffer in
            var offset: Int32 = 0
            while offset < actual {
                let length = min(512, actual - offset)
                let batch = llama_batch_get_one(buffer.baseAddress!.advanced(by: Int(offset)), length)
                guard llama_decode(context, batch) == 0 else { return "prompt decoding failed" }
                past += length
                offset += length
            }
            return nil
        }
        if let error { return error }
        for token in tokens { llama_sampler_accept(sampler, token) }
        generating = true
        return nil
    }

    func next() -> KotlinByteArray? {
        guard generating, past < contextSize, let context, let vocab, let sampler else { return nil }
        var token = llama_sampler_sample(sampler, context, -1)
        if llama_vocab_is_eog(vocab, token) {
            generating = false
            return nil
        }
        var size = piece.withUnsafeMutableBufferPointer {
            llama_token_to_piece(vocab, token, $0.baseAddress, Int32($0.count), 0, false)
        }
        if size < 0 && size != Int32.min {
            piece = [CChar](repeating: 0, count: Int(-size))
            size = piece.withUnsafeMutableBufferPointer {
                llama_token_to_piece(vocab, token, $0.baseAddress, Int32($0.count), 0, false)
            }
        }
        guard size >= 0 else {
            generating = false
            print("LLM token detokenization failed")
            return nil
        }
        let status = withUnsafeMutablePointer(to: &token) {
            llama_decode(context, llama_batch_get_one($0, 1))
        }
        guard status == 0 else {
            generating = false
            print("LLM token decoding failed: \(status)")
            return nil
        }
        past += 1
        let output = KotlinByteArray(size: size)
        for index in 0..<Int(size) { output.set(index: Int32(index), value: piece[index]) }
        return output
    }

    func unload() {
        generating = false
        if let sampler { llama_sampler_free(sampler) }
        if let context { llama_free(context) }
        if let model { llama_model_free(model) }
        sampler = nil
        context = nil
        model = nil
        vocab = nil
        past = 0
        contextSize = 0
    }

    deinit { unload() }
}
