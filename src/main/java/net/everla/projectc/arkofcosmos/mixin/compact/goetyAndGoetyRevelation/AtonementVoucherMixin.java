package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.mega.revelationfix.common.item.other.AtonementVoucher")
public abstract class AtonementVoucherMixin {
    @Inject(method = {"use", "m_7203_"}, remap = false, at = @At("HEAD"))
    private void clearCalamitas(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (!level.isClientSide)
            for (var boss : level.getEntitiesOfClass(SupremeCalamitasEntity.class, new AABB(player.blockPosition()).inflate(72)))
                boss.endBattle(false);
    }
}
