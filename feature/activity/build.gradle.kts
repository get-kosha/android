// Activity (M5): the month's entries, filters, swipe actions, tag and event view, transaction and transfer detail.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.activity"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:ui"))
    // The camera, photo and file pickers for attachments, and the FileProvider that hands files to other apps.
    implementation(libs.activity.compose)
    implementation(libs.core)
    testImplementation(libs.junit)
}
