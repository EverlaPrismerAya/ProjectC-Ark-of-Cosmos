package net.everla.projectc.arkofcosmos.mixin.entity.combat;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.everla.projectc.arkofcosmos.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class VanillaPlayerAttackMixin {
    @org.spongepowered.asm.mixin.Unique private float aocCritical=1;
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraftforge/common/ForgeHooks;getCriticalHit(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)Lnet/minecraftforge/event/entity/player/CriticalHitEvent;",remap=false))
    private net.minecraftforge.event.entity.player.CriticalHitEvent critical(Player player,Entity target,boolean vanilla,float modifier) {
        var result=net.minecraftforge.common.ForgeHooks.getCriticalHit(player,target,vanilla,modifier);
        aocCritical=result==null?1:result.getDamageModifier();return result;
    }
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void spearAttack(Entity target,CallbackInfo ci) {
        Player player=(Player)(Object)this;
        if(player.getMainHandItem().getItem() instanceof SpearItem) { SpearCombat.jab(player);ci.cancel(); }
    }
    @Redirect(method="attack",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",ordinal=0))
    private boolean maceDamage(Entity target,DamageSource source,float amount) {
        Player player=(Player)(Object)this;
        if(player.getMainHandItem().getItem() instanceof MaceItem&&MaceCombat.canSmash(player)) {
            amount+=MaceCombat.bonus(player.fallDistance,VanillaCombat.enchant(player.getMainHandItem(),VanillaCombat.DENSITY))*aocCritical;
            source=VanillaCombat.source(player,"mace_smash");
        }
        return target.hurt(source,amount);
    }
}
