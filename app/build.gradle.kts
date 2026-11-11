
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.lekien.aicamoverlay"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.lekien.aicamoverlay"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
}

dependencies {
    // Nhận diện vật thể hiện tại
    implementation("com.google.mlkit:object-detection:17.0.2")

    // Nhận diện 33 điểm khớp cơ thể, chạy offline
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
