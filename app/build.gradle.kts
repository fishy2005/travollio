plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Gemini + DeepSeek agent config: read from gemini.local.properties (gitignored) at build time.
// NOTE: this file lives at the PROJECT ROOT — `file(...)` here would resolve against
// the app module dir (app/), so use rootProject.file(...). This was a real bug:
// debugging the v0.2.1 APK on the emulator showed the agent pill "local · offline-safe"
// because the key was never baked (empty geminiLines).
val agentLines = rootProject.file("gemini.local.properties").takeIf { it.exists() }?.readLines().orEmpty()
fun agentProp(k: String): String =
    agentLines.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')?.trim() ?: ""
val geminiKey = agentProp("GEMINI_API_KEY")
val geminiModel = agentProp("GEMINI_MODEL").ifEmpty { "gemini-2.5-flash" }
val deepSeekKey = agentProp("DEEPSEEK_API_KEY")
val deepSeekBase = agentProp("DEEPSEEK_BASE_URL").ifEmpty { "https://api.deepseek.com/v1" }
val deepSeekModel = agentProp("DEEPSEEK_MODEL").ifEmpty { "deepseek-chat" }

// GitHub update feed identity — drives the in-app "check for updates". Read from a
// gitignored root file `github.local.properties` (same pattern as gemini.*).
val ghProp = { k: String -> rootProject.file("github.local.properties").takeIf { it.exists() }?.readLines()
    ?.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')?.trim() ?: "" }
val ghOwner = ghProp("github.owner").ifEmpty { "odago" }
val ghRepo = ghProp("github.repo").ifEmpty { "travollio" }
val ghApiBase = ghProp("github.apiBase").ifEmpty { "https://api.github.com" }

// APK signing. The app is delivered by sideloading, so the *upgrade* APK MUST be
// signed by the SAME certificate as the installed one or Android refuses to update
// it ("app is not installed" / signature mismatch). Default = this box's debug
// keystore (Android standard storePassword "android"). Override at release time via
// a gitignored root file `signing.local.properties`:
//   keystore=/path/to/release.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val signLines = rootProject.file("signing.local.properties").takeIf { it.exists() }?.readLines().orEmpty()
fun signProp(k: String): String =
    signLines.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')?.trim() ?: ""
val ksPath = signProp("keystore")
    .ifEmpty { (System.getenv("HOME") ?: "/home/odago") + "/.android/debug.keystore" }
val ksStorePass = signProp("storePassword").ifEmpty { "android" }
val ksAlias = signProp("keyAlias").ifEmpty { "androiddebugkey" }
val ksKeyPass = signProp("keyPassword").ifEmpty { ksStorePass }

android {
    namespace = "com.odago.travollio"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(ksPath)
            storePassword = ksStorePass
            keyAlias = ksAlias
            keyPassword = ksKeyPass
        }
    }

    defaultConfig {
        applicationId = "com.odago.travollio"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.3"

        // In-app updater feed (settings tab). HTTPS-only; see Updater.kt.
        buildConfigField("String", "GH_API_BASE", "\"$ghApiBase\"")
        buildConfigField("String", "GH_OWNER", "\"$ghOwner\"")
        buildConfigField("String", "GH_REPO", "\"$ghRepo\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
            buildConfigField("String", "DEEPSEEK_BASE_URL", "\"$deepSeekBase\"")
            buildConfigField("String", "DEEPSEEK_MODEL", "\"$deepSeekModel\"")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Hardening: R8 minify + resource shrink on release (OWASP M9 — makes static
            // analysis of the shipped artifact meaningfully harder). Room/Compose ship
            // their own consumer rules; debug stays unminified for fast iteration.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The user's own DeepSeek key is baked so the shipped build's AI works on device.
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
            buildConfigField("String", "DEEPSEEK_BASE_URL", "\"$deepSeekBase\"")
            buildConfigField("String", "DEEPSEEK_MODEL", "\"$deepSeekModel\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
