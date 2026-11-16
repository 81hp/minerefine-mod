package minerefinehud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import minerefinehud.client.TabListHook;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

/**
 * Sees the tab list's header and footer, where servers often print their name.
 *
 * VERSION SENSITIVE. Both are private fields with no getter; the setters are the one place every
 * header and footer passes through, and reset is where the game forgets them: on disconnect, and
 * when a proxy moves the player to another server. Unchanged from 1.21.11 to 26.2.
 *
 * require = 0, so a Minecraft update that moves them only loses the tab list as a way to tell
 * MineRefine; the sidebar still tells it.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {

    @Inject(method = "setHeader", at = @At("HEAD"), require = 0)
    private void minerefine$header(Component header, CallbackInfo ci) {
        try {
            TabListHook.header(header == null ? "" : header.getString());
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "setFooter", at = @At("HEAD"), require = 0)
    private void minerefine$footer(Component footer, CallbackInfo ci) {
        try {
            TabListHook.footer(footer == null ? "" : footer.getString());
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "reset", at = @At("HEAD"), require = 0)
    private void minerefine$reset(CallbackInfo ci) {
        TabListHook.clear();
    }
}
