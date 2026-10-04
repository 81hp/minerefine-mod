pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the JDK a node needs (21 for 1.21.11, 25 for 26.x) when the machine lacks it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies the remapping Loom on 1.21.11 (still obfuscated) and plain Loom on 26.x.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

// One source tree, one jar per Minecraft version. Code that differs between versions is wrapped
// in `//? if >=26.1 { ... } else { ... }` comments, which Stonecutter switches per version.
//
// To add a version: append it below, add versions/<version>/gradle.properties, and guard whatever
// no longer compiles. Every version is built and game-tested by CI on every push.
stonecutter {
    create(rootProject) {
        versions("1.21.11", "26.1.2", "26.2")
        // The version the checked-in source is written for. Switch nodes to test another version
        // (./gradlew "Set active project to 26.2"), but switch back before committing.
        vcsVersion = "26.2"
    }
}

rootProject.name = "minerefine-hud"
