package minerefinehud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import minerefinehud.client.ActionBarHook;
import net.minecraft.network.chat.Component;

/**
 * Sees every action bar message.
 *
 * VERSION SENSITIVE. Fabric's ClientReceiveMessageEvents never fires for the action bar on modern
 * servers: they send it as its own packet, which vanilla hands straight to
 * setOverlayMessage without going through the message handler Fabric hooks. This is the one place
 * every action bar passes through, whichever packet carried it. It lives on Gui up to 26.1 and on
 * the Hud class 26.2 split out of it.
 *
 * require = 0 so that if another mod or a Minecraft update moves this method, the injection is
 * skipped and live progress quietly stops, rather than the game refusing to start. The client game
 * test (src/gametest) fails the build when that happens, so it never ships unnoticed.
 */
//? if >=26.2 {
@Mixin(net.minecraft.client.gui.Hud.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?}
public abstract class InGameHudMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), require = 0)
    private void minerefine$captureActionBar(Component message, boolean tinted, CallbackInfo ci) {
        ActionBarHook.fire(message == null ? "" : message.getString());
    }
}
