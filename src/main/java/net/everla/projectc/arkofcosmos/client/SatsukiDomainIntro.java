package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.SatsukiContent;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class SatsukiDomainIntro {
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event){
        draw(event.getGuiGraphics(),event.getPartialTick());
    }
    @SubscribeEvent public static void hiddenHud(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        var mc=Minecraft.getInstance();
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!mc.options.hideGui||mc.level==null)return;
        var gui=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        var stack=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();stack.pushPose();stack.setIdentity();
        stack.translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,gui.guiWidth(),gui.guiHeight(),0,1000,21000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        try{draw(gui,event.renderTickTime);gui.flush();}
        finally{stack.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();}
    }
    private static void draw(net.minecraft.client.gui.GuiGraphics gui,float partial){
        var mc=Minecraft.getInstance();
        if(!SatsukiContent.ENABLED||mc.level==null||mc.player==null)return;
        float opacity=0;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof SatsukiRinEntity rin&&rin.distanceToSqr(mc.player)<=128*128){
            if(rin.isDeadOrDying()){
                float t=rin.domainIntro()+partial;
                if(t>0&&t<SatsukiRinEntity.INTRO_TICKS)
                    opacity=Math.max(opacity,Math.min(1,Math.min(t/10,(SatsukiRinEntity.INTRO_TICKS-t)/10)));
            }
        }
        int alpha=(int)(opacity*255);if(alpha<4)return;
        int w=gui.guiWidth(),h=gui.guiHeight();
        gui.fill(0,0,w,h,alpha<<24);
        var text=Component.translatable("skill.arkofcosmos.satsuki_rin.fumo_shrine");
        float scale=Math.min(3,(w-32f)/mc.font.width(text));
        gui.pose().pushPose();gui.pose().translate(w/2f,h/2f,0);gui.pose().scale(scale,scale,1);
        int x=-mc.font.width(text)/2,y=-mc.font.lineHeight/2;
        // GR APOLLYON formatting: black fill with the dark-red (0xAA0000) eight-neighbour outline.
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
            gui.drawString(mc.font,text,x+dx,y+dy,(alpha<<24)|0xAA0000,false);
        gui.drawString(mc.font,text,x,y,alpha<<24,false);gui.pose().popPose();
    }
}
