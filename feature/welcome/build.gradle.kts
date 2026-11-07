// First run (M8): name, currency and time zone, the first accounts, then done.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.welcome"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.activity.compose)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
