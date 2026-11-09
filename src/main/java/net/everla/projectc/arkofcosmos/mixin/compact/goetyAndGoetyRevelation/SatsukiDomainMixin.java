package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.everla.projectc.arkofcosmos.combat.SatsukiDomain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import z1gned.goetyrevelation.util.ApollyonAbilityHelper;

@Pseudo
@Mixin(targets="com.mega.revelationfix.common.apollyon.common.DeathPerformance",remap=false)
public abstract class SatsukiDomainMixin {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="perform",at={@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"),@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;m_82400_(D)Lnet/minecraft/world/phys/AABB;")},remap=false,require=1)
    private static net.minecraft.world.phys.AABB ark$range(net.minecraft.world.phys.AABB box,Apostle source,ApollyonAbilityHelper helper,int left){
        return source instanceof net.everla.projectc.arkofcosmos.combat.SatsukiBoss&&Math.abs(box.getXsize()-35)<.01?box.inflate(17):box;
    }
    @WrapMethod(method="perform",require=1)
    private static boolean ark$slashes(Apostle source,ApollyonAbilityHelper helper,int left,Operation<Boolean> original){
        var victims=SatsukiDomain.before(source);
        boolean result=original.call(source,helper,left);
        SatsukiDomain.after(victims);
        return result;
    }
}
