// Sign-in through the browser (SignIn, OwnServer, Finishing, SignInError) and the app lock screen.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.auth"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.browser)
    implementation(libs.activity.compose)
    implementation(libs.biometric)
    implementation(libs.fragment)
}
