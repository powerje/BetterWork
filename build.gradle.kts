import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktfmt)
}

allprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt.yml"))
    source.setFrom(files("shared/src", "platform/src", "phone/src", "wear/src"))
}

tasks.register("precommit") {
    dependsOn(
        "ktfmtCheck",
        "detekt",
        ":shared:jvmTest",
        ":shared:verifyCommonMainBetterDatabaseMigration",
        ":phone:assembleDebug",
        ":wear:assembleDebug",
        ":phone:lintDebug",
        ":wear:lintDebug",
        ":platform:lintDebug",
    )
    subprojects.forEach { dependsOn("${it.path}:ktfmtCheck") }
}

val formatRootScripts =
    tasks.register<KtfmtFormatTask>("formatRootScripts") {
        source = fileTree(projectDir) { include("*.gradle.kts") }
    }
val checkRootScripts =
    tasks.register<KtfmtCheckTask>("checkRootScripts") {
        source = fileTree(projectDir) { include("*.gradle.kts") }
    }

tasks.named("ktfmtFormat") { dependsOn(formatRootScripts) }

tasks.named("ktfmtCheck") { dependsOn(checkRootScripts) }
