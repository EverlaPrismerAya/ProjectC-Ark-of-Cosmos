package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** RF's Atonement Voucher clears nearby Apollyons with hurt()+kill(); Satsuki's damage/removal guards need the command-removal path. */
@Pseudo
@Mixin(targets="com.mega.revelationfix.common.item.other.AtonementVoucher")
public abstract class SatsukiAtonementMixin {
    @WrapOperation(method={"use","m_7203_"},at={@At(value="INVOKE",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=false),@At(value="INVOKE",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=false)},remap=false,require=0)
    private boolean ark$skipHurt(Apostle apostle,DamageSource source,float amount,Operation<Boolean> original){
        return apostle instanceof SatsukiBoss?false:original.call(apostle,source,amount);
    }
    @WrapOperation(method={"use","m_7203_"},at={@At(value="INVOKE",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;kill()V",remap=false),@At(value="INVOKE",target="Lcom/Polarice3/Goety/common/entities/boss/Apostle;m_6074_()V",remap=false)},remap=false,require=0)
    private void ark$clearSatsuki(Apostle apostle,Operation<Void> original){
        if(apostle instanceof SatsukiBoss boss)boss.removeByPlayerCommand();else original.call(apostle);
    }
}
