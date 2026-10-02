package minerefinehud.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import minerefinehud.client.ActionBarHook;

/**
 * Sees every action bar message.
 *
 * VERSION SENSITIVE. Fabric's ClientReceiveMessageEvents never fires for the action bar on modern
 * servers: they send it as its own packet, which vanilla hands straight to
 * InGameHud.setOverlayMessage without going through the message handler Fabric hooks. This is the
 * one place every action bar passes through, whichever packet carried it.
 *
 * require = 0 so that if another mod or a Minecraft update moves this method, the injection is
 * skipped and live progress quietly stops, rather than the game refusing to start.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), require = 0)
    private void minerefine$captureActionBar(Text message, boolean tinted, CallbackInfo ci) {
        ActionBarHook.fire(message == null ? "" : message.getString());
    }
}
