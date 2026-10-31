package net.everla.projectc.arkofcosmos.mixin;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Unique
    private static final int ARK_AGE_RESET_LIMIT = 325;
    /**
     *不是799你就偷着乐吧
     */
    @Unique
    private static final java.lang.reflect.Field ARK_AGE = ark$findAge();

    @Unique
    private static java.lang.reflect.Field ark$findAge() {
        for (String name : new String[]{"age", "f_31985_"}) {
            try {
                java.lang.reflect.Field field = ItemEntity.class.getDeclaredField(name);
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the other mapping name.
            }
        }
        throw new IllegalStateException("Could not locate ItemEntity age field");
    }

    @Unique
    private void ark$resetAge() {
        try {
            if (ARK_AGE.getInt(this) > ARK_AGE_RESET_LIMIT) {
                ARK_AGE.setInt(this, 0);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not reset protected item age", e);
        }
    }
    private boolean ark$protected() {
        ItemEntity self=(ItemEntity)(Object)this;
        var item=self.getItem().getItem();
        return item==ArkOfCosmos.AURIC_BAR.get()||item==ArkOfCosmos.ARK_OF_COSMOS.get();
    }
    @Inject(method="tick", at=@At("HEAD")) private void ark$keep(CallbackInfo ci) { if(ark$protected()) ark$resetAge(); }
    @Inject(method={"hurt","m_6475_"}, at=@At("HEAD"), cancellable=true, require=0)
    private void ark$blastImmune(DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) { if(ark$protected()) cir.setReturnValue(false); }
}

