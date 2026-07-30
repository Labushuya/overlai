import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// Convention für reine JVM/Kotlin-Module (v.a. :core-llm, :core-common).
// KEINE Android-Deps -> läuft im schnellen JVM-Test-Sourceset ohne Emulator.
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure(KotlinJvmProjectExtension::class.java) {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }

            tasks.withType<Test>().configureEach {
                useJUnit()
            }

            dependencies.add("testImplementation", libs.findLibrary("junit").get())
            dependencies.add("testImplementation", libs.findLibrary("truth").get())
            dependencies.add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
