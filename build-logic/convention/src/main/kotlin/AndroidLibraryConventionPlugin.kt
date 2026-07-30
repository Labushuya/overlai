import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Convention für Android-Library-Module (core-ui, core-data, feature-*).
// Namespace wird aus dem Gradle-Pfad abgeleitet (":core:core-llm" -> de.overlai.core.llm).
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = deriveNamespace(path)

                defaultConfig {
                    consumerProguardFiles("consumer-rules.pro")
                }

                // Unit-Tests dürfen Android-Ressourcen/Manifest sehen (Robolectric).
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }

    // ":core:core-llm" -> "de.overlai.core.llm"; "feature-chat" -> "de.overlai.feature.chat"
    private fun deriveNamespace(path: String): String {
        val segment = path.trim(':').substringAfterLast(':')
            .removePrefix("core-")
            .removePrefix("feature-")
            .replace('-', '.')
        val group = when {
            path.contains(":core:") -> "core"
            path.contains(":feature:") -> "feature"
            else -> "lib"
        }
        return "${OverlaiConfig.NAMESPACE_PREFIX}.$group.$segment"
    }
}
