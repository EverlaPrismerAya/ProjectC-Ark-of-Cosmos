package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Source Grief/Lament/Epiphany scene music, sharing the existing original assets. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID, value=Dist.CLIENT)
public final class CalamitasMusic {
    private static Track current;
    private static final java.util.Map<Integer, Track> tracks = new java.util.HashMap<>();
    private static Acceptance acceptance;
    private static Object eventConnection;
    private static java.util.UUID lastAcceptance;
    private CalamitasMusic() {}
    public static void acceptance(net.minecraft.nbt.CompoundTag data) {
        var mc = Minecraft.getInstance();
        resetConnection(mc);
        if (mc.getConnection() == null || !data.hasUUID("Owner")) return;
        var owner = data.getUUID("Owner");
        if (!data.getBoolean("Start")) {
            if (acceptance != null && acceptance.owner.equals(owner)) stopAcceptance(mc);
        } else if (!owner.equals(lastAcceptance)) {
            lastAcceptance = owner; stopAcceptance(mc); current = null;
            acceptance = new Acceptance(owner); mc.getSoundManager().play(acceptance);
        }
    }
    private static void stopAcceptance(Minecraft mc) {
        if (acceptance != null) mc.getSoundManager().stop(acceptance);
        acceptance = null;
    }
    private static void resetConnection(Minecraft mc) {
        if (eventConnection != mc.getConnection()) {
            stopAcceptance(mc); lastAcceptance = null; eventConnection = mc.getConnection();
        }
    }
    private static int stage(SupremeCalamitasEntity boss) {
        if (boss.view().getString("Phase").equals("FINISHED")) return 0;
        if (boss.view().getString("Phase").equals("ACCEPTANCE")) return 4;
        float ratio = boss.getHealth() / boss.getMaxHealth();
        return ratio <= .3f ? 3 : ratio <= .5f ? 2 : 1;
    }
    private static boolean eligible(Minecraft mc, SupremeCalamitasEntity boss) {
        double pixels = boss.view().getDouble("Pixels");
        // Source scene rectangle becomes a camera-centered cube in the 3D port.
        var delta = boss.getBoundingBox().getCenter().subtract(mc.gameRenderer.getMainCamera().getPosition());
        return mc.level == boss.level() && boss.isAlive() && !boss.isRemoved() && pixels > 0
                && stage(boss) != 0 && Math.max(Math.max(Math.abs(delta.x), Math.abs(delta.y)), Math.abs(delta.z)) <= 5000 / pixels;
    }
    private static void stop(Minecraft mc) {
        tracks.values().forEach(mc.getSoundManager()::stop); tracks.clear();
        current = null;
    }
    /** Terraria Main computes channel gain before updating its fade coefficient. */
    private static void fade(Music main) {
        for (int sourceFrame = 0; sourceFrame < 3; sourceFrame++) {
            boolean audible = main.fade > .25f;
            if (acceptance != null) acceptance.fade(acceptance == main, audible);
            tracks.values().forEach(track -> track.fade(track == main, audible));
        }
        tracks.values().removeIf(track -> track.isStopped());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        resetConnection(mc);
        if (acceptance != null) {
            long now = System.nanoTime();
            if (!mc.isPaused()) acceptance.elapsed += Math.max(0, now - acceptance.lastTime);
            acceptance.lastTime = now;
            if (mc.level == null || acceptance.elapsed >= 58_689_000_000L) stopAcceptance(mc);
            else {
                mc.getMusicManager().stopPlaying();
                if (!mc.isPaused() && mc.getOverlay() == null) fade(acceptance);
                return;
            }
        }
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) { stop(mc); return; }
        if (mc.isPaused() || mc.getOverlay() != null) return;
        var boss = current != null && eligible(mc, current.boss) ? current.boss : SupremeCalamitasEntity.loaded(mc.level).stream()
                .filter(candidate -> eligible(mc, candidate)).min(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mc.player))).orElse(null);
        if (boss == null) { stop(mc); return; }
        mc.getMusicManager().stopPlaying();
        if (tracks.values().stream().anyMatch(track -> track.boss != boss)) stop(mc);
        int stage = stage(boss);
        current = tracks.get(stage);
        if (current == null || current.isStopped() || !mc.getSoundManager().isActive(current)) {
            if (current != null) mc.getSoundManager().stop(current);
            current = new Track(boss); tracks.put(stage, current); mc.getSoundManager().play(current);
        }
        fade(current);
    }
    private abstract static class Music extends AbstractTickableSoundInstance {
        float fade;
        Music(String event, boolean loop) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,
                    event)), SoundSource.MUSIC, RandomSource.create());
            relative = true; attenuation = Attenuation.NONE; looping = loop; volume = 0;
        }
        void fade(boolean selected, boolean mainAudible) {
            float before = fade;
            if (selected) fade = Math.min(1, fade + .005f);
            else if (mainAudible) fade = Math.max(0, fade - .005f);
            if (!selected && fade <= 0) { volume = 0; stop(); }
            else volume = (float)Math.pow(10, (31 * (double)before - 25 - 11.94) / 20);
        }
        @Override public boolean canStartSilent() { return true; }
    }
    private static final class Acceptance extends Music {
        final java.util.UUID owner;
        long elapsed, lastTime = System.nanoTime();
        Acceptance(java.util.UUID owner) {
            super("calamitas_acceptance", false);
            this.owner = owner;
        }
        @Override public void tick() {}
    }
    private static final class Track extends Music {
        final SupremeCalamitasEntity boss;
        final int stage;
        Track(SupremeCalamitasEntity boss) {
            super("calamitas_music_" + stage(boss), true);
            this.boss = boss; stage = stage(boss);
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.isAlive() || !eligible(mc, boss)) stop();
        }
    }
}
