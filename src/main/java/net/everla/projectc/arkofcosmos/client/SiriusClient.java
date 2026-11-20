package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.SiriusShield;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class SiriusClient {
    private static boolean pressed;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        boolean down=mc.player!=null&&mc.screen==null&&mc.isWindowActive()&&SiriusShield.holding(mc.player)&&ArkClient.DASH.isDown()&&mc.options.keyUse.isDown();
        if(mc.getConnection()!=null&&down!=pressed)ArkNetwork.send(false,down,down);
        pressed=down;
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        var mc=Minecraft.getInstance();
        if(e.isUseItem()&&mc.player!=null&&SiriusShield.holding(mc.player)&&ArkClient.DASH.isDown()) {
            e.setCanceled(true);e.setSwingHand(false);ArkNetwork.send(false,true,true);pressed=true;
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e) {
        var mc=Minecraft.getInstance();
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||mc.level==null||ArkFxDraw.forcefieldShader==null)return;
        ArkFxDraw.matrix=e.getPoseStack().last().pose();ArkFxDraw.camera=e.getCamera().getPosition();ArkFxDraw.fadeEye=null;
        for(Player p:mc.level.players()) {
            if(SiriusShield.time(p)<=0||SiriusShield.capacity(p)<=0||p.isInvisible())continue;
            if(p==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson())continue;
            Vec3 at=p.getPosition(e.getPartialTick()).add(0,p.getBbHeight()/2,0);
            ArkFxDraw.forcefield(at,NativeSprites.billboard(),1,1,.75f,(p.tickCount+e.getPartialTick())/20f,SiriusShield.flash(p)>0,1.4);
        }
    }
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.getCameraEntity()!=p||!mc.options.getCameraType().isFirstPerson()||mc.options.hideGui)return;
        boolean hit=SiriusShield.flash(p)>0;
        if(!hit&&(SiriusShield.time(p)<=0||SiriusShield.capacity(p)<=0))return;
        int w=e.getGuiGraphics().guiWidth(),h=e.getGuiGraphics().guiHeight();
        int edge=Math.max(12,Math.min(w,h)/10);
        for(int i=0;i<edge;i++) {
            int alpha=(int)((hit?100:65)*Math.pow(1-i/(double)edge,2));
            int color=alpha<<24|(hit?0xFF2020:0x208CFF);
            var g=e.getGuiGraphics();
            g.fill(i,i,w-i,i+1,color);g.fill(i,h-i-1,w-i,h-i,color);
            g.fill(i,i+1,i+1,h-i-1,color);g.fill(w-i-1,i+1,w-i,h-i-1,color);
        }
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void siriusDecorations(RegisterItemDecorationsEvent e) {
            e.register(ArkOfCosmos.SIRIUS.get(),(g,font,stack,x,y)->{
                var p=Minecraft.getInstance().player;if(p==null)return false;
                int[] values={Math.round(13f*SiriusShield.cooldown(p)/300),Math.round(13f*SiriusShield.time(p)/160),Math.round(13f*SiriusShield.capacity(p)/(float)Math.max(.001,net.everla.projectc.arkofcosmos.ProjectCCommonConfig.value("weapons.sirius.shield_capacity")))};
                int[] colors={0xFFFF9A2E,0xFF55CCFF,0xFF3266FF};
                for(int i=0;i<3;i++)if(values[i]>0){int row=y+10+i*2;g.fill(x+2,row,x+15,row+2,0xFF000000);g.fill(x+2,row,x+2+Math.min(13,values[i]),row+1,colors[i]);}
                return false;
            });
        }
    }
}
