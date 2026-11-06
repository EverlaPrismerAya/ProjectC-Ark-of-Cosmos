package net.everla.projectc.arkofcosmos.client;

import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.CalamitasAttackEntity;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ordinary-world BrimstoneMonster drone; one native channel per tracked vortex. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID, value=Dist.CLIENT)
public final class CalamitasVortexSound {
    private static final Map<CalamitasAttackEntity, Loop> LOOPS = new IdentityHashMap<>();
    private CalamitasVortexSound() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        Set<CalamitasAttackEntity> live = Collections.newSetFromMap(new IdentityHashMap<>());
        if (mc.level != null && mc.player != null) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof CalamitasAttackEntity attack) || attack.kind() != CalamitasAttackEntity.Kind.MAELSTROM
                        || attack.isRemoved() || attack.view().getInt("Remaining") < 90) continue;
                var snapshot = attack.view();
                if (!snapshot.hasUUID("Owner") || snapshot.getDouble("Pixels") <= 0) continue;
                boolean ownerLoaded = SupremeCalamitasEntity.loaded(mc.level).stream().anyMatch(boss ->
                        boss.isAlive() && !boss.isRemoved() && boss.getUUID().equals(snapshot.getUUID("Owner")));
                if (!ownerLoaded) continue;
                live.add(attack);
                if (mc.isPaused()) continue;
                var loop = LOOPS.get(attack);
                if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop)) {
                    if (loop != null) mc.getSoundManager().stop(loop);
                    loop = new Loop(attack); LOOPS.put(attack, loop);
                    loop.follow(); mc.getSoundManager().play(loop);
                } else loop.follow();
            }
        }
        LOOPS.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            mc.getSoundManager().stop(entry.getValue()); return true;
        });
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final CalamitasAttackEntity vortex;
        private float sourceVolume = 1;
        Loop(CalamitasAttackEntity vortex) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,
                    "calamitas_vortex_drone")), SoundSource.HOSTILE, RandomSource.create());
            this.vortex = vortex; looping = true; attenuation = Attenuation.NONE;
        }
        void follow() {
            x = vortex.getX(); y = vortex.getY(); z = vortex.getZ();
            var mc = Minecraft.getInstance(); var player = mc.player;
            double pixels = vortex.view().getDouble("Pixels");
            double distance = player.isAlive() ? player.getBoundingBox().getCenter().distanceTo(vortex.position()) * pixels : 2000;
            if (distance <= 1400) {
                // Terraria Utils.GetLerpValue defaults to unclamped, including below 700px.
                float shift = (float)((1400 - distance) / 700);
                sourceVolume = Mth.lerp(shift, .3f, .8f);
                // XNA SoundEffectInstance.Pitch is in octaves; OpenAL pitch is a ratio.
                pitch = (float)Math.pow(2, .7f * shift);
            }
            double cameraDistance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(vortex.position()) * pixels;
            // SoundEngine applies the final clamp after the native HOSTILE volume slider.
            volume = sourceVolume * (float)Mth.clamp(1 - cameraDistance / 2500, 0, 1);
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (mc.level != vortex.level() || mc.player == null || vortex.isRemoved()
                    || vortex.view().getInt("Remaining") < 90) stop();
        }
    }
}
