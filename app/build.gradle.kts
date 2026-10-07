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
val appVersion = "3.6.0"

android {
    namespace = "com.lujinyu.xiting"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lujinyu.xiting"
        minSdk = 26
        targetSdk = 35
        versionCode = 74
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

    testOptions {
        unitTests {
            // android.util.Log 在 JVM 单测里是「未实现」的 stub，不设此项会抛
            // RuntimeException: Method w in android.util.Log not mocked。
            // 本项目的解析路径只拿 Log 记日志，返回默认值即可。
            isReturnDefaultValues = true
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

dependencies {
    testImplementation("junit:junit:4.13.2")
    // 单测跑在 JVM 上：android.jar 里的 org.json 是「调用即抛异常」的 stub，
    // 引入真实实现才能测存档/能量的解析容错路径。
    // main 源码集仍编译进 APK 的 org.json，两者不冲突（单测 classpath 里本实现优先）。
    testImplementation("org.json:json:20231013")
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    // AGP 只把 javac 的输出目录挂上单测运行时 classpath。本模块的测试全是纯 Kotlin，
    // javac 什么都不产出，于是 tmp/kotlin-classes/<variant>UnitTest 里的测试类
    // 不在 classpath 上 —— Gradle 能扫到类名（它自己在 daemon 里读目录），
    // worker 却 Class.forName 失败，于是每个测试类都报
    // 「java.lang.ClassNotFoundException: com.lujinyu.xiting.XxxTest」。
    // 这里按变体名补回 Kotlin 编译输出。task 名形如 testDebugUnitTest → debugUnitTest。
    val variantDir = name.removePrefix("test").replaceFirstChar { it.lowercase() }
    classpath += files(layout.buildDirectory.dir("tmp/kotlin-classes/$variantDir"))
}
