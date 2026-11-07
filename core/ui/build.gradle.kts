// What every screen shares: routes and navigation, the main header and its sheets, banners, money text,
// the theme from the account's preferences, and the session and state from core:data.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.ui"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
}

dependencies {
    api(project(":core:design"))
    api(project(":core:data"))
    api(libs.navigation.compose)
    api(libs.lifecycle.viewmodel.compose)
    api(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.browser)
}
