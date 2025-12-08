import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal fun ProviderFactory.requiredGradleProperty(name: String): Provider<String> =
    gradleProperty(name).orElse(
        provider { throw GradleException("Missing gradle property '$name' (define it in gradle.properties or configure intellijPlugin { ... })") }
    )

internal fun ProviderFactory.intGradleProperty(name: String, defaultValue: Int): Provider<Int> =
    gradleProperty(name).map(String::toInt).orElse(defaultValue)

internal fun ProviderFactory.boolGradleProperty(name: String, defaultValue: Boolean): Provider<Boolean> =
    gradleProperty(name).map(String::toBoolean).orElse(defaultValue)

