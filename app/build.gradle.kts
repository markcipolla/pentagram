plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pentagram.airplay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pentagram.airplay"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // CMake arguments for native build
        externalNativeBuild {
            cmake {
                // Allow undefined symbols for BoringSSL functions (resolved at runtime)
                arguments += listOf(
                    "-DANDROID_ALLOW_UNDEFINED_SYMBOLS=TRUE"
                )
                // Disable strict linker checks
                cFlags += listOf("-Wno-error")
                cppFlags += listOf("-Wno-error")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "25.2.9519653"
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Network Service Discovery (NSD) for mDNS/Bonjour
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Binary plist parsing for AirPlay protocol
    implementation("com.googlecode.plist:dd-plist:1.28")

    // Cryptography for AirPlay pairing
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Conscrypt - Google's OpenSSL provider (same crypto as RPiPlay!)
    implementation("org.conscrypt:conscrypt-android:2.5.2")

    // Libsodium - Native C crypto library for X25519/Ed25519 (OpenSSL compatible!)
    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
