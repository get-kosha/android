// Accounts (M6): net worth, account and investment detail, the account form, fix balance, gains, exchanges.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.accounts"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Recurring's Kit.kt: cached reads, writes, keypad and date sheets, the form scaffold.
    implementation(project(":feature:recurring"))
    testImplementation(libs.junit)
}
