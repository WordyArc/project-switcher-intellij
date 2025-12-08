plugins {
    `kotlin-dsl`
}

//name = "dev.owlmajin.intellij-plugin"
group = "dev.owlmajin.buildlogic"

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.intellijPlatformGradlePlugin)
    implementation(libs.changelogGradlePlugin)
    implementation(libs.composeCompilerGradlePlugin)
}
