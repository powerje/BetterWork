plugins {
    alias(libs.plugins.android.library)
}

kotlin { jvmToolchain(21) }

android {
    namespace = "dev.betterwork.platform"
    compileSdk = 37
    defaultConfig { minSdk = 30 }
}

dependencies {
    api(project(":shared"))
    implementation(libs.sqlite.android)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(libs.serialization)
    implementation(libs.kermit)
    implementation(libs.core)
    implementation(libs.play.wear)
    implementation(libs.wear.ongoing)
}
