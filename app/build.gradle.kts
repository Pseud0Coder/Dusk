plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Reads a key from the build environment (GitHub Actions secrets). Empty if not set. */
fun secret(name: String, fallback: String = ""): String =
    (System.getenv(name) ?: "").trim().ifEmpty { fallback }

// On-device Kokoro voice runs on sherpa-onnx. Its Android library is fetched once
// from the official release instead of being committed to the repo.
val sherpaVersion = "1.13.8"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
        .toURL().openStream().use { input -> sherpaAar.outputStream().use { input.copyTo(it) } }
}

android {
    namespace = "com.dusk.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dusk.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "OPENROUTER_KEY", "\"${secret("OPENROUTER_KEY")}\"")
        buildConfigField("String", "INWORLD_KEY", "\"${secret("INWORLD_KEY")}\"")
        buildConfigField("String", "INWORLD_VOICE", "\"${secret("INWORLD_VOICE", "Sarah")}\"")
        buildConfigField("String", "INWORLD_MODEL", "\"${secret("INWORLD_MODEL", "inworld-tts-2")}\"")
        buildConfigField("String", "FISH_KEY", "\"${secret("FISH_KEY")}\"")
        buildConfigField("String", "FISH_VOICE_ID", "\"${secret("FISH_VOICE_ID")}\"")
        buildConfigField("String", "FISH_MODEL", "\"${secret("FISH_MODEL", "s2.1-pro")}\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation(files(sherpaAar))
}
