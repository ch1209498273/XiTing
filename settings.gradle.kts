// ⚠ 国内网络用阿里云镜像加速；CI（海外 runner）直接走上游仓库。
// 原因：阿里云 CDN 在海外偶发 502，而 Gradle 对 5xx 不会像 404 那样
// 「跳过此仓库、继续试下一个」，而是直接判该构件解析失败 —— 2026-10-10
// CI 首跑就栽在这里。GitHub Actions 自带 CI=true，用它精确分流。
pluginManagement {
    repositories {
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        if (System.getenv("CI").isNullOrEmpty()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "XiTing"
include(":app")
