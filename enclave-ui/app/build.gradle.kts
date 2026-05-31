import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
    id("com.google.devtools.ksp")
}

val properties = Properties()
val propertiesFile = rootProject.file("local.properties")
if (propertiesFile.exists()) {
    propertiesFile.inputStream().use { properties.load(it) }
}


android {
    namespace = "com.enclave.app"
    compileSdk = 35

    val storeFilePath = properties.getProperty("RELEASE_STORE_FILE")
    val storePass = properties.getProperty("RELEASE_STORE_PASSWORD")
    val alias = properties.getProperty("RELEASE_KEY_ALIAS")
    val aliasPass = properties.getProperty("RELEASE_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = aliasPass
            }
        }
    }

    defaultConfig {
        applicationId = "com.enclave.app"
        minSdk = 34
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        
        // BuildConfig keys are no longer hardcoded at compile time.
        // Server infrastructure details are loaded dynamically at runtime.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!storeFilePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
    implementation("androidx.compose.material:material-icons-extended")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Security & Cryptography
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("org.signal:libsignal-client:0.39.2")
    implementation("org.signal:libsignal-android:0.39.2")

    // WebRTC & Signaling
    implementation("im.conversations.webrtc:webrtc-android:129.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Media3 (ExoPlayer & Session)
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Supabase
    val supabaseVersion = "2.6.1"
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-websockets:2.3.11")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json") {
        version {
            strictly("1.6.3")
        }
    }

    // Explicit Fragment Upgrade to satisfy ActivityResult API & Lint
    implementation("androidx.fragment:fragment-ktx:1.7.1")
}
