package net.everla.projectc.arkofcosmos.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBarrage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Source non-looping end-of-barrage telegraph, following the battle target. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID, value=Dist.CLIENT)
public final class CalamitasRumbleSound {
    private static final Map<SupremeCalamitasEntity, State> STATES = new IdentityHashMap<>();
    private static final class State { int sequence; Rumble sound; }
    private CalamitasRumbleSound() {}
    private static boolean valid(SupremeCalamitasEntity boss) {
        var mc = Minecraft.getInstance(); var view = boss.view();
        if (mc.level != boss.level() || mc.player == null || !boss.isAlive() || boss.isRemoved()
                || !view.hasUUID("Target") || view.getDouble("Pixels") <= 0) return false;
        var target = mc.level.getEntity(view.getInt("TargetId"));
        return !(target instanceof LivingEntity living) || !target.getUUID().equals(view.getUUID("Target")) || living.isAlive();
    }
    private static void stop(State state) {
        if (state.sound != null) { Minecraft.getInstance().getSoundManager().stop(state.sound); state.sound = null; }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        Set<SupremeCalamitasEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
        if (mc.level != null) for (var boss : SupremeCalamitasEntity.loaded(mc.level)) {
            if (boss.isRemoved() || !boss.isAlive()) continue;
            loaded.add(boss); var state = STATES.computeIfAbsent(boss, key -> new State());
            if (!valid(boss)) { state.sequence = boss.rumbleCue(); stop(state); continue; }
            if (mc.isPaused()) continue;
            if (state.sequence != boss.rumbleCue()) {
                state.sequence = boss.rumbleCue(); stop(state);
                state.sound = new Rumble(boss); state.sound.follow(); mc.getSoundManager().play(state.sound);
            } else if (state.sound != null && !mc.getSoundManager().isActive(state.sound)) state.sound = null;
        }
        STATES.entrySet().removeIf(entry -> {
            if (loaded.contains(entry.getKey())) return false;
            stop(entry.getValue()); return true;
        });
    }
    private static final class Rumble extends AbstractTickableSoundInstance {
        private final SupremeCalamitasEntity boss;
        private final int round;
        private boolean located;
        Rumble(SupremeCalamitasEntity boss) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,
                    "calamitas_barrage_rumble")), SoundSource.HOSTILE, RandomSource.create());
            this.boss = boss; round = boss.view().getInt("Round"); attenuation = Attenuation.NONE;
        }
        void follow() {
            var mc = Minecraft.getInstance(); var view = boss.view();
            if (!located || view.getString("Phase").equals("BARRAGE") && view.getInt("Round") == round && view.getInt("Frame") >= SupremeCalamitasBarrage.RUMBLE_FRAME) {
                var target = mc.level.getEntity(view.getInt("TargetId"));
                Vec3 center = target instanceof LivingEntity && target.getUUID().equals(view.getUUID("Target"))
                        ? target.getBoundingBox().getCenter() : new Vec3(view.getDouble("TargetX"), view.getDouble("TargetY"), view.getDouble("TargetZ"));
                x = center.x; y = center.y; z = center.z; located = true;
            }
            double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z)) * view.getDouble("Pixels");
            volume = (float)Mth.clamp(1 - distance / 2500, 0, 1);
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() { if (!valid(boss)) stop(); else follow(); }
    }
}
