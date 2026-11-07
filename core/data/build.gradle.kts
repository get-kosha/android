// Repositories over the API: the offline cache, the outbox for new entries, the session and app-wide state.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha.data"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:network"))
    implementation(libs.work.runtime)
    implementation(libs.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
