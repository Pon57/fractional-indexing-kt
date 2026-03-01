import org.gradle.kotlin.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
}

group = "dev.pon"
version = rootProject.file("VERSION").readText().trim()

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
    explicitApi()
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
        val perfStrict = providers.systemProperty("fractionalIndexing.perf.strict")
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                perfStrict.orNull?.let { strict ->
                    systemProperty("fractionalIndexing.perf.strict", strict)
                }
            }
        }
    }

    androidLibrary {
        namespace = "dev.pon.fractionalindexing"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
                }
            }
        }
        withHostTestBuilder {}
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    js {
        nodejs()
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {}
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.property)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "fractional-indexing", version.toString())

    pom {
        name = "Fractional Indexing"
        description = "A Kotlin Multiplatform library for generating sortable keys for user-defined ordering with minimal reindexing."
        inceptionYear = "2026"
        url = "https://github.com/Pon57/fractional-indexing-kt"
        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "Pon57"
                name = "Pon"
                url = "https://github.com/Pon57"
            }
        }
        scm {
            url = "https://github.com/Pon57/fractional-indexing-kt"
            connection = "scm:git:https://github.com/Pon57/fractional-indexing-kt.git"
            developerConnection = "scm:git:ssh://git@github.com/Pon57/fractional-indexing-kt.git"
        }
    }
}
