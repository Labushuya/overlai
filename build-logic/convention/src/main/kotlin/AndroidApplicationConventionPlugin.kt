import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

// Convention für das :app-Modul (die einzige Application). Setzt targetSdk +
// applicationId zentral, damit release.yml/versionCode dort verankert sind.
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                namespace = "${OverlaiConfig.NAMESPACE_PREFIX}.app"

                defaultConfig {
                    applicationId = "${OverlaiConfig.NAMESPACE_PREFIX}.app"
                    targetSdk = OverlaiConfig.TARGET_SDK
                    // versionCode/versionName werden im :app/build.gradle.kts
                    // aus der CI (Tag) bzw. Default gesetzt.
                }
            }
        }
    }
}
