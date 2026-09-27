plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lujinyu.xiting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lujinyu.xiting"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.0.0"

        // 公开发布版：广告跳过引擎在代码层被禁用（MainActivity会据此隐藏入口）
        buildConfigField("boolean", "ADS_ENABLED", "false")
    }

    buildFeatures {
        buildConfig = true
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
