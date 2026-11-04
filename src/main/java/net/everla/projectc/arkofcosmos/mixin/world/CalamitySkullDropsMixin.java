package net.everla.projectc.arkofcosmos.mixin.world;

import net.everla.projectc.arkofcosmos.combat.CalamityVariants;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(Block.class)
public class CalamitySkullDropsMixin {
    @Inject(method="getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",at=@At("RETURN"))
    private static void ark$preserve(BlockState state,ServerLevel level,BlockPos pos,BlockEntity block,Entity breaker,ItemStack tool,CallbackInfoReturnable<List<ItemStack>> cir) {
        if(block==null||!block.getPersistentData().getBoolean(CalamityVariants.VARIANT))return;
        for(var stack:cir.getReturnValue())if(stack.is(Items.WITHER_SKELETON_SKULL))
            stack.getOrCreateTag().merge(CalamityVariants.skull().getTag());
    }
}
