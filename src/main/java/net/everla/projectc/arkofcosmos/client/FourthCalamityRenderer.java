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
                    boss.isUsingItem()?HumanoidModel.ArmPose.BLOCK:HumanoidModel.ArmPose.ITEM;
                leftArmPose=HumanoidModel.ArmPose.EMPTY;
                super.setupAnim(boss,swing,amount,age,yaw,pitch);
                if(boss.maceWindup())rightArm.xRot=-2.6f;
                leftSleeve.copyFrom(leftArm);rightSleeve.copyFrom(rightArm);
            }
        },.5f);
        addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)),context.getModelManager()));
    }
    @Override protected void setupRotations(FourthCalamityEntity boss,PoseStack pose,float age,float yaw,float partial) {
        super.setupRotations(boss,pose,age,yaw,partial);
        if(boss.isFallFlying()) {
            float ticks=boss.getFallFlyingTicks()+partial;
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(ticks*ticks/100,0,1)*(-90-boss.getXRot())));
        }
    }
    @Override public ResourceLocation getTextureLocation(FourthCalamityEntity boss) { return SKIN; }
}
