package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.MahouAttackEntity;
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

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class MahouVortexSound {
    private static Loop current;
    private MahouVortexSound() {}
    static float volume(double distance){return (float)(1.4*Math.max(0,Math.min(1,(24-distance)/20)));}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.isPaused())return;
        MahouAttackEntity nearest=null;double distance=24;
        if(mc.level!=null&&mc.player!=null){
            var eye=mc.gameRenderer.getMainCamera().getPosition();
            for(var entity:mc.level.entitiesForRendering())
                if(entity instanceof MahouAttackEntity attack&&attack.kind()==MahouAttackEntity.VOID_VORTEX&&!attack.isRemoved()){
                    double d=attack.position().distanceTo(eye);
                    if(d<distance){nearest=attack;distance=d;}
                }
        }
        if(nearest==null){
            if(current!=null)mc.getSoundManager().stop(current);
            current=null;return;
        }
        if(current==null||!mc.getSoundManager().isActive(current)){
            current=new Loop();current.follow(nearest,distance);mc.getSoundManager().play(current);
        }else current.follow(nearest,distance);
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        Loop(){
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"mahou_vortex")),SoundSource.HOSTILE,RandomSource.create());
            looping=true;attenuation=Attenuation.NONE;
        }
        void follow(MahouAttackEntity vortex,double distance){
            x=vortex.getX();y=vortex.getY();z=vortex.getZ();volume=MahouVortexSound.volume(distance);
        }
        @Override public void tick() {}
    }
}
