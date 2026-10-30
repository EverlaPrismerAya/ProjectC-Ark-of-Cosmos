package net.everla.projectc.arkofcosmos.calamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.GalaxiaEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.*;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;
import static net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.Blend.*;

/** Galaxia projectile PreDraw ports. Particle CustomDraw lives in ArkFxParticle. */
public final class GalaxiaRenderer extends EntityRenderer<GalaxiaEntity> {
    public GalaxiaRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(GalaxiaEntity e){return ArkFxDraw.texture("galaxia/galaxiaextra2");}
    @Override public boolean shouldRender(GalaxiaEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(GalaxiaEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        Vec3 base=new Vec3(net.minecraft.util.Mth.lerp(partial,e.xOld,e.getX()),net.minecraft.util.Mth.lerp(partial,e.yOld,e.getY()),net.minecraft.util.Mth.lerp(partial,e.zOld,e.getZ()));
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=base;ArkFxDraw.ownerView(e.ownerId(),Vec3.ZERO);
        // vertex() subtracts base, while this entity pose already includes its translation.
        try{
            int kind=s.getInt("Kind"),mode=s.getInt("Mode");
            AttackFrame f=GalaxiaEffects.frame(s);Vec3 p=vector(s,"Position");
            double r=s.getFloat("Rotation"),scale=s.getFloat("Scale"),time=ArkFxDraw.time();
            if(kind==BLADE){
                String name="galaxia/"+(mode<2?"galaxiaextra2":"galaxiaextra");
                int[] dimensions=ArkFxDraw.size(name);
                if(mode==0 && s.getInt("State")==0 && s.getInt("Charge")>240){
                    var visual=GalaxiaEffects.visual(e.getId());
                    if(visual!=null){int index=0;for(CompoundTag old:visual.history){
                        if(old.getInt("Age")>=s.getInt("Age"))continue;
                        if(index>=10)break;
                        double opacity=(1-index/10d)*.5*clamp((s.getInt("Charge")-240)/360d);
                        blade(name,p,f,old.getFloat("Rotation")+Math.PI/4,scale-.2*index/10d,0,dimensions[1],mul(WHITE,opacity),null);index++;
                    }}
                }
                Vec3 at=mode==2?vector(s,"Center").add(vector(s,"Direction").scale(10/16d)):p;
                ArkFxDraw.Tint tint=mode==3?new ArkFxDraw.Tint(1,129/255f,153/255f,Math.max(0,s.getInt("OverCharge"))/20f):null;
                ArkFxDraw.extrudedBlade(name,at,f,r+Math.PI/4,scale,mode==1?dimensions[0]/2d:0,mode==1?dimensions[1]/2d:dimensions[1],WHITE,tint);
                if(mode==0 && s.getInt("State")==1)phoenixChain(s,f,p,time);
            }else if(kind==BOLT){
                // DrawAfterimagesCentered(mode=0), cache length 2; source does not add another current sprite.
                var visual=GalaxiaEffects.visual(e.getId());int i=0;
                double age=s.contains("RenderAge")?s.getDouble("RenderAge"):s.getInt("Age");
                if(visual!=null && !visual.history.isEmpty())for(;i<2;i++){
                    Vec3 at=historyPosition(visual.history,age-1-i);
                    ArkFxDraw.centered("galaxia/galaxiabolt",at,f,r+Math.PI/2,scale,scale,mul(WHITE,(2-i)/2d),ALPHA);
                }
                if(i==0)ArkFxDraw.centered("galaxia/galaxiabolt",p,f,r+Math.PI/2,scale,scale,WHITE,ALPHA);
            }else if(kind==STAR){
                double fade=s.getInt("Left")>15?1:s.getInt("Left")/15d;
                centered("sparkle",p,f,time*10+Math.PI/4,scale*1.5,scale*1.5,mul(WHITE,.5*fade));
                centered("sparkle",p,f,time*10,scale*2,scale*2,mul(WHITE,.8*fade));
            }else if(kind==CHAIN)constellation(s,f,p,time);
            else if(kind==RING)tropicRing(s,f,p,time);
            else if(kind==SLASH)dash(s,f,time);
        }finally{ArkFxDraw.fadeEye=null;}
    }
    static Vec3 historyPosition(java.util.List<CompoundTag> history,double age){
        CompoundTag newer=history.get(0);
        if(age>=newer.getInt("Age"))return GalaxiaEffects.vector(newer,"Position");
        for(int i=1;i<history.size();i++){
            CompoundTag older=history.get(i);
            if(age>=older.getInt("Age")){
                double span=newer.getInt("Age")-older.getInt("Age");
                return GalaxiaEffects.vector(older,"Position").lerp(GalaxiaEffects.vector(newer,"Position"),span<=0?0:(age-older.getInt("Age"))/span);
            }
            newer=older;
        }
        return GalaxiaEffects.vector(newer,"Position");
    }
    private static void blade(String name,Vec3 p,AttackFrame f,double r,double scale,double ox,double oy,float[] color,ArkFxDraw.Tint tint){
        int[] size=ArkFxDraw.size(name);
        ArkFxDraw.sprite(name,p,f,r,scale,scale,ox,oy,color,ALPHA,0,0,size[0],size[1],tint);
    }
    private static AttackFrame billboard(){
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        var look=camera.getLookVector();var up=camera.getUpVector();
        Vec3 forward=new Vec3(look.x(),look.y(),look.z()),vertical=new Vec3(up.x(),up.y(),up.z());
        return new AttackFrame(forward.cross(vertical).normalize(),vertical.scale(-1));
    }
    private static void centered(String name,Vec3 p,AttackFrame f,double r,double x,double y,float[] color){ArkFxDraw.centered(name,p,f,r,x,y,color,ADDITIVE);}
    public static AttackFrame lineFrame(Vec3 direction,AttackFrame camera){
        Vec3 along=direction.normalize(),side=camera.normal().cross(along).normalize();
        if(side.lengthSqr()<1e-8)side=camera.right();
        return new AttackFrame(side,along);
    }
    private static void line(String name,Vec3 a,Vec3 b,AttackFrame f,double width,float[] color,boolean capped){
        Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-12)return;
        f=lineFrame(d,billboard());
        int[] size=ArkFxDraw.size(name);double angle=f.angle(d)+Math.PI/2;
        ArkFxDraw.sprite(name,a,f,angle,width,d.length()*16/size[1],size[0]/2d,size[1],color,ADDITIVE);
        if(capped){int[] cap=ArkFxDraw.size("bloomlinecap");
            ArkFxDraw.sprite("bloomlinecap",a,f,angle+Math.PI,width,width,cap[0]/2d,cap[1],color,ADDITIVE);
            ArkFxDraw.sprite("bloomlinecap",b,f,angle,width,width,cap[0]/2d,cap[1],color,ADDITIVE);
        }
    }
    private static void phoenixChain(CompoundTag s,AttackFrame f,Vec3 p,double time){
        double timer=(90-s.getInt("Left"))/90d,snap=timer<.45?0:(timer-.45)/.55,retract=timer<.6?0:(timer-.6)/.4;
        if(snap<=0)return;
        Vec3 center=vector(s,"Center"),previous=center;
        double angle=random()*Math.PI*2;Vec3 jitter=retract>0?Vec3.ZERO:f.direction(angle).scale(Math.sqrt(2)*(1-snap)*10/16);
        int count=(int)(center.distanceTo(p));
        double opacity=(retract<.5?1:(retract-.5)/.5)*.7;
        ArkFxDraw.Tint tint=retract<=0?new ArkFxDraw.Tint(1,1,1,(float)clamp(.8-snap)):null;
        for(int i=1;i<=count;i++){
            Vec3 at=center.lerp(p,i/(double)count).add(jitter.scale(Math.sin(i/(double)count*Math.PI))),d=at.subtract(previous);
            ArkFxDraw.sprite("galaxia/phoenixchain",at,f,f.angle(d)-Math.PI/2,1,d.length()*16/18,6,18,mul(WHITE,opacity),ADDITIVE,0,18*(i%2),12,18,tint);
            previous=at;
        }
    }
    private static void constellation(CompoundTag s,AttackFrame f,Vec3 p,double time){
        var points=s.getList("Points",10);Vec3 last=p,prior=p;
        double age=Math.max(0,s.getInt("ChainAge")),opacity=Math.sin(age/20*Math.PI);
        if(age>=20)return;
        for(int i=0;i<points.size();i++){
            CompoundTag point=points.getCompound(i);Vec3 at=p.add(vector(point,"At"));
            if(i>0){Vec3 from=point.getBoolean("Branch")?prior:last;line("bloomline",from,at,f,.8,mul(rgb(199,21,133),.75),true);}
            double scale=point.getDouble("Scale"),ratio=(double)ArkFxDraw.size("sparkle")[1]/ArkFxDraw.size("bloomcircle")[1];
            centered("bloomcircle",at,billboard(),0,scale*3*ratio,scale*3*ratio,mul(rgb(221,160,221),opacity*.5));
            // Stored random initial rotation; GenericSparkle spin is zero for this constellation.
            double r=point.getDouble("Rotation");
            centered("sparkle",at,billboard(),r+Math.PI/4,scale*.75,scale*.75,mul(WHITE,opacity*.5));
            centered("sparkle",at,billboard(),r,scale,scale,mul(WHITE,opacity));prior=last;last=at;
        }
    }
    private static void tropicRing(CompoundTag s,AttackFrame f,Vec3 p,double time){
        int left=s.getInt("Left");double fade=left>250?Math.sin((300-left)/50d*Math.PI/2)*.6+.4:left>50?1:Math.sin(left/50d*Math.PI/2);
        double scale=s.getFloat("Scale"),pulse=time*5%10/10;
        float[] tint=mul(s.getInt("Mode")==0?rgb(255,0,0):rgb(66,0,176),fade);
        String icon="galaxia/"+(s.getInt("Mode")==0?"galaxiacancer":"galaxiacapricorn");
        centered("bloomcircle",p,f,0,2.5,2.5,mul(tint,.5));
        centered("galaxia/bloomring",p,f,0,scale,scale,mul(tint,.8));
        centered(icon,p,f,0,scale+pulse*2,scale+pulse*2,mul(tint,lerp(.7,0,pulse)));
        centered(icon,p,f,0,scale,scale,mul(WHITE,fade));
        double radius=scale*ArkFxDraw.size("galaxia/bloomring")[0]*.4/16;
        for(int i=0;i<5;i++){
            // Source starPosition uses cos for the rotation axis and sin for its perpendicular.
            Vec3 a=p.add(f.direction(time*3+i*Math.PI*2/5).scale(radius)),b=p.add(f.direction(time*3+(i+1)*Math.PI*2/5).scale(radius));
            line("bloomline",a,b,f,.3,mul(WHITE,fade*.7),false);
            centered("bloomcircle",a,billboard(),0,.1,.1,mul(tint,.5));
            centered("sparkle",a,billboard(),time*5+Math.PI/4*i,1,1,mul(tint,.5));
            centered("sparkle",a,billboard(),time*5,1.4,1.4,mul(WHITE,fade));
        }
    }
    private static void dash(CompoundTag s,AttackFrame f,double time){
        double left=s.getInt("Left"),t=(20-left)/20,opacity=Math.sin(t*Math.PI),colorRatio=Math.sin(t*Math.PI/2);
        Vec3 start=vector(s,"Start"),end=vector(s,"End");
        Vec3 jitter=left>=15?f.direction(random()*Math.PI*2).scale(Math.sqrt(2)*(15-left/5)*.5/16):Vec3.ZERO;
        line("thinendedline",start.add(jitter),end.add(jitter),f,.2,mix(WHITE,mul(CORNFLOWER,.7),colorRatio),false);
        double bloomScale=opacity*6*ArkFxDraw.size("thinsparkle")[0]/ArkFxDraw.size("bloomcircle")[1];
        centered("bloomcircle",end,f,0,bloomScale,bloomScale,mul(CORNFLOWER,opacity*.5));
        ArkFxDraw.sprite("thinsparkle",end,f,colorRatio*Math.PI*2,opacity*3,opacity*3,7,7,mul(mix(WHITE,CORNFLOWER,colorRatio),opacity),ADDITIVE,0,0,14,14);
    }
}
