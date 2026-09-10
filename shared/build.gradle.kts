plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "dev.betterwork.shared"
        compileSdk = 37
        minSdk = 30
        withHostTest {}
    }
    jvm()
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization)
            implementation(libs.sqlite.runtime)
            implementation(libs.molecule)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
        }
        jvmTest.dependencies { implementation(libs.sqlite.jvm) }
    }
}

sqldelight {
    databases {
        create("BetterDatabase") {
            packageName.set("dev.betterwork.db")
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}
