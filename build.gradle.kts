// Runs once per Minecraft version (a Stonecutter "node"). The version-specific values live in
// versions/<version>/gradle.properties; everything shared lives in the root gradle.properties.

plugins {
    // The remapping Loom on 1.21.11, plain Loom on 26.x. Its version is loomx.loom_version.
    id("dev.kikugie.loom-back-compat")
    java
}

fun prop(key: String): String =
    project.findProperty(key) as? String ?: error("Missing property '$key' in gradle.properties")

val mcVersion: String = stonecutter.current.version
val modVersion: String = prop("mod_version")
val javaVersion: Int = prop("java_version").toInt()

// minerefine-mod-1.1.0+26.2.jar: one version number, the Minecraft version after the plus.
version = "$modVersion+$mcVersion"
group = prop("maven_group")
base.archivesName = prop("archives_base_name")

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    // Mojang's names on every version: 26.x ships with them, 1.21.11 is mapped to them. That keeps
    // the source identical across versions wherever Minecraft itself did not change.
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
}

// Client game tests: src/gametest holds a separate test mod that starts the real game, opens a
// world and checks the HUD, the screens and the action bar hook. It never ships in the jar.
//   ./gradlew :26.2:runClientGameTest
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "minerefine-hud-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

tasks.processResources {
    val expansions = mapOf(
        "version" to modVersion,
        "minecraft_range" to prop("minecraft_range"),
        "java_version" to javaVersion.toString(),
    )
    inputs.properties(expansions)
    filesMatching("fabric.mod.json") { expand(expansions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

java {
    withSourcesJar()
    // 1.21.11 runs on Java 21, 26.x on Java 25. Gradle fetches the right JDK if it is missing.
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

// The logic tests are plain main() classes with no Minecraft in them (tools/run-core-tests.sh runs
// them without Gradle). `check` runs them too, so a build never ships with a failing one.
tasks.test { enabled = false }
val logicTests = listOf("CoreTests", "ShopTests", "LayoutTests", "ProgressTests").map { name ->
    tasks.register<JavaExec>("run$name") {
        group = "verification"
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass = "minerefinehud.$name"
    }
}
tasks.check { dependsOn(logicTests) }

// Copies this version's finished jar to the root build/libs, next to the other versions' jars.
tasks.register<Copy>("collectJar") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}
tasks.named("build") { finalizedBy("collectJar") }
