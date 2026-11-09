package net.everla.projectc.arkofcosmos.client;
import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import com.Polarice3.Goety.client.render.ModModelLayer;
import com.Polarice3.Goety.client.render.model.ApostleModel;
import com.Polarice3.Goety.common.entities.hostile.cultists.Cultist;
import net.minecraft.client.model.geom.ModelPart;
import com.Polarice3.Goety.client.render.ApostleRenderer;
import com.Polarice3.Goety.client.render.ModRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
public final class SatsukiRinRenderer extends HumanoidMobRenderer<SatsukiRinEntity,PlayerModel<SatsukiRinEntity>> {
    public SatsukiRinRenderer(EntityRendererProvider.Context c){super(c,new SatsukiModel(c.bakeLayer(ModelLayers.PLAYER_SLIM),c.bakeLayer(ModModelLayer.APOSTLE)),.4f);addLayer(new ItemInHandLayer<SatsukiRinEntity,PlayerModel<SatsukiRinEntity>>(this,c.getItemInHandRenderer()){
        @Override protected void renderArmWithItem(net.minecraft.world.entity.LivingEntity entity,net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.ItemDisplayContext context,net.minecraft.world.entity.HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light){
            // Substitute only the rendered weapon; equipment, bow checks and native attack AI stay authoritative.
            if(arm!=entity.getMainArm())return;
            var rin=(SatsukiRinEntity)entity;
            var shown=rin.isSpellcasting()?new net.minecraft.world.item.ItemStack(net.everla.projectc.arkofcosmos.BejeweledContent.STAFF.get()):rin.getMainHandItem();
            if(!rin.isSpellcasting()){
                var bow=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("goety_revelation","bow_of_revelation"));
                if(bow!=null)shown=new net.minecraft.world.item.ItemStack(bow);
            }
            if(shown.isEmpty())return;
            pose.pushPose();
            getParentModel().translateToHand(arm,pose);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            boolean left=arm==net.minecraft.world.entity.HumanoidArm.LEFT;
            pose.translate((left?-1:1)/16f,.125,-.625);
            Minecraft.getInstance().getItemRenderer().renderStatic(entity,shown,context,left,pose,buffers,entity.level(),light,OverlayTexture.NO_OVERLAY,entity.getId());
            pose.popPose();
        }
    });addLayer(new HaloLayer(this,new ApostleRenderer(c)));}
    @Override public ResourceLocation getTextureLocation(SatsukiRinEntity e){return ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/entity/satsuki_rin.png");}
    @Override public boolean shouldRender(SatsukiRinEntity e,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        return super.shouldRender(e,frustum,x,y,z)||e.domainScale(1)>0&&frustum.isVisible(e.getBoundingBox().inflate(17));
    }
    @Override public void render(SatsukiRinEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float scale=e.domainScale(partial);
        pose.pushPose();
        if(scale>0){
            SatsukiFumoClient.draw(pose,buffers,light,OverlayTexture.NO_OVERLAY,7,8*scale,
                    net.minecraft.util.Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot));
        }
        super.render(e,yaw,partial,pose,buffers,light);
        pose.popPose();
    }
    @Override protected void setupRotations(SatsukiRinEntity e,PoseStack pose,float age,float yaw,float partial){
        if(e.domainScale(partial)>0){
            // RenderLivingEvent stays at the original origin so GR/RF's range frame never moves.
            pose.translate(0,8*e.domainScale(partial)-.75,0);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-yaw));
        }
        else super.setupRotations(e,pose,age,yaw,partial);
    }

    private static final class HaloLayer extends RenderLayer<SatsukiRinEntity,PlayerModel<SatsukiRinEntity>> {
        private final ApostleRenderer nativeRenderer;
        HaloLayer(SatsukiRinRenderer parent,ApostleRenderer nativeRenderer){super(parent);this.nativeRenderer=nativeRenderer;}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,SatsukiRinEntity entity,float swing,float amount,float partial,float age,float yaw,float pitch){
            if(entity.isInvisible())return;
            var skin=(SatsukiModel)getParentModel();
            var texture=nativeRenderer.getTextureLocation(entity);
            pose.pushPose();
            skin.head.translateAndRotate(pose);
            skin.apostle.halo.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,OverlayTexture.NO_OVERLAY);
            var glow=ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),texture.getPath().replace(".png","_glow.png"));
            if(!entity.isDeadOrDying()&&Minecraft.getInstance().getResourceManager().getResource(glow).isPresent())
                skin.apostle.halo.render(pose,buffers.getBuffer(ModRenderType.wraith(glow)),15728640,OverlayTexture.NO_OVERLAY,1,1,1,.15f);
            pose.popPose();
        }
    }

    /** Run the installed Goety/GR model's animation without rendering its geometry. */
    static final class SatsukiModel extends PlayerModel<SatsukiRinEntity> {
        private final ApostleModel<SatsukiRinEntity> apostle;
        SatsukiModel(ModelPart skin,ModelPart nativeRoot){super(skin,true);apostle=new ApostleModel<>(nativeRoot);}
        @Override public void prepareMobModel(SatsukiRinEntity entity,float swing,float amount,float partialTick){
            riding=entity.domainScale(partialTick)>0||riding;
            super.prepareMobModel(entity,swing,amount,partialTick);
            apostle.attackTime=attackTime;apostle.riding=riding;apostle.young=young;
            apostle.prepareMobModel(entity,swing,amount,partialTick);
        }
        @Override public void setupAnim(SatsukiRinEntity entity,float swing,float amount,float age,float yaw,float pitch){
            super.setupAnim(entity,swing,amount,age,yaw,pitch);
            apostle.setupAnim(entity,swing,amount,age,yaw,pitch);
            if(entity.getArmPose()!=Cultist.CultistArmPose.CROSSED){
                // Keep the slim model's shoulder pivots and three-pixel arm geometry.
                rotation(rightArm,apostle.rightArm);rotation(leftArm,apostle.leftArm);
                rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);
                if(entity.getArmPose()==Cultist.CultistArmPose.DYING){rotation(head,apostle.head);hat.copyFrom(head);}
            }
            if(entity.domainScale(1)>0){
                head.xRot=0;head.zRot=0;hat.copyFrom(head);
                rightArm.xRot=-.35f;leftArm.xRot=-.35f;
                rightArm.zRot=.1f;leftArm.zRot=-.1f;
                rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);
            }
        }
        private static void rotation(ModelPart skin,ModelPart nativePart){skin.xRot=nativePart.xRot;skin.yRot=nativePart.yRot;skin.zRot=nativePart.zRot;}
    }
}
