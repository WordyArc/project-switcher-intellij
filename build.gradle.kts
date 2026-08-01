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
    // Synthesizing a Compose KeyEvent without a real skiko event needs the internal factory.
    compilerOptions.optIn.add("androidx.compose.ui.InternalComposeUiApi")
}

tasks.test {
    useJUnitPlatform()

    // The bundled Station plugin watches recent projects and, in a test IDE, dies looking up a class
    // it only ships to its own test distribution. Its failure would be attributed to whichever test
    // happens to be running when it fires.
    systemProperty("idea.suppressed.plugins.id", "com.jetbrains.station")
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        // Inlined rather than extracted into a helper: a build script function reference cannot be
        // stored in the configuration cache.
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
