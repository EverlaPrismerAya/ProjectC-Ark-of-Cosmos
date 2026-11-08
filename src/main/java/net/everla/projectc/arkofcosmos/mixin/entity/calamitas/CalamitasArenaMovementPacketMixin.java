package net.everla.projectc.arkofcosmos.mixin.entity.calamitas;

import net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class CalamitasArenaMovementPacketMixin {
    @Shadow public ServerPlayer player;
    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket ark$arenaPosition(ServerboundMovePlayerPacket packet) {
        // The first invocation may still be on Netty; vanilla re-enqueues it on the server thread.
        return player.getServer().isSameThread() ? CalamitasArenaBoundary.constrainPacket(player, packet) : packet;
    }
}
