import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
        mainRun {
            mainClass.set("dev.pon.fractionalindexing.example.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
