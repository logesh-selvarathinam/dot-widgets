plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nandhini.dotwidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nandhini.dotwidgets"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("dotwidgets.jks")
            storePassword = "dotwidgets"
            keyAlias = "dotwidgets"
            keyPassword = "dotwidgets"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
