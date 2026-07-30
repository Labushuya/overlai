import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension

// Aktiviert Jetpack Compose (Compiler-Plugin + BOM + Basis-Deps). Anwendbar auf
// Application- ODER Library-Modul; erkennt den Typ zur Laufzeit.
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.findByType(ApplicationExtension::class.java)
                ?: extensions.getByType<LibraryExtension>()
            configureCompose(extension)
        }
    }

    private fun Project.configureCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
        commonExtension.apply {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())

            add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
