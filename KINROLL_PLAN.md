# Kinroll（相知）— KMP + Compose Multiplatform 首版实现计划

## Context
按 `docs/1.md` 从零实现 Kinroll：完全离线的 AI 相册管理 App，Android + iOS，标识符 `net.mioyi.kinroll`。技术栈 Kotlin 2.4.21 + Gradle 9.8.2 + Compose Multiplatform，iOS 平台接口（PhotoKit / ONNX Runtime / llama.cpp）用 Swift 实现并注入 Kotlin。模型：MobileCLIP-S2（ONNX）、PaddleOCR（PP-OCRv6_small ONNX，官方移动端部署同样基于 ONNX Runtime）、Qwen3.5-0.8B UD-Q4_K_XL（llama.cpp）。数据库 `photo` 表保存唯一标识、元数据、CLIP 结果、OCR 结果、哈希、向量。界面：中英文 + 深色模式；首次进入引导授权与下载模型（HF / hf-mirror / ModelScope）；主界面三个入口（图片清理、图片概览、帖文生成）+ 索引进度条。分发：APK（始终 debug 签名）+ unsigned IPA，本地脚本 + GitHub Actions。

仓库当前只有 `README.md`、`README.zh.md`、`docs/1.md`、`.gitignore`（仅 `.DS_Store`）、`AGENTS.md`（空）。README 功能列表含“重复图片检测”，因此清理页额外提供“重复”分组（复用已算出的哈希与向量）。

### 已确定的用户决策
- minSdk 30（Android 11），iOS 部署目标 17.0。
- 删除 = 移到系统回收站：Android `MediaStore.createTrashRequest`；iOS `PHAssetChangeRequest.deleteAssets`（进入“最近删除”）。
- 帖文风格预设：小红书 / 朋友圈 / 微博 / Instagram；输出语言跟随界面语言；可附加用户补充说明。
- Release APK 始终使用 debug 签名。
- 分发：`scripts/` 本地脚本 + GitHub Actions（推 `v*` tag 时构建并上传 apk 与 unsigned ipa 到 Release）。

### 本机环境（已核实）
JDK 25 (Temurin)、Gradle 9.8.2、Android SDK `platforms/android-37.2`、build-tools 37.0.0、NDK `30.0.16248370`、SDK 内无 cmake（可装 `cmake/4.1.2`）、Homebrew cmake 4.4.5 + ninja、xcodegen、cocoapods、Xcode 27.2、Python 3.15、AVD `Pixel_10_API_37.2`（arm64-v8a）、iOS 模拟器 `iPhone 18 Pro`。

## 版本锁定（全部已在 Maven/GitHub 元数据中核实）
| 组件 | 版本 / 坐标 |
|---|---|
| Kotlin（multiplatform、serialization、compose compiler 插件） | 2.4.21 |
| Gradle wrapper | 9.8.2 |
| AGP（`com.android.application`、`com.android.library`、`com.android.kotlin.multiplatform.library`） | 9.3.1（Kotlin 2.4.21 官方兼容上限；Gradle 9.8.2 超出官方测试上限 9.7.0，按要求仍使用） |
| Compose Multiplatform 插件 `org.jetbrains.compose` + runtime/ui/foundation/components-resources | 1.12.2 |
| `org.jetbrains.compose.material3:material3` | 1.9.0 |
| `org.jetbrains.compose.material:material-icons-core` | 1.7.3（不要用 icons-extended） |
| `org.jetbrains.androidx.navigation:navigation-compose` | 2.10.0 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` / `lifecycle-runtime-compose` | 2.11.0 |
| kotlinx-coroutines-core 1.11.0 · kotlinx-serialization-json 1.12.0 · kotlinx-datetime 0.8.0 · kotlinx-io-core 0.9.1 | |
| Ktor `ktor-client-core` / `ktor-client-okhttp` / `ktor-client-darwin` | 3.6.1 |
| `androidx.activity:activity-compose` 1.13.0 · `androidx.work:work-runtime-ktx` 2.12.0 · `androidx.exifinterface:exifinterface` 1.4.2 | |
| Room `androidx.room:room-runtime` / `room-compiler` / 插件 `androidx.room` | 2.8.5（不是 Room3 alpha） |
| `androidx.sqlite:sqlite-bundled` | 2.7.1 |
| KSP `com.google.devtools.ksp` | 2.3.13 |
| ONNX Runtime Android `com.microsoft.onnxruntime:onnxruntime-android` | 1.31.1 |
| ONNX Runtime iOS：SPM `https://github.com/microsoft/onnxruntime-swift-package-manager`，产品 `onnxruntime`，import `OnnxRuntimeBindings` | exactVersion 1.24.2 |
| llama.cpp | tag `b12121`（Android 源码 submodule；iOS `llama-b12121-xcframework.zip`，SHA-256 `00bc6fec00beba802a3912a6afb81e1678da5248a6a92548a9ce66724a3e7638`） |

## 模型清单（运行时下载，大小单位字节，已从 HF/ModelScope 文件 API 核实）
| id | repo | 远端路径 | 大小 | 分组 |
|---|---|---|---:|---|
| `clip_vision` | `Xenova/mobileclip_s2` | `onnx/vision_model.onnx`（fp32） | 143020962 | CLIP |
| `clip_text` | `Xenova/mobileclip_s2` | `onnx/text_model.onnx`（fp32；int8 版余弦未达 0.98，已按下方预案切换） | 253894023 | CLIP |
| `clip_tokenizer` | `Xenova/mobileclip_s2` | `tokenizer.json` | 2224081 | CLIP |
| `ocr_det` | `PaddlePaddle/PP-OCRv6_small_det_onnx` | `inference.onnx` | 9880512 | OCR |
| `ocr_rec` | `PaddlePaddle/PP-OCRv6_small_rec_onnx` | `inference.onnx` | 21159378 | OCR |
| `ocr_rec_config` | `PaddlePaddle/PP-OCRv6_small_rec_onnx` | `inference.yml` | 150579 | OCR |
| `llm` | `unsloth/Qwen3.5-0.8B-GGUF` | `Qwen3.5-0.8B-UD-Q4_K_XL.gguf` | 558772480 | LLM |

三个源的 URL 模板（`{repo}`、`{path}` 原样拼接）：
- `HUGGING_FACE`：`https://huggingface.co/{repo}/resolve/main/{path}`
- `HF_MIRROR`：`https://hf-mirror.com/{repo}/resolve/main/{path}`
- `MODELSCOPE`：`https://modelscope.cn/models/{repo}/resolve/master/{path}`

本地路径：`<filesDir>/models/<id>/<path 的文件名>`（例如 `models/ocr_det/inference.onnx`）。校验：`.onnx`/`.gguf` 必须与上表字节数完全相等；其它文件只要求非空。

## Approach

实施顺序按步骤编号；步骤 1–2 完成后，3–10 中标注“可并行”的切片可分给不同实现者（共享契约在步骤 2 冻结），步骤 11–12 收尾。每一步结束时 `./gradlew :androidApp:assembleDebug` 必须可构建（iOS 部分从步骤 10 起可构建）。

### 1. 工程骨架（先做，单人）
1. 执行 `gradle wrapper --gradle-version 9.8.2` 生成 wrapper。
2. `.gitignore` 追加：`.gradle/`、`build/`、`.kotlin/`、`local.properties`、`.idea/`、`*.iml`、`xcuserdata/`、`iosApp/Kinroll.xcodeproj/`、`iosApp/build/`、`dist/`、`.cxx/`。
3. `git submodule add https://github.com/ggml-org/llama.cpp.git third_party/llama.cpp` 并 checkout tag `b12121`。
4. `gradle.properties`：`org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8`、`org.gradle.caching=true`、`android.useAndroidX=true`、`kotlin.code.style=official`。
5. `gradle/libs.versions.toml`：按“版本锁定”表声明 versions/libraries/plugins（插件别名：`kotlin-multiplatform`、`kotlin-serialization`、`compose-multiplatform`、`compose-compiler`、`android-application`、`android-library`、`android-kmp-library`、`ksp`、`room`）。
6. `settings.gradle.kts`：pluginManagement 仓库 `google()`、`mavenCentral()`、`gradlePluginPortal()`；dependencyResolutionManagement 仓库 `google()`、`mavenCentral()`；`rootProject.name = "Kinroll"`；`include(":shared", ":androidApp", ":llm")`。根 `build.gradle.kts` 以 `apply false` 声明全部插件。
7. `shared/build.gradle.kts`：插件 kotlin-multiplatform、kotlin-serialization、compose-multiplatform、compose-compiler、android-kmp-library、ksp、room。
   - `kotlin { android { namespace = "net.mioyi.kinroll.shared"; compileSdk { version = release(37) { minorApiLevel = 2 } }; minSdk = 30; androidResources { enable = true }; withHostTest {}; compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } } }`。不要用 `androidTarget` / `androidLibrary`。
   - `iosArm64()`、`iosSimulatorArm64()`，`binaries.framework { baseName = "Shared"; isStatic = true }`。
   - commonMain：compose runtime/ui/foundation/material3/material-icons-core/components-resources、navigation-compose、lifecycle-viewmodel-compose、lifecycle-runtime-compose、coroutines、serialization-json、datetime、kotlinx-io-core、ktor-client-core、room-runtime、sqlite-bundled。commonTest：`kotlin("test")`、`kotlinx-coroutines-test`（1.11.0）。
   - androidMain：ktor-client-okhttp、onnxruntime-android、work-runtime-ktx、exifinterface、activity-compose、`project(":llm")`。iosMain：ktor-client-darwin。
   - `dependencies { add("kspAndroid", libs.room.compiler); add("kspIosArm64", …); add("kspIosSimulatorArm64", …) }`；`room { schemaDirectory("$projectDir/schemas") }`。
   - `compose.resources { packageOfResClass = "net.mioyi.kinroll.resources"; publicResClass = false }`。
8. `androidApp/build.gradle.kts`：插件 android-application + compose-compiler（**不要**应用 `org.jetbrains.kotlin.android`，AGP 9 内置 Kotlin）。`namespace = "net.mioyi.kinroll"`、`applicationId = "net.mioyi.kinroll"`、同样的 compileSdk 37.2 DSL、`minSdk = 30`、`targetSdk = 37`、`versionCode = 1`、`versionName = "0.1.0"`、`ndk { abiFilters += "arm64-v8a" }`、Java 17；`buildTypes.release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }`；依赖 `project(":shared")`、activity-compose、work-runtime-ktx。
9. `llm/build.gradle.kts`：`com.android.library`，`namespace = "net.mioyi.kinroll.llm"`，compileSdk 同上，`minSdk = 30`，`ndkVersion = "30.0.16248370"`，`defaultConfig.ndk.abiFilters += "arm64-v8a"`，`externalNativeBuild.cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" }`，`defaultConfig.externalNativeBuild.cmake.arguments += listOf("-DCMAKE_BUILD_TYPE=Release")`，Java 17。先执行 `android sdk install cmake/4.1.2`。
10. 先放一个最小 `App()`（Text）+ `MainActivity`，确认 `./gradlew :androidApp:assembleDebug` 通过后再继续。

### 2. 平台契约（冻结后其余切片并行）
全部位于 `shared/src/commonMain/kotlin/net/mioyi/kinroll/platform/`，签名必须一字不差：
```kotlin
enum class PermissionState { NOT_DETERMINED, DENIED, LIMITED, FULL }
enum class PhotoKind { UNKNOWN, CAMERA, SCREENSHOT, SAVED }

data class PhotoAsset(
    val id: String, val uri: String, val fileName: String?, val mimeType: String?,
    val sizeBytes: Long, val width: Int, val height: Int, val orientation: Int,
    val takenAt: Long?, val addedAt: Long?, val modifiedAt: Long?,      // 全部 epoch 毫秒
    val album: String?, val relativePath: String?, val ownerPackage: String?,
    val isFavorite: Boolean, val isScreenshotHint: Boolean,
    val latitude: Double?, val longitude: Double?,
)
data class PhotoDetails(
    val cameraMake: String?, val cameraModel: String?, val lensModel: String?,
    val iso: Int?, val exposureTime: Double?, val fNumber: Double?, val focalLength: Double?,
    val latitude: Double?, val longitude: Double?, val sizeBytes: Long?,
)
class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray) // RGBA8888，行优先，无行填充
data class PhotoRef(val id: String, val uri: String)

interface PhotoLibrary {
    fun permissionState(): PermissionState
    suspend fun listAssets(): List<PhotoAsset>
    suspend fun readDetails(id: String): PhotoDetails?          // null = 原图不在本地/不可读
    suspend fun loadPixels(id: String, maxSide: Int): RgbaImage? // 长边 ≤ maxSide，已按 EXIF 方向校正
    suspend fun loadThumbnail(id: String, maxSide: Int): ImageBitmap?
}

class TensorInput(val name: String, val shape: LongArray, val floats: FloatArray? = null, val longs: LongArray? = null)
class TensorOutput(val shape: LongArray, val data: FloatArray)
interface InferenceSession : AutoCloseable {
    val inputNames: List<String>
    val outputNames: List<String>
    fun run(inputs: List<TensorInput>, outputs: List<String>): List<TensorOutput>
}
interface InferenceRuntime { fun open(modelPath: String): InferenceSession }

data class SamplingParams(val temperature: Float, val topP: Float, val topK: Int, val presencePenalty: Float, val seed: Int)
interface LlamaNative {
    fun load(modelPath: String, contextSize: Int, threads: Int): String?  // null=成功，否则错误信息
    fun begin(prompt: String, params: SamplingParams): String?            // 清空 KV、重建采样链、解码 prompt；null=成功
    fun next(): ByteArray?                                                 // 下一个 token 的 UTF-8 字节；null=结束(EOG/上下文满)
    fun unload()
}

interface IndexScheduler { fun request() }

class PlatformServices(
    val photoLibrary: PhotoLibrary,
    val inferenceRuntime: InferenceRuntime,
    val llamaNative: LlamaNative,
    val databaseBuilder: RoomDatabase.Builder<KinrollDatabase>,
    val filesDir: String,              // 模型根目录的父目录
    val httpEngine: HttpClientEngineFactory<*>,
    val cpuThreads: Int,               // availableProcessors 限制到 2..4
    val indexSchedulerFactory: (AppGraph) -> IndexScheduler,
)

@Composable expect fun rememberPermissionRequester(onResult: (PermissionState) -> Unit): () -> Unit
@Composable expect fun rememberTrashRequester(onResult: (trashedIds: List<String>) -> Unit): (List<PhotoRef>) -> Unit
expect fun plainTextClipEntry(text: String): ClipEntry
```
`AppGraph(platform: PlatformServices)`（commonMain `net.mioyi.kinroll.AppGraph`）用 `by lazy` 持有：`database`、`settings: SettingsRepository`、`modelManager`、`clip: ClipEngine`、`ocr: OcrEngine`、`llm: LlmService`、`embeddingIndex`、`indexer`、`indexScheduler`、`search: SearchService`、`cleanup: CleanupRepository`、`posts: PostGenerator`、`thumbnails: ThumbnailCache`、`appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`。手写依赖注入，不引入 Koin。

### 3. 数据层（可并行）
`shared/src/commonMain/kotlin/net/mioyi/kinroll/data/`
- `PhotoEntity`（`@Entity(tableName = "photo", indices = [Index("kind"), Index("category"), Index("isBadShot"), Index("indexVersion")])`），普通 `class`（含 ByteArray）。字段：`@PrimaryKey id: String`（Android MediaStore `_ID` 字符串；iOS `localIdentifier`）、`uri`、PhotoAsset 的全部字段、PhotoDetails 的 make/model/lens/iso/exposureTime/fNumber/focalLength、`kind: String`（PhotoKind.name）、`dHash: Long?`、`blurScore: Float?`、`brightness: Float?`、`clipEmbedding: ByteArray?`（512×float32 小端，2048 字节）、`category: String?`、`categoryScore: Float?`、`ocrText: String?`、`ocrJson: String?`、`isBadShot: Boolean`、`badShotReasons: Int`（位：BLUR=1、DARK=2、OVEREXPOSED=4、ACCIDENTAL=8）、`indexVersion: Int`（0=未索引）、`indexedAt: Long?`、`indexError: String?`。
- `SettingEntity(@PrimaryKey key: String, value: String)`，表 `app_setting`；`SettingsRepository` 只存 `download_source`（`DownloadSource.name`）。不引入 DataStore。
- `KinrollDatabase`：`@Database(entities = [PhotoEntity::class, SettingEntity::class], version = 1)`、`@ConstructedBy(KinrollDatabaseConstructor::class)`、`expect object KinrollDatabaseConstructor : RoomDatabaseConstructor<KinrollDatabase>`；`AppGraph` 中 `builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()`。
- `PhotoDao` 方法（全部时间排序键 `COALESCE(takenAt, addedAt) DESC`）：`observeCount(): Flow<Int>`、`observeIndexedCount(version: Int): Flow<Int>`、`syncInfo(): List<SyncInfo(id, modifiedAt, sizeBytes)>`、`insertAll(@Insert(onConflict = REPLACE))`、`deleteByIds(ids)`（调用方按 500 分块）、`pendingIds(version, limit)`、`getById(id)`、`update(entity)`、`observeSummaries()`、`observeByKind(kind)`、`observeBadShots()`、`observeByCategory(category)`、`observeCategoryCounts(): Flow<List<CategoryCount(category, count)>>`、`searchOcr(pattern, limit)`（`ocrText LIKE :pattern`）、`allEmbeddings(): List<IdEmbedding(id, clipEmbedding)>`、`duplicateCandidates(): List<DupRow(id, uri, dHash, clipEmbedding, blurScore, width, height, takenAt, sizeBytes)>`（`dHash IS NOT NULL AND clipEmbedding IS NOT NULL`）、`getByIds(ids)`。`PhotoSummary(id, uri, takenAt, addedAt, sizeBytes, width, height, kind, category, badShotReasons)`。
- `EmbeddingCodec`：`FloatArray ↔ ByteArray`（`toRawBits` 小端），无新依赖。

### 4. 模型下载（可并行）
`net.mioyi.kinroll.model`
- `enum class DownloadSource { HUGGING_FACE, HF_MIRROR, MODELSCOPE; fun url(repo: String, path: String): String }`（模板见上）。默认值：系统首选语言以 `zh` 开头 → `MODELSCOPE`，否则 `HUGGING_FACE`；用户选择写入 `SettingsRepository`。
- `enum class ModelGroup { CLIP, OCR, LLM }`；`data class ModelFile(id, repo, path, sizeBytes: Long, group: ModelGroup, exactSize: Boolean)`；`ModelManifest.files` = 上表 7 项。
- `ModelManager(filesDir, settings, httpClient)`：`localPath(id)`、`isReady(file)`（存在且满足校验）、`allReady()`、`readyFlow: StateFlow<Boolean>`、`suspend fun downloadAll(source, onProgress: (DownloadProgress) -> Unit)`（`DownloadProgress(fileId, fileBytes, fileTotal, overallBytes, overallTotal)`）。
- 下载实现（`Downloader`）：Ktor `HttpClient(platform.httpEngine) { install(HttpTimeout) { requestTimeoutMillis = INFINITE; socketTimeoutMillis = 60_000; connectTimeoutMillis = 30_000 } }`，默认跟随重定向。逐文件顺序下载到 `<目标>.part`：已有 `.part` 时带 `Range: bytes=<len>-`；响应 206 追加写，200 截断重写，416 时若 `.part` 大小等于期望大小视为完成否则删除重下一次。`bodyAsChannel()` 以 64 KiB 缓冲读写（kotlinx-io `SystemFileSystem.sink(path, append)`），每 512 KiB 回调进度。完成后校验大小，`SystemFileSystem.atomicMove(.part → 目标)`；校验失败删除 `.part` 并抛 `IllegalStateException("size mismatch: <id>")`。已就绪文件跳过。网络错误向上抛给 UI 显示“重试”。

### 5. 推理运行时 + 图像/CLIP/OCR 算法（可并行；纯 Kotlin 部分在 commonMain）
**5a. Android `OrtInferenceRuntime`（androidMain）**：单例 `OrtEnvironment.getEnvironment()`；`SessionOptions().apply { setIntraOpNumThreads(cpuThreads); setOptimizationLevel(OptLevel.ALL_OPT) }`，仅 CPU EP。输入：float → `OnnxTensor.createTensor(env, FloatBuffer.wrap(arr), shape)`，int64 → `LongBuffer`。输出：`(result.get(name).get() as OnnxTensor)` 取 `info.shape` 与 `floatBuffer` 拷入 FloatArray。所有 OnnxTensor/Result 用 `use {}` 关闭。

**5b. iOS（iosMain 适配 + Swift 实现，Swift 部分见步骤 10）**：iosMain 声明（Swift 实现）：
```kotlin
interface OrtBridge { fun openSession(modelPath: String): OrtBridgeSession?; fun lastError(): String? }
interface OrtBridgeSession {
    fun inputNames(): List<String>
    fun outputNames(): List<String>
    // types: 1=float32, 7=int64（ONNX 元素类型码）；失败返回 null 并设置 OrtBridge.lastError
    fun run(names: List<String>, datas: List<NSData>, shapes: List<List<Long>>, types: List<Int>, outputNames: List<String>): List<OrtBridgeOutput>?
    fun close()
}
class OrtBridgeOutput(val data: NSData, val shape: List<Long>)  // data 为 float32
```
`IosInferenceRuntime(bridge)` 实现 `InferenceRuntime`：FloatArray/LongArray 用 `usePinned` + `NSData.create(bytes, length)` 转 NSData，输出 NSData 用 `memcpy` 拷入 FloatArray（放 `ios/NSDataExt.kt`）。返回 null 时抛 `IllegalStateException(bridge.lastError())`。

**5c. 图像工具（commonMain `ml/image/`）**：
- `ImageOps.resize(src: RgbaImage, w, h)`：缩小时按面积平均（box filter），放大时双线性；`toGray(src): FloatArray`（Y = 0.299R+0.587G+0.114B）；`cropRotatedRect(src, quad: FloatArray(8), outW, outH)`：仿射映射 + 双线性采样。
- `Quality`：先缩放到长边 512 → `blurScore` = 4 邻域拉普拉斯（0..255 灰度）方差；`brightness` = 平均 Y/255；`dHash`：灰度面积缩放到 9×8，`px[x] > px[x+1]` 置位，64 位 Long；`hamming(a, b) = (a xor b).countOneBits()`。

**5d. CLIP（commonMain `ml/clip/`）**：
- `ClipTokenizer.fromJson(text)`：用 kotlinx.serialization `JsonObject` 读取 `model.vocab`（token→id）与 `model.merges`（兼容 `"a b"` 字符串与 `["a","b"]` 数组两种格式）。编码：小写、合并空白、trim → 正则 `'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+` 切词 → GPT-2 byte→unicode 映射 → BPE（末字符加 `</w>`）→ id。序列 = `[vocab["<|startoftext|>"]] + ids.take(75) + [vocab["<|endoftext|>"]]`，0 填充到 77。
- `ClipEngine(runtime, paths, tokenizer)`：图像 → 短边双线性缩放到 256 → 中心裁剪 256×256 → RGB、`/255`、无均值方差 → `[1,3,256,256]`；首个输入名作为图像输入；输出取 `image_embeds`（不存在则取第一个输出），L2 归一化。文本 → `input_ids` int64 `[1,77]`；若 `session.inputNames` 含 `attention_mask`，则 EOT 及之前为 1、之后为 0；输出取 `text_embeds`（否则第一个输出），L2 归一化。text session 与 vision session 各用一个 `Mutex` 串行。`textEmbedding(prompt)` 结果按字符串缓存在内存 Map。
- `Prompts.kt`：
  - `enum class PhotoCategory(val prompt: String)`：`PEOPLE("a photo of people")`、`CHILD("a photo of a baby or a young child")`、`PET("a photo of a pet cat or dog")`、`FOOD("a photo of food or a meal")`、`LANDSCAPE("a photo of a natural landscape")`、`CITY("a photo of city streets or buildings")`、`NIGHT("a photo taken at night")`、`PLANT("a photo of flowers or plants")`、`VEHICLE("a photo of a car or other vehicle")`、`DOCUMENT("a photo of a document or a page of text")`、`RECEIPT("a photo of a receipt or an invoice")`、`ART("a drawing, painting or illustration")`、`OBJECT("a photo of an everyday object or product")`、`OTHER("")`。分类 = 对前 13 项做 `softmax(100 × cos)`，最高概率 < 0.30 → `OTHER`。
  - 废片提示词：bad = `a blurry photo`、`an accidental photo of the floor`、`a photo taken inside a pocket`、`a very dark underexposed photo`；good = `a clear sharp photo`、`a well-composed photo`。6 项一起 `softmax(100 × cos)`，bad 概率和 ≥ 0.70 → ACCIDENTAL。

**5e. OCR（commonMain `ml/ocr/`，不依赖 OpenCV/Clipper）**：
- `RecDictionary.parse(ymlText)`：定位行 `  character_dict:`，读取其后所有以 `  - ` 开头的行：单引号标量（`''` → `'`）、双引号标量（处理 `\\`、`\"`、`\uXXXX`）、其它原样。遇到非 `  - ` 行停止。推理时若 `dict.size + 1 < 类别数 C`，补一个 `" "`。
- 检测：长边限制 960（大于才缩放），H/W 四舍五入到 32 的倍数且 ≥ 32；通道顺序 **BGR**，第 c 通道 `(v/255 − mean[c]) / std[c]`，mean `[0.485,0.456,0.406]`、std `[0.229,0.224,0.225]`；输入 `[1,3,H,W]`。
- `DbPostProcess`（PP-OCRv6_small yml 参数）：`thresh 0.2`、`boxThresh 0.45`、`unclipRatio 1.4`、`maxCandidates 3000`。二值化 → 8 连通区域标记 → 每个区域像素点做凸包（Andrew 单调链）→ 旋转卡壳求最小外接矩形 → 短边 < 3 丢弃 → 分数 = 该矩形内概率均值 < 0.45 丢弃 → unclip：`d = 面积 × 1.4 / 周长`，矩形宽高各加 `2d` → 短边 < 5 丢弃 → 映射回原图坐标并裁剪到边界 → 角点按 tl、tr、br、bl 排序。框排序：按 tl.y，再在 |Δy| < 10 时按 x。
- 识别：每个框用 `cropRotatedRect` 裁出（宽 = |tl→tr|，高 = |tl→bl|；高/宽 ≥ 1.5 时旋转 90°）→ 高 48、宽 `min(3200, ceil(48 × w/h))`，画布宽 `max(320, 宽)`，右侧以 0 填充（归一化后空间）；通道 BGR，`v/127.5 − 1`；batch 1。输出 `[1,T,C]`：逐步 argmax，blank=0，合并相邻重复，id i → `dict[i-1]`，置信度取保留字符概率均值；置信度 < 0.5 的行丢弃。
- `OcrEngine.recognize(img): OcrResult(lines: List<OcrLine(text, score, box: FloatArray)>)`；`ocrText = lines.joinToString("\n") { it.text }`；`ocrJson` = `[{"t":text,"s":score,"b":[8 floats]}]`（kotlinx.serialization）。

### 6. LLM（可并行）
**6a. Android `:llm` 模块**
- `llm/src/main/cpp/CMakeLists.txt`：`cmake_minimum_required(VERSION 3.22)`；`set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)`；以 `CACHE BOOL "" FORCE` 设置 `BUILD_SHARED_LIBS OFF`、`GGML_NATIVE OFF`、`GGML_OPENMP OFF`、`GGML_LLAMAFILE OFF`、`GGML_CPU_KLEIDIAI OFF`（避免配置期联网拉取）、`LLAMA_OPENSSL OFF`、`LLAMA_BUILD_COMMON OFF`、`LLAMA_BUILD_TESTS OFF`、`LLAMA_BUILD_TOOLS OFF`、`LLAMA_BUILD_EXAMPLES OFF`、`LLAMA_BUILD_SERVER OFF`；`add_subdirectory(${LLAMA_SRC} build-llama)`；`add_library(kinroll_llm SHARED kinroll_llm.cpp)`；`target_link_libraries(kinroll_llm llama log android)`。
- `kinroll_llm.cpp` 实现 `net.mioyi.kinroll.llm.LlamaJni`：
```kotlin
object LlamaJni {
    init { System.loadLibrary("kinroll_llm") }
    external fun load(path: String, nCtx: Int, nThreads: Int): Long          // 0=失败
    external fun lastError(): String
    external fun begin(handle: Long, promptUtf8: ByteArray, temperature: Float, topP: Float, topK: Int, presencePenalty: Float, seed: Int): Boolean
    external fun next(handle: Long): ByteArray?
    external fun free(handle: Long)
}
```
  prompt 以 UTF-8 ByteArray 传入（JNI `GetStringUTFChars` 是 modified UTF-8，emoji 会损坏）。C++ 状态结构体 `{model, ctx, vocab, sampler, n_past, n_ctx}`，handle = 指针。`load`：`llama_backend_init()`（进程内一次）、`llama_model_load_from_file`（`n_gpu_layers = 0`）、`llama_init_from_model`（`n_ctx`、`n_batch = 512`、`n_threads`/`n_threads_batch`）。`begin`：`llama_memory_clear(llama_get_memory(ctx), true)`；释放旧采样器并重建链：temperature ≤ 0 时只加 `llama_sampler_init_greedy()`，否则依次 `llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.0f, 0.0f, presencePenalty)`（b12121 为 5 参数版本）、`top_k`、`top_p(p, 1)`、`temp`、`dist(seed)`；`llama_tokenize(..., add_special=true, parse_special=true)`；prompt token 数 ≥ `n_ctx − 16` 时设错误 `"prompt too long"` 返回 false；按 512 分块 `llama_batch_get_one` + `llama_decode`。`next`：`n_past ≥ n_ctx` → null；`llama_sampler_sample(smpl, ctx, -1)`；`llama_vocab_is_eog` → null；`llama_token_to_piece(..., special=false)` 取字节（不以 \0 结尾）；再 `llama_decode` 该 token；返回字节数组。
- shared androidMain `AndroidLlamaNative : LlamaNative` 包装 LlamaJni（`load` 返回 0 时返回 `LlamaJni.lastError()`；`begin` 用 `prompt.encodeToByteArray()`）。

**6b. commonMain `llm/`**
- `QwenPrompt.format(system: String, user: String): String` =
  `"<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"`（不用 `llama_chat_apply_template`：它不处理 Qwen3.5 的 enable_thinking）。
- `Utf8Accumulator`：缓存字节，只输出最长的完整 UTF-8 前缀。
- `LlmService(native, modelManager, cpuThreads)`：单 `Mutex`；首次使用时 `load(path, 4096, cpuThreads)`，此后常驻；`fun generate(system, user, maxTokens, params): Flow<String>`（在 `Dispatchers.Default` 上循环 `next()`，每次 emit 增量文本，`ensureActive()` 支持取消，结果若以 `<think>` 开头则去掉到 `</think>` 为止）；`suspend fun translateToEnglish(query): String?`（system `"Translate the user's photo search query into concise English for an image search engine. Output only the translation."`，maxTokens 32，`SamplingParams(0f, 1f, 1, 0f, 0)`，模型未就绪/异常返回 null）。
- 帖文默认采样：`SamplingParams(temperature = 0.7f, topP = 0.8f, topK = 20, presencePenalty = 1.5f, seed = Random.nextInt())`，maxTokens 512。

### 7. 索引器与后台（依赖 3、5）
- `KindResolver.resolve(asset, details)`：`isScreenshotHint` → SCREENSHOT；`relativePath` 小写以 `download/` 开头或包含 `weixin`、`wechat`、`tencent`、`qq`、`weibo`、`xiaohongshu`、`douyin`、`telegram`、`whatsapp` 之一 → SAVED；details 有 make 或 model → CAMERA；details 为 null → UNKNOWN；否则 SAVED。
- `Indexer(graph)`，常量 `INDEX_VERSION = 1`；`state: StateFlow<IndexerState>`（`Idle`、`Syncing`、`Indexing(done, total)`、`Failed(message)`）；`suspend fun runOnce()`：`Mutex.tryLock()` 失败直接返回；权限非 FULL/LIMITED 或 `!modelManager.allReady()` 时返回。
  1. 同步：`listAssets()` 与 `syncInfo()` 比对：新 id → 插入（index 字段为默认值）；`modifiedAt` 或 `sizeBytes` 变化 → 以新元数据 REPLACE（索引字段清零）；DB 中有但列表中没有 → 删除并从 `EmbeddingIndex` 移除。
  2. 循环 `pendingIds(INDEX_VERSION, 8)` 直到为空，每张：`readDetails` → `KindResolver` → `loadPixels(id, 1280)`（null → `indexError = "unavailable"`）→ `Quality` → CLIP 向量 → 分类 + 废片 CLIP 分数 → 若 kind ∈ {SCREENSHOT, SAVED} 或 category ∈ {DOCUMENT, RECEIPT} 则 OCR → `isBadShot = kind == CAMERA && reasons != 0`（BLUR：blurScore < 60；DARK：brightness < 0.12；OVEREXPOSED：brightness > 0.92；ACCIDENTAL 见 5d）→ iOS 若 `details.sizeBytes != null` 覆盖 `sizeBytes` → `update`，`indexVersion = INDEX_VERSION`，`indexedAt = now`，并 `embeddingIndex.put`。单张异常：记录 `indexError = e.message`，仍写 `indexVersion = INDEX_VERSION`（不死循环），继续下一张。每张后 `ensureActive()`。每张记录一行日志 `println("kinroll-index id=… kind=… category=… ocrChars=… ms=…")`。
- Android（shared androidMain）：`IndexWorker : CoroutineWorker` 开始时 `setForeground(ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))`（捕获 `IllegalStateException`/`ForegroundServiceStartNotAllowedException` 后不带前台继续），通知渠道 `indexing`（IMPORTANCE_LOW），每 20 张更新进度；调用 `KinrollGraph.get().indexer.runOnce()`。`AndroidIndexScheduler.request()` = `enqueueUniqueWork("kinroll-index", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<IndexWorker>().build())`。
- iOS（iosMain）：`IosIndexScheduler.request()` = `graph.appScope.launch { indexer.runOnce() }`；`KinrollIos.runBackgroundIndexing(onDone: (Boolean) -> Unit): IndexJobHandle`（`IndexJobHandle.cancel()` 取消 Job，完成后回调 `onDone(!cancelled)`）。
- UI 触发：`App()` 中当 Home 首次显示与生命周期 `ON_RESUME` 时调用 `graph.indexScheduler.request()`（`LifecycleEventEffect`）。

### 8. 检索 / 分类 / 清理 / 重复（依赖 3、5d）
- `EmbeddingIndex`：首次使用时 `allEmbeddings()` 载入 `Map<String, FloatArray>`；`put`/`remove`；`topK(query, k, minScore)` 暴力点积。
- `SearchService.search(query): List<String>`：trim 后为空 → 空；`ocrHits = searchOcr("%$q%", 100)`；英文查询 = 含 CJK（`\u3400-\u9FFF`）时 `llm.translateToEnglish(q)`，否则 `q`；非 null 时 `clip.textEmbedding` → `topK(k = 60, minScore = 0.15)`；结果 = OCR 命中在前 + CLIP 命中（去重），上限 120。
- `CleanupRepository`：截图 = `observeByKind(SCREENSHOT)`；保存图 = `observeByKind(SAVED)`；废片 = `observeBadShots()`；重复 = `DuplicateFinder.find(duplicateCandidates())`：两两比较 `hamming ≤ 6 && cos ≥ 0.92` → 并查集分组（≥2 张）；每组保留项 = blurScore 最高 → 像素数最多 → takenAt 最新，其余预选。`trash` 成功回调后 `deleteByIds` + `embeddingIndex.remove`。

### 9. UI（依赖 2；可与 3–8 并行，用假数据先行）
- 主题 `ui/theme/Theme.kt`：`KinrollTheme { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light) }`，种子主色 `#3B6EA8`（light: primary `#3B6EA8`；dark: primary `#A5C8FF`），其余用 `lightColorScheme`/`darkColorScheme` 默认。语言与深浅色均跟随系统（不做应用内切换）。
- 字符串：`shared/src/commonMain/composeResources/values/strings.xml`（英文）与 `values-zh/strings.xml`（中文），Kotlin 中不得硬编码用户可见文字。核心文案：应用名 Kinroll / 相知；入口 Photo Cleanup / 图片清理、Photo Overview / 图片概览、Post Writer / 帖文生成；清理 Tab：Screenshots / 截图、Saved images / 保存图、Bad shots / 废片、Duplicates / 重复；风格：Xiaohongshu / 小红书、Moments / 朋友圈、Weibo / 微博、Instagram；PhotoCategory 13 项 + OTHER 的中英文标签。
- `ThumbnailCache`：容量 300 的 LRU（LinkedHashMap，访问时 remove 后重新 put），键 `"$id@$maxSide"`；`PhotoThumbnail(id, maxSide = 256)` 用 `produceState` 调 `photoLibrary.loadThumbnail`。`PhotoGrid`：`LazyVerticalGrid(GridCells.Adaptive(96.dp))`，可选多选模式（勾选角标）。
- 导航（navigation-compose 2.10.0，`@Serializable` 路由）：`OnboardingRoute`、`HomeRoute`、`CleanupRoute`、`OverviewRoute`、`PhotoDetailRoute(id: String)`、`PostRoute`。起始路由：权限 ∈ {FULL, LIMITED} 且 `modelManager.allReady()` → Home，否则 Onboarding（因此“首次进入”与模型缺失都会进入引导）。ViewModel 用 `viewModel { XxxViewModel(graph) }`。
- **Onboarding**：步骤 1 权限（说明 + “授权相册访问”按钮 → `rememberPermissionRequester`；DENIED 时显示前往系统设置提示；LIMITED 显示提示但允许继续）；步骤 2 模型（三组大小：CLIP ≈ 399 MB、OCR ≈ 31 MB、LLM ≈ 559 MB，界面按清单实时计算；下载源单选 Hugging Face / hf-mirror（国内镜像）/ ModelScope（魔搭）；“开始下载”显示当前文件与总进度条；失败显示错误与“重试”）；全部就绪后“进入”→ `navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }`。已满足的步骤自动跳过。
- **Home**：TopAppBar 应用名；索引卡片：`LinearProgressIndicator(progress = indexed / total)` + 文本（“正在同步…” / “正在索引 x / y” / “已完成 y 张” / 失败信息）；三张入口卡片。
- **Cleanup**：`PrimaryTabRow` 四个 Tab；网格多选，“全选/全不选”，底栏显示已选数量与总大小（`sizeBytes` 求和）+“移到回收站”按钮 → `rememberTrashRequester`；废片项显示原因标签；重复 Tab 按组分段展示，非保留项预选。
- **Overview**：顶部搜索框（提交时搜索，翻译/检索中显示进度）；分类 FilterChip 行（全部、截图、各 PhotoCategory 及数量，数量为 0 不显示）；网格；点击进入 **PhotoDetail**：`loadThumbnail(id, 1600)` 大图 + 元数据列表（拍摄时间、尺寸、大小、相机/镜头/ISO/快门/光圈/焦距、经纬度、类型、分类、OCR 文本，缺失项不显示）。
- **Post**：照片多选（全部照片，最多 9 张，已选缩略条）→ 风格 SegmentedButton（4 个预设）→ 补充说明输入框 → “生成”流式显示（可“停止”）→ “复制”（`LocalClipboard.current.setClipEntry(plainTextClipEntry(text))`）。LLM 首次加载时显示“正在加载模型”。
- `PostPrompts`（`PostGenerator.build(photos, style, notes, zh: Boolean)`）：system（中文界面）`"你是一名社交媒体文案助手。根据用户提供的照片信息，写一篇{平台}帖文。只输出帖文正文。{风格要求}"`；风格要求：小红书 = “第一行是带 emoji 的标题，正文分段并适量使用 emoji，结尾 3–5 个 #话题，总长 150–300 字”；朋友圈 = “1–3 句口语化短文，可用 1–2 个 emoji，不加话题标签”；微博 = “140 字以内，可带 1–3 个 #话题#”；Instagram = “简短配文，空一行后附 5–10 个 hashtag”。英文界面使用对应英文版本。user 消息：逐张 `"{序号}. 拍摄于 {yyyy-MM-dd}，类别：{分类标签}，图中文字：{ocrText 前 120 字，换行替换为空格}"`（无值项省略），末尾 `"补充说明：{notes}"`（为空省略）。界面语言判断：`Locale.current.language.startsWith("zh")`（Compose `androidx.compose.ui.text.intl.Locale`）。

### 10. 平台实现
**10a. Android（shared androidMain + androidApp）**
- `AndroidPhotoLibrary(context)`：查询 `MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)`，projection：`_ID, DISPLAY_NAME, MIME_TYPE, SIZE, WIDTH, HEIGHT, ORIENTATION, DATE_TAKEN, DATE_ADDED, DATE_MODIFIED, RELATIVE_PATH, BUCKET_DISPLAY_NAME, OWNER_PACKAGE_NAME, IS_FAVORITE`，selection `IS_TRASHED = 0`（API 30 默认已排除，仍显式写）；`DATE_ADDED/DATE_MODIFIED` 秒 ×1000；`uri = ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, id)`；`isScreenshotHint = relativePath 含 "screenshot"（忽略大小写）|| fileName 以 "screenshot" 开头（忽略大小写）`；经纬度在 listAssets 中为 null。`readDetails`：已授予 `ACCESS_MEDIA_LOCATION` 时 `MediaStore.setRequireOriginal(uri)` 打开流，`androidx.exifinterface.media.ExifInterface(stream)` 读 `TAG_MAKE`、`TAG_MODEL`、`TAG_LENS_MODEL`、`TAG_PHOTOGRAPHIC_SENSITIVITY`、`TAG_EXPOSURE_TIME`、`TAG_F_NUMBER`、`TAG_FOCAL_LENGTH`、`latLong`；`sizeBytes = null`；任何异常 → null。`loadPixels`/`loadThumbnail`：`contentResolver.loadThumbnail(uri, Size(maxSide, maxSide), null)`，`HARDWARE` 配置时 `copy(ARGB_8888)`；loadPixels 用 `copyPixelsToBuffer` 得 RGBA 字节，loadThumbnail 返回 `asImageBitmap()`；IO 异常 → null。全部在 `Dispatchers.IO`。`permissionState()`：API ≥ 33 看 `READ_MEDIA_IMAGES`，否则 `READ_EXTERNAL_STORAGE` → FULL；API ≥ 34 且仅 `READ_MEDIA_VISUAL_USER_SELECTED` → LIMITED；否则 DENIED。
- `rememberPermissionRequester`（android actual）：`RequestMultiplePermissions`，申请集合：API ≥ 34 `READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED, ACCESS_MEDIA_LOCATION, POST_NOTIFICATIONS`；API 33 `READ_MEDIA_IMAGES, ACCESS_MEDIA_LOCATION, POST_NOTIFICATIONS`；API 30–32 `READ_EXTERNAL_STORAGE, ACCESS_MEDIA_LOCATION`；回调结果后重新计算 `permissionState()`。
- `rememberTrashRequester`（android actual）：`StartIntentSenderForResult`；把 uri 按 2000 分批，依次 `MediaStore.createTrashRequest(resolver, batch, true)` 发起；每批 RESULT_OK 累计其 id，遇到取消即停止；最后回调已移入回收站的 id。
- `plainTextClipEntry`（android）= `ClipEntry(ClipData.newPlainText("kinroll", text))`；iOS = `ClipEntry.withPlainText(text)`。
- `AndroidPlatform.create(context): PlatformServices`（databaseBuilder = `Room.databaseBuilder<KinrollDatabase>(context, context.getDatabasePath("kinroll.db").absolutePath)`、filesDir = `context.filesDir.absolutePath`、`OkHttp` 引擎、`AndroidIndexScheduler`）；`KinrollGraph`（`init(platform)` / `get()` 单例）。
- `androidApp`：`KinrollApplication.onCreate` → `KinrollGraph.init(AndroidPlatform.create(this))`；`MainActivity`：`enableEdgeToEdge()`；`setContent { App(KinrollGraph.get()) }`。`AndroidManifest.xml`：权限 `READ_EXTERNAL_STORAGE`(maxSdkVersion=32)、`READ_MEDIA_IMAGES`、`READ_MEDIA_VISUAL_USER_SELECTED`、`ACCESS_MEDIA_LOCATION`、`INTERNET`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`；`<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="dataSync" tools:node="merge" />`；`android:label="@string/app_name"`（`res/values` = Kinroll，`res/values-zh` = 相知）；自适应启动图标：背景 `#3B6EA8`，前景为白色 Material `photo_library` 矢量。

**10b. iOS（iosMain + Swift，xcodegen）**
- iosMain 额外桥接（Swift 实现）：
```kotlin
interface PhotoKitBridge {
    fun authorizationStatus(): Int                      // PHAuthorizationStatus 原始值：0 未决定 1 受限 2 拒绝 3 允许 4 有限
    fun requestAuthorization(completion: (Int) -> Unit)
    fun fetchAssets(): List<PhotoAsset>                 // 阻塞调用
    fun readDetails(id: String): PhotoDetails?          // 阻塞调用
    fun loadPixels(id: String, maxSide: Int): BridgePixels?  // 阻塞调用，RGBA8 非预乘
    fun deleteAssets(ids: List<String>, completion: (Boolean) -> Unit)
}
class BridgePixels(val width: Int, val height: Int, val rgba: NSData)
```
  `IosPhotoLibrary(bridge)`：阻塞方法在 `Dispatchers.Default` 上调用；状态映射 0→NOT_DETERMINED、1/2→DENIED、3→FULL、4→LIMITED；`loadThumbnail` 用 Skia `Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, w * 4).toComposeImageBitmap()`。iOS actual 的两个 remember* 通过 `KinrollIos.photoKit`（initialize 时保存的 bridge）调用，回调中切回主线程；Android actual 通过 `LocalContext` 取 Activity/ContentResolver。删除成功时回调全部 id，失败回调空列表。
- `KinrollIos`（iosMain `object`）：`fun initialize(photoKit: PhotoKitBridge, ort: OrtBridge, llama: LlamaNative)` 构建 `AppGraph`（databaseBuilder = `Room.databaseBuilder<KinrollDatabase>(name = <NSDocumentDirectory>/kinroll.db)`；filesDir = Application Support 目录（不存在则创建，并设置 `NSURLIsExcludedFromBackupKey = true`）；`Darwin` 引擎；cpuThreads = `NSProcessInfo.processInfo.activeProcessorCount` 限制 2..4）；`fun runBackgroundIndexing(...)`（见步骤 7）。`MainViewController.kt`：`fun MainViewController(): UIViewController = ComposeUIViewController { App(KinrollIos.graph) }`。
- Swift（`iosApp/Kinroll/`）：
  - `KinrollApp.swift`（`@main` SwiftUI App）：`init` 中 `KinrollIos.shared.initialize(photoKit: PhotoKitBridgeImpl(), ort: OrtBridgeImpl(), llama: LlamaBridgeImpl())`，注册 `BGTaskScheduler.shared.register(forTaskWithIdentifier: "net.mioyi.kinroll.index", using: nil)`：处理器调用 `runBackgroundIndexing`，`expirationHandler` 调 `cancel()`，完成时 `setTaskCompleted(success:)`；`scenePhase == .background` 时提交 `BGProcessingTaskRequest`（`requiresExternalPower = true`、`requiresNetworkConnectivity = false`）。`WindowGroup { ComposeView().ignoresSafeArea() }`。
  - `ComposeView.swift`：`UIViewControllerRepresentable` 返回 `MainViewControllerKt.MainViewController()`。
  - `PhotoKitBridgeImpl.swift`：`PHPhotoLibrary.requestAuthorization(for: .readWrite)`；`fetchAssets` 用 `PHAsset.fetchAssets(with: .image, options: 按 creationDate 排序)`：id/uri = `localIdentifier`、fileName = `PHAssetResource.assetResources(for:).first?.originalFilename`、`isScreenshotHint = mediaSubtypes.contains(.photoScreenshot)`、`location` → 经纬度、`creationDate/modificationDate` → 毫秒、`sizeBytes = 0`、album/relativePath/ownerPackage = nil。`readDetails`：`requestImageDataAndOrientation`（`version = .original`、`isNetworkAccessAllowed = false`、`isSynchronous = true`）→ `CGImageSourceCopyPropertiesAtIndex` 读 TIFF Make/Model、EXIF LensModel/ISOSpeedRatings[0]/ExposureTime/FNumber/FocalLength，`sizeBytes = data.count`；无数据 → nil。`loadPixels`：`requestImage`（`deliveryMode = .highQualityFormat`、`resizeMode = .exact`、`isSynchronous = true`、`isNetworkAccessAllowed = false`、`targetSize` 按长边 maxSide 等比、`.aspectFit`）→ 绘制到 8 位 RGBA `CGContext`（`CGImageAlphaInfo.noneSkipLast`、sRGB 色彩空间，alpha 字节恒为 255）→ `NSData`。`deleteAssets`：`performChanges { PHAssetChangeRequest.deleteAssets(PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)) }`。Kotlin 函数类型参数在 Swift 中是 `(KotlinInt) -> KotlinUnit` / `(KotlinBoolean) -> KotlinUnit`。
  - `OrtBridgeImpl.swift`：`import OnnxRuntimeBindings`；一个 `ORTEnv(loggingLevel: .warning)`；`ORTSessionOptions` 设 `setIntraOpNumThreads`；`ORTValue(tensorData: NSMutableData(data:), elementType: .float/.int64, shape:)`；`session.run(withInputs:outputNames:runOptions: nil)`；输出 `tensorData()` 与 `tensorTypeAndShapeInfo().shape` → `OrtBridgeOutput`；所有 `throws` 捕获后写 `lastError` 返回 nil。
  - `LlamaBridgeImpl.swift`：`import llama`，实现 `LlamaNative`，逻辑与 6a 的 C++ 完全一致（同样的采样链与 5 参数 penalties）；`n_gpu_layers`：`#if targetEnvironment(simulator)` 为 0，否则 99；`next()` 返回 `KotlinByteArray`（逐字节 `set`，token 片段很短）。
  - `Info.plist`：`CFBundleDisplayName = Kinroll`、`CFBundleDevelopmentRegion = en`、`CFBundleLocalizations = [en, zh-Hans]`、`NSPhotoLibraryUsageDescription`、`PHPhotoLibraryPreventAutomaticLimitedAccessAlert = true`、`BGTaskSchedulerPermittedIdentifiers = [net.mioyi.kinroll.index]`、`UIBackgroundModes = [processing]`、`CADisableMinimumFrameDurationOnPhone = true`、`UILaunchScreen = {}`。`zh-Hans.lproj/InfoPlist.strings`：`CFBundleDisplayName = "相知"` 与中文 `NSPhotoLibraryUsageDescription`；`en.lproj/InfoPlist.strings` 英文版。
  - `iosApp/Packages/LlamaBinary/Package.swift`（swift-tools-version 5.9，platforms `.iOS(.v17)`）：product `.library(name: "LlamaBinary", targets: ["llama"])`，`.binaryTarget(name: "llama", url: "https://github.com/ggml-org/llama.cpp/releases/download/b12121/llama-b12121-xcframework.zip", checksum: "00bc6fec00beba802a3912a6afb81e1678da5248a6a92548a9ce66724a3e7638")`。
  - `iosApp/project.yml`（xcodegen；生成的 `Kinroll.xcodeproj` 不入库）：`name: Kinroll`；`options.deploymentTarget.iOS: "17.0"`；packages：`OnnxRuntime`（url 见版本表，`exactVersion: 1.24.2`）、`LlamaBinary`（`path: Packages/LlamaBinary`）；target `Kinroll`（application/iOS，sources `Kinroll`，依赖 `OnnxRuntime/onnxruntime` 与 `LlamaBinary/LlamaBinary`）；settings：`PRODUCT_BUNDLE_IDENTIFIER: net.mioyi.kinroll`、`MARKETING_VERSION: 0.1.0`、`CURRENT_PROJECT_VERSION: 1`、`TARGETED_DEVICE_FAMILY: "1,2"`、`SWIFT_VERSION: 5.0`、`ENABLE_USER_SCRIPT_SANDBOXING: NO`、`FRAMEWORK_SEARCH_PATHS: "$(inherited) $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"`、`OTHER_LDFLAGS: "$(inherited) -framework Shared"`、`LD_RUNPATH_SEARCH_PATHS: "$(inherited) @executable_path/Frameworks"`、`INFOPLIST_FILE: Kinroll/Info.plist`；`preBuildScripts`：名称 “Compile Kotlin Framework”，`basedOnDependencyAnalysis: false`，脚本：
    ```sh
    if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then exit 0; fi
    cd "$SRCROOT/.."
    ./gradlew :shared:embedAndSignAppleFrameworkForXcode
    ```

### 11. 脚本与 CI
- `scripts/gen-ios-project.sh`：`cd iosApp && xcodegen generate`。
- `scripts/build-apk.sh`：`./gradlew :androidApp:assembleRelease`，复制 `androidApp/build/outputs/apk/release/androidApp-release.apk` 到 `dist/Kinroll-${VERSION:-0.1.0}.apk`。
- `scripts/build-ipa.sh`：调用 gen 脚本；`xcodebuild archive -project iosApp/Kinroll.xcodeproj -scheme Kinroll -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -archivePath build/ios/Kinroll.xcarchive CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""`；清空并新建 `build/ios/unsigned/Payload`，复制 `Kinroll.app`，`zip -qry` 生成 `dist/Kinroll-${VERSION:-0.1.0}-unsigned.ipa`。所有脚本 `set -euo pipefail`、从仓库根运行（脚本内 `cd "$(dirname "$0")/.."`）。
- `.github/workflows/release.yml`：触发 `push.tags: ['v*']` 与 `workflow_dispatch`；`VERSION=${GITHUB_REF_NAME#v}`。
  - job `android`（`ubuntu-latest`）：`actions/checkout@v4`（`submodules: recursive`）、`actions/setup-java@v4`（temurin 21）、`gradle/actions/setup-gradle@v4`、`$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.2" "ndk;30.0.16248370" "cmake;4.1.2"`、`scripts/build-apk.sh`、`actions/upload-artifact@v4`。
  - job `ios`（`macos-26`）：checkout（不需要 submodule）、setup-java temurin 21、`brew install xcodegen`、`scripts/build-ipa.sh`、upload-artifact。
  - job `release`（needs 两者，仅 tag 触发）：`actions/download-artifact@v4` → `softprops/action-gh-release@v2` 上传 `dist/*`。

### 12. 单元测试（commonTest，随对应切片编写）
- `ClipTokenizerTest`：手工构造小 vocab/merges 的 tokenizer.json，校验 BPE 合并、`</w>`、SOT/EOT 包裹、77 长度填充、两种 merges 格式。
- `DbPostProcessTest`：在 64×64 概率图中放一个 20×8 的 0.9 矩形 → 恰 1 个框，unclip 后宽高 ≈ 原尺寸 + 2d（误差 ≤ 1px）；低于 boxThresh 的块被丢弃。`GeometryTest`：旋转 30° 的矩形点集 → 最小外接矩形面积误差 < 2%。
- `CtcDecoderTest`：`[blank, a, a, blank, b]` → `"ab"`。`RecDictionaryTest`：`''''` → `'`、`'"'` → `"`、plain、遇到非列表行停止。
- `Utf8AccumulatorTest`：把 “猫🐱” 的字节拆成单字节逐个喂入，输出拼接等于原文且中途不产生替换字符。
- `QualityTest`：纯色图 blurScore = 0；dHash 对同一图片缩放版本 hamming ≤ 2。`DuplicateFinderTest`：两张相同 hash + 相同向量成组，保留 blurScore 高者。`KindResolverTest`：各分支。`DownloadSourceTest`：三个 URL 模板。

## Critical files & anchors
- `shared/build.gradle.kts` — AGP 9 的 `kotlin { android { … } }` DSL、KSP 按目标添加 Room 编译器、`Shared` 静态框架。
- `shared/src/commonMain/kotlin/net/mioyi/kinroll/platform/` — 步骤 2 冻结的契约；Android/iOS/Swift 三方都实现它。
- `llm/src/main/cpp/kinroll_llm.cpp` 与 `iosApp/Kinroll/LlamaBridgeImpl.swift` — 两份必须语义一致的 llama.cpp 调用序列（以 `third_party/llama.cpp/include/llama.h` 为准）。
- `shared/src/commonMain/kotlin/net/mioyi/kinroll/ml/ocr/DbPostProcess.kt` — 替代 OpenCV/Clipper 的纯 Kotlin 几何实现，精度风险集中处。
- `iosApp/project.yml` — Kotlin 框架构建脚本、SPM（ONNX Runtime、llama xcframework）与签名/搜索路径设置。

## Verification
前置：仓库根目录；`android sdk install cmake/4.1.2`；`git submodule update --init`；联网（下载依赖与约 800 MB 模型）。
1. 单元测试：`./gradlew :shared:allTests` 全部通过（覆盖步骤 12 的用例；任务需同时跑 `iosSimulatorArm64Test` 与 Android host test）。
2. 构建：`./gradlew :androidApp:assembleDebug`；`scripts/build-apk.sh` 产出 `dist/Kinroll-0.1.0.apk`，`unzip -l` 可见 `lib/arm64-v8a/libkinroll_llm.so` 与 `libonnxruntime.so`。`scripts/build-ipa.sh` 产出 `dist/Kinroll-0.1.0-unsigned.ipa`，`unzip -l` 可见 `Payload/Kinroll.app/Kinroll` 与 `Payload/Kinroll.app/Frameworks/llama.framework`。
3. Android 冒烟（AVD `Pixel_10_API_37.2`）：`android emulator start Pixel_10_API_37.2`；准备素材：`curl -o /tmp/cats.jpg http://images.cocodataset.org/val2017/000000039769.jpg`（两只猫）、`curl -o /tmp/receipt.jpg` 任意含中文文字的票据/文档图片，`adb push` 到 `/sdcard/DCIM/Camera/` 与 `/sdcard/Download/`；`adb shell screencap -p /sdcard/Pictures/Screenshots/shot1.png`；同一张猫图再 push 一份到 `/sdcard/DCIM/Camera/cats_copy.jpg`；`adb shell content call --uri content://media --method scan_volume --arg external_primary`。`android run --apks=dist/Kinroll-0.1.0.apk` 后验证：
   - 首次启动进入引导 → 授权 → 选 ModelScope → 下载完成（7 个文件）→ 进入主页；再次启动直接进入主页。
   - 主页进度条走到 “已完成 N 张”；`adb logcat | grep kinroll-index` 每张一行，截图 kind=SCREENSHOT、Download 中图片 kind=SAVED、猫图 category=PET。
   - 概览：搜索 `cat` 首位为猫图；搜索 `猫` 经翻译后同样命中；搜索票据中的中文词通过 OCR 命中票据图；分类 Chip “宠物”含猫图。
   - 清理：截图 Tab 含 shot1.png；重复 Tab 出现猫图与其副本一组且副本预选；“移到回收站”弹出系统确认，确认后项目从列表消失。
   - 帖文：选 2 张图、选“小红书”、生成 → 流式出现中文文案并可复制；切换系统语言为英文后界面与生成内容为英文；系统深色模式下界面为深色。
4. iOS 冒烟（模拟器 `iPhone 18 Pro`）：`xcrun simctl boot "iPhone 18 Pro"`；`xcrun simctl addmedia booted /tmp/cats.jpg /tmp/receipt.jpg <一张模拟器截图>`；`scripts/gen-ios-project.sh` 后 `xcodebuild -project iosApp/Kinroll.xcodeproj -scheme Kinroll -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 18 Pro' build`，`xcrun simctl install booted <DerivedData>/Kinroll.app`，`xcrun simctl launch --console booted net.mioyi.kinroll`；重复步骤 3 的引导/索引/搜索/清理/帖文检查（控制台可见 `kinroll-index` 行）。

## Assumptions & contingencies
- MobileCLIP-S2 官方权重许可（apple/MobileCLIP-S2 LICENSE）限非商业研究用途；按 docs 指定仍使用 `Xenova/mobileclip_s2` 转换版，App 运行时下载、不随包分发。
- 文本编码器选 int8 版以省 190 MB：若 `text_model_int8.onnx` 在任一平台加载失败，或与 fp32 版对同一提示词向量余弦 < 0.98，则清单改为 `onnx/text_model.onnx`（253894023 字节）。**已触发**：实测 int8/fp32 余弦 0.85–0.985（多语言提示词 0.85），清单已改为 fp32。
- 若 Material3 1.9.0 与 CMP 1.12.2 编译/运行不兼容，改用 `1.12.0-alpha03`（CMP 1.12.2 发布配套版本）。
- 若 KSP 2.3.13 无法处理 Kotlin 2.4.21 的 Room 生成，改用 SQLDelight 2.4.1（插件 `app.cash.sqldelight`），表结构与 DAO 方法名保持步骤 3 不变。
- 若 Xcode 链接报找不到 `Shared` 或 C++ 符号，在 `OTHER_LDFLAGS` 追加 `-lc++`；若 SPM 无法解析 llama xcframework zip 布局，改为 `scripts/fetch-llama.sh` 下载解压到 `iosApp/Frameworks/llama.xcframework`（gitignore）并在 project.yml 以 `framework: Frameworks/llama.xcframework, embed: true` 引用，CI 中先执行该脚本。**已触发**：官方 b12121 xcframework 不含 iOS 模拟器切片；`scripts/fetch-llama.sh` 校验并保留官方真机二进制，另从同一 tag 源码构建 arm64 模拟器切片（`KINROLL_IOS_DEVICE_ONLY=1` 时跳过，供 IPA/CI 使用）。
- 若 GitHub 无 `macos-26` 镜像，iOS job 改用 `macos-latest` 并加 `sudo xcode-select -s` 选择最新 Xcode。
- CI 每次构建使用 runner 自带的新 debug keystore，不同版本 APK 签名不同（用户选择“始终 debug 签名”）。
