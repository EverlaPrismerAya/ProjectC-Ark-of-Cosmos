package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.everla.projectc.arkofcosmos.entity.MahouInfernumSummonEntity;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.everla.projectc.arkofcosmos.calamity.client.GalaxiaRenderer;

/** Reuses the same baked vanilla models as Calamitas, plus the requested demon replacements. */
public final class MahouInfernumSummonRenderer extends EntityRenderer<MahouInfernumSummonEntity> {
    private final net.minecraft.client.model.PlayerModel<MahouInfernumSummonEntity> brotherModel;
    private final Map<MahouInfernumSummonEntity.Kind,ModelPart> models=new EnumMap<>(MahouInfernumSummonEntity.Kind.class);
    public MahouInfernumSummonRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=.5f;
        brotherModel=new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);
        models.put(MahouInfernumSummonEntity.Kind.WORM,context.bakeLayer(ModelLayers.RAVAGER));models.put(MahouInfernumSummonEntity.Kind.SEGMENT,context.bakeLayer(ModelLayers.RAVAGER));
        models.put(MahouInfernumSummonEntity.Kind.SEEKER,context.bakeLayer(ModelLayers.VEX));models.put(MahouInfernumSummonEntity.Kind.SHADOW,context.bakeLayer(ModelLayers.BLAZE));models.put(MahouInfernumSummonEntity.Kind.BOMBER,context.bakeLayer(ModelLayers.HOGLIN));
    }
    @Override public ResourceLocation getTextureLocation(MahouInfernumSummonEntity e){
        if(e.kind()==MahouInfernumSummonEntity.Kind.CATACLYSM||e.kind()==MahouInfernumSummonEntity.Kind.CATASTROPHE)
            return ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/entity/"+(e.kind()==MahouInfernumSummonEntity.Kind.CATACLYSM?"mahou_saint":"mahou_fox")+".png");
        String name=switch(e.kind()){
        case WORM,SEGMENT->"illager/ravager";case CATACLYSM->"illager/pillager";case CATASTROPHE->"illager/vindicator";case SEEKER->"illager/vex";case SHADOW->"blaze";case BOMBER->"hoglin/hoglin";};return ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/"+name+".png");}
    @Override public boolean shouldRender(MahouInfernumSummonEntity e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){
        double radius=e.kind()==MahouInfernumSummonEntity.Kind.CATACLYSM||e.kind()==MahouInfernumSummonEntity.Kind.CATASTROPHE?e.shieldDiameter()/2:(e.kind()==MahouInfernumSummonEntity.Kind.SEEKER||e.kind()==MahouInfernumSummonEntity.Kind.SHADOW)?e.getBbWidth()*1.5:0;
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z))&&f.isVisible(e.getBoundingBox().inflate(radius));
    }
    @Override public void render(MahouInfernumSummonEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(e.kind()==MahouInfernumSummonEntity.Kind.CATACLYSM||e.kind()==MahouInfernumSummonEntity.Kind.CATASTROPHE){
            var saved=new PoseStack();saved.last().pose().set(pose.last().pose());saved.last().normal().set(pose.last().normal());
            net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.matrix=saved.last().pose();
            net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.camera=e.getPosition(partial);
            net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.fadeEye=null;
            if(net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat.defer(()->{
                var immediate=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
                render(e,yaw,partial,saved,immediate,light);immediate.endBatch();
            },true))return;
        }
        if(e.view().isEmpty())return;
        if(e.kind()==MahouInfernumSummonEntity.Kind.CATACLYSM||e.kind()==MahouInfernumSummonEntity.Kind.CATASTROPHE){
            brotherModel.setAllVisible(true);brotherModel.young=false;brotherModel.riding=false;brotherModel.crouching=false;brotherModel.attackTime=0;
            brotherModel.setupAnim(e,0,0,e.sourceAge()+partial*3,0,Mth.lerp(partial,e.xRotO,e.getXRot()));
            pose.pushPose();pose.translate(0,(e.getBbHeight()-1.875)/2,0);
            pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,e.yRotO,e.getYRot())));
            pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.5,0);
            brotherModel.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e))),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
            pose.popPose();
            if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
            CalamitasShieldRenderer.renderBrother(e,partial,pose);super.render(e,yaw,partial,pose,buffers,light);return;
        }
        var model=models.get(e.kind());model.getAllParts().forEach(ModelPart::resetPose);
        float age=e.sourceAge()+partial*3;
        if(e.kind()==MahouInfernumSummonEntity.Kind.SHADOW){
            double angle=age*Math.PI*-.1;
            for(int i=0;i<12;i++){
                var rod=model.getChild("part"+i);int ring=i/4;
                double a=ring==0?angle+i*Math.PI/2:ring==1?Math.PI/4+age*Math.PI*.03+(i-4)*Math.PI/2:.47123894+age*Math.PI*-.05+(i-8)*Math.PI/2;
                double radius=ring==0?9:ring==1?7:5;
                rod.x=(float)(Math.cos(a)*radius);rod.z=(float)(Math.sin(a)*radius);rod.y=(ring==0?-2:ring==1?2:11)+(float)Math.cos((i*2+age)*.25);
            }
        }
        if(e.kind()==MahouInfernumSummonEntity.Kind.WORM||e.kind()==MahouInfernumSummonEntity.Kind.SEGMENT||e.kind()==MahouInfernumSummonEntity.Kind.BOMBER){
            float stride=(float)Math.cos(age*.12)*.18f;
            model.getChild("right_hind_leg").xRot=stride;
            model.getChild("left_hind_leg").xRot=-stride;model.getChild("right_front_leg").xRot=-stride;model.getChild("left_front_leg").xRot=stride;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,e.yRotO,e.getYRot())));
        if(e.kind()==MahouInfernumSummonEntity.Kind.WORM||e.kind()==MahouInfernumSummonEntity.Kind.SEGMENT){
            float scale=e.getBbWidth()/1.95f;
            pose.translate(0,e.getBbHeight()/2,0);
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,e.xRotO,e.getXRot())));
            pose.scale(-scale,-scale,scale);pose.translate(0,-.4,0);
            model.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e))),light,OverlayTexture.NO_OVERLAY);
            pose.popPose();super.render(e,yaw,partial,pose,buffers,light);return;
        }
        if(e.kind()==MahouInfernumSummonEntity.Kind.SEEKER){
            var body=model.getChild("root").getChild("body");body.xRot=.15707964f;
            body.getChild("right_arm").zRot=(float)Math.PI/5;body.getChild("left_arm").zRot=-(float)Math.PI/5;
            var left=body.getChild("left_wing");var right=body.getChild("right_wing");
            left.yRot=1.0995574f+Mth.cos(age*.8f)*Mth.DEG_TO_RAD*16.2f;right.yRot=-left.yRot;
            left.xRot=right.xRot=.47123888f;left.zRot=-.47123888f;right.zRot=.47123888f;
        }
        if(e.kind()==MahouInfernumSummonEntity.Kind.BOMBER){pose.scale(.5f,.5f,.5f);pose.translate(0,1.5,0);model.getChild("head").xScale=model.getChild("head").yScale=model.getChild("head").zScale=1.5f;}
        pose.scale(-1,-1,1);pose.translate(0,-1.5,0);
        model.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e))),light,OverlayTexture.NO_OVERLAY);pose.popPose();
        if(e.kind()==MahouInfernumSummonEntity.Kind.BOMBER&&age>=90){
            Vec3 at=e.getPosition(partial),v=net.everla.projectc.arkofcosmos.entity.MahouInfernumAttackEntity.vector(e.view(),"Velocity");
            var frame=GalaxiaRenderer.lineFrame(v,NativeSprites.billboard());var draw=new NativeSprites.Draw(pose,buffers,at,-1);
            var samples=e.view().getList("Samples",10);
            for(int i=0;i<samples.size();i++)draw.centered("fx/galaxia_bolt",net.everla.projectc.arkofcosmos.entity.MahouInfernumAttackEntity.vector(samples.getCompound(i),"Position").add(0,e.getBbHeight()/2,0),frame,0,.65,.65,ArkEffects.mul(ArkEffects.CORNFLOWER,(i+1d)/samples.size()),true);
        }
        if(e.kind()==MahouInfernumSummonEntity.Kind.SEEKER||e.kind()==MahouInfernumSummonEntity.Kind.SHADOW){if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();CalamitasShieldRenderer.renderSeeker(e,partial,pose);}
        super.render(e,yaw,partial,pose,buffers,light);
    }
}
