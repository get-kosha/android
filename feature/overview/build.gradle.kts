// Overview (M5): the month's cards and charts, search and quick add, and Ask Kosha (M9).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.overview"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:ui"))
    testImplementation(libs.junit)
}
