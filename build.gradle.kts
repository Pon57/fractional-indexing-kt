import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmExtension

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.ksp) apply false
}

plugins.withType<NodeJsRootPlugin> {
    extensions.configure<NodeJsRootExtension>("kotlinNodeJs") {
        versions.webpack.version = "5.107.2"
    }

    extensions.configure<NpmExtension>("kotlinNpm") {
        override("diff", "8.0.3")
        override("serialize-javascript", "7.0.5")
    }
}
