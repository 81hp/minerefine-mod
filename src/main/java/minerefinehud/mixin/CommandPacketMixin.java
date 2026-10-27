package minerefinehud.mixin;

import minerefinehud.client.AutoCommands;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes every command and chat message as it leaves, so the mod's own commands keep clear of
 * them (see {@link AutoCommands}). Every path goes through send: typed in chat, clicked in chat,
 * sent by another mod.
 *
 * VERSION SENSITIVE, though send(Packet) and the three packets are the same on 1.21.11, 26.1 and
 * 26.2. require = 0: without it, only the mod's own commands are spaced from each other.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class CommandPacketMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 0)
    private void minerefine$send(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundChatCommandPacket || packet instanceof ServerboundChatCommandSignedPacket
                || packet instanceof ServerboundChatPacket) {
            AutoCommands.noteSent();
        }
    }
}
