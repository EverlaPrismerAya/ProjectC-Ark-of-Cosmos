package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.ArrayList;

@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class SatsukiDeathBurst {
    private record Burst(Vec3 center,long born){}
    private static final ArrayList<Burst> bursts=new ArrayList<>();
    private static Object world;
    public static void receive(CompoundTag tag){
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(world!=level){bursts.clear();world=level;}
        bursts.add(new Burst(new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z")),level.getGameTime()));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||world!=mc.level){bursts.clear();return;}
        bursts.removeIf(b->mc.level.getGameTime()-b.born()>40);
        if(bursts.isEmpty())return;
        ArkFxDraw.matrix=event.getPoseStack().last().pose();ArkFxDraw.camera=event.getCamera().getPosition();
        var inverse=new Matrix4f(event.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose()).invert();
        var snapshot=java.util.List.copyOf(bursts);float partial=event.getPartialTick();
        Runnable draw=()->{
            for(var burst:snapshot){
                float age=mc.level.getGameTime()-burst.born()+partial;
                SatsukiRefraction.capture();
                var shader=ArkFxDraw.satsukiDeathShader;
                shader.setSampler("SceneSampler",SatsukiRefraction.texture());
                shader.safeGetUniform("InverseViewProjection").set(inverse);
                var center=burst.center().subtract(ArkFxDraw.camera);
                shader.safeGetUniform("Center").set((float)center.x,(float)center.y,(float)center.z);
                shader.safeGetUniform("Age").set(age);
                var main=mc.getMainRenderTarget();shader.safeGetUniform("ScreenSize").set((float)main.width,(float)main.height);
                RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShader(()->shader);
                var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,ArkFxDraw.FORMAT);
                for(float[] p:new float[][]{{-1,-1},{1,-1},{1,1},{-1,1}}){b.vertex(p[0],p[1],0).color(255,255,255,255);b.putFloat(0,0);b.putFloat(4,0);b.putFloat(8,0);b.nextElement();b.endVertex();}
                BufferUploader.drawWithShader(b.end());RenderSystem.enableDepthTest();ArkFxDraw.end();
            }
        };
        if(!OculusShaderCompat.defer(draw,true))draw.run();
    }
}
