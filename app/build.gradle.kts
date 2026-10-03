plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.tvshell"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.tvshell"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Постоянный ключ подписи: так новые версии ставятся поверх старых
    // без удаления (и без потери избранного). Для личного использования — ок.
    signingConfigs {
        create("shell") {
            storeFile = rootProject.file("keystore/tvshell.jks")
            storePassword = "tvshell"
            keyAlias = "tvshell"
            keyPassword = "tvshell"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("shell")
        }
        debug {
            signingConfig = signingConfigs.getByName("shell")
        }
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
}
