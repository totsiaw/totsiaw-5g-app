import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.totsiaw.nr5g"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.totsiaw.nr5g"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // One fixed key (from CI secrets) so every build can update the installed app.
    // Without it, each CI runner signs with a fresh debug key and updates are rejected.
    val keystorePath = System.getenv("SIGNING_KEYSTORE")
    val keystorePass = System.getenv("SIGNING_PASSWORD")
    val fixedKey = if (keystorePath != null && keystorePass != null) {
        signingConfigs.create("fixed") {
            storeFile = file(keystorePath)
            storePassword = keystorePass
            keyAlias = "nr5g"
            keyPassword = keystorePass
        }
    } else null

    buildTypes {
        getByName("debug") {
            if (fixedKey != null) signingConfig = fixedKey
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = fixedKey ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // MIUIX (HyperOS-style Compose UI) — single Android AAR, bundles basic/extra/theme/icon.
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")

    // Shizuku: run shell commands as uid 2000 without root.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("dev.rikka.shizuku:aidl:13.1.5")
}
