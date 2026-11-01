package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;

import net.everla.projectc.arkofcosmos.entity.MahouAttackEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** World-only domain presentation; no screen/UI renderer is modified. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class DomainEffects {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void fog(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor event){
        if(inside){event.setRed(0);event.setGreen(0);event.setBlue(0);}
    }
    public static net.minecraft.client.renderer.MultiBufferSource blackBlocks(net.minecraft.client.renderer.MultiBufferSource buffers){
        if(!inside)return buffers;
        return type->new BlackVertex(buffers.getBuffer(type));
    }
    private record BlackVertex(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){delegate.color(0,0,0,a);return this;}
        public VertexConsumer uv(float u,float v){delegate.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){delegate.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){delegate.uv2(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        public void endVertex(){delegate.endVertex();}
        public void defaultColor(int r,int g,int b,int a){delegate.defaultColor(0,0,0,a);}
        public void unsetDefaultColor(){delegate.unsetDefaultColor();}
    }
    private static PostChain chain;
    private static boolean failed;
    private static boolean inside;
    public static boolean inside(){return inside;}
    public static void beginFrame(){
        var mc=Minecraft.getInstance();
        inside=false;
        if(mc.level==null)return;
        Vec3 eye=mc.gameRenderer.getMainCamera().getPosition();
        for(var entity:mc.level.entitiesForRendering())
            if(entity instanceof MahouAttackEntity attack&&attack.kind()==MahouAttackEntity.DOMAIN
                    &&!attack.isRemoved()&&attack.distanceToSqr(eye)<32*32){inside=true;break;}
    }
    public static void reset(){if(chain!=null)chain.close();chain=null;failed=false;inside=false;}
    public static void grayscale(float partial){
        if(!inside||failed)return;
        var mc=Minecraft.getInstance();
        try{
            if(chain==null){
                chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),
                        ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"shaders/post/domain.json"));
                chain.resize(mc.getWindow().getWidth(),mc.getWindow().getHeight());
            }
            var swap=chain.getTempTarget("swap");
            if(swap.width!=mc.getWindow().getWidth()||swap.height!=mc.getWindow().getHeight())
                chain.resize(mc.getWindow().getWidth(),mc.getWindow().getHeight());
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            chain.process(partial);
        }catch(java.io.IOException|com.google.gson.JsonSyntaxException error){
            failed=true;com.mojang.logging.LogUtils.getLogger().error("Cannot load domain shader",error);
        }finally{
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.defaultBlendFunc();
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void render(net.minecraftforge.client.event.RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        boolean background=event.getStage()==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY;
        boolean terrain=event.getStage()==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if(!background&&!terrain)return;
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof MahouAttackEntity attack)||attack.kind()!=MahouAttackEntity.DOMAIN||attack.isRemoved())continue;
            ArkFxDraw.matrix=event.getPoseStack().last().pose();
            ArkFxDraw.camera=event.getCamera().getPosition();
            if(background)sphere(attack.position(),32);
            else DomainTerrain.render(attack.position());
        }
    }
    public static void sphere(Vec3 center,double radius){

        boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        var shader=RenderSystem.getShader();
        float[] color=RenderSystem.getShaderColor().clone();
        // A visual boundary must not become an opaque depth occluder for entities or shields.
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{
            var buffer=Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int latitude=0;latitude<24;latitude++)for(int longitude=0;longitude<48;longitude++){
                patch(buffer,center,radius,latitude,longitude,latitude+1,longitude+1);

            }
            BufferUploader.drawWithShader(buffer.end());
        }finally{
            RenderSystem.depthMask(depthWrite);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShader(()->shader);
        }
    }
    private static void patch(BufferBuilder buffer,Vec3 center,double radius,double lat0,double lon0,double lat1,double lon1){
        point(buffer,center,radius,lat0,lon0);
        point(buffer,center,radius,lat1,lon0);
        point(buffer,center,radius,lat1,lon1);
        point(buffer,center,radius,lat0,lon1);
    }
    private static void point(BufferBuilder buffer,Vec3 center,double radius,double latitude,double longitude){
        double a=Math.PI*latitude/24,b=Math.PI*2*longitude/48;
        Vec3 p=center.add(radius*Math.sin(a)*Math.cos(b),radius*Math.cos(a),radius*Math.sin(a)*Math.sin(b))
                .subtract(ArkFxDraw.camera);
        buffer.vertex(ArkFxDraw.matrix,(float)p.x,(float)p.y,(float)p.z)
                .color(0,0,0,255).endVertex();
    }
}
