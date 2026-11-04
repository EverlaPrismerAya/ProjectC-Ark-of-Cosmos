package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
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

/** Source sprites through Minecraft built-in shaders; dedicated Calamity shader passes are deferred. */
public final class BiomeRenderer extends EntityRenderer<BiomeEntity> {
    private static final float[] WHITE={1,1,1,1};
    public BiomeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(BiomeEntity e){return NativeSprites.texture(texture("item"));}
    @Override public boolean shouldRender(BiomeEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(BiomeEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        var draw=new NativeSprites.Draw(pose,buffers,e.getPosition(partial),e.ownerId(),true);var f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        Vec3 at=vector(s,"Position"),center=vector(s,"Center"),dir=vector(s,"Direction");int kind=s.getInt("Kind"),mode=s.getInt("Mode"),left=s.getInt("Left");double scale=s.getDouble("Scale"),r=f.angle(dir),time=(e.level().getGameTime()+partial)/20d;
        if(kind==BLADE){
            Vec3 hilt=at;String base=texture(mode==HOLY||mode==MARINE?"attune":"item"),glow=null;
            if(mode<=PURE)hilt=at;
            else if(mode==HOT)hilt=center.add(dir.scale(10/16d));
            else if(mode==COLD||mode==EVIL)hilt=at.add(dir.scale(10/16d));
            if(mode==TROPICAL){
                Vec3 c1=vector(s,"CP1"),c2=vector(s,"CP2");Vec3 prev=center;
                for(int i=1;i<32;i++){Vec3 point=bezier(center,c1,c2,at,i/31d);chain(draw,"vine",prev,point,f,1,1,light);prev=point;}
                base=texture("tropical");glow=texture("tropical_glow");
            }else if(mode>PURE)glow=texture(switch(mode){case HOT->"hot";case COLD->s.getInt("Combo")==2?"cold_thrust":"cold";case EVIL->"evil";case HOLY->"holy";case ASTRAL->"astral";default->"attune";});
            blade(draw,base,hilt,f,r,scale,WHITE,false,light);
            if(glow!=null&&mode!=MARINE){double opacity=mode==COLD?.8:.9;if(mode==HOLY&&s.getInt("State")==1)opacity*=1-clamp(((90-left)/90d-.6)/.4);if(mode!=TROPICAL||s.getDouble("Age")/90>.35&&(s.getInt("State")==0||s.getInt("Coyote")>0))blade(draw,glow,hilt,f,r,scale,alpha(opacity),true,15728880);}
            if(mode==HOT)phantoms(draw,center,dir,f,time,scale,clamp(s.getDouble("Shred")/250),s.getInt("OverCharge"),1);
            if(mode==HOLY){
                if(s.getInt("State")==0&&s.getInt("Charge")>240){ListTag history=s.getList("Trail",10);for(int i=0;i<history.size();i++)blade(draw,texture("holy"),hilt,f,history.getCompound(i).getDouble("Rotation"),scale-.2*i/10,alpha((1-i/10d)*.5*clamp((s.getInt("Charge")/600d-.4)/.1)),true,15728880);}
                if(s.getInt("State")==0&&s.getInt("Charge")>=450)draw.centered(texture("smear"),center,f,r+Math.PI/2,scale*1.5,scale*1.5,new float[]{1,.4f,.75f,(float)clamp((s.getInt("Charge")/600d-.75)/.25*.8)},true);
                if(s.getInt("State")==1&&(90-left)/90d>.45)chain(draw,"holy_chain",center,at,f,1,1,light);
            }
        }else if(kind==HOT_SHOT)phantoms(draw,at,dir,f,time,scale,clamp(s.getDouble("Shred")/250),0,left>15?1:left/15d);
        else if(kind==BEAM&&left<=85){String tex=texture("pure");ListTag history=s.getList("Trail",10);for(int i=0;i<history.size();i++)draw.centered(tex,vector(history.getCompound(i),"Position"),f,r+Math.PI/4,scale,scale,alpha(.6*(1-i/(double)history.size())),false);draw.centered(tex,at,f,r+Math.PI/4,scale,scale,alpha((255-s.getInt("Alpha"))/255d),false);}
        else if(kind==SIGIL){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.getId()==e.ownerId())draw.centered(texture("sigil"),at,NativeSprites.billboard(),0,1,1,WHITE,false);}
        else if(kind==VINE){Vec3 a=vector(s,"Start"),b=vector(s,"End"),prev=a;int count=Math.max(1,(int)a.distanceTo(b));Vec3 side=f.right();for(int i=1;i<=count;i++){double t=i/(double)count;Vec3 point=a.lerp(b,t).add(side.scale(Math.sin(t*Math.PI)*count/10d));chain(draw,"vine",prev,point,f,1,Math.min(1,left/10d),light);prev=point;}}
        else if(kind==DASH){Vec3 a=vector(s,"Start"),b=vector(s,"End"),prev=a;int count=Math.max(1,(int)a.distanceTo(b));for(int i=1;i<=count;i++){double t=i/(double)count;Vec3 point=a.lerp(b,t);chain(draw,"dash",prev,point,f,t*5,clamp(Math.sin(t*Math.PI/2)-t*(20-left)/25d),15728880);prev=point;}double t=(20-left)/20d;draw.centered(texture("bloom"),b,NativeSprites.billboard(),0,Math.sin(t*Math.PI)*.6,Math.sin(t*Math.PI)*.6,new float[]{.86f,.08f,.24f,(float)(Math.sin(t*Math.PI)*.5)},true);double flash=Math.sin(t*Math.PI),spin=Math.sin(t*Math.PI/2);draw.region(texture("spark"),b,NativeSprites.billboard(),spin*Math.PI*2,flash*3,flash*3,7,7,new float[]{(float)lerp(1,.86,spin),(float)lerp(1,.08,spin),(float)lerp(1,.24,spin),(float)flash},0,0,14,14,true,15728880);}
        else if(kind==MONOLITH&&s.getDouble("Wait")<=0){double t=(100-left)/100d,sx=monolithWidth(t)*s.getDouble("Size"),sy=monolithHeight(t)*s.getDouble("Size");AttackFrame standing=new AttackFrame(f.right(),dir.scale(-1));Vec3 foot=at.subtract(dir.scale(26/16d));int variant=s.getInt("Variant");for(String key:new String[]{"monolith","monolith_glow"})draw.region(texture(key),foot,standing,0,sx,sy,47,420,alpha(clamp(1-(t-.85)/.15)),variant*94,0,94,420,key.endsWith("glow"),key.endsWith("glow")?15728880:light);}
        else if(kind==MIST){String tex=texture("mist");int w=NativeSprites.width(tex),h=NativeSprites.height(tex)/3;float fade=(255-s.getInt("Alpha"))/255f;draw.region(tex,at,NativeSprites.billboard(),s.getDouble("Rotation"),scale,w/2d,h/2d,new float[]{.67f,.93f,1,fade*.5f},0,s.getInt("Variant")*h,w,h,true,15728880);}
        else if(kind==ORB){
            if(s.getBoolean("Water")){draw.centered(texture("aqua"),at,f,r+Math.PI,scale,scale,alpha(.5),false);draw.centered(texture("aqua"),at.subtract(vector(s,"Velocity")),f,r+Math.PI,Math.max(0,scale-.2),Math.max(0,scale-.2),alpha(.2),false);}
            else if(s.getInt("State")==1&&s.getInt("LandTime")>10){AttackFrame standing=new AttackFrame(new Vec3(dir.x,0,dir.z).normalize(),new Vec3(0,-1,0));double land=s.getInt("LandTime"),sx=1+.1*(1-Math.sin((land-10)*.1));for(int i=2;i>=0;i--)draw.region(texture("wave"),at.subtract(dir.scale(10*i/16d)),standing,0,sx,Math.max(0,waveY(land,left)-.05*i),0,110,alpha(clamp(land/30)*(1-.24*i)),0,(s.getInt("Age")/6%3)*110,40,110,false,light);}
            else draw.centered(texture("orb"),at,NativeSprites.billboard(),s.getDouble("Rotation"),1,1,alpha(165/255d),false);
        }
    }
    private static float[] alpha(double opacity){return new float[]{1,1,1,(float)clamp(opacity)};}
    private static void blade(NativeSprites.Draw d,String tex,Vec3 at,AttackFrame f,double angle,double scale,float[] color,boolean additive,int light){d.sprite(tex,at,f,angle+Math.PI/4,scale,scale,0,NativeSprites.height(tex),color,additive,true,light);}
    private static void phantoms(NativeSprites.Draw d,Vec3 center,Vec3 dir,AttackFrame f,double time,double scale,double ratio,int bounce,double opacity){
        for(int i=0;i<4;i++){double wobble=Math.sin(time*5+i*Math.PI/2),angle=f.angle(dir)+wobble*Math.PI/10-wobble*Math.PI/9*ratio;Vec3 side=f.direction(f.angle(dir)+Math.PI/2);Vec3 at=center.add(dir.scale(Math.sin(time*7)*10/16d+clamp(bounce/20d)*20/16d)).add(side.scale(Math.sin(wobble)*(20+40*ratio)/16));blade(d,texture("hot_extra"),at,f,angle,scale,alpha(.8*opacity),true,15728880);}
    }
    private static void chain(NativeSprites.Draw d,String key,Vec3 a,Vec3 b,AttackFrame f,double width,double opacity,int light){
        Vec3 delta=b.subtract(a);if(delta.lengthSqr()<1e-8)return;AttackFrame line=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(delta.normalize(),f.right());String tex=texture(key);int height=key.equals("holy_chain")?18:NativeSprites.height(tex);int segments=Math.max(1,(int)Math.ceil(delta.length()*16/height));
        for(int i=0;i<segments;i++){Vec3 at=a.lerp(b,(i+1d)/segments);d.region(tex,at,line,-Math.PI/2,width,delta.length()*16/segments/height,NativeSprites.width(tex)/2d,height,alpha(opacity),0,key.equals("holy_chain")?18*(i%2):0,NativeSprites.width(tex),height,key.equals("dash"),light);}
    }
}
