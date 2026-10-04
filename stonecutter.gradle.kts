// Stonecutter's controller: picks which Minecraft version the source in src/ is currently written
// for. Switch with ./gradlew "Set active project to 26.2" and switch back before committing.
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

// ./gradlew buildAll builds every Minecraft version; the jars land together in build/libs.
tasks.register("buildAll") {
    group = "build"
    description = "Builds the jar for every Minecraft version."
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

// ./gradlew testAll runs the in-game client tests on every Minecraft version, one after another.
tasks.register("testAll") {
    group = "verification"
    description = "Runs the client game tests on every Minecraft version."
    dependsOn(stonecutter.tasks.named("runClientGameTest").map { it.values })
}

// Plain renames between versions. The source uses the 26.2 names; older versions get the old name
// swapped in, in the generated copy only. Anything that changed shape rather than name goes in
// client/Compat.java behind a `//? if` guard instead.
// A replacement must never produce text that itself appears in the source as a declaration.
stonecutter parameters {
    replacements {
        string(current.parsed < "26.1") {
            replace("GuiGraphicsExtractor", "GuiGraphics")
            replace("extractRenderState(", "render(")
            replace("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
                    "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper")
            replace("KeyMappingHelper.registerKeyMapping", "KeyBindingHelper.registerKeyBinding")
            replace("net.fabricmc.fabric.api.client.command.v2.ClientCommands;",
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;")
            replace("ClientCommands.literal", "ClientCommandManager.literal")
            // Drawing calls. Matched with the `context.` receiver so `line.text()` is left alone.
            replace("context.text(", "context.drawString(")
            replace("context.centeredText(", "context.drawCenteredString(")
            replace("context.outline(", "context.renderOutline(")
        }
    }
}
