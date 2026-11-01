package net.everla.projectc.arkofcosmos.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.everla.projectc.arkofcosmos.client.vanilla.SpearAnimations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandLayer.class)
public abstract class SpearHeldItemMixin {
    @Inject(method="renderArmWithItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void transform(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci) {
        if(!(stack.getItem() instanceof SpearItem spear))return;
        float partial=net.minecraft.client.Minecraft.getInstance().getFrameTime();
        boolean used=entity.isUsingItem()&&entity.getUseItem()==stack;
        SpearAnimations.thirdItem(pose,arm,spear.material,used?entity.getTicksUsingItem()+partial:0,entity.getAttackAnim(partial));
    }
}
