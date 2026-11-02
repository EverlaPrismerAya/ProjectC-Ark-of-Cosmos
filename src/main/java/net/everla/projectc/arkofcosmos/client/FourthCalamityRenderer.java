package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

public final class FourthCalamityRenderer extends HumanoidMobRenderer<FourthCalamityEntity,PlayerModel<FourthCalamityEntity>> {
    private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("arkofcosmos","textures/entity/kira_official.png");
    public FourthCalamityRenderer(EntityRendererProvider.Context context) {
        super(context,new PlayerModel<FourthCalamityEntity>(context.bakeLayer(ModelLayers.PLAYER_SLIM),true) {
            @Override public void setupAnim(FourthCalamityEntity boss,float swing,float amount,float age,float yaw,float pitch) {
                rightArmPose=boss.getMainHandItem().is(Items.CROSSBOW)?(boss.isUsingItem()?HumanoidModel.ArmPose.CROSSBOW_CHARGE:HumanoidModel.ArmPose.CROSSBOW_HOLD):
                    boss.isUsingItem()?(boss.getMainHandItem().is(Items.TRIDENT)?HumanoidModel.ArmPose.THROW_SPEAR:HumanoidModel.ArmPose.BLOCK):HumanoidModel.ArmPose.ITEM;
                leftArmPose=HumanoidModel.ArmPose.EMPTY;
                super.setupAnim(boss,swing,amount,age,yaw,pitch);
                if(boss.defeated()) {
                    // Peter's prone death silhouette: head sideways, hands behind the back, uneven legs.
                    head.xRot=.1f;head.yRot=1.25f;head.zRot=0;
                    body.xRot=body.yRot=body.zRot=0;
                    rightArm.xRot=.65f;leftArm.xRot=.45f;
                    rightArm.yRot=-.65f;leftArm.yRot=.5f;
                    rightArm.zRot=-.4f;leftArm.zRot=.3f;
                    rightLeg.xRot=.08f;leftLeg.xRot=-.2f;
                    rightLeg.yRot=0;leftLeg.yRot=.2f;
                    rightLeg.zRot=-.08f;leftLeg.zRot=.35f;
                    hat.copyFrom(head);jacket.copyFrom(body);leftPants.copyFrom(leftLeg);rightPants.copyFrom(rightLeg);
                } else if(boss.maceWindup())rightArm.xRot=-2.6f;
                leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);
            }
        },.5f);
        addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)),context.getModelManager()));
    }
    @Override protected void setupRotations(FourthCalamityEntity boss,PoseStack pose,float age,float yaw,float partial) {
        super.setupRotations(boss,pose,age,yaw,partial);
        if(boss.defeated()) {
            float fall=Mth.clamp((boss.defeatTicks()+partial)/12f,0,1);
            fall=fall*fall*(3-2*fall);
            pose.translate(0,.18f*fall,0);pose.mulPose(Axis.XP.rotationDegrees(-90*fall));
        } else if(boss.isFallFlying()) {
            float ticks=boss.getFallFlyingTicks()+partial;
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(ticks*ticks/100,0,1)*(-90-boss.getXRot())));
        }
    }
    @Override public ResourceLocation getTextureLocation(FourthCalamityEntity boss) { return SKIN; }
}
