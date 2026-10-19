import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.2.3")
        testFramework(TestFrameworkType.Platform)

        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        // Not bundled in IntelliJ IDEA; this build is compatible with 262.*
        plugin("gherkin", "262.8665.173")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

tasks.runIde {
    // Opens the manual-testing project in the sandbox IDE
    args(layout.projectDirectory.dir("playground").asFile.absolutePath)

    // Without a subscription the sandbox runs in free mode, which disables Node.js and therefore this plugin
    val hasSubscriptionKey = intellijPlatform.subscriptionKey.map { true }.orElse(false)
    doFirst {
        if (!hasSubscriptionKey.get()) {
            throw GradleException(
                "runIde needs an IntelliJ IDEA subscription key, otherwise Node.js and this plugin are not loaded. " +
                    "Set org.jetbrains.intellij.platform.subscriptionKey=<path to idea.key> in ~/.gradle/gradle.properties."
            )
        }
    }
}
