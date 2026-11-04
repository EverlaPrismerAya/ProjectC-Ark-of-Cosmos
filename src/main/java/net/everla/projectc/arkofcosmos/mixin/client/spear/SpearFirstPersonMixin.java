package net.everla.projectc.arkofcosmos.mixin.client.spear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.everla.projectc.arkofcosmos.client.vanilla.SpearAnimations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public abstract class SpearFirstPersonMixin {
    @Shadow public abstract void renderItem(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffer,int light);
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void spear(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {
        if(!(stack.getItem() instanceof SpearItem))return;
        HumanoidArm arm=hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();int invert=arm==HumanoidArm.RIGHT?1:-1;
        pose.pushPose();
        if(player.isUsingItem()&&player.getUsedItemHand()==hand) {
            pose.translate(invert*.56f,-.52f,-.72f);SpearAnimations.firstPersonUse(net.everla.projectc.arkofcosmos.client.vanilla.VanillaClient.feedbackAge(player,partial),pose,stack.getUseDuration()-(player.getUseItemRemainingTicks()-partial+1),arm,stack);
        } else {
            pose.translate(invert*.56f,-.52f-equip*.6f,-.72f);SpearAnimations.firstPersonAttack(swing,pose,invert,arm);
        }
        renderItem(player,stack,arm==HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND,arm==HumanoidArm.LEFT,pose,buffer,light);
        pose.popPose();ci.cancel();
    }
}
