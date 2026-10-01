import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------- 溯源水印 ----------
// 每次构建生成唯一构建ID，注入到 APK 的隐藏资产、资源表和界面上。
// 给不同渠道/对象分发时，追加参数即可打出专属水印：
//   gradle assembleDebug -Pdist=coolapk
//   gradle assembleDebug -Pdist=wechat-张三
// 收到疑似抄袭的包时，用 tools/trace.py 读取水印即可定位来源。
val distId = (project.findProperty("dist") as String?) ?: "public-github"
val buildId = UUID.randomUUID().toString().replace("-", "").take(8)
val buildTs = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
val appVersion = "2.19.0"

android {
    namespace = "com.lujinyu.xiting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lujinyu.xiting"
        minSdk = 26
        targetSdk = 35
        versionCode = 50
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
