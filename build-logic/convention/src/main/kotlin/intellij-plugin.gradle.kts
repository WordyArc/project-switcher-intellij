import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

fun requiredProp(name: String): String =
    providers.gradleProperty(name).orNull
        ?: throw GradleException("Missing gradle property '$name' in gradle.properties")

val pluginGroup = requiredProp("pluginGroup")
val pluginName = requiredProp("pluginName")
val pluginVersion = requiredProp("pluginVersion")
val pluginRepositoryUrl = requiredProp("pluginRepositoryUrl")

val platformVersion = requiredProp("platformVersion")
val pluginSinceBuild = requiredProp("pluginSinceBuild")

val javaVersion = providers.gradleProperty("javaVersion").orNull?.toInt() ?: 21

group = pluginGroup
version = pluginVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

repositories {
    mavenCentral()
    google()
    intellijPlatform { defaultRepositories() }
}


dependencies {
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("opentest4j").get())
    add("testImplementation", libs.findLibrary("hamcrest").get())
    add("testImplementation", libs.findLibrary("composeuitest").get())
    add("testImplementation", libs.findLibrary("jewelstandalone").get())
    add("testImplementation", libs.findLibrary("skikoAwtRuntimeAll").get())

    intellijPlatform {
        intellijIdea(platformVersion)
        bundledPlugin("com.intellij.java")
        composeUI()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = pluginName
        version = project.version.toString()

        description = providers.fileContents(layout.projectDirectory.file("README.md"))
            .asText
            .map { readme ->
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"
                val lines = readme.lines()

                if (!lines.containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }

                lines.subList(lines.indexOf(start) + 1, lines.indexOf(end))
                    .joinToString("\n")
                    .let(::markdownToHTML)
            }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { v ->
            changelog.renderItem(
                (changelog.getOrNull(v) ?: changelog.getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML,
            )
        }

        ideaVersion {
            sinceBuild = pluginSinceBuild
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides { recommended() }
    }
}

changelog {
    groups.empty()
    repositoryUrl = pluginRepositoryUrl
}

tasks {
    named("publishPlugin") {
        dependsOn("patchChangelog")
    }

    // лучше как флажок, но оставлю как у тебя:
    named("verifyPlugin") {
        enabled = false
    }
}
