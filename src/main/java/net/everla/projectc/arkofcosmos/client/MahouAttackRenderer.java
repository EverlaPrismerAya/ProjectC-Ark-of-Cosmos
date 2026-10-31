package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.everla.projectc.arkofcosmos.entity.MahouAttackEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.MahouAttackEntity.*;

/** Reuses source-faithful weapon textures; lunar geometry/shader is original, not extracted Genshin art. */
public final class MahouAttackRenderer extends EntityRenderer<MahouAttackEntity> {
    private static final AttackFrame GROUND=new AttackFrame(new Vec3(1,0,0),new Vec3(0,0,1));
    public MahouAttackRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(MahouAttackEntity entity){return ArkFxDraw.texture("galaxia/galaxiabolt");}
    @Override public boolean shouldRender(MahouAttackEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(MahouAttackEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var data=e.view();int kind=e.kind();
        double age=data.getInt("Age")+partial,delay=data.getInt("Delay"),life=data.getInt("Life");
        Vec3 p=new Vec3(Mth.lerp(partial,e.xOld,e.getX()),Mth.lerp(partial,e.yOld,e.getY()),Mth.lerp(partial,e.zOld,e.getZ()));
        Vec3 velocity=MahouAttackEntity.velocity(data);
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=p;ArkFxDraw.fadeEye=null;
        float time=(float)((e.level().getGameTime()+partial)/20d),fade=(float)Math.min(1,(life-age)/8);
        if(kind==PILLAR||kind==COVER){
            pose.pushPose();
            if(kind==PILLAR){pose.mulPose(MahouAttackEntity.pillarRotation(velocity));pose.translate(0,-2.5,0);}
            for(int x=0;x<2;x++)for(int y=0;y<5;y++)for(int z=0;z<2;z++){
                pose.pushPose();pose.translate(x-1,y,z-1);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.END_STONE.defaultBlockState(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
            if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
            ArkFxDraw.moon(p.add(0,.06,0),GROUND,1.8,fade*.7f,time,false);
            ArkFxDraw.moon(kind==PILLAR?p:p.add(0,2.5,0),NativeSprites.billboard(),1.65,fade*.65f,time,true);
            if(kind==PILLAR&&age<=delay)
                line(p,p.add(velocity.normalize().scale(16)),.08,new float[]{.65f,.65f,1,.5f});
        }else if(kind==WAVE){
            double radius=MahouAttackEntity.radius(age,(int)delay,(int)life);
            if(age<=delay){
                ArkFxDraw.moon(p,GROUND,26/.79,(float)(.35+.35*age/delay),time,false);
                ArkFxDraw.moon(p.add(0,2.8,0),NativeSprites.billboard(),3,(float)(.6+.3*age/delay),time,true);
                for(int i=0;i<32;i++){
                    double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                    Vec3 edge=p.add(Math.cos(a)*26,.06,Math.sin(a)*26);
                    line(edge,p.add(Math.cos(b)*26,.06,Math.sin(b)*26),.4,new float[]{1,.25f,.3f,.95f});
                    if(i%2==0)line(p,edge,.16,new float[]{1,.3f,.35f,.75f});
                    line(edge,p.add(Math.cos(b)*26,.06,Math.sin(b)*26),.10,new float[]{1,.85f,.75f,1});
                }
            }else{
                // Shader ring sits at .79 of quad radius; physical wave and visible front coincide.
                ArkFxDraw.moon(p,GROUND,radius/.79,fade,time,false);
                ArkFxDraw.moon(p.add(0,.5,0),GROUND,radius/.79,fade*.4f,time+.4f,false);
            }
        }else if(kind==BLOOM||kind==GRAVITY){
            double r=kind==BLOOM?3:6;
            boolean charging=age<=delay;
            float strength=charging?(float)(.25+.45*age/delay):fade;
            ArkFxDraw.moon(p,GROUND,r/.79,strength,time,false);
            if(charging){
                ArkFxDraw.moon(p.add(0,3+Math.sin(time)*.3,0),NativeSprites.billboard(),r*.45,strength,time,true);
            }else{
                for(int i=0;i<6;i++){
                    double angle=i*Math.PI/3+time*.3;
                    Vec3 tip=p.add(Math.cos(angle)*r*.7,0,Math.sin(angle)*r*.7);
                    line(tip.add(0,kind==BLOOM?9:16,0),tip,.45,new float[]{.65f,.65f,1,fade});
                    ArkFxDraw.moon(p.add(0,i*.75,0),GROUND,r*(1-i*.1)/.79,fade*.55f,time+i*.2f,false);
                }
            }
        }else if(kind==VOID_VORTEX){
            AttackFrame plane=NativeSprites.billboard();
            float pulse=(float)(.8+.2*Math.sin(time*1.7));
            ArkFxDraw.moon(p,plane,8.5,pulse*.55f,time,false);
            ArkFxDraw.moon(p,plane,6.2,pulse*.75f,-time*1.6f,true);
            ArkFxDraw.moon(p,plane,3.8,pulse,time*2.2f,false);
        }else if(kind==DOMAIN_TEXT){
            AttackFrame plane=NativeSprites.billboard();
            ArkFxDraw.moon(p,plane,1.2,fade,time,false);
            line(p,p.add(velocity.normalize().scale(3)),.06,new float[]{.45f,.8f,1,fade});
        }else if(kind==DOMAIN){
            Vec3 right=new Vec3(-velocity.z,0,velocity.x).normalize();
            if(right.lengthSqr()<.1)right=new Vec3(1,0,0);
            AttackFrame plane=new AttackFrame(right,new Vec3(0,1,0));
            ArkFxDraw.moon(p.add(0,5,0),plane,12,fade*.55f,time,false);
        }else if(kind==NEEDLE_GATE){
            ArkFxDraw.moon(p,NativeSprites.billboard(),.9,fade,time,false);
            if(age<=delay){
                ArkFxDraw.centered("elements/solar_needle",p,NativeSprites.billboard(),time,.8,.8,new float[]{1,.7f,.3f,fade},ArkFxDraw.Blend.ADDITIVE);
                if(age>=delay-3)line(p,p.add(velocity),.10,new float[]{1,.35f,.12f,.85f});
            }
        }else if(kind==CASTING){
            Vec3 direction=velocity.normalize();
            AttackFrame aimed=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(direction,new Vec3(1,0,0));
            AttackFrame plane=new AttackFrame(aimed.right(),aimed.normal());
            double charge=Math.min(1,age/Math.max(1,delay)),radius=2+charge*1.5;
            ArkFxDraw.moon(p,plane,radius,(float)(.6+.4*charge),time,false);
            ArkFxDraw.moon(p.add(direction.scale(.15)),plane,radius*.62,.85f,-time*1.4f,false);
            float[] blue={.55f,.75f,1,.85f};
            for(int i=0;i<8;i++){
                double angle=i*Math.PI/4+time;
                Vec3 at=p.add(plane.direction(angle).scale(radius*.79));
                Vec3 next=p.add(plane.direction(angle+Math.PI/2).scale(radius*.79));
                line(at,next,.08,blue);
                ArkFxDraw.centered("galaxia/sparkle",at,plane,time,1,1,blue,ArkFxDraw.Blend.ADDITIVE);
            }
            ArkFxDraw.centered("galaxia/sparkle",p,plane,-time,2+charge,2+charge,new float[]{.8f,.9f,1,.9f},ArkFxDraw.Blend.ADDITIVE);
            line(p,p.add(direction.scale(32)),.08,new float[]{.5f,.65f,1,.45f});
        }else if(kind==RAIN){
            float[] color=data.getInt("SwingMode")==0?new float[]{.35f,.6f,1,.85f}:new float[]{1,.4f,.08f,.85f};
            double radius=1.35;
            for(int i=0;i<32;i++){
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                line(p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),p.add(Math.cos(b)*radius,0,Math.sin(b)*radius),.16,color);
            }
            line(p.add(-1,0,0),p.add(1,0,0),.08,color);line(p.add(0,0,-1),p.add(0,0,1),.08,color);
            if(age<=delay)line(p,p.add(0,14,0),.035,color);
        }else if(kind==SWING_WARNING){
            Vec3 forward=velocity.multiply(1,0,1).normalize();
            if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);
            Vec3 right=new Vec3(-forward.z,0,forward.x);
            double start=data.getInt("SwingMode")==0?-Math.PI*3/8:-Math.PI*5/8;
            double end=start+Math.PI*3/4;
            float[] red={1,.12f,.04f,(float)(.75+.2*Math.sin(age*.4)*Math.sin(age*.4))};
            double radius=19;
            Vec3 previous=p.add(forward.scale(Math.cos(start)*radius)).add(right.scale(Math.sin(start)*radius));
            line(p,previous,.35,red);
            for(int i=1;i<=32;i++){
                double angle=start+(end-start)*i/32;
                Vec3 next=p.add(forward.scale(Math.cos(angle)*radius)).add(right.scale(Math.sin(angle)*radius));
                line(previous,next,.35,red);
                line(previous,next,.10,new float[]{1,.8f,.6f,1});
                line(p,next,.16,new float[]{1,.08f,.02f,.55f});
                previous=next;
            }
            line(p,previous,.35,red);
        }else if(kind==WARNING){
            line(p,p.add(velocity),.32,new float[]{.25f,.65f,1,.75f});
            line(p,p.add(velocity),.10,new float[]{1,1,1,1});
            ArkFxDraw.centered("galaxia/sparkle",p.add(velocity),NativeSprites.billboard(),time,1.3,1.3,new float[]{.65f,.65f,1,.85f},ArkFxDraw.Blend.ADDITIVE);
            ArkFxDraw.centered("galaxia/sparkle",p.add(velocity),NativeSprites.billboard(),time+Math.PI/4,.8,.8,new float[]{1,1,1,1},ArkFxDraw.Blend.ADDITIVE);
        }
    }

    private static void line(Vec3 a,Vec3 b,double width,float[] color){
        Vec3 direction=b.subtract(a);if(direction.lengthSqr()<1e-8)return;
        Vec3 side=NativeSprites.billboard().normal().cross(direction).normalize();if(side.lengthSqr()<.1)side=NativeSprites.billboard().forward();
        AttackFrame f=new AttackFrame(side,direction.normalize());int[] size=ArkFxDraw.size("galaxia/bloomline");
        ArkFxDraw.sprite("galaxia/bloomline",a,f,0,width,direction.length()*16/size[1],size[0]/2d,0,color,ArkFxDraw.Blend.ADDITIVE);
    }
}
