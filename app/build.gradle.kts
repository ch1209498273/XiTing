import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------- 正式签名 ----------
// 密钥与密码保存在项目根目录 keystore.properties（已 gitignore，绝不入库）。
// 公开仓库 clone 后无此文件，release 构建自动回退 debug 签名，保证可构建。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.isNotEmpty()

// ---------- 溯源水印 ----------
// 每次构建生成唯一构建ID，注入到 APK 的隐藏资产、资源表和界面上。
// 给不同渠道/对象分发时，追加参数即可打出专属水印：
//   gradle assembleDebug -Pdist=coolapk
//   gradle assembleDebug -Pdist=wechat-张三
// 收到疑似抄袭的包时，用 tools/trace.py 读取水印即可定位来源。
val distId = (project.findProperty("dist") as String?) ?: "public-github"
val buildId = UUID.randomUUID().toString().replace("-", "").take(8)
val buildTs = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
val appVersion = "3.2.0"

android {
    namespace = "com.lujinyu.xiting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lujinyu.xiting"
        minSdk = 26
        targetSdk = 35
        versionCode = 67
        versionName = appVersion

        // 溯源水印：界面页脚 + 隐藏资产 + 资源表三处冗余
        resValue("string", "build_stamp", "$buildTs-$buildId-$distId")
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
        buildConfigField("String", "DIST_ID", "\"$distId\"")
        buildConfigField("String", "BUILD_TS", "\"$buildTs\"")
    }

    // 隐藏溯源资产（.trace），随 APK 签名固化，转发/改名都不会丢
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/traceAssets"))
    tasks.register("genTraceAssets") {
        doLast {
            val dir = layout.buildDirectory.dir("generated/traceAssets").get().asFile
            dir.mkdirs()
            val content = "{\"app\":\"XiTing\",\"version\":\"$appVersion\",\"dist\":\"$distId\"," +
                "\"build_id\":\"$buildId\",\"time\":\"$buildTs\"," +
                "\"author\":\"ch1209498273\",\"contact\":\"1209498273@qq.com\"," +
                "\"repo\":\"https://github.com/ch1209498273/XiTing\",\"license\":\"non-commercial\"}"
            dir.resolve("trace.json").writeText(content)
        }
    }
    tasks.named("preBuild") { dependsOn("genTraceAssets") }
    // 确保资产合并任务在溯源文件生成之后执行
    tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
        .configureEach { dependsOn("genTraceAssets") }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
