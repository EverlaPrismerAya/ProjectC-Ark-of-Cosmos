package net.everla.projectc.arkofcosmos.mixin.client.spear;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.everla.projectc.arkofcosmos.client.vanilla.SpearAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class SpearHumanoidMixin {
    @Shadow public ModelPart rightArm;@Shadow public ModelPart leftArm;@Shadow public ModelPart head;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void pose(LivingEntity e,float walk,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(!e.isUsingItem()||!(e.getUseItem().getItem() instanceof SpearItem spear))return;
        boolean right=(e.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND)==(e.getMainArm()==HumanoidArm.RIGHT);
        SpearAnimations.thirdHand(right?rightArm:leftArm,head,right,spear.material,e.getTicksUsingItem()+age-e.tickCount,e.isFallFlying()||e.getSwimAmount(0)>0);
    }
    @Inject(method="setupAttackAnimation",at=@At("HEAD"),cancellable=true)
    private void attack(LivingEntity e,float age,CallbackInfo ci) {
        if(!(e.getMainHandItem().getItem() instanceof SpearItem))return;
        HumanoidModel<?> model=(HumanoidModel<?>)(Object)this;
        if(model.attackTime>0) {
            ModelPart arm=e.getMainArm()==HumanoidArm.RIGHT?rightArm:leftArm;
            float f=net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(model.attackTime)*(float)Math.PI*2)*.2f;
            if(e.getMainArm()==HumanoidArm.LEFT)f=-f;
            model.body.yRot=f;
            rightArm.z=net.minecraft.util.Mth.sin(f)*5;rightArm.x=-net.minecraft.util.Mth.cos(f)*5;
            leftArm.z=-net.minecraft.util.Mth.sin(f)*5;leftArm.x=net.minecraft.util.Mth.cos(f)*5;
            arm.xRot+=SpearAnimations.stabArm(model.attackTime);
        }
        ci.cancel();
    }
}
