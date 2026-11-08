package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.GlStateManager;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.*;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.everla.projectc.arkofcosmos.entity.OldOneEntity.Kind.*;

/** Legacy SpriteBatch draws with XNA's premultiplied blend, using built-in Minecraft shaders. */
public final class OldOneRenderer extends EntityRenderer<OldOneEntity> {
    public OldOneRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(OldOneEntity e){return NativeSprites.texture(texture(e.kind()));}
    @Override public boolean shouldRender(OldOneEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    public static String texture(OldOneEntity.Kind k){return switch(k){case SWORD->"item/ark_of_the_old_one";case EON->"old_one/eon";case EON_V2->"old_one/eon_v2";case EON_V3->"old_one/eon_v3";case EON_V4->"old_one/eon_v4";case ORB->"old_one/orb";case ASTRAL_STAR->"old_one/astral_star";default->"old_one/projectile_"+k.nativeId;};}
    @Override public void render(OldOneEntity e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        // Flush after the arena composite, retaining the dispatcher's entity-local transform.
        PoseStack captured=new PoseStack();
        captured.last().pose().set(poses.last().pose());
        captured.last().normal().set(poses.last().normal());
        if(net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat.defer(()->{
            var deferred=MultiBufferSource.immediate(new BufferBuilder(256));
            render(e,yaw,partial,captured,deferred,light);
            deferred.endBatch();
        },true))return;
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        OldOneEntity.Kind k=e.kind();Vec3 at=ElementsEntity.vector(s,"Position"),velocity=ElementsEntity.vector(s,"Velocity");
        AttackFrame f=new AttackFrame(ElementsEntity.vector(s,"F"),ElementsEntity.vector(s,"R"));
        if(f.forward().lengthSqr()<.5)return;
        // Position passed by the entity dispatcher is interpolated independently of the source trajectory.
        Vec3 base=new Vec3(net.minecraft.util.Mth.lerp(partial,e.xo,e.getX()),net.minecraft.util.Mth.lerp(partial,e.yo,e.getY()),net.minecraft.util.Mth.lerp(partial,e.zo,e.getZ()));
        double rotation=s.getDouble("Rotation"),scale=s.getDouble("Scale"),alpha=s.getDouble("Alpha");
        String name=texture(k);int frames=switch(k){case FLOWER_POW,SOLAR_EXPLOSION->5;case STARDUST_CELL,NEBULA->4;default->1;};
        int frame=Math.min(frames-1,s.getInt("Frame"));float opacity=(float)(1-alpha/255);
        float[] color={opacity,opacity,opacity,opacity};
        if(k==SWORD){
            double sourceAge=(e.level().getGameTime()-s.getLong("StartTick")+partial)*3;
            if(sourceAge>=15)return;
            rotation=OldOneEntity.swordAngle(sourceAge);
            var owner=e.level().getEntity(e.ownerId());
            if(owner instanceof net.minecraft.world.entity.player.Player p)at=p.getPosition(partial).add(0,p.getBbHeight()*.6,0);
            new NativeSprites.Draw(poses,buffers,base,e.ownerId(),true).sprite(name,at,f,rotation+Math.PI/4,1,1,0,NativeSprites.height(name),new float[]{1,1,1,1},false,true,light,0,true);return;
        }
        boolean billboard=k!=EON&&k!=EON_V2&&k!=EON_V3&&k!=EON_V4;
        if(billboard)f=NativeSprites.billboard();
        if(k==EON||k==EON_V2||k==EON_V3||k==EON_V4||k==ORB||k==ASTRAL_STAR){
            int[] disco=OldOneParticles.disco();
            color=switch(k){case EON->rgba(disco[0],100,255,alpha);case EON_V2->rgba(53,disco[1],255,alpha);case EON_V3->rgba(disco[0],203,103,alpha);case EON_V4->rgba(255,disco[1],53,alpha);case ASTRAL_STAR->rgba(200,100,250,alpha);default->rgba(disco[0],disco[1],disco[2],alpha);};
        }
        if(k==NEBULA)color=new float[]{opacity,opacity,opacity,opacity*127/255};
        if(k==SOLAR_EXPLOSION)color=rgba(255,255,255,127);
        if(k==TERRARIAN||k==RAINBOW_CRYSTAL)color=new float[]{opacity,opacity,opacity,0};
        if(k==ORB||k==ASTRAL_STAR){
            ListTag history=s.getList("Trail",10);int length=k==ORB?6:10;float fade=1;
            for(int i=0;i<history.size();i+=(k==ASTRAL_STAR?2:1)){
                if(k==ORB)fade=(length-i)/(float)length;else if(i>0)fade*=(length-i)/(length*1.5f);
                sprite(poses,buffers,base,name,ElementsEntity.vector(history.getCompound(i),"Position"),f,rotation,scale,scale,frames,frame,multiply(color,fade));
            }
            return;
        }
        if(k==RAINBOW_CRYSTAL){
            float[] red={1,0,0,0};sprite(poses,buffers,base,name,at,f,rotation,scale*2,scale*2,1,0,red);sprite(poses,buffers,base,name,at,f,0,scale*2,scale*2,1,0,red);
            var parent=e.level().getEntity(s.getInt("Parent"));
            if(parent!=null&&opacity>.3){Vec3 delta=parent.position().subtract(at);double fade=Math.max(0,Math.min(1,Math.abs(30-s.getDouble("Age"))/20));
                AttackFrame line=flight(delta,f);double sy=delta.length()*16/NativeSprites.height(name);
                sprite(poses,buffers,base,name,at.add(delta.scale(.5)),line,Math.PI/2,1,sy,1,0,multiply(red,(float)fade));
                sprite(poses,buffers,base,name,at.add(delta.scale(.5)),line,Math.PI/2,.5,sy*.5,1,0,multiply(color,(float)fade));}
            sprite(poses,buffers,base,name,at,f,rotation,scale,scale,1,0,color);sprite(poses,buffers,base,name,at,f,0,scale,scale,1,0,color);return;
        }
        if(!billboard&&velocity.lengthSqr()>1e-10){
            // Preserve source sprite rotation in the flight plane after three-dimensional homing.
            AttackFrame flight=flight(velocity,f);rotation-=f.angle(velocity);f=flight;
        }
        if(!billboard){beam(poses,buffers,base,name,at,f,rotation,scale,color);return;}
        if(k==HOLY_ARROW)for(int i=0;i<4;i++)region(poses,buffers,base,"old_one/extra_75",at.add(f.direction(rotation+i*Math.PI/2).scale(.25)),f,rotation,scale,scale,0,0,NativeSprites.width(name),NativeSprites.height(name),rgba(120,40,222,120));
        double sx=k==NEBULA&&velocity.dot(ElementsEntity.vector(s,"F"))<0?-scale:scale;
        sprite(poses,buffers,base,name,at,f,rotation,sx,scale,frames,frame,color);
        if(k==STARDUST_CELL)sprite(poses,buffers,base,"old_one/glow_190",at,f,rotation,scale,scale,4,frame,new float[]{(128-(int)alpha/2)/255f,(128-(int)alpha/2)/255f,(128-(int)alpha/2)/255f,0});
    }
    private static AttackFrame flight(Vec3 direction,AttackFrame original){Vec3 forward=direction.normalize(),side=original.normal().cross(forward).normalize();if(side.lengthSqr()<.5)side=original.right();return new AttackFrame(forward,side);}
    private static float[] rgba(double r,double g,double b,double a){return new float[]{(float)r/255,(float)g/255,(float)b/255,(float)a/255};}
    private static float[] multiply(float[] c,float f){return new float[]{c[0]*f,c[1]*f,c[2]*f,c[3]*f};}
    /** Three source pixels of extrusion, closed along the original texture's opaque contour. */
    private static void beam(PoseStack pose,MultiBufferSource buffers,Vec3 base,String name,Vec3 at,AttackFrame plane,double angle,double scale,float[] color){
        int width=NativeSprites.width(name),height=NativeSprites.height(name);
        Vec3 x=plane.direction(angle).scale(scale/16),y=plane.direction(angle+Math.PI/2).scale(scale/16),n=plane.normal(),depth=n.scale(1.5*scale/16);
        VertexConsumer out=buffers.getBuffer(SourceBlend.type(NativeSprites.texture(name),true));
        for(int side:new int[]{-1,1})for(int i=0;i<4;i++){
            int corner=side>0?3-i:i,u=corner<2?0:1,v=corner==0||corner==3?0:1;
            vertex(out,pose,base,at.add(x.scale((u-.5)*width)).add(y.scale((v-.5)*height)).add(depth.scale(side)),n.scale(side),u,v,color);
        }
        for(int[] edge:NativeSprites.contour(name)){
            int px=edge[0],py=edge[1],side=edge[2];double ax=px,ay=py,bx=px,by=py;
            if(side<2){ax+=side;bx=ax;by++;}else{ay+=side-2;by=ay;bx++;}
            Vec3 a=at.add(x.scale(ax-width/2d)).add(y.scale(ay-height/2d)),b=at.add(x.scale(bx-width/2d)).add(y.scale(by-height/2d));
            Vec3 normal=(side<2?x:y).normalize().scale(side==0||side==2?-1:1);float u=(px+.5f)/width,v=(py+.5f)/height;
            if(b.subtract(a).cross(depth.scale(-2)).dot(normal)<0){Vec3 swap=a;a=b;b=swap;}
            vertex(out,pose,base,a.add(depth),normal,u,v,color);vertex(out,pose,base,b.add(depth),normal,u,v,color);
            vertex(out,pose,base,b.subtract(depth),normal,u,v,color);vertex(out,pose,base,a.subtract(depth),normal,u,v,color);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack pose,Vec3 base,Vec3 at,Vec3 normal,float u,float v,float[] color){
        Vec3 local=at.subtract(base);out.vertex(pose.last().pose(),(float)local.x,(float)local.y,(float)local.z).color(color[0],color[1],color[2],color[3]).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    static void sprite(PoseStack pose,MultiBufferSource buffers,Vec3 base,String name,Vec3 at,AttackFrame plane,double angle,double sx,double sy,int frames,int frame,float[] color){
        int width=NativeSprites.width(name),height=NativeSprites.height(name)/frames;
        region(pose,buffers,base,name,at,plane,angle,sx,sy,0,frame*height,width,height,color);
    }
    static void region(PoseStack pose,MultiBufferSource buffers,Vec3 base,String name,Vec3 at,AttackFrame plane,double angle,double sx,double sy,int tx,int ty,int width,int height,float[] color){
        Vec3 x=plane.direction(angle).scale(sx/16),y=plane.direction(angle+Math.PI/2).scale(sy/16),n=plane.normal();
        VertexConsumer out=buffers.getBuffer(SourceBlend.type(NativeSprites.texture(name)));
        for(int i=0;i<4;i++){
            int u=i<2?0:1,v=i==0||i==3?0:1;Vec3 local=at.add(x.scale((u-.5)*width)).add(y.scale((v-.5)*height)).subtract(base);
            out.vertex(pose.last().pose(),(float)local.x,(float)local.y,(float)local.z).color(color[0],color[1],color[2],color[3]).uv((tx+u*width)/(float)NativeSprites.width(name),(ty+v*height)/(float)NativeSprites.height(name))
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        }
    }
    private static final class SourceBlend extends RenderType {
        private SourceBlend(){super("old_one_xna",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
        private static final Map<ResourceLocation,RenderType> SOLID_TYPES=new HashMap<>();
        private static final TransparencyStateShard XNA=new TransparencyStateShard("xna_premultiplied",()->{RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        static RenderType type(ResourceLocation texture){return type(texture,false);}
        static RenderType type(ResourceLocation texture,boolean solid){return (solid?SOLID_TYPES:TYPES).computeIfAbsent(texture,t->create("old_one_xna",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(XNA).setCullState(solid?CULL:NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    }
}
