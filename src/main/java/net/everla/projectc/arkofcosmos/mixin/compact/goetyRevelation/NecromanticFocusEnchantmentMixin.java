package net.everla.projectc.arkofcosmos.mixin.compact.goetyRevelation;

import net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Goety additionally restricts focus enchantments to its own item types. */
@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.enchantments.FocusEnchantments",remap=false)
public abstract class NecromanticFocusEnchantmentMixin {
    @Inject(method="canApplyAtEnchantingTable",at=@At("HEAD"),cancellable=true)
    private void ark$necromanticWeapon(ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        if(stack.getItem() instanceof NecromanticWeaponItem item)
            cir.setReturnValue(item.canApplyAtEnchantingTable(stack,(Enchantment)(Object)this));
    }
}
