package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.combat.BrokenBiomeRules.*;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.clamp;
import static net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules.exp;
import static net.everla.projectc.arkofcosmos.client.BrokenBiomeAssets.texture;

/** BrokenBiomeBlade original PreDraw layers; no new shader or synthetic attack sprites. */
public final class BrokenBiomeRenderer extends EntityRenderer<BrokenBiomeEntity> {
    private static final float[] WHITE={1,1,1,1};
    public BrokenBiomeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(BrokenBiomeEntity e){return NativeSprites.texture(texture("item"));}
    @Override public boolean shouldRender(BrokenBiomeEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(BrokenBiomeEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        var draw=new TrueBiomeDraw(new NativeSprites.Draw(pose,buffers,e.getPosition(partial),e.ownerId(),true));
        var f=new AttackFrame(vector(s,"F"),vector(s,"R"));Vec3 at=vector(s,"Position"),center=vector(s,"Center"),dir=vector(s,"Direction");
        int mode=s.getInt("Mode"),combo=s.getInt("Combo");double scale=s.getDouble("Scale"),angle=f.angle(dir),time=(e.level().getGameTime()+partial)/20d;
        if(s.getInt("Kind")==BrokenBiomeEntity.BEAM){
            if(s.getInt("Left")>35)return;
            ListTag trail=s.getList("Trail",10);double opacity=clamp((s.getDouble("Age")-5)/10)*(255-s.getInt("Alpha"))/255d;
            if(trail.isEmpty())draw.centered(texture("pure"),at,f,angle+Math.PI/4,scale,scale,color(opacity),false);
            for(int i=0;i<trail.size();i++)draw.centered(texture("pure"),vector(trail.getCompound(i),"Position"),f,angle+Math.PI/4,scale,scale,color(opacity*(trail.size()-i)/trail.size()),false);
            return;
        }
        if(mode<=PURE){
            double age=(e.level().getGameTime()-s.getLong("StartTick")+partial)*3;
            if(age>=30)return;
            if(e.level().getEntity(e.ownerId()) instanceof net.minecraft.world.entity.player.Player p)at=p.getPosition(partial).add(0,p.getBbHeight()*.6,0);
            blade(draw,"item",at,f,swordAngle(age),1,1,false,light);return;
        }
        if(mode==HOT){
            blade(draw,"item",center.add(dir.scale(10/16d)),f,angle,scale,1,false,light);
            blade(draw,"hot",center.add(dir.scale(32/16d*scale)),f,angle,scale,.9,true,15728880);
            double ratio=clamp(s.getDouble("Shred")/250);
            for(int i=0;i<4;i++){
                double wobble=Math.sin(time*5+i*Math.PI/2),a=angle+wobble*Math.PI/10-wobble*Math.PI/7*ratio;
                Vec3 axis=dir.normalize(),across=f.normal().cross(axis).normalize();double orbit=time*5+i*Math.PI/2;
                Vec3 radial=across.scale(Math.cos(orbit)).add(f.normal().scale(Math.sin(orbit)));
                AttackFrame phantom=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(axis,radial);
                Vec3 pos=center.add(axis.scale(Math.sin(time*7)*10/16d)).add(radial.scale(Math.abs(Math.sin(wobble))*(20+40*ratio)/16));
                blade(draw,"hot_extra",pos,phantom,a-angle,scale,.8,true,15728880);
            }
        }else if(mode==COLD){
            Vec3 hilt=(combo==2?at:center).add(dir.scale(10/16d));blade(draw,"item",hilt,f,angle,scale,1,false,light);
            Vec3 glow=(combo==2?at:center).add(dir.scale(28/16d*scale));
            if(combo==2)draw.region(texture("cold_thrust"),glow,f,angle+Math.PI/4,scale,0,114,color(.9),0,116*s.getInt("Variant"),114,114,true,15728880);
            else blade(draw,combo==0?"cold_small":"cold_big",glow,f,angle,scale,.8,true,15728880);
        }else if(mode==EVIL){
            blade(draw,"item",at.add(dir.scale(10/16d)),f,angle,scale,1,false,light);
            blade(draw,"evil",at.add(dir.scale(24/16d*scale)),f,angle,scale,.9,true,15728880);
        }else if(mode==TROPICAL&&s.getDouble("Age")>0){
            String tex=texture("tropical");
            Vec3 bottom=at.add(f.direction(angle+Math.PI/4).scale(-NativeSprites.width(tex)*.375/16)).add(f.direction(angle+Math.PI*3/4).scale(NativeSprites.height(tex)*.375/16));
            Vec3 c1=vector(s,"CP1"),c2=vector(s,"CP2"),prev=center;
            double progress=s.getInt("Coyote")>0?0:(s.getDouble("Age")-63)/27,opacity=s.getInt("State")==0?1:1-exp(clamp(progress));
            for(int i=1;i<30;i++){
                Vec3 point=BrokenBiomeEntity.bezier(center,c1,c2,bottom,i/29d),delta=point.subtract(prev);
                if(delta.lengthSqr()>1e-8){AttackFrame line=net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.aimFrame(delta.normalize(),f.right());
                    draw.region(texture("vine"),point,line,-Math.PI/2,1,delta.length()*16/NativeSprites.height(texture("vine")),NativeSprites.width(texture("vine"))/2d,NativeSprites.height(texture("vine")),color(opacity),0,0,NativeSprites.width(texture("vine")),NativeSprites.height(texture("vine")),false,light);}
                prev=point;
            }
            Vec3 tangent=bottom.subtract(BrokenBiomeEntity.bezier(center,c1,c2,bottom,28/29d));double bladeAngle=f.angle(tangent)+(s.getInt("Coyote")>0?Math.PI:0);
            blade(draw,"tropical",bottom,f,bladeAngle,scale,1,false,light);
            blade(draw,"tropical_glow",bottom,f,bladeAngle,scale,.9,true,15728880);
        }
    }
    private static float[] color(double a){return new float[]{(float)a,(float)a,(float)a,(float)a};}
    private static void blade(TrueBiomeDraw d,String key,Vec3 at,AttackFrame f,double angle,double scale,double opacity,boolean additive,int light){
        String name=texture(key);d.sprite(name,at,f,angle+Math.PI/4,scale,scale,0,NativeSprites.height(name),color(opacity),additive,!additive,light);
    }
}
