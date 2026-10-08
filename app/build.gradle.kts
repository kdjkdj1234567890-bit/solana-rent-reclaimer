plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.rentreclaimer"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.rentreclaimer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../rent-reclaimer-release.keystore")
            storePassword = "rentreclaimer"
            keyAlias = "rentreclaimer"
            keyPassword = "rentreclaimer"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Solana Mobile Stack — Mobile Wallet Adapter (authorize + signAndSendTransactions)
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
