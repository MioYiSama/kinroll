#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <climits>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::once_flag backend_once;
thread_local std::string last_error;

struct State {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    llama_sampler * sampler = nullptr;
    int32_t n_past = 0;
    int32_t n_ctx = 0;
    bool generating = false;
    std::vector<char> piece = std::vector<char>(256);

    ~State() {
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

State * state(jlong handle) { return reinterpret_cast<State *>(handle); }

void fail_next(JNIEnv * env, State * s, const char * message) {
    s->generating = false;
    last_error = message;
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_net_mioyi_kinroll_llm_LlamaJni_load(JNIEnv * env, jobject, jstring path, jint n_ctx, jint n_threads) {
    last_error.clear();
    if (n_ctx < 32 || n_threads < 1) {
        last_error = "invalid context size or thread count";
        return 0;
    }
    std::call_once(backend_once, [] { llama_backend_init(); });
    auto s = std::make_unique<State>();
    const char * file = env->GetStringUTFChars(path, nullptr);
    if (!file) return 0;
    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    s->model = llama_model_load_from_file(file, mparams);
    env->ReleaseStringUTFChars(path, file);
    if (!s->model) {
        last_error = "could not load LLM model";
        return 0;
    }
    auto cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    s->ctx = llama_init_from_model(s->model, cparams);
    if (!s->ctx) {
        last_error = "could not create LLM context";
        return 0;
    }
    s->vocab = llama_model_get_vocab(s->model);
    s->n_ctx = static_cast<int32_t>(llama_n_ctx(s->ctx));
    return reinterpret_cast<jlong>(s.release());
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_mioyi_kinroll_llm_LlamaJni_lastError(JNIEnv * env, jobject) {
    return env->NewStringUTF(last_error.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_mioyi_kinroll_llm_LlamaJni_begin(JNIEnv * env, jobject, jlong handle, jbyteArray prompt,
    jfloat temperature, jfloat top_p, jint top_k, jfloat presence_penalty, jint seed) {
    auto * s = state(handle);
    last_error.clear();
    if (!s) {
        last_error = "LLM model is not loaded";
        return JNI_FALSE;
    }
    s->generating = false;
    s->n_past = 0;
    llama_memory_clear(llama_get_memory(s->ctx), true);
    if (s->sampler) llama_sampler_free(s->sampler);
    s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0) {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(s->sampler,
            llama_sampler_init_penalties(llama_vocab_n_tokens(s->vocab), 64, 1.0f, 0.0f, presence_penalty));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }
    const auto length = env->GetArrayLength(prompt);
    auto * bytes = env->GetByteArrayElements(prompt, nullptr);
    if (!bytes) return JNI_FALSE;
    auto count = llama_tokenize(s->vocab, reinterpret_cast<const char *>(bytes), length,
        nullptr, 0, true, true);
    if (count == INT32_MIN || count >= 0 || -count >= s->n_ctx - 16) {
        env->ReleaseByteArrayElements(prompt, bytes, JNI_ABORT);
        last_error = count == INT32_MIN ? "prompt tokenization overflow" :
            count >= 0 ? "prompt tokenization failed" : "prompt too long";
        return JNI_FALSE;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(-count));
    count = llama_tokenize(s->vocab, reinterpret_cast<const char *>(bytes), length,
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    env->ReleaseByteArrayElements(prompt, bytes, JNI_ABORT);
    if (count <= 0) {
        last_error = "prompt tokenization failed";
        return JNI_FALSE;
    }
    for (int32_t offset = 0; offset < count; offset += 512) {
        const auto size = std::min<int32_t>(512, count - offset);
        if (llama_decode(s->ctx, llama_batch_get_one(tokens.data() + offset, size)) != 0) {
            last_error = "prompt decoding failed";
            return JNI_FALSE;
        }
        s->n_past += size;
    }
    for (const auto token : tokens) llama_sampler_accept(s->sampler, token);
    s->generating = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_mioyi_kinroll_llm_LlamaJni_next(JNIEnv * env, jobject, jlong handle) {
    auto * s = state(handle);
    if (!s || !s->generating || s->n_past >= s->n_ctx) return nullptr;
    auto token = llama_sampler_sample(s->sampler, s->ctx, -1); // also accepts the sampled token
    if (llama_vocab_is_eog(s->vocab, token)) {
        s->generating = false;
        return nullptr;
    }
    auto size = llama_token_to_piece(s->vocab, token, s->piece.data(),
        static_cast<int32_t>(s->piece.size()), 0, false);
    if (size < 0 && size != INT32_MIN) {
        s->piece.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(s->vocab, token, s->piece.data(),
            static_cast<int32_t>(s->piece.size()), 0, false);
    }
    if (size < 0) {
        fail_next(env, s, "token detokenization failed");
        return nullptr;
    }
    if (llama_decode(s->ctx, llama_batch_get_one(&token, 1)) != 0) {
        fail_next(env, s, "token decoding failed");
        return nullptr;
    }
    ++s->n_past;
    auto result = env->NewByteArray(size);
    if (result && size) env->SetByteArrayRegion(result, 0, size, reinterpret_cast<const jbyte *>(s->piece.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_net_mioyi_kinroll_llm_LlamaJni_free(JNIEnv *, jobject, jlong handle) {
    delete state(handle);
}
