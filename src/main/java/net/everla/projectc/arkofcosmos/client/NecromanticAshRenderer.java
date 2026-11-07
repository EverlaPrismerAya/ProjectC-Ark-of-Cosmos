package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity.lerpValue;

/** Samples the actual animated model's textured surface, rather than its bounding box. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class NecromanticAshRenderer {
    private static final Set<UUID> hidden=new HashSet<>();
    private static final List<Ash> ashes=new ArrayList<>();
    private static final Random random=new Random();
    private static boolean capturing;
    private static class Ash {
        Vec3 at,velocity;final int lifetime=105+random.nextInt(40),id;final float brightness;double scale=.7+random.nextDouble()*.6;int age;
        Ash(Vec3 at,Vec3 velocity,float brightness,int id){this.at=at;this.velocity=velocity;this.brightness=brightness*.67f;this.id=id;}
        void step(double time){
            age++;scale=Math.max(0,Math.min(1,scale-(brightness<.1?.08:.008)));
            double dissipate=lerpValue(6,16,velocity.length()*16),mix=lerpValue(25-dissipate*10,80-dissipate*45,age)*.16;
            // Minecraft has no ambient wind; retain the source's zero-wind vertical oscillation.
            velocity=velocity.lerp(new Vec3(0,(1-Math.sin(time/20+id*.01)*3)/16,0),mix);at=at.add(velocity);
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void request(int id,UUID uuid){
        var mc=Minecraft.getInstance();if(mc.level==null)return;var target=mc.level.getEntity(id);
        if(target instanceof net.minecraft.world.entity.LivingEntity living&&target.getUUID().equals(uuid)&&!hidden.contains(uuid)&&mc.getEntityRenderDispatcher().getRenderer(living) instanceof LivingEntityRenderer renderer)captureModel(living,renderer,mc.getFrameTime());
    }
    public static void clear(){hidden.clear();ashes.clear();}
    public static boolean empty(){return ashes.isEmpty();}
    public static void tick(){
        var level=Minecraft.getInstance().level;
        Set<UUID> loaded=new HashSet<>();for(var entity:level.entitiesForRendering())loaded.add(entity.getUUID());hidden.retainAll(loaded);
        for(int step=0;step<3;step++)for(var ash:ashes)ash.step(level.getGameTime()*3+step);
        ashes.removeIf(a->a.age>=a.lifetime);
    }
    @SubscribeEvent public static void capture(RenderLivingEvent.Pre<?,?> event){if(!capturing&&hidden.contains(event.getEntity().getUUID()))event.setCanceled(true);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void captureModel(net.minecraft.world.entity.LivingEntity entity,LivingEntityRenderer renderer,float partial){
        UUID uuid=entity.getUUID();var texture=renderer.getTextureLocation(entity);int before=ashes.size();
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture).open();var image=NativeImage.read(stream)){
            capturing=true;Map<Object,Surface> surfaces=new HashMap<>();
            // Capture immediately on the death packet, before ArkDamage's removal packet.
            renderer.render(entity,entity.getYRot(),partial,new PoseStack(),type->surfaces.computeIfAbsent(type,k->new Surface(entity.getPosition(partial),entity.getDeltaMovement().scale(.7/3),image,type.toString().contains(texture.toString()))),15728880);
            if(ashes.size()>before)hidden.add(uuid);
        }catch(java.io.IOException|RuntimeException error){org.slf4j.LoggerFactory.getLogger(NecromanticAshRenderer.class).warn("Unable to capture necromantic ash surface for {}",entity.getType(),error);}
        finally{capturing=false;}
    }
    private record Point(Vec3 at,float u,float v) {}
    private static final class Surface implements VertexConsumer {
        private final Vec3 origin,velocity;private final NativeImage image;private final boolean accept;
        private final List<Point> quad=new ArrayList<>(4);private Vec3 at;private float u,v;
        Surface(Vec3 origin,Vec3 velocity,NativeImage image,boolean accept){this.origin=origin;this.velocity=velocity;this.image=image;this.accept=accept;}
        public VertexConsumer vertex(double x,double y,double z){at=new Vec3(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
        public void endVertex(){if(!accept)return;quad.add(new Point(at,u,v));if(quad.size()==4){sample();quad.clear();}}
        private void sample(){
            Point a=quad.get(0),b=quad.get(1),d=quad.get(3);
            int columns=Math.max(1,(int)Math.ceil(a.at.distanceTo(d.at)*16)),rows=Math.max(1,(int)Math.ceil(a.at.distanceTo(b.at)*16));
            for(int x=0;x<columns;x++)for(int y=0;y<rows;y++){
                if(ashes.size()>=45000)return;
                double s=(x+.5)/columns,t=(y+.5)/rows;
                float u=(float)(a.u+(d.u-a.u)*s+(b.u-a.u)*t),v=(float)(a.v+(d.v-a.v)*s+(b.v-a.v)*t);
                int px=Math.max(0,Math.min(image.getWidth()-1,(int)(u*image.getWidth()))),py=Math.max(0,Math.min(image.getHeight()-1,(int)(v*image.getHeight())));
                int color=image.getPixelRGBA(px,py);float brightness=((color&255)+(color>>>8&255)+(color>>>16&255))/765f;
                if((color>>>24)==0||brightness<=.05)continue;
                Vec3 point=origin.add(a.at).add(d.at.subtract(a.at).scale(s)).add(b.at.subtract(a.at).scale(t));
                ashes.add(new Ash(point,velocity,brightness,ashes.size()));
            }
        }
    }
    public static void draw(float partial){
        if(ashes.isEmpty())return;
        ArkFxDraw.begin("necro_circle",ArkFxDraw.Blend.ALPHA,false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try{
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var face=NativeSprites.billboard();
            for(var ash:ashes){Vec3 at=ash.at.add(ash.velocity.scale(partial)).subtract(ArkFxDraw.camera),x=face.right().scale(ash.scale*3.5/16),y=face.forward().scale(ash.scale*3.5/16);float c=(float)(ash.brightness*ash.scale),alpha=(float)ash.scale;
                vertex(out,at.subtract(x).subtract(y),c,alpha);vertex(out,at.subtract(x).add(y),c,alpha);vertex(out,at.add(x).add(y),c,alpha);vertex(out,at.add(x).subtract(y),c,alpha);
            }BufferUploader.drawWithShader(out.end());
        }finally{ArkFxDraw.end();}
    }
    private static void vertex(BufferBuilder out,Vec3 at,float color,float alpha){out.vertex(ArkFxDraw.matrix,(float)at.x,(float)at.y,(float)at.z).color(color,color,color,alpha).endVertex();}
}
