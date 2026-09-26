import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.intellijPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())

    compilerOptions {
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("org.jetbrains.jewel.foundation.ExperimentalJewelApi")
    }
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly(libs.junit4)

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        composeUI()

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

tasks.compileTestKotlin {
    compilerOptions.optIn.add("androidx.compose.ui.InternalComposeUiApi")
}

tasks.test {
    useJUnitPlatform()

    // Station crashes in a test IDE on a class from its own test distribution, failing a random test.
    systemProperty("idea.suppressed.plugins.id", "com.jetbrains.station")
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }
}

tasks.verifyPluginSignature {
    inputArchiveFile = tasks.signPlugin.flatMap { it.signedArchiveFile }
}

tasks.publishPlugin {
    archiveFiles.setFrom(tasks.signPlugin.flatMap { it.signedArchiveFile })
    dependsOn(tasks.verifyPluginSignature)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md"))
            .asText
            .map { changelog ->
                changelog.lineSequence()
                    .dropWhile { !it.startsWith("## ") }
                    .drop(1)
                    .takeWhile { !it.startsWith("## ") }
                    .map(String::trim)
                    .filter { it.startsWith("- ") }
                    .joinToString(separator = "", prefix = "<ul>", postfix = "</ul>") {
                        "<li>${it.removePrefix("- ")}</li>"
                    }
            }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
