rootProject.name = "Project Switcher"

//plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
//}

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
