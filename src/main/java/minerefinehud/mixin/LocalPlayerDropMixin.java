package minerefinehud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import minerefinehud.client.DropKeyHook;
import net.minecraft.client.player.LocalPlayer;

/**
 * Sees every press of the drop key: the key handler calls the player's drop once per press. The
 * Halloween hoe fires its ability on drop and the server says nothing, so this is when the
 * ability's cooldown starts.
 *
 * VERSION SENSITIVE, though drop(boolean) is the same on 1.21.11, 26.1 and 26.2. require = 0, so a
 * moved method only costs the cooldown start; "recharging" and "recharged" in chat still correct it.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDropMixin {

    @Inject(method = "drop", at = @At("HEAD"), require = 0)
    private void minerefine$onDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        DropKeyHook.fire();
    }
}
