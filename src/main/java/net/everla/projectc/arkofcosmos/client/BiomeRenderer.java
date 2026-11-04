package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.BiomeEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.BiomeEntity.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.*;
import static net.everla.projectc.arkofcosmos.client.BiomeAssets.texture;

/** Calamity PreDraw passes, using the shared BasicTint and world/depth FX pipeline. */
public final class BiomeRenderer extends EntityRenderer<BiomeEntity> {
    private static final float[] WHITE={1,1,1,1};
    public BiomeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(BiomeEntity e){return NativeSprites.texture(texture("item"));}
    @Override public boolean shouldRender(BiomeEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(BiomeEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        var draw=new TrueBiomeDraw(new NativeSprites.Draw(pose,buffers,e.getPosition(partial),e.ownerId(),true));var f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        Vec3 at=vector(s,"Position"),center=vector(s,"Center"),dir=vector(s,"Direction");int kind=s.getInt("Kind"),mode=s.getInt("Mode"),left=s.getInt("Left");double scale=s.getDouble("Scale"),r=f.angle(dir),time=(e.level().getGameTime()+partial)/20d;
        if(kind==BLADE&&mode<=PURE){
            double sourceAge=(e.level().getGameTime()-s.getLong("StartTick")+partial)*3;
            if(sourceAge>=15)return;
            var owner=e.level().getEntity(e.ownerId());
            if(owner instanceof net.minecraft.world.entity.player.Player p)at=p.getPosition(partial).add(0,p.getBbHeight()*.6,0);
            String tex=texture("item");
            double sx=net.everla.projectc.arkofcosmos.combat.OldOneBlade.WIDTH/(double)NativeSprites.width(tex),sy=net.everla.projectc.arkofcosmos.combat.OldOneBlade.HEIGHT/(double)NativeSprites.height(tex);
            draw.nativeDraw().sprite(tex,at,f,net.everla.projectc.arkofcosmos.entity.OldOneEntity.swordAngle(sourceAge)+Math.PI/4,sx,sy,0,NativeSprites.height(tex),WHITE,false,true,light,0,true);return;
        }
        if(kind==BLADE){
            Vec3 hilt=at;String base=texture(mode==HOLY||mode==MARINE?"attune":"item"),glow=null;
            if(mode<=PURE)hilt=at;
            else if(mode==HOT)hilt=center.add(dir.scale(10/16d));
            else if(mode==COLD||mode==EVIL)hilt=at.add(dir.scale(10/16d));
            if(mode==TROPICAL){
                base=texture("tropical");glow=texture("tropical_glow");
                Vec3 bottom=at.add(f.direction(r+Math.PI/4).scale(-NativeSprites.width(base)*.375/16)).add(f.direction(r+Math.PI*3/4).scale(NativeSprites.height(base)*.375/16));
                Vec3 c1=vector(s,"CP1"),c2=vector(s,"CP2"),prev=center;
                for(int i=1;i<30;i++){Vec3 point=bezier(center,c1,c2,bottom,i/29d);chain(draw,"vine",prev,point,f,1,s.getInt("State")==0?1:1-exp(s.getInt("Coyote")>0?0:clamp((s.getInt("Age")-49.5)/40.5)),light);prev=point;}
                Vec3 tangent=bottom.subtract(bezier(center,c1,c2,bottom,28/29d));
                double angle=f.angle(tangent)+(s.getInt("Coyote")>0?Math.PI:0);
                if(s.getInt("State")!=0){double progress=s.getInt("Coyote")>0?0:(s.getInt("Age")-49.5)/40.5;angle+=Math.atan2(Math.sin(r-angle),Math.cos(r-angle))*progress;}
                r=angle;hilt=bottom;
            }else if(mode>PURE)glow=texture(switch(mode){case HOT->"hot";case COLD->s.getInt("Combo")==2?"cold_thrust":"cold";case EVIL->"evil";case HOLY->"holy";case ASTRAL->"astral";default->"attune";});
            blade(draw,base,hilt,f,r,scale,WHITE,false,light);
            if(glow!=null&&mode!=MARINE){
                double opacity=mode==COLD&&s.getInt("Combo")!=2?.8:.9;
                double thrown=(90-left)/90d,snap=clamp((thrown-.45)/.55),retract=clamp((thrown-.6)/.4);
                if(mode==HOLY&&s.getInt("State")==1)opacity*=1-retract;
                ArkFxDraw.Tint tint=mode==ASTRAL?new ArkFxDraw.Tint(154/255f,244/255f,240/255f,(float)clamp(s.getInt("OverCharge")/20d))
                        :mode==HOLY&&s.getInt("State")==1&&snap>0&&retract<=0?new ArkFxDraw.Tint(1,1,1,(float)clamp(.8-snap)):null;
                if(mode!=TROPICAL||s.getDouble("Age")/90>.35&&(s.getInt("State")==0||s.getInt("Coyote")>0)){
                    if(tint==null)blade(draw,glow,hilt,f,r,scale,alpha(opacity),true,15728880);
                    else draw.tinted(glow,hilt,f,r+Math.PI/4,scale,0,NativeSprites.height(glow),alpha(opacity),tint);
                }
                if(mode==EVIL&&s.getInt("DashTime")>0&&s.getInt("DashTime")<20)blade(draw,glow,hilt,f,r,scale*(1+Math.sin(s.getInt("DashTime")/20d*Math.PI)*.2),alpha(.9),true,15728880);
            }
            if(mode==HOT)phantoms(draw,center,dir,f,time,scale,clamp(s.getDouble("Shred")/250),s.getInt("OverCharge"),1);
            if(mode==HOLY){
                if(s.getInt("State")==0&&s.getInt("Charge")>240){ListTag history=s.getList("Trail",10);for(int i=0;i<history.size();i++)blade(draw,texture("holy"),hilt,f,history.getCompound(i).getDouble("Rotation"),scale-.2*i/10,alpha((1-i/10d)*.5*clamp((s.getInt("Charge")/600d-.4)/.1)),true,15728880);}
                if(s.getInt("State")==0&&s.getInt("Charge")>=450)draw.centered(texture("smear"),center,f,r+Math.PI/2,scale*1.5,scale*1.5,multiply(mix(0xFF69B4,0x00FFFF,Math.sin(time*2)),(s.getInt("Charge")/600d-.75)/.25*.8),true);
                if(s.getInt("State")==1&&(90-left)/90d>.45)holyChain(draw,center,at,f,clamp(((90-left)/90d-.45)/.55),clamp(((90-left)/90d-.6)/.4),light);
            }
        }else if(kind==HOT_SHOT)phantoms(draw,at,dir,f,time,scale,clamp(s.getDouble("Shred")/250),0,left>15?1:left/15d);
        else if(kind==BEAM&&left<=85){String tex=texture("pure");ListTag history=s.getList("Trail",10);if(history.isEmpty())draw.centered(tex,at,f,r+Math.PI/4,scale,scale,alpha((255-s.getInt("Alpha"))/255d),false);else for(int i=0;i<history.size();i++)draw.centered(tex,vector(history.getCompound(i),"Position"),f,r+Math.PI/4,scale,scale,alpha((255-s.getInt("Alpha"))/255d*(1-i/(double)history.size())),false);}
        else if(kind==SIGIL){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.getId()==e.ownerId()){ListTag trail=s.getList("Trail",10);int count=Math.min(2,trail.size());if(count==0)draw.centered(texture("sigil"),at,NativeSprites.billboard(),0,1,1,WHITE,false);else for(int i=0;i<count;i++)draw.centered(texture("sigil"),vector(trail.getCompound(i),"Position"),NativeSprites.billboard(),0,1,1,alpha(1-i/2d),false);}}
        else if(kind==VINE){Vec3 a=vector(s,"Start"),b=vector(s,"End"),prev=a,jitter=jitter(f,left);int count=Math.max(1,(int)a.distanceTo(b));Vec3 side=f.normal().cross(b.subtract(a).normalize()).normalize();for(int i=1;i<=count;i++){double t=i/(double)count;Vec3 point=a.lerp(b,t).add(side.scale(Math.sin(t*Math.PI)*count/10d)).add(jitter.scale(Math.sin(t*Math.PI)));chain(draw,"vine",prev,point,f,1,Math.min(1,left/10d),light);prev=point;}}
        else if(kind==DASH){Vec3 a=vector(s,"Start"),b=vector(s,"End"),prev=a,jitter=jitter(f,left);int count=Math.max(1,(int)a.distanceTo(b));for(int i=1;i<=count;i++){double t=i/(double)count;Vec3 point=a.lerp(b,t).add(jitter.scale(Math.sin(t*Math.PI/2)));chain(draw,"dash",prev,point,f,t*5,clamp(Math.sin(t*Math.PI/2)-t*(20-left)/25d),15728880);prev=point;}double t=(20-left)/20d;draw.centered(texture("bloom"),b,NativeSprites.billboard(),0,Math.sin(t*Math.PI)*6*NativeSprites.width(texture("spark"))/NativeSprites.height(texture("bloom")),Math.sin(t*Math.PI)*6*NativeSprites.width(texture("spark"))/NativeSprites.height(texture("bloom")),multiply(mix(0xDC143C,0xDC143C,0),Math.sin(t*Math.PI)*.5),true);double flash=Math.sin(t*Math.PI),spin=Math.sin(t*Math.PI/2);draw.region(texture("spark"),b,NativeSprites.billboard(),spin*Math.PI*2,flash*3,flash*3,7,7,multiply(mix(0xFFFFFF,0xDC143C,spin),flash),0,0,14,14,true,15728880);}
        else if(kind==MONOLITH&&s.getDouble("Wait")<=0){double t=(100-left)/100d,sx=monolithWidth(t)*s.getDouble("Size"),sy=monolithHeight(t)*s.getDouble("Size");AttackFrame standing=new AttackFrame(f.right(),dir.scale(-1));Vec3 foot=at.subtract(dir.scale(26/16d));int variant=s.getInt("Variant");for(String key:new String[]{"monolith","monolith_glow"})draw.region(texture(key),foot,standing,0,sx,sy,47,420,alpha(clamp(1-(t-.85)/.15)),variant*94,0,94,420,false,key.endsWith("glow")?15728880:light);}
        else if(kind==MIST){String tex=texture("mist");int w=NativeSprites.width(tex),h=NativeSprites.height(tex)/3;AttackFrame face=NativeSprites.billboard();Vec3 origin=at.subtract(face.forward().add(face.right()).scale(17/16d));draw.region(tex,origin,face,s.getDouble("Rotation"),scale,w/2d,h/2d,mistColor(s.getInt("Alpha")),0,s.getInt("Variant")*h,w,h,true,15728880);}
        else if(kind==ORB){
            if(s.getBoolean("Water")){draw.centered(texture("aqua"),at.subtract(vector(s,"Velocity")),f,r+Math.PI,Math.max(0,scale-.2),Math.max(0,scale-.2),multiply(WHITE,.2),false);draw.centered(texture("aqua"),at,f,r+Math.PI,scale,scale,multiply(WHITE,.5),false);}
            else if(s.getInt("State")==1&&s.getInt("LandTime")>10){AttackFrame standing=new AttackFrame(new Vec3(dir.x,0,dir.z).normalize(),new Vec3(0,-1,0));double land=s.getInt("LandTime"),sx=1+.1*(1-Math.sin((land-10)*.1));for(int i=2;i>=0;i--){double sy=(1+.2*(1+Math.sin((land-10)*.1+.2*i)))*clamp((land-10)/30)*clamp(left/30d)-.05*i;draw.region(texture("wave"),at.subtract(dir.scale(10*i/16d)),standing,0,sx,Math.max(0,sy),0,110,multiply(new float[]{(float)(1-.15*i),(float)(1-.15*i),(float)(1-.15*i),1},clamp(land/30)*(1-.24*i)),0,(s.getInt("Age")/6%3)*110,40,110,false,light);}}
            else draw.centered(texture("orb"),at,NativeSprites.billboard(),s.getDouble("Rotation"),scale,scale,multiply(WHITE,clamp(1-s.getInt("LandTime")/10d)),false);
        }
    }
    private static float[] alpha(double opacity){return multiply(WHITE,clamp(opacity));}
    private static float[] multiply(float[] c,double m){float[] r=c.clone();for(int i=0;i<4;i++)r[i]*=(float)m;return r;}
    private static float[] mix(int a,int b,double t){t=clamp(t);return new float[]{(float)lerp((a>>16&255)/255d,(b>>16&255)/255d,t),(float)lerp((a>>8&255)/255d,(b>>8&255)/255d,t),(float)lerp((a&255)/255d,(b&255)/255d,t),1};}
    private static float[] mistColor(int alpha){
        // Preserve the source's 255 - alpha / 255 expression, including XNA Color clamping.
        float[] c=mix(0xACEEFF,0x91AABC,(alpha-100)/80d);
        for(int i=0;i<4;i++)c[i]=(float)(Math.min(1,c[i]*(255-alpha/255d))*.5*(255-alpha)/255d);
        return c;
    }
    private static void blade(TrueBiomeDraw d,String tex,Vec3 at,AttackFrame f,double angle,double scale,float[] color,boolean additive,int light){d.sprite(tex,at,f,angle+Math.PI/4,scale,scale,0,NativeSprites.height(tex),color,additive,true,light);}
    private static void phantoms(TrueBiomeDraw d,Vec3 center,Vec3 dir,AttackFrame f,double time,double scale,double ratio,int bounce,double opacity){
        for(int i=0;i<4;i++){double wobble=Math.sin(time*5+i*Math.PI/2),angle=f.angle(dir)+wobble*Math.PI/10-wobble*Math.PI/9*ratio;Vec3 side=f.direction(f.angle(dir)+Math.PI/2);Vec3 at=center.add(dir.scale(Math.sin(time*7)*10/16d+clamp(bounce/20d)*20/16d)).add(side.scale(Math.sin(wobble)*(20+40*ratio)/16));blade(d,texture("hot_extra"),at,f,angle,scale,alpha(.8*opacity),true,15728880);}
    }
    private static Vec3 jitter(AttackFrame f,int left){return left<15?Vec3.ZERO:f.direction(java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI*2)).scale(Math.sqrt(2)*(15-left/5d)*.5/16);}
    private static void holyChain(TrueBiomeDraw draw,Vec3 a,Vec3 b,AttackFrame f,double snap,double retract,int light){
        int count=(int)a.distanceTo(b);if(count==0)return;
        Vec3 jitter=retract>0?Vec3.ZERO:f.direction(java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI*2)).scale(Math.sqrt(2)*(1-snap)*10/16);
        Vec3 prev=a;double opacity=(retract<.5?1:(retract-.5)/.5)*.7;
        String tex=texture("holy_chain");
        for(int i=1;i<=count;i++){
            double t=i/(double)count;Vec3 at=a.lerp(b,t).add(jitter.scale(Math.sin(t*Math.PI))),delta=at.subtract(prev);
            AttackFrame line=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(delta.normalize(),f.right());
            draw.region(tex,at,line,-Math.PI/2,1,delta.length()*16/18,6,18,alpha(opacity),0,18*(i%2),12,18,false,light);prev=at;
        }
    }
    private static void chain(TrueBiomeDraw d,String key,Vec3 a,Vec3 b,AttackFrame f,double width,double opacity,int light){
        Vec3 delta=b.subtract(a);if(delta.lengthSqr()<1e-8)return;AttackFrame line=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(delta.normalize(),f.right());String tex=texture(key);int height=key.equals("holy_chain")?18:NativeSprites.height(tex);d.region(tex,b,line,-Math.PI/2,width,delta.length()*16/height,NativeSprites.width(tex)/2d,height,key.equals("dash")?multiply(mix(0xDC143C,0xDC143C,0),opacity):alpha(opacity),0,0,NativeSprites.width(tex),height,key.equals("dash"),light);
    }
}
