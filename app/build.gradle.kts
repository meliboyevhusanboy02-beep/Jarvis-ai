
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.jarvis.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.jarvis.ai"
        minSdk = 26
        targetSdk = 32
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "BASE_URL", "\"https://jarvis-ai-1-nlmn.onrender.com/\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
