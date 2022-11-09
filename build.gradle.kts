import cc.tweaked.gradle.*
import groovy.util.Node
import groovy.util.NodeList
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.minecraftforge.gradle.common.util.RunConfig
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.settings

plugins {
    // Build
    id("net.minecraftforge.gradle")
    alias(libs.plugins.mixinGradle)
    id("org.parchmentmc.librarian.forgegradle")
    alias(libs.plugins.shadow)
    // Publishing
    alias(libs.plugins.curseForgeGradle)
    alias(libs.plugins.githubRelease)
    alias(libs.plugins.minotaur)
    // Utility
    alias(libs.plugins.taskTree)
    id("org.jetbrains.gradle.plugin.idea-ext")

    id("cc-tweaked.illuaminate")
    id("cc-tweaked.gametest")
    id("cc-tweaked.publishing")
    id("cc-tweaked")
}

val isStable = true
val modVersion: String by extra
val mcVersion: String by extra

val allProjects = listOf(":core-api", ":core", ":forge-api").map { evaluationDependsOn(it) }
cct {
    allProjects.forEach { externalSources(it) }
}

sourceSets {
    // ForgeGradle adds a dep on the clientClasses task, despite forge-api coming from a separate project. Register an
    // empty one.
    register("client")

    main {
        resources.srcDir("src/generated/resources")
    }
}

minecraft {
    runs {
        // configureEach would be better, but we need to eagerly configure configs or otherwise the run task doesn't
        // get set up properly.
        all {
            lazyToken("minecraft_classpath") {
                configurations["shade"].copyRecursive().resolve().joinToString(File.pathSeparator) { it.absolutePath }
            }

            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")

            forceExit = false

            mods.register("computercraft") {
                cct.sourceDirectories.get().forEach {
                    if (it.classes) sources(it.sourceSet)
                }
            }
        }

        val client by registering {
            workingDirectory(file("run"))
        }

        val server by registering {
            workingDirectory(file("run/server"))
            arg("--nogui")
        }

        val data by registering {
            workingDirectory(file("run"))
            args(
                "--mod",
                "computercraft",
                "--all",
                "--output",
                file("src/generated/resources/"),
                "--existing",
                file("src/main/resources/"),
            )
            property("cct.pretty-json", "true")
        }

        fun RunConfig.configureForGameTest() {
            val old = lazyTokens.get("minecraft_classpath")
            lazyToken("minecraft_classpath") {
                // We do some terrible hacks here to basically find all things not already on the runtime classpath
                // and add them. /Except/ for our source sets, as those need to load inside the Minecraft classpath.
                val testMod = configurations["testModRuntimeClasspath"].resolve()
                val implementation = configurations.runtimeClasspath.get().resolve()
                val new = (testMod - implementation)
                    .asSequence()
                    .filter { it.isFile && !it.name.endsWith("-test-fixtures.jar") }
                    .map { it.absolutePath }
                    .joinToString(File.pathSeparator)
                if (old == null) new else old.get() + File.pathSeparator + new
            }

            property("cctest.sources", file("src/testMod/resources/data/cctest").absolutePath)

            arg("--mixin.config=computercraft-gametest.mixins.json")

            mods.register("cctest") {
                source(sourceSets["testMod"])
                source(sourceSets["testFixtures"])
                // FIXME: We need this for running in-dev but not from Gradle:
                // source(project(":core").sourceSets.testFixtures.get())
            }
        }

        val testClient by registering {
            workingDirectory(file("run/testClient"))
            parent(client.get())
            configureForGameTest()
        }

        val gameTestServer by registering {
            workingDirectory(file("run/testServer"))
            configureForGameTest()

            property("forge.logging.console.level", "info")
        }
    }

    mappings("parchment", "${libs.versions.parchmentMc.get()}-${libs.versions.parchment.get()}-$mcVersion")

    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))
}

mixin {
    add(sourceSets.main.get(), "computercraft.mixins.refmap.json")
    config("computercraft.mixins.json")
}

reobf {
    register("shadowJar")
}

configurations {
    val shade by registering { isTransitive = false }
    implementation { extendsFrom(shade.get()) }
    register("cctJavadoc")
}

dependencies {
    minecraft("net.minecraftforge:forge:$mcVersion-${libs.versions.forge.get()}")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SQUID:processor")

    errorprone(project(":lints"))

    compileOnly(libs.jetbrainsAnnotations)
    annotationProcessorEverywhere(libs.autoService)

    libs.bundles.externalMods.forge.compile.get().map { compileOnly(fg.deobf(it)) }
    libs.bundles.externalMods.forge.runtime.get().map { runtimeOnly(fg.deobf(it)) }

    implementation(project(":core"))
    implementation(commonClasses(project(":forge-api")))
    implementation(clientClasses(project(":forge-api")))
    "shade"(libs.cobalt)
    "shade"(libs.netty.http)

    testFixturesApi(libs.bundles.test)
    testFixturesApi(libs.bundles.kotlin)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.kotlin)
    testRuntimeOnly(libs.bundles.testRuntime)

    testModImplementation(testFixtures(project(":core")))

    "cctJavadoc"(libs.cctJavadoc)
}

illuaminate {
    version.set(libs.versions.illuaminate)
}

// Compile tasks

val luaJavadoc by tasks.registering(Javadoc::class) {
    description = "Generates documentation for Java-side Lua functions."
    group = JavaBasePlugin.DOCUMENTATION_GROUP

    source(sourceSets.main.get().java)
    source(project(":core").sourceSets.main.get().java)

    setDestinationDir(buildDir.resolve("docs/luaJavadoc"))
    classpath = sourceSets.main.get().compileClasspath

    options.docletpath = configurations["cctJavadoc"].files.toList()
    options.doclet = "cc.tweaked.javadoc.LuaDoclet"
    (options as StandardJavadocDocletOptions).noTimestamp(false)

    javadocTool.set(
        javaToolchains.javadocToolFor {
            languageVersion.set(cc.tweaked.gradle.CCTweakedPlugin.JAVA_VERSION)
        },
    )
}

tasks.processResources {
    inputs.property("modVersion", modVersion)
    inputs.property("forgeVersion", libs.versions.forge.get())

    filesMatching("META-INF/mods.toml") {
        expand(mapOf("forgeVersion" to libs.versions.forge.get(), "file" to mapOf("jarVersion" to modVersion)))
    }
}

tasks.jar {
    finalizedBy("reobfJar")
    archiveClassifier.set("slim")

    from(allProjects.map { zipTree(it.tasks.jar.get().archiveFile) })
}

tasks.shadowJar {
    finalizedBy("reobfShadowJar")
    archiveClassifier.set("")

    from(allProjects.map { zipTree(it.tasks.jar.get().archiveFile) })

    configurations = listOf(project.configurations["shade"])
    relocate("org.squiddev.cobalt", "cc.tweaked.internal.cobalt")
    relocate("io.netty.handler", "cc.tweaked.internal.netty")
    // TODO: minimize(): Would be good to support once our build scripts are stabilised.
}

tasks.assemble { dependsOn("shadowJar") }

// Check tasks

tasks.test {
    systemProperty("cct.test-files", buildDir.resolve("tmp/testFiles").absolutePath)
}

val lintLua by tasks.registering(IlluaminateExec::class) {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Lint Lua (and Lua docs) with illuaminate"

    // Config files
    inputs.file("illuaminate.sexp").withPropertyName("illuaminate.sexp")
    // Sources
    inputs.files(fileTree("doc")).withPropertyName("docs")
    inputs.files(fileTree("src/main/resources/data/computercraft/lua")).withPropertyName("lua rom")
    inputs.files(luaJavadoc)

    args = listOf("lint")

    doFirst { if (System.getenv("GITHUB_ACTIONS") != null) println("::add-matcher::.github/matchers/illuaminate.json") }
    doLast { if (System.getenv("GITHUB_ACTIONS") != null) println("::remove-matcher owner=illuaminate::") }
}

val runGametest by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests on a temporary Minecraft instance."
    dependsOn("cleanRunGametest")

    // Copy from runGameTestServer. We do it in this slightly odd way as runGameTestServer
    // isn't created until the task is configured (which is no good for us).
    val exec = tasks.getByName<JavaExec>("runGameTestServer")
    dependsOn(exec.dependsOn)
    exec.copyToFull(this)
}

cct.jacoco(runGametest)

tasks.check { dependsOn(runGametest) }

// Upload tasks

val publishCurseForge by tasks.registering(TaskPublishCurseForge::class) {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Upload artifacts to CurseForge"

    apiToken = findProperty("curseForgeApiKey") ?: ""
    enabled = apiToken != ""

    val mainFile = upload("282001", tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar) // Ughr.
    mainFile.changelog =
        "Release notes can be found on the [GitHub repository](https://github.com/cc-tweaked/CC-Tweaked/releases/tag/v$mcVersion-$modVersion)."
    mainFile.changelogType = "markdown"
    mainFile.releaseType = if (isStable) "release" else "alpha"
    mainFile.gameVersions.add(mcVersion)
}

tasks.publish { dependsOn(publishCurseForge) }

modrinth {
    token.set(findProperty("modrinthApiKey") as String? ?: "")
    projectId.set("gu7yAYhd")
    versionNumber.set("$mcVersion-$modVersion")
    versionName.set(modVersion)
    versionType.set(if (isStable) "release" else "alpha")
    uploadFile.set(tasks.shadowJar as Any)
    gameVersions.add(mcVersion)
    changelog.set("Release notes can be found on the [GitHub repository](https://github.com/cc-tweaked/CC-Tweaked/releases/tag/v$mcVersion-$modVersion).")

    syncBodyFrom.set(provider { file("doc/mod-page.md").readText() })
}

tasks.publish { dependsOn(tasks.modrinth) }

githubRelease {
    token(findProperty("githubApiKey") as String? ?: "")
    owner.set("cc-tweaked")
    repo.set("CC-Tweaked")
    targetCommitish.set(cct.gitBranch)

    tagName.set("v$mcVersion-$modVersion")
    releaseName.set("[$mcVersion] $modVersion")
    body.set(
        provider {
            "## " + file("src/main/resources/data/computercraft/lua/rom/help/whatsnew.md")
                .readLines()
                .takeWhile { it != "Type \"help changelog\" to see the full version history." }
                .joinToString("\n").trim()
        },
    )
    prerelease.set(!isStable)
}

tasks.publish { dependsOn(tasks.githubRelease) }

// Don't publish the slim jar
for (cfg in listOf(configurations.apiElements, configurations.runtimeElements)) {
    cfg.configure { artifacts.removeIf { it.classifier == "slim" } }
}

publishing {
    publications {
        named("maven", MavenPublication::class) {
            fg.component(this)
            // Remove all dependencies: they're shaded anyway! This is very ugly, but not found a better way :(.
            pom.withXml {
                for (node in asNode().get("dependencies") as NodeList) {
                    asNode().remove(node as Node)
                }
            }
        }
    }
}

idea.project.settings.compiler.javac {
    // We want ErrorProne to be present when compiling via IntelliJ, as it offers some helpful warnings
    // and errors. Loop through our source sets and find the appropriate flags.
    moduleJavacAdditionalOptions = subprojects
        .asSequence()
        .map { evaluationDependsOn(it.path) }
        .flatMap { project ->
            val sourceSets = project.extensions.findByType(SourceSetContainer::class) ?: return@flatMap sequenceOf()
            sourceSets.asSequence().map { sourceSet ->
                val name = "${idea.project.name}.${project.name}.${sourceSet.name}"
                val compile = project.tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class).get()
                name to compile.options.allCompilerArgs.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
            }
        }
        .toMap()
}
