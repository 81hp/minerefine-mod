package minerefinehud.mixin;

import minerefinehud.client.BossIndexPoller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the automatic /bossindex check read the menu without it ever opening: its packets go to
 * {@link BossIndexPoller} instead of the screen. Every other menu passes straight through.
 *
 * Each handler runs twice: first on the network thread, where vanilla only hands the packet to
 * the game thread, then on the game thread. Only the second call is looked at, so the poller is
 * only ever touched from the game thread.
 *
 * VERSION SENSITIVE, though these four handlers and packets are the same on 1.21.11, 26.1 and
 * 26.2. require = 0: a moved handler shows the menu once a check instead of crashing.
 */
@Mixin(ClientPacketListener.class)
public abstract class ContainerPacketMixin {

    private static boolean minerefine$onGameThread() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.isSameThread();
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$openScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        try {
            if (minerefine$onGameThread() && BossIndexPoller.onOpenScreen(packet.getContainerId(), packet.getTitle())) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        try {
            if (minerefine$onGameThread() && BossIndexPoller.onContent(packet.containerId(), packet.items())) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        try {
            if (minerefine$onGameThread()
                    && BossIndexPoller.onSlot(packet.getContainerId(), packet.getSlot(), packet.getItem())) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void minerefine$close(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        try {
            if (minerefine$onGameThread() && BossIndexPoller.onClose(packet.getContainerId())) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
