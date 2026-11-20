import java.time.Instant
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kponseel.vocaux"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kponseel.vocaux"
        minSdk = 29
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "BUILD_TIME", "\"" + Instant.now().toString() + "\"")
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Clé de signature fixe : chaque nouvelle version peut s'installer par-dessus la précédente.
    // App personnelle installée à la main (pas sur le Play Store).
    signingConfigs {
        create("sideload") {
            storeFile = file("vocaux.keystore")
            storePassword = "vocaux-sideload"
            keyAlias = "vocaux"
            keyPassword = "vocaux-sideload"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug { signingConfig = signingConfigs.getByName("sideload") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.anthropic:anthropic-java:3.10.0")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
