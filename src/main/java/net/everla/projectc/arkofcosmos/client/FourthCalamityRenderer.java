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
                rightArmPose=boss.getMainHandItem().is(Items.BOW)&&boss.isUsingItem()?HumanoidModel.ArmPose.BOW_AND_ARROW:
                    boss.getMainHandItem().is(Items.CROSSBOW)?(boss.isUsingItem()?HumanoidModel.ArmPose.CROSSBOW_CHARGE:HumanoidModel.ArmPose.CROSSBOW_HOLD):
                    boss.isUsingItem()?(boss.getMainHandItem().is(Items.TRIDENT)?HumanoidModel.ArmPose.THROW_SPEAR:HumanoidModel.ArmPose.BLOCK):HumanoidModel.ArmPose.ITEM;
                leftArmPose=HumanoidModel.ArmPose.EMPTY;
                super.setupAnim(boss,swing,amount,age,yaw,pitch);
                if(boss.defeated())pronePose(this);
                else if(boss.maceWindup())rightArm.xRot=-2.6f;
                leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);
            }
        },.5f);
        addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)),context.getModelManager()));
    }
    @Override protected void setupRotations(FourthCalamityEntity boss,PoseStack pose,float age,float yaw,float partial) {
        super.setupRotations(boss,pose,age,yaw,partial);
        if(boss.defeated())proneFall(pose,(boss.defeatTicks()+partial)/FALL_TICKS);
        else if(boss.isFallFlying()) {
            float ticks=boss.getFallFlyingTicks()+partial;
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(ticks*ticks/100,0,1)*(-90-boss.getXRot())));
        }
    }
    public static final float FALL_TICKS=12;
    /** Peter's prone death silhouette: head sideways, hands behind the back, uneven legs. */
    public static void pronePose(PlayerModel<?> m) {
        m.head.xRot=.1f;m.head.yRot=1.25f;m.head.zRot=0;
        m.body.xRot=m.body.yRot=m.body.zRot=0;
        m.rightArm.xRot=.65f;m.leftArm.xRot=.45f;
        m.rightArm.yRot=-.65f;m.leftArm.yRot=.5f;
        m.rightArm.zRot=-.4f;m.leftArm.zRot=.3f;
        m.rightLeg.xRot=.08f;m.leftLeg.xRot=-.2f;
        m.rightLeg.yRot=0;m.leftLeg.yRot=.2f;
        m.rightLeg.zRot=-.08f;m.leftLeg.zRot=.35f;
        m.hat.copyFrom(m.head);m.jacket.copyFrom(m.body);m.leftPants.copyFrom(m.leftLeg);m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);m.rightSleeve.copyFrom(m.rightArm);
    }
    /** Tips the body forward onto the ground; {@code progress} 0 is standing and 1 is flat. */
    public static void proneFall(PoseStack pose,float progress) {
        float fall=Mth.clamp(progress,0,1);
        fall=fall*fall*(3-2*fall);
        pose.translate(0,.18f*fall,0);pose.mulPose(Axis.XP.rotationDegrees(-90*fall));
    }
    @Override public ResourceLocation getTextureLocation(FourthCalamityEntity boss) { return SKIN; }
}
