import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String, default: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: default)

// Deliberate hurdle, not a real secret boundary: an app-embedded value can always be recovered by a
// determined reverse engineer (there's no way around that without a backend proxy, which this project
// doesn't have). XOR + Base64 keeps the raw token from appearing as a plain readable string in the
// compiled APK's constant pool — defeating a casual `strings`/apktool scan — at essentially zero
// runtime cost. The key must match SecretObfuscator.KEY in the app source exactly, since decoding
// happens there at runtime.
fun obfuscate(raw: String): String {
    val key = "Twinster-2026-ObfKey".toByteArray(Charsets.UTF_8)
    val bytes = raw.toByteArray(Charsets.UTF_8)
    val xored = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(xored)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKeystoreConfig = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.twinster.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.twinster.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "FACEBOOK_APP_ID_OBF", "\"${obfuscate(localProp("FACEBOOK_APP_ID", "YOUR_FACEBOOK_APP_ID_HERE"))}\"")
        buildConfigField("String", "GENIUS_ACCESS_TOKEN_OBF", "\"${obfuscate(localProp("GENIUS_ACCESS_TOKEN", "YOUR_GENIUS_ACCESS_TOKEN_HERE"))}\"")
        // TheAudioDb's own docs (theaudiodb.com/free_music_api) document "123" as the freely usable
        // shared test key for development/low-volume use — works out of the box with no signup.
        buildConfigField("String", "THEAUDIODB_API_KEY_OBF", "\"${obfuscate(localProp("THEAUDIODB_API_KEY", "123"))}\"")
    }

    signingConfigs {
        if (hasKeystoreConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // TFLite models must stay uncompressed in the APK — the interpreter/Task Library memory-maps the
    // asset file directly, which fails if AAPT compresses it (a well-known TFLite/Android gotcha).
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.google.zxing:core:3.5.3")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device content-based genre detection for Local Library (see YamnetClassifier) — the Task
    // Library's AudioClassifier wraps the bundled YAMNet model (assets/yamnet.tflite) with framing,
    // tensor formatting and label lookup already handled, so no separate tensorflow-lite/-support
    // dependency is needed on top of it.
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
