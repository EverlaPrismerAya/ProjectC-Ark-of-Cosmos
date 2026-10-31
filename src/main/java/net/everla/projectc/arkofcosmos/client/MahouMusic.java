package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
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

/** Independent client music ownership; no Goety classes or mixins are required. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class MahouMusic {
    private static Track current;
    private static DefeatTrack defeat;

    private MahouMusic() {}

    public static boolean postMusicHit(MahouTsukaiEntity boss) {
        return current != null && current.boss == boss && current.phase == 3 && current.elapsedTicks >= 694;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop(mc);
            return;
        }
        MahouTsukaiEntity defeated = mc.level.getEntitiesOfClass(MahouTsukaiEntity.class, mc.player.getBoundingBox().inflate(96), MahouTsukaiEntity::defeating)
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (defeated != null) {
            if (current != null) stop(mc);
            if (defeat == null || defeat.boss != defeated) { if (defeat != null) mc.getSoundManager().stop(defeat); defeat = new DefeatTrack(defeated); mc.getSoundManager().play(defeat); }
            return;
        }
        if (defeat != null) { mc.getSoundManager().stop(defeat); defeat = null; }
        if (mc.isPaused()) return;
        // Keep the current boss until it leaves range, so nearby bosses cannot alternate tracks.
        MahouTsukaiEntity boss = current != null && eligible(mc, current.boss) ? current.boss :
                mc.level.getEntitiesOfClass(MahouTsukaiEntity.class, mc.player.getBoundingBox().inflate(64),
                        candidate -> eligible(mc, candidate)).stream()
                        .min(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mc.player)))
                        .orElse(null);
        if (boss == null) {
            stop(mc);
            return;
        }
        mc.getMusicManager().stopPlaying();
        if (current == null || current.boss != boss || current.phase != boss.phase()) {
            stop(mc);
            current = new Track(boss);
            mc.getSoundManager().play(current);
        }
    }

    private static boolean eligible(Minecraft mc, MahouTsukaiEntity boss) {
        return boss.level() == mc.level && boss.isAlive() && !boss.isRemoved()
                && boss.distanceToSqr(mc.player) <= 64 * 64;
    }

    private static void stop(Minecraft mc) {
        if (current != null) mc.getSoundManager().stop(current);
        current = null;
    }

    private static final class Track extends AbstractTickableSoundInstance {
        private final MahouTsukaiEntity boss;
        private final int phase;
        private int elapsedTicks;

        Track(MahouTsukaiEntity boss) {
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation(ArkOfCosmos.MOD_ID,
                    "mahou_music_phase_" + boss.phase())), SoundSource.MUSIC, RandomSource.create());
            this.boss = boss;
            phase = boss.phase();
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = .35f;
        }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !eligible(mc, boss) || boss.phase() != phase) stop();
            else if (!mc.isPaused()) elapsedTicks++;
        }
    }

    private static final class DefeatTrack extends AbstractTickableSoundInstance {
        private final MahouTsukaiEntity boss;
        DefeatTrack(MahouTsukaiEntity boss) {
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation(ArkOfCosmos.MOD_ID, "mahou_defeat")), SoundSource.MUSIC, RandomSource.create());
            this.boss=boss; looping=false; relative=true; attenuation=Attenuation.NONE; volume=.35f;
        }
        @Override public void tick() { if (!boss.defeating()) stop(); }
    }
}
