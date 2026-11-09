package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-owned looping battle theme; fades out once Satsuki Rin dies (or leaves range) instead of playing to the end. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class SatsukiMusic {
    private static final int FADE_TICKS=60;
    private static final double RANGE=128;
    private static Track current;
    // Once a boss is seen dying it never restarts the theme, even if its synced health briefly reads >0 during the finale.
    private static final java.util.Set<java.util.UUID> DEFEATED=new java.util.HashSet<>();
    private static Object world;
    private SatsukiMusic(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=world){world=mc.level;DEFEATED.clear();}
        if(mc.level==null||mc.player==null){if(current!=null)mc.getSoundManager().stop(current);current=null;return;}
        if(current!=null&&current.isStopped())current=null;
        if(current!=null&&!current.fading)return;
        LivingEntity boss=mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(RANGE),SatsukiMusic::fighting)
                .stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).orElse(null);
        if(boss==null||current!=null)return; // a fading track finishes before a new fight restarts the theme
        mc.getMusicManager().stopPlaying();
        current=new Track(boss);mc.getSoundManager().play(current);
    }
    private static boolean fighting(LivingEntity e){
        if(!(e instanceof SatsukiBoss)||e.isRemoved()||DEFEATED.contains(e.getUUID()))return false;
        if(e.isDeadOrDying()||e.deathTime>0){DEFEATED.add(e.getUUID());return false;}
        return true;
    }

    private static final class Track extends AbstractTickableSoundInstance {
        private final LivingEntity boss;
        private boolean fading;
        private int fade;
        Track(LivingEntity boss){
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"satsuki_rin_battle_theme")),SoundSource.MUSIC,RandomSource.create());
            this.boss=boss;looping=true;relative=true;attenuation=Attenuation.NONE;volume=1;
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();
            if(!fading&&(mc.player==null||boss.level()!=mc.level||!fighting(boss)||boss.distanceToSqr(mc.player)>RANGE*RANGE))fading=true;
            if(fading){
                if(!mc.isPaused())fade++;
                volume=Math.max(0,1-(float)fade/FADE_TICKS);
                if(fade>=FADE_TICKS)stop();
            }else mc.getMusicManager().stopPlaying();
        }
    }
}
