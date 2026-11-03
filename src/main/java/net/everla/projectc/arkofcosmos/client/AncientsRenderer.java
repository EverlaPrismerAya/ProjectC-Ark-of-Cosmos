package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.AncientsEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;
import static net.everla.projectc.arkofcosmos.calamity.client.ArkEffects.*;

/** Source sprites through Minecraft's native pipeline; no custom shader registration or GLSL. */
public final class AncientsRenderer extends EntityRenderer<AncientsEntity> {
    private static final String BLADE="item/ark_of_the_ancients", GLOW=BLADE+"_glow", STAR="entity/ancient_star";
    public AncientsRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(AncientsEntity e){return NativeSprites.texture(BLADE);}
    @Override public boolean shouldRender(AncientsEntity e,Frustum f,double x,double y,double z){return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z));}
    @Override public void render(AncientsEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        CompoundTag s=e.renderState(partial);if(s.isEmpty())return;
        int kind=s.getInt("Kind");double age=s.getDouble("Age"),scale=s.getDouble("Scale"),angle=s.getDouble("Rotation");
        if(kind==AncientsEntity.BEAM&&age<5 || kind==AncientsEntity.BLADE&&age>=40)return;
        var f=new AttackFrame(vector(s,"F"),vector(s,"R"));
        Vec3 base=e.getPosition(partial),at=vector(s,"Position"),center=vector(s,"Center");
        var player=e.level().getEntity(e.ownerId());if(player!=null)center=player.getPosition(partial).add(0,player.getBbHeight()/2,0);
        ArkFxDraw.matrix=pose.last().pose(); ArkFxDraw.camera=base;
        ArkFxDraw.ownerView(e.ownerId(),base);
        try {
        var draw=new net.everla.projectc.arkofcosmos.calamity.client.ElementsRenderer.Draw(new NativeSprites.Draw(pose,buffers,base,e.ownerId()));
        ListTag trail=s.getList("Trail",10);double height=NativeSprites.height(BLADE);
        double time=(e.level().getGameTime()+partial)/20;
        float[] rainbow=net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.hsl((float)(time*.6%1),1,.85f,1);
        if(kind==AncientsEntity.STAR){
            draw.centered(STAR,at,f,angle,1,1,WHITE,true);
            for(int i=0;i<trail.size();i++){var old=trail.getCompound(i);draw.centered(STAR,vector(old,"Position"),f,old.getDouble("Rotation"),1,1,mul(WHITE,(8-i)/8f*.6),true);}
            if(s.getInt("Charge")>0)sparkle(draw,at,NativeSprites.billboard(),time,1,rainbow);
            return;
        }
        if(kind==AncientsEntity.BEAM){
            for(int i=0;i<trail.size();i++){var old=trail.getCompound(i);draw.centered(GLOW,vector(old,"Position"),f,angle,scale,scale,mul(WHITE,(5-i)/5f*.6),true);}
            sparkle(draw,at.subtract(vector(s,"Velocity").normalize().scale(30.5/16d)),NativeSprites.billboard(),time,2,rainbow);
            return;
        }
        Vec3 hilt;
        if(kind==AncientsEntity.BLAST){
            hilt=center.add(f.forward().scale(10*(1+Math.sin(age/20*Math.PI)*.8)/16));
            double width=3*(age/20>.5?Math.sin(age/20*Math.PI)*.2+.8:Math.sin(age/20*Math.PI));
            float[] pink=rgb(255,182,193);double length=vector(s,"Velocity").length();
            draw.sprite("fx/bloomline",at,f,Math.PI/2,width,length*16/NativeSprites.height("fx/bloomline"),32,1,pink,true,false,15728880);
            draw.sprite("fx/bloomlinecap",at,f,Math.PI*1.5,width,width,32,32,pink,true,false,15728880);
            draw.sprite("fx/bloomlinecap",at.add(vector(s,"Velocity")),f,Math.PI/2,width,width,32,32,pink,true,false,15728880);
        }else hilt=kind==AncientsEntity.BLADE?center.add(f.direction(angle).scale(10/16d)):at;
        if(kind==AncientsEntity.BLADE&&age>10)for(int i=0;i<trail.size();i++){
            float[] color=net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.hsl(i/10f*.7f,1,s.getInt("Charge")>0?.9f:.6f,1);
            draw.sprite(GLOW,hilt,f,trail.getCompound(i).getDouble("Rotation")+Math.PI/4,scale-.2*i/10,scale-.2*i/10,0,height,mul(color,.15),true,false,15728880);
        }
        if(kind==AncientsEntity.BLADE&&s.getInt("Charge")>0&&age>20){
            double turn=(-Math.PI/8+Math.PI/8*age/40)*s.getInt("Sign");
            float[] color=net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.hsl((float)((age-20)/20*.7),1,.6f,1);
            draw.centered("fx/trientcircularsmear",center,f,Math.PI+turn,scale*1.5,scale*1.5,mul(color,.5*Math.sin(age/40*Math.PI)),true);
        }
        draw.sprite(BLADE,hilt,f,angle+Math.PI/4,scale,scale,0,height,WHITE,false,true,light);
        draw.sprite(GLOW,hilt,f,angle+Math.PI/4,scale,scale,0,height,WHITE,true,true,15728880);
        int success=net.everla.projectc.arkofcosmos.calamity.client.AncientsEffects.parriedAt(s);
        if(kind==AncientsEntity.PARRY&&success>=0){
            double ratio=Math.max(1,age-success+1)/15;
            if(ratio<1){float[] color=net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.hsl((float)(time%1),1,.8f,1);
                ArkFxDraw.sprite(GLOW,hilt.add(f.forward().scale(34/16d)),f,angle+Math.PI/4,scale+ratio,scale+ratio,0,48,mul(color,1-ratio),ArkFxDraw.Blend.ALPHA,24,0,48,48);
            }
        }
        } finally { ArkFxDraw.fadeEye=null; }
    }
    private static void sparkle(net.everla.projectc.arkofcosmos.calamity.client.ElementsRenderer.Draw d,Vec3 at,AttackFrame face,double time,double scale,float[] color){
        double ratio=(double)NativeSprites.height("fx/sparkle")/NativeSprites.height("fx/bloomcircle");
        d.centered("fx/bloomcircle",at,face,0,2*scale*ratio,2*scale*ratio,mul(color,.5),true);
        d.centered("fx/sparkle",at,face,time*8+Math.PI/4,.75*scale,.75*scale,mul(color,.5),true);
        d.centered("fx/sparkle",at,face,time*8,scale,scale,WHITE,true);
    }
}
