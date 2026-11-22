package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.entity.MahouInfernumAttackEntity;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.everla.projectc.arkofcosmos.calamity.client.GalaxiaRenderer;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Weapon sprites and materials applied to authoritative Infernum trajectories. */
public final class MahouInfernumAttackRenderer extends EntityRenderer<MahouInfernumAttackEntity> {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static final AttackFrame[] BLAST_RINGS={new AttackFrame(X,Z),new AttackFrame(X,Y),new AttackFrame(Z,Y)};
    public MahouInfernumAttackRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(MahouInfernumAttackEntity e){return ArkFxDraw.texture("galaxia_bolt");}
    @Override public boolean shouldRender(MahouInfernumAttackEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(MahouInfernumAttackEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var s=e.view();if(s.isEmpty())return;var kind=e.kind();double pixels=s.getDouble("Pixels");if(pixels<=0)return;
        Vec3 at=e.getPosition(partial),v=MahouInfernumAttackEntity.vector(s,"Velocity");
        AttackFrame face=NativeSprites.billboard();
        double age=s.getInt("Age")+partial*3,time=ArkFxDraw.time();
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=at;ArkFxDraw.fadeEye=null;
        float opacity=s.contains("Opacity")?s.getFloat("Opacity"):1;float[] white=ArkEffects.mul(ArkEffects.WHITE,opacity),cornflower=ArkEffects.mul(ArkEffects.CORNFLOWER,opacity);
        switch(kind){
            case WARNING,BOMB_WARNING->{Vec3 end=at.add(v.normalize().scale((s.getInt("Parameter")==MahouInfernumAttackEntity.OVERLOAD_WARNING?3950:3330)/pixels));beam(at,end,Math.min(.12,age/120),ArkEffects.rgb(150,200,255));}
            case PILLAR_WARNING->{var shape=MahouInfernumAttackEntity.pillarShape(52.5,pixels);beam(at.add(0,shape.bottom()-shape.radius(),0),at.add(0,shape.top()+shape.radius(),0),Math.min(.12,age/120),ArkEffects.rgb(150,200,255));}
            case PILLAR->{
                // Do not predict shrinkage ahead of the authoritative damage volume.
                var shape=MahouInfernumAttackEntity.pillarShape(s.getInt("Age"),pixels);
                NecromanticAttackRenderer.pillar(at.add(0,shape.bottom(),0),shape.top()-shape.bottom(),shape.radius());
            }
            case JEWEL_BEAM,OVERLOAD_BEAM->{double length=s.getDouble("BeamLength");attackBeam(at,at.add(v.scale(length)),kind==MahouInfernumAttackEntity.Kind.JEWEL_BEAM?10/pixels*Math.min(2,1+age*.15):30/pixels*Math.min(2-(2-.051)*Math.max(0,Math.min(1,(age-390)/60d)),.05+age*.04));}
            case RITUAL->{double scale=Math.sin(Math.min(1,age/84)*Math.PI)*2;Vec3 plane=MahouInfernumAttackEntity.vector(s,"Plane");AttackFrame ritual=new AttackFrame(plane,new Vec3(0,-1,0));sprite("elements/elemental_glass_star",at,ritual,time,scale,scale,white,false);sprite("elements/techy_holosquare",at,ritual,-time,scale,scale,cornflower,true);}
            case EXPLOSION,ORB->{double radius=s.getDouble("Radius"),scale=radius*32/ArkFxDraw.size("bloomcircle")[0];sprite("bloomcircle",at,face,0,scale,scale,ArkEffects.mul(cornflower,.55),true);sprite("sparkle",at,face,time*3,scale*.5,scale*.5,white,true);}
            case HEART_STAR,BOMB->{
                if(kind==MahouInfernumAttackEntity.Kind.BOMB&&s.getInt("Left")<=60)blastWarning(s);
                double scale=kind==MahouInfernumAttackEntity.Kind.HEART_STAR?.6:1.2;
                sprite("elements/elemental_glass_star",at,face,time*3,scale,scale,white,false);
                sprite("fx/sparkle",at,face,time*8,scale,scale,cornflower,true);
            }
            case GIGA,SOUL_BOMB->{AttackFrame f=GalaxiaRenderer.lineFrame(v,face);double scale=kind==MahouInfernumAttackEntity.Kind.SOUL_BOMB?Math.max(.3,s.getDouble("Radius")*.25):1.2;sprite("fx/galaxia_bolt",at,f,0,scale,scale,white,false);sprite("fx/sparkle",at,face,time*3,scale,scale,cornflower,true);}
            case SHADOW_GIGA,SHADOW_BLAST,SHADOW_BOLT,SHADOW_SPARK->{
                AttackFrame frame=GalaxiaRenderer.lineFrame(v,face);
                double scale=kind==MahouInfernumAttackEntity.Kind.SHADOW_GIGA||kind==MahouInfernumAttackEntity.Kind.SHADOW_BLAST?1.2:.6;
                var samples=s.getList("Samples",10);
                galaxyShot(at,frame,scale,samples,time,opacity);
            }
            case ARROW,FLAME,REDIRECT_HELLFIRE->{AttackFrame f=GalaxiaRenderer.lineFrame(v,face);sprite("elements/solar_needle",at,f,0,.6,.6,white,false);}
            case SLASH,FIST->{
                Vec3 plane=MahouInfernumAttackEntity.vector(s,"Plane");
                AttackFrame f=new AttackFrame(plane,new Vec3(0,-1,0));
                String blade=kind==MahouInfernumAttackEntity.Kind.SLASH?"fx/sunderingscissorsleft":"fx/sunderingscissorsright";
                double angle=f.angle(v)+Math.PI/4;
                sprite(blade,at,f,angle,.35,.35,white,false);
                sprite(blade+"glow",at,f,angle,.35,.35,cornflower,true);
            }
            default->{
                AttackFrame f=GalaxiaRenderer.lineFrame(v,face);int index=0;
                var samples=s.getList("Samples",10);for(int i=samples.size()-1;i>=0&&index<2;i--,index++)sprite("fx/galaxia_bolt",MahouInfernumAttackEntity.vector(samples.getCompound(i),"Position"),f,0,.6,.6,ArkEffects.mul(white,(2-index)/2d),false);
                if(index==0)sprite("fx/galaxia_bolt",at,f,0,.6,.6,white,false);
            }
        }
    }
    private static void blastWarning(net.minecraft.nbt.CompoundTag state){
        double radius=state.getDouble("BlastRadius");if(radius<=0)return;
        Vec3 center=MahouInfernumAttackEntity.vector(state,"Detonation");
        String texture="hollowcirclehardedge";
        int[] size=ArkFxDraw.size(texture);
        float[] color=ArkEffects.mul(ArkEffects.rgb(255,120,70),.4+.6*(1-state.getInt("Left")/60d));
        for(var frame:BLAST_RINGS)sprite(texture,center,frame,0,radius*32/size[0],radius*32/size[1],color,true);
    }
    /** Draw over the arena backdrop, with scene depth testing and no depth writes. */
    private static void sprite(String name,Vec3 at,AttackFrame frame,double angle,double sx,double sy,float[] color,boolean additive){
        if(OculusShaderCompat.defer(()->sprite(name,at,frame,angle,sx,sy,color,additive),true))return;
        ArkFxDraw.centered(name,at,frame,angle,sx,sy,color,additive?ArkFxDraw.Blend.ADDITIVE:ArkFxDraw.Blend.ALPHA);
    }
    private static void galaxyShot(Vec3 at,AttackFrame frame,double scale,net.minecraft.nbt.ListTag samples,double time,float opacity){
        if(OculusShaderCompat.defer(()->galaxyShot(at,frame,scale,samples,time,opacity),true))return;
        int drawn=0;
        for(int i=samples.size()-1;i>=0&&drawn<2;i--,drawn++)
            ArkFxDraw.centered("galaxia_bolt",MahouInfernumAttackEntity.vector(samples.getCompound(i),"Position"),frame,0,scale,scale,ArkEffects.mul(ArkEffects.WHITE,opacity*(2-drawn)/2d),ArkFxDraw.Blend.ALPHA);
        if(drawn==0)ArkFxDraw.centered("galaxia_bolt",at,frame,0,scale,scale,ArkEffects.mul(ArkEffects.WHITE,opacity),ArkFxDraw.Blend.ALPHA);
        ArkFxDraw.centered("sparkle",at,NativeSprites.billboard(),time*3,scale,scale,ArkEffects.mul(ArkEffects.CORNFLOWER,opacity),ArkFxDraw.Blend.ADDITIVE);
    }
    /** Same bloomline/thinendedline materials as the native Ark snap and Galaxia slash. */
    private static void attackBeam(Vec3 at,Vec3 end,double radius){
        Vec3 delta=end.subtract(at);double length=delta.length();
        if(length>1e-6&&radius>0)NecromanticAttackRenderer.beam(at,delta.scale(1/length),length,radius);
    }
    private static void beam(Vec3 at,Vec3 end,double radius,float[] color){
        Vec3 delta=end.subtract(at);if(delta.lengthSqr()<1e-9)return;
        if(OculusShaderCompat.defer(()->beam(at,end,radius,color),true))return;
        AttackFrame frame=GalaxiaRenderer.lineFrame(delta,NativeSprites.billboard());
        int[] dimensions=ArkFxDraw.size("thinendedline");
        ArkFxDraw.sprite("thinendedline",at,frame,Math.PI,Math.max(.01,radius)*32/dimensions[0],delta.length()*16/dimensions[1],dimensions[0]/2d,dimensions[1],color,ArkFxDraw.Blend.ADDITIVE);
    }
}


