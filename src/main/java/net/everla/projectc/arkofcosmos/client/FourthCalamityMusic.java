package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
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

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class FourthCalamityMusic {
    private static Track current;

    private FourthCalamityMusic() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            stop(mc);
            return;
        }
        if (mc.isPaused()) return;
        FourthCalamityEntity boss = current != null && eligible(mc, current.boss) ? current.boss :
                mc.level.getEntitiesOfClass(FourthCalamityEntity.class, mc.player.getBoundingBox().inflate(128),
                        candidate -> eligible(mc, candidate)).stream()
                        .min(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mc.player)))
                        .orElse(null);
        if (boss == null) {
            stop(mc);
            return;
        }
        mc.getMusicManager().stopPlaying();
        if (current == null || current.boss != boss) {
            stop(mc);
            current = new Track(boss);
            mc.getSoundManager().play(current);
        }
    }

    private static boolean eligible(Minecraft mc, FourthCalamityEntity boss) {
        return boss.level() == mc.level && boss.isAlive() && !boss.isRemoved() && !boss.defeated()
                && boss.distanceToSqr(mc.player) <= 128 * 128;
    }

    private static void stop(Minecraft mc) {
        if (current != null) mc.getSoundManager().stop(current);
        current = null;
    }

    private static final class Track extends AbstractTickableSoundInstance {
        private final FourthCalamityEntity boss;

        Track(FourthCalamityEntity boss) {
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation(ArkOfCosmos.MOD_ID,
                    "fourth_calamity_music")), SoundSource.MUSIC, RandomSource.create());
            this.boss = boss;
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = .35f;
        }

        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.isAlive() || !eligible(mc, boss)) stop();
        }
    }
}
