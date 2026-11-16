package minerefinehud.mixin;

import minerefinehud.client.ContainerClickHook;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sees every click in a menu before the game acts on it or tells the server, and can stop it. The
 * salvage guard uses it to make a big turret take two clicks.
 *
 * Here rather than on the screen's own click: the hotbar keys, the drop key and other mods'
 * inventory shortcuts skip the screen and all end in this one method.
 *
 * VERSION SENSITIVE. 26.1 renamed the method and the enum of click kinds; the arguments and the
 * kinds' names stayed. require = 0: a renamed or removed method costs the guard, not the game.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ContainerClickMixin {

    //? if >=26.1 {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$click(int containerId, int slot, int button,
                                  net.minecraft.world.inventory.ContainerInput input, Player player, CallbackInfo ci) {
        if (ContainerClickHook.fire(containerId, slot, button, input == null ? "" : input.name())) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$click(int containerId, int slot, int button,
                                  net.minecraft.world.inventory.ClickType input, Player player, CallbackInfo ci) {
        if (ContainerClickHook.fire(containerId, slot, button, input == null ? "" : input.name())) {
            ci.cancel();
        }
    }
    *///?}
}
