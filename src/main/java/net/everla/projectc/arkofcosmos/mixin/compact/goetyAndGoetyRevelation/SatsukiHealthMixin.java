package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
/** GR overrides the final vanilla getter globally, bypassing attribute dispatch. */
@Mixin(value=LivingEntity.class,priority=-5000)
public abstract class SatsukiHealthMixin {
    @WrapMethod(method="getMaxHealth")
    private float ark$satsukiMaxHealth(Operation<Float> original){
        return (Object)this instanceof SatsukiBoss?(float)((LivingEntity)(Object)this).getAttributeValue(Attributes.MAX_HEALTH):original.call();
    }
}
