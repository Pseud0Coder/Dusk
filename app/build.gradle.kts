plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Reads a key from the build environment (GitHub Actions secrets). Empty if not set. */
fun secret(name: String, fallback: String = ""): String =
    (System.getenv(name) ?: "").trim().ifEmpty { fallback }

android {
    namespace = "com.dusk.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dusk.app"
        minSdk = 26
        targetSdk = 36
        // Play rejects an upload whose versionCode isn't higher than the last one, so CI numbers every build.
        versionCode = secret("GITHUB_RUN_NUMBER", "1").toIntOrNull() ?: 1
        versionName = "1.0"
        buildConfigField("String", "OPENROUTER_KEY", "\"${secret("OPENROUTER_KEY")}\"")
        buildConfigField("String", "INWORLD_KEY", "\"${secret("INWORLD_KEY")}\"")
        buildConfigField("String", "INWORLD_MODEL", "\"${secret("INWORLD_MODEL", "inworld-tts-2")}\"")
        buildConfigField("String", "FISH_KEY", "\"${secret("FISH_KEY")}\"")
        buildConfigField("String", "FISH_MODEL", "\"${secret("FISH_MODEL", "s2.1-pro-free")}\"")
        // Where "Report this reply" goes. REPORT_URL sends it from inside the app; SUPPORT_EMAIL is the fallback.
        buildConfigField("String", "REPORT_URL", "\"${secret("DUSK_REPORT_URL")}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${secret("DUSK_SUPPORT_EMAIL")}\"")
    }
    signingConfigs {
        // Google Play upload key. Defined only when the environment supplies it (see README, "Publishing to Google Play").
        val uploadKeystore = secret("DUSK_KEYSTORE_FILE")
        if (uploadKeystore.isNotEmpty()) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = secret("DUSK_KEYSTORE_PASSWORD")
                keyAlias = secret("DUSK_KEY_ALIAS")
                keyPassword = secret("DUSK_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // No fallback to the debug key: Play rejects debug-signed bundles, so without the upload key
            // the release build comes out unsigned instead of looking valid.
            signingConfig = signingConfigs.findByName("upload")
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

    testImplementation("junit:junit:4.13.2")
}
