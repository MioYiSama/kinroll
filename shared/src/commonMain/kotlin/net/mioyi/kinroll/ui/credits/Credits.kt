package net.mioyi.kinroll.ui.credits

/** A third-party component with the license text bundled under composeResources/files/licenses. */
internal data class Credit(
    val name: String,
    val holder: String,
    val license: String,
    val licenseFile: String,
    /** Attribution the license requires to be shown verbatim. */
    val notice: String? = null,
)

private const val APACHE = "Apache License 2.0"
private const val APACHE_FILE = "files/licenses/apache-2.0.txt"
private const val MIT = "MIT License"

/** Everything compiled into the Android or iOS app, including transitive runtime dependencies. */
internal val softwareCredits = listOf(
    Credit("llama.cpp / ggml", "The ggml authors", MIT, "files/licenses/llama.cpp.txt"),
    Credit("ONNX Runtime", "Microsoft Corporation", MIT, "files/licenses/onnxruntime.txt"),
    Credit(
        "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime, kotlinx-io, atomicfu",
        "JetBrains s.r.o. and contributors",
        APACHE,
        APACHE_FILE,
    ),
    Credit("Compose Multiplatform, Skiko", "JetBrains s.r.o. and The Android Open Source Project", APACHE, APACHE_FILE),
    Credit(
        "Android Jetpack (Compose, Navigation, Lifecycle, Room, SQLite, WorkManager, ExifInterface, Activity)",
        "The Android Open Source Project",
        APACHE,
        APACHE_FILE,
    ),
    Credit("Ktor", "JetBrains s.r.o. and contributors", APACHE, APACHE_FILE),
    Credit("OkHttp, Okio", "Square, Inc.", APACHE, APACHE_FILE),
    Credit("Guava ListenableFuture", "The Guava Authors", APACHE, APACHE_FILE),
    Credit("JSpecify", "The JSpecify Authors", APACHE, APACHE_FILE),
    Credit("JetBrains Java Annotations", "JetBrains s.r.o.", APACHE, APACHE_FILE),
    Credit("SLF4J", "QOS.ch", MIT, "files/licenses/slf4j.txt"),
    Credit("Skia", "Google Inc.", "BSD 3-Clause License", "files/licenses/skia.txt"),
    Credit("SQLite", "The SQLite authors", "Public domain", "files/licenses/sqlite.txt"),
)

/** Models listed in ModelManifest; downloaded at runtime. */
internal val modelCredits = listOf(
    Credit(
        "MobileCLIP-S2 (ONNX export by Xenova)",
        "Apple Inc.",
        "Apple Machine Learning Research Model License",
        "files/licenses/apple-amlr.txt",
        notice = "Apple Machine Learning Research Model is licensed under the " +
            "Apple Machine Learning Research Model License Agreement.",
    ),
    Credit("PP-OCRv6 small", "PaddlePaddle Authors", APACHE, APACHE_FILE),
    Credit("Qwen3.5 0.8B (GGUF by Unsloth)", "Alibaba Cloud", APACHE, APACHE_FILE),
)
