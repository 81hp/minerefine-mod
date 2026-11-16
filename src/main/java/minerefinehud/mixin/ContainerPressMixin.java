package minerefinehud.mixin;

import minerefinehud.client.ContainerClickHook;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes when the game starts and finishes handling the mouse button going down and up on a menu
 * screen, so the salvage guard can tell the player's own click from the ones the game adds by
 * itself when the button is let go, and from another mod's (see {@link ContainerClickMixin} for
 * the clicks themselves). The game says with each press whether it took it for the second of a
 * double click, which is when it adds them.
 *
 * VERSION SENSITIVE, though mouseClicked(MouseButtonEvent, boolean) and
 * mouseReleased(MouseButtonEvent) are the same on 1.21.11, 26.1 and 26.2. require = 0: without
 * them the guard falls back to telling clicks apart by time alone.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerPressMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), require = 0)
    private void minerefine$press(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ContainerClickHook.pressed(doubleClick);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), require = 0)
    private void minerefine$pressDone(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ContainerClickHook.pressDone();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), require = 0)
    private void minerefine$release(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        ContainerClickHook.released();
    }

    @Inject(method = "mouseReleased", at = @At("RETURN"), require = 0)
    private void minerefine$releaseDone(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        ContainerClickHook.releaseDone();
    }
}
