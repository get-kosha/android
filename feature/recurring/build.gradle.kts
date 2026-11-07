// Recurring (M6): bills, subscriptions, income and transfers on a schedule, their payments and documents.
// Its Kit.kt (cached reads, writes, keypad and date sheets, form scaffold) is shared with feature:accounts.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.recurring"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:ui"))
    implementation(libs.core)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}
