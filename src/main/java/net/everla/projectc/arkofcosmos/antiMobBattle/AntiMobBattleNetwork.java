package net.everla.projectc.arkofcosmos.antiMobBattle;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.antiMobBattle.client.ObliterationText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AntiMobBattleNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("arkofcosmos", "anti_mob_battle"), () -> "2", "2"::equals, "2"::equals);
    private record Popup(UUID event, ResourceLocation dimension, Vec3 position, boolean procedureSword) {}
    private AntiMobBattleNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Popup.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, b) -> { b.writeUUID(p.event); b.writeResourceLocation(p.dimension); b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z); b.writeBoolean(p.procedureSword); })
                .decoder(b -> new Popup(b.readUUID(), b.readResourceLocation(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readBoolean()))
                .consumerMainThread((p, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ObliterationText.receive(p.event, p.dimension, p.position, p.procedureSword))).add();
    }

    public static void popup(Entity target, boolean procedureSword) {
        if (!(target.level() instanceof ServerLevel level)) return;
        var packet = new Popup(UUID.randomUUID(), level.dimension().location(), target.getBoundingBox().getCenter(), procedureSword);
        for (var player : level.players()) if (player.distanceToSqr(target) <= 128 * 128)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
