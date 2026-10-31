package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Shadow private int age;
    private boolean ark$protected() {
        ItemEntity self=(ItemEntity)(Object)this;
        var item=self.getItem().getItem();
        return item==ArkOfCosmos.AURIC_BAR.get()||item==ArkOfCosmos.ARK_OF_COSMOS.get();
    }
    @Inject(method="tick", at=@At("HEAD")) private void ark$keep(CallbackInfo ci) { if(ark$protected()) age=0; }
    @Inject(method={"hurt","m_6475_"}, at=@At("HEAD"), cancellable=true, require=0)
    private void ark$blastImmune(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) { if(ark$protected()) cir.setReturnValue(false); }
}
