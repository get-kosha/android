// KoshaTheme, fonts, icons, the generated strings, and every control Kosha draws itself.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.getkosha.design"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
