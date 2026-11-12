package minerefinehud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import minerefinehud.client.DropKeyHook;

/**
 * Sees every press of the drop key: the key handler calls the player's drop once per press. The
 * Halloween hoe fires its ability on drop and the server says nothing, so this is when the
 * ability's cooldown starts.
 *
 * VERSION SENSITIVE. 26.3 removed LocalPlayer.drop(boolean) and moved the drop to
 * MultiPlayerGameMode.dropItem(LocalPlayer, boolean); the mixin follows it there. require = 0, so a
 * moved method only costs the cooldown start; "recharging" and "recharged" in chat still correct it.
 */
//? if >=26.3 {
@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
//?} else {
/*@Mixin(net.minecraft.client.player.LocalPlayer.class)
*///?}
public abstract class DropKeyMixin {

    //? if >=26.3 {
    @Inject(method = "dropItem", at = @At("HEAD"), require = 0)
    private void minerefine$onDrop(net.minecraft.client.player.LocalPlayer player, boolean all,
                                   org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        DropKeyHook.fire();
    }
    //?} else {
    /*@Inject(method = "drop", at = @At("HEAD"), require = 0)
    private void minerefine$onDrop(boolean fullStack,
                                   org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        DropKeyHook.fire();
    }
    *///?}
}
