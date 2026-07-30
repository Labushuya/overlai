import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

// Convention für feature-*-Module: Android-Library + Compose + Hilt + die übliche
// Lifecycle/Navigation-Basis. Reduziert jedes feature/build.gradle.kts auf ~5 Zeilen.
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("overlai.android.library")
                apply("overlai.android.compose")
                apply("overlai.android.hilt")
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-core-ktx").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-compose-material-icons-extended").get())
            }
        }
    }
}
