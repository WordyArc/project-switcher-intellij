import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class IntellijPluginConventionExtension @Inject constructor(objects: ObjectFactory) {
    val pluginGroup: Property<String> = objects.property(String::class.java)
    val pluginName: Property<String> = objects.property(String::class.java)
    val pluginVersion: Property<String> = objects.property(String::class.java)
    val pluginRepositoryUrl: Property<String> = objects.property(String::class.java)

    val platformVersion: Property<String> = objects.property(String::class.java)
    val pluginSinceBuild: Property<String> = objects.property(String::class.java)

    val javaVersion: Property<Int> = objects.property(Int::class.java)
    val enablePluginVerification: Property<Boolean> = objects.property(Boolean::class.java)
}
