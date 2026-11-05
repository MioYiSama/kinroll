plugins { alias(libs.plugins.android.library) }
android {
    namespace = "net.mioyi.kinroll.llm"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    ndkVersion = "30.0.16248370"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild.cmake.arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
