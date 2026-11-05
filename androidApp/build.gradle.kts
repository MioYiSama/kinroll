plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "net.mioyi.kinroll"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    // Same NDK as :llm, so packaging can strip debug info from the native libraries.
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "net.mioyi.kinroll"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)
}
