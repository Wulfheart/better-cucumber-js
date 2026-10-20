import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

val ideaVersion = "2026.2.3"

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(ideaVersion)
        testFramework(TestFrameworkType.Platform)

        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        // Not bundled in IntelliJ IDEA; this build is compatible with 262.*
        plugin("gherkin", "262.8665.173")
    }
}

// Where an activated IntelliJ IDEA keeps its settings, including idea.key
val jetBrainsConfigDir: Provider<File> = providers.systemProperty("os.name").zip(providers.systemProperty("user.home")) { os, home ->
    when {
        os.startsWith("Windows") -> File(providers.environmentVariable("APPDATA").getOrElse("$home/AppData/Roaming"), "JetBrains")
        os.startsWith("Mac") -> File(home, "Library/Application Support/JetBrains")
        else -> File(providers.environmentVariable("XDG_CONFIG_HOME").getOrElse("$home/.config"), "JetBrains")
    }
}

intellijPlatform {
    // The installed IDE's key, so its path needn't be written down anywhere: the one for the target version,
    // else the newest. An explicit org.jetbrains.intellij.platform.subscriptionKey still wins.
    subscriptionKey = providers.gradleProperty("org.jetbrains.intellij.platform.subscriptionKey")
        .map { layout.projectDirectory.file(it) }
        .orElse(jetBrainsConfigDir.flatMap { dir ->
            val targetVersion = ideaVersion.split('.').take(2).joinToString(".")
            val key = File(dir, "IntelliJIdea$targetVersion/idea.key").takeIf { it.isFile }
                ?: dir.listFiles { file -> file.name.startsWith("IntelliJIdea") }.orEmpty()
                    .map { File(it, "idea.key") }.filter { it.isFile }.maxByOrNull { it.parentFile.name }
            providers.provider { key?.let { layout.projectDirectory.file(it.absolutePath) } }
        })

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

// Cucumber for Java goes into the runIde sandbox only, not onto the compile or test classpath: the playground's
// java/ module makes it accept every feature file, which checks that ours still wins for JS features
val runIdePlugin: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    // Same attributes as intellijPlatformPlugin, so the Marketplace ZIP arrives extracted like the gherkin plugin
    attributes.addAllLater(configurations.named("intellijPlatformPlugin").get().attributes)
}

dependencies {
    runIdePlugin("com.jetbrains.plugins:cucumber-java:262.8665.176")
}

tasks.prepareSandbox {
    pluginsClasspath.from(runIdePlugin)
}

// Links playground/java/pom.xml as a Maven project of the playground, so the sandbox imports it on opening.
// playground/.idea is ignored by git: it is created when missing, and an existing one only gets the Maven entry.
val linkPlaygroundMavenProject by tasks.registering {
    val ideaDir = layout.projectDirectory.dir("playground/.idea").asFile
    doLast {
        ideaDir.mkdirs()
        val modules = File(ideaDir, "modules.xml")
        if (!modules.exists()) {
            modules.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="ProjectModuleManager">
                    <modules>
                      <module fileurl="file://${'$'}PROJECT_DIR${'$'}/.idea/playground.iml" filepath="${'$'}PROJECT_DIR${'$'}/.idea/playground.iml" />
                    </modules>
                  </component>
                </project>
                """.trimIndent() + "\n"
            )
            File(ideaDir, "playground.iml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <module type="WEB_MODULE" version="4">
                  <component name="NewModuleRootManager">
                    <content url="file://${'$'}MODULE_DIR${'$'}/.." />
                    <orderEntry type="inheritedJdk" />
                    <orderEntry type="sourceFolder" forTests="false" />
                  </component>
                </module>
                """.trimIndent() + "\n"
            )
        }

        val misc = File(ideaDir, "misc.xml")
        val builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = if (misc.exists()) builder.parse(misc) else builder.newDocument().apply {
            appendChild(createElement("project").apply { setAttribute("version", "4") })
        }
        val project = document.documentElement
        val components = project.getElementsByTagName("component")
        val existing = (0 until components.length).map { components.item(it) as org.w3c.dom.Element }
            .firstOrNull { it.getAttribute("name") == "MavenProjectsManager" }
        val pom = "${'$'}PROJECT_DIR${'$'}/java/pom.xml"
        if (existing != null && existing.getElementsByTagName("option").let { options ->
                (0 until options.length).any { (options.item(it) as org.w3c.dom.Element).getAttribute("value") == pom }
            }) return@doLast
        existing?.let(project::removeChild)
        project.appendChild(document.createElement("component").apply {
            setAttribute("name", "MavenProjectsManager")
            appendChild(document.createElement("option").apply {
                setAttribute("name", "originalFiles")
                appendChild(document.createElement("list").apply {
                    appendChild(document.createElement("option").apply { setAttribute("value", pom) })
                })
            })
        })
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }.transform(javax.xml.transform.dom.DOMSource(document), javax.xml.transform.stream.StreamResult(misc))
    }
}

tasks.runIde {
    dependsOn(linkPlaygroundMavenProject)
    // Opens the manual-testing project in the sandbox IDE
    args(layout.projectDirectory.dir("playground").asFile.absolutePath)

    // Without a subscription or trial the sandbox runs in free mode, which disables Node.js and therefore this plugin
    val hasSubscriptionKey = intellijPlatform.subscriptionKey.map { true }.orElse(false)
    val keyLocation = jetBrainsConfigDir.map { "$it/IntelliJIdea*/idea.key" }
    doFirst {
        if (!hasSubscriptionKey.get()) {
            logger.warn(
                "No IntelliJ IDEA subscription key found (looked in ${keyLocation.get()}), so Node.js and this plugin " +
                    "only load after you start a trial in the sandbox (Help | Register)."
            )
        }
    }
}
