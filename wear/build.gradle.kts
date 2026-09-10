plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

android {
    namespace = "dev.betterwork.wear"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.betterwork"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] =
            "dev.betterwork.device.HostDrivenAcceptance"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").java.directories.add(rootProject.file("device-tests/src").path)
}

dependencies {
    androidTestImplementation(libs.android.test.core)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.uiautomator)
    implementation(project(":platform"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.core)
    implementation(libs.wear.foundation)
    implementation(libs.wear.material3)
    implementation(libs.wear.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.serialization)
    implementation(libs.wear.tooling)
}
