import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmExtension

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

plugins.withType<NodeJsRootPlugin> {
    extensions.configure<NodeJsRootExtension>("kotlinNodeJs") {
        versions.webpack.version = "5.107.2"
    }

    extensions.configure<NpmExtension>("kotlinNpm") {
        // Mocha 11 requires vulnerable versions; Mocha 12 is incompatible with Kotlin's reporter.
        override("diff", "8.0.3")
        override("serialize-javascript", "7.1.4")
        // Mocha and karma-webpack still request vulnerable minimatch versions.
        override("minimatch@>=7 <11", "11.0.5")
    }
}
