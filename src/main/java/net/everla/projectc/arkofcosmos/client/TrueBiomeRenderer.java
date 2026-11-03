package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.combat.TrueBiomeRules.*;

/** Source PreDraw sprites using the native pipeline; intentionally no weapon-specific shaders. */
public final class TrueBiomeRenderer extends EntityRenderer<TrueBiomeEntity> {
    private static final String ITEM="item/true_biome_blade";
    private static final float[] WHITE={1,1,1,1};
    private static final String[] MODE={"pride","chained","fury","tides"};
    public TrueBiomeRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TrueBiomeEntity e){return NativeSprites.texture(ITEM);}
    @Override public boolean shouldRender(TrueBiomeEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(TrueBiomeEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        var draw=new NativeSprites.Draw(pose,buffers,e.getPosition(partial),e.ownerId());
        var f=new AttackFrame(vector(s,"F"),vector(s,"R"));Vec3 at=vector(s,"Position"),center=vector(s,"Center"),dir=vector(s,"Direction");
        int kind=s.getInt("Kind"),mode=s.getInt("Mode"),left=s.getInt("Left");double scale=s.getDouble("Scale"),r=s.getDouble("Rotation"),time=(e.level().getGameTime()+partial)/20d;
        if(kind==BLADE){
            String glow=TrueBiomeAssets.texture(MODE[mode]);Vec3 hilt=mode==FURY?center.add(dir.scale(10/16d)):mode==CHAINED?at.subtract(dir.scale(55/16d)):at;
            draw.sprite(ITEM,hilt,f,r+Math.PI/4,scale,scale,0,NativeSprites.height(ITEM),WHITE,false,true,light);
            draw.sprite(glow,hilt,f,r+Math.PI/4,scale,scale,0,NativeSprites.height(glow),alpha(.9),true,true,15728880);
            if(mode==PRIDE){
                if(s.getInt("State")==0&&s.getInt("Charge")>240){ListTag trail=s.getList("Trail",10);for(int i=0;i<trail.size();i++)draw.sprite(glow,hilt,f,trail.getCompound(i).getDouble("Rotation")+Math.PI/4,scale-.2*i/10,scale-.2*i/10,0,NativeSprites.height(glow),alpha((1-i/10d)*.5*clamp((s.getInt("Charge")/600d-.4)/.1)),true,false,15728880);}
                if(s.getInt("State")==1&&(90-left)/90d>.45)chain(draw,TrueBiomeAssets.texture("pride_chain"),center,at,f,1,1);
                if(s.getInt("Charge")>=450&&s.getInt("State")==0){Vec3 aim=vector(s,"Aim");String tex=TrueBiomeAssets.texture("thin_line");draw.sprite(tex,center.add(aim.scale(scale*1.88*40/16)),lineFrame(aim,f),Math.PI,.2,scale*1.88*38/NativeSprites.height(tex),NativeSprites.width(tex)/2d,NativeSprites.height(tex),color(0xDC69C5,(s.getInt("Charge")/600d-.75)/.25*.8*.7),true,false,15728880);}
                if(s.getInt("Charge")>=450&&s.getInt("State")==0)draw.centered(TrueBiomeAssets.texture("circular_smear"),center,f,r+Math.PI/2,scale*1.9,scale*1.9,color(0xDC69C5,(s.getInt("Charge")/600d-.75)/.25*.8),true);
            }
            if(mode==FURY)for(int i=0;i<4;i++){
                double wobble=Math.sin(time*5+i*Math.PI/2),angle=r+Math.PI/4+wobble*Math.PI/10-wobble*Math.PI/9*shred(s.getDouble("Shred"));
                Vec3 extraAt=center.add(dir.scale(Math.sin(time*7)*10/16d)).add(f.normal().cross(dir).scale(Math.sin(wobble)*(20+40*shred(s.getDouble("Shred")))/16)).add(dir.scale(clamp(s.getInt("Bounce")/20d)*20/16));
                String extra=TrueBiomeAssets.texture("fury_extra");draw.sprite(extra,extraAt,f,angle,scale,scale,0,NativeSprites.height(extra),alpha(.8),true,false,15728880);
            }
            if(mode==CHAINED){ListTag curves=s.getList("Chains",10);for(int k=0;k<curves.size();k++){
                Vec3[] c=new Vec3[5];for(int i=0;i<5;i++)c[i]=vector(curves.getCompound(k),"P"+i);
                Vec3 prev=c[0];String tex=TrueBiomeAssets.texture("flail");
                for(int i=1;i<40;i++){
                    Vec3 point=bezier(c,i/39d),delta=point.subtract(prev);var lf=lineFrame(delta,f);double taper=i<30?i/30d:1-(i-30)/10d;
                    draw.region(tex,point,lf,0,1+.75*Math.sin(taper*Math.PI/2),delta.length()*16/14,12,14,alpha(clamp(i/40d*3)),0,70,24,14,false,light);
                    if(i==38)draw.region(tex,point,lf,0,1,12,48,WHITE,0,0,24,48,true,15728880);
                    if(i==39)draw.region(tex,point,lf,0,1,12,18,WHITE,0,50,24,18,false,light);
                    prev=point;
                }
            }}
        }else if(kind==BEAM){
            if(left>75)return;String tex=TrueBiomeAssets.texture("beam");ListTag trail=s.getList("Trail",10);
            float[] color=mix(0xFF69B4,0xADFF2F,Math.sin(time*2));
            for(int i=0;i<trail.size();i++)draw.centered(tex,vector(trail.getCompound(i),"Position"),f,r+Math.PI/4,scale,scale,multiply(color,(2-i)/2d),false);
            draw.centered(tex,at,f,r+Math.PI/4,1,1,color,true);
        }else if(kind==WHEEL){String tex=TrueBiomeAssets.texture("fury_extra");for(int i=0;i<4;i++){
            double a=i*Math.PI/2+time*6;
            draw.sprite(tex,at.subtract(f.direction(a).scale(20/16d)),f,a,scale*.85,scale*.85,0,NativeSprites.height(tex),alpha(.8*Math.min(1,left/15d)),true,false,15728880);
        }}else if(kind==DASH){
            Vec3 a=vector(s,"Start"),b=vector(s,"End"),delta=b.subtract(a);double t=(20-left)/20d;
            String tex=TrueBiomeAssets.texture("thin_line");draw.sprite(tex,a,lineFrame(delta,f),Math.PI, .2,delta.length()*16/NativeSprites.height(tex),NativeSprites.width(tex)/2d,NativeSprites.height(tex),mix(0xFFFFFF,0xDC143C,Math.sin(t*Math.PI/2)*.7),true,false,15728880);
            draw.centered(TrueBiomeAssets.texture("bloom_circle"),b,NativeSprites.billboard(),0,Math.sin(t*Math.PI)*.7,Math.sin(t*Math.PI)*.7,color(0xDC143C,.5*Math.sin(t*Math.PI)),true);
        }else if(kind==CHAIN){
            Vec3 a=vector(s,"Start"),b=vector(s,"End");double g=s.getDouble("Gravity");Vec3[] c={a,a.add(0,-g,0),b.add(0,-g,0),b};Vec3 prev=a;
            for(int i=1;i<20;i++){Vec3 point=bezier(c,i/19d);chain(draw,TrueBiomeAssets.texture("ghost_chain"),prev,point,f,1,Math.min(1,left/10d));prev=point;}
        }else if(kind==HOOK){
            String body=TrueBiomeAssets.texture("hook_body"),glow=TrueBiomeAssets.texture("hook");double angle=f.angle(at.subtract(center))+Math.PI/4;
            draw.sprite(body,at,f,angle,1,1,0,NativeSprites.height(body),WHITE,false,true,light);
            draw.sprite(glow,at,f,angle,1,1,0,NativeSprites.height(glow),alpha(.9),true,true,15728880);
            chain(draw,TrueBiomeAssets.texture("ghost_chain"),center,at,f,1,.5);
        }else if(kind==BLAST){
            draw.region(TrueBiomeAssets.texture("shockwave"),at,f,0,scale,85,83,WHITE,0,Math.min(5,Math.max(0,6-(int)Math.ceil(left/10d)))*168,170,166,false,15728880);
        }else if(kind==MONOLITH&&s.getDouble("Wait")<=0){
            double t=(100-left)/100d,size=s.getDouble("Size");var vertical=lineFrame(dir,f);
            draw.region(TrueBiomeAssets.texture("monolith"),at.subtract(dir.scale(26/16d)),vertical,Math.PI,monolithWidth(t)*size,monolithHeight(t)*size,47,420,alpha(clamp(1-(t-.85)/.15)),s.getInt("State")*94,0,94,420,false,light);
            draw.region(TrueBiomeAssets.texture("monolith_glow"),at.subtract(dir.scale(26/16d)),vertical,Math.PI,monolithWidth(t)*size,monolithHeight(t)*size,47,420,alpha(clamp(1-(t-.85)/.15)),s.getInt("State")*94,0,94,420,true,15728880);
        }else if(kind==SIGIL){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player!=null&&mc.player.getId()==e.ownerId())draw.centered(TrueBiomeAssets.texture("sigil"),at,NativeSprites.billboard(),0,1,1,WHITE,false);}
    }
    private static AttackFrame lineFrame(Vec3 direction,AttackFrame f){Vec3 along=direction.normalize(),side=f.normal().cross(along).normalize();if(side.lengthSqr()<1e-8)side=f.right();return new AttackFrame(side,along);}
    private static void chain(NativeSprites.Draw draw,String tex,Vec3 a,Vec3 b,AttackFrame f,double width,double opacity){
        Vec3 delta=b.subtract(a);if(delta.lengthSqr()<1e-12)return;int n=Math.max(1,(int)(delta.length()*16/16));
        int textureWidth=NativeSprites.width(tex),textureHeight=NativeSprites.height(tex);int frames=tex.equals(TrueBiomeAssets.texture("pride_chain"))?2:1,frameHeight=textureHeight/frames;
        for(int i=1;i<=n;i++){Vec3 point=a.lerp(b,i/(double)n);draw.region(tex,point,lineFrame(delta,f),0,width,delta.length()*16/n/frameHeight,textureWidth/2d,frameHeight,alpha(opacity),0,frameHeight*(i%frames),textureWidth,frameHeight,false,15728880);}
    }
    private static float[] alpha(double a){return new float[]{1,1,1,(float)clamp(a)};}
    private static float[] color(int rgb,double a){return new float[]{(rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,(float)clamp(a)};}
    private static float[] multiply(float[] a,double value){return new float[]{a[0],a[1],a[2],(float)(a[3]*value)};}
    private static float[] mix(int a,int b,double t){t=clamp(t);return color(((int)lerp(a>>16&255,b>>16&255,t)<<16)|((int)lerp(a>>8&255,b>>8&255,t)<<8)|(int)lerp(a&255,b&255,t),1);}
}
