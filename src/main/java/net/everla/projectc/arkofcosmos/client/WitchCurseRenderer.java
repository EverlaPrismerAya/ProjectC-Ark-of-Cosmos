package net.everla.projectc.arkofcosmos.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.entity.WitchCurseEntity;
import net.everla.projectc.arkofcosmos.client.render.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
/** Native Calamity sprite sheets, using the existing depth-aware sprite path. */
public final class WitchCurseRenderer extends EntityRenderer<WitchCurseEntity> {
    public WitchCurseRenderer(EntityRendererProvider.Context context){super(context);}
    private String sprite(WitchCurseEntity e){if(e.kind()==WitchCurseEntity.Kind.SMALL_EXPLOSION)return "witch_explosion";if(e.kind()==WitchCurseEntity.Kind.BOMBER && e.view().getBoolean("Friendly"))return "witch_bomber_friendly";return "witch_"+e.kind().name().toLowerCase(java.util.Locale.ROOT);}
    @Override public ResourceLocation getTextureLocation(WitchCurseEntity e){return ArkFxDraw.texture(e.kind()==WitchCurseEntity.Kind.HEAL||e.kind()==WitchCurseEntity.Kind.HEART?"calamitas_dust_5_0":sprite(e));}
    @Override public void render(WitchCurseEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(e.kind()==WitchCurseEntity.Kind.ARM && e.getOwner()==mc.player && mc.player!=null
                && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity()==mc.player)return;
        int age=e.view().getInt("Age");
        ArkFxDraw.matrix=pose.last().pose();ArkFxDraw.camera=e.getPosition(partial);ArkFxDraw.fadeEye=null;
        Vec3 at=e.getPosition(partial);
        if(e.kind()==WitchCurseEntity.Kind.PORTAL){
            float opacity=Math.min(1,age/30f)*Math.max(0,Math.min(1,(420-age)/30f));
            portal(at,age*.18f,opacity,new float[]{.45f,.45f,.45f},1.8f);
            portal(at,-age*.18f,opacity,new float[]{.45f,.45f,.45f},1.8f);
            portal(at,age*.108f,opacity,new float[]{.247f,.153f,.247f},1.6f);
            portal(at,-age*.108f,opacity,new float[]{1,.45f,.45f},1.6f);
        }else if(e.kind()==WitchCurseEntity.Kind.ORB){
            int frame=e.view().getInt("Frame");ArkFxDraw.litSprite("witch_orb",at,NativeSprites.billboard(),0,1,32,45,new float[]{1,1,1,1},frame/22*64,frame%22*90,64,90,light,0);
        }else if(e.kind()==WitchCurseEntity.Kind.ARM){
            var p=e.getOwner();if(p!=null){
                long left=e.view().getLong("SwingLeft");
                // The player's ordinary swing animation is also available to remote clients.
                boolean swing=p.swinging||left>0;double completion=left>0?Math.max(0,Math.min(1,1-left/25d)):p.getAttackAnim(partial);
                var geometry=net.everla.projectc.arkofcosmos.enchant.TaintedGeometry.of(p,p.getPosition(partial).add(0,p.getBbHeight()/2,0),at,e.view().getInt("Hand"),age,completion,swing);
                segment("witch_forearm",geometry.shoulder(),geometry.elbow(),light);segment("witch_arm",geometry.elbow(),geometry.hand(),light);
                draw(e.view().getInt("Hand")==0?"witch_hand":"witch_hand2",geometry.hand(),1,0,1,0,light);
                var item=p.getMainHandItem();Vec3 offset=geometry.blade().subtract(at);pose.pushPose();pose.translate(offset.x,offset.y,offset.z);net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(item,net.minecraft.world.item.ItemDisplayContext.GROUND,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,e.level(),e.getId());pose.popPose();
            }
        }else if(e.kind()!=WitchCurseEntity.Kind.HEAL&&e.kind()!=WitchCurseEntity.Kind.HEART){
            int frames=switch(e.kind()){case DEMON->5;case ORB->15;case BOMBER->12;case EXPLOSION,SMALL_EXPLOSION->6;default->1;};
            int frame=switch(e.kind()){case BOMBER->age<45?(age/6)%5:age<90?Math.min(10,5+(age-45)*5/45):11;default->(age/5)%frames;};
            float alpha=switch(e.kind()){case PORTAL->Math.min(1,age/30f)*Math.min(1,(420-age)/30f);case MANA->Math.min(1,age/30f)*Math.min(1,(300-age)/15f);default->Math.min(1,age/15f);};
            draw(sprite(e),at,alpha,frame,frames,0,light);
            if(e.kind()==WitchCurseEntity.Kind.BOMBER){
                boolean friendly=e.view().getBoolean("Friendly");draw("witch_bomber_"+(friendly?"glowmaskfriendly":"glowmask"),at,alpha,frame,12,0,15728880);
                if(age>=90){String orb="witch_bomber_"+(friendly?"orbfriendly":"orb");int[] size=ArkFxDraw.size(orb);int height=size[1]/12;float glow=Math.min(1,(age-90)/8f);
                    for(int i=0;i<12;i++){Vec3 offset=NativeSprites.billboard().direction(Math.PI*2*i/12+(e.level().getGameTime()+partial)*.1).scale(glow*3/16);float intensity=glow*.125f;ArkFxDraw.sprite(orb,at.add(offset),NativeSprites.billboard(),0,1,1,size[0]/2d,height/2d,new float[]{.878f*intensity,intensity,intensity,0},ArkFxDraw.Blend.ALPHA,0,frame*height,size[0],height,null);}
                }
            }
        }
        super.render(e,yaw,partial,pose,buffers,light);
    }
    private void portal(Vec3 at,float angle,float opacity,float[] tint,float gain){ArkFxDraw.sprite("witch_portal",at,NativeSprites.billboard(),angle,opacity*1.2,opacity*1.2,30,30,new float[]{tint[0]*opacity*gain,tint[1]*opacity*gain,tint[2]*opacity*gain,opacity*gain},ArkFxDraw.Blend.NONPREMULTIPLIED,0,0,60,60,null);}
    private void segment(String texture,Vec3 from,Vec3 to,int light){
        int[] size=ArkFxDraw.size(texture);Vec3 along=to.subtract(from).normalize();Vec3 camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();Vec3 cross=along.cross(camera.subtract(from)).normalize();if(cross.lengthSqr()<.01)cross=new Vec3(1,0,0);
        var plane=new net.everla.projectc.arkofcosmos.util.AttackFrame(cross,along);
        ArkFxDraw.sprite(texture,from.lerp(to,.5),plane,0,1,from.distanceTo(to)*16/size[1],size[0]/2d,size[1]/2d,new float[]{1,1,1,1},ArkFxDraw.Blend.ALPHA,0,0,size[0],size[1],null);
    }
    private void draw(String texture,Vec3 at,float opacity,int frame,int frames,float angle,int light){int[] size=ArkFxDraw.size(texture);int h=size[1]/frames;ArkFxDraw.litSprite(texture,at,NativeSprites.billboard(),angle,1,size[0]/2d,h/2d,new float[]{opacity,opacity,opacity,opacity},0,frame*h,size[0],h,light,0);}
}
