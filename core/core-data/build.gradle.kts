// :core-data — Persistenz (DataStore für Settings/Provider-Config, Room für Chat-Historie).
plugins {
    alias(libs.plugins.overlai.android.library)
    alias(libs.plugins.overlai.android.hilt)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-llm"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
