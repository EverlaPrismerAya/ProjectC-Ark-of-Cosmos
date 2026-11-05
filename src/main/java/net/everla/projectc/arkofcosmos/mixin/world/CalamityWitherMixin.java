package net.everla.projectc.arkofcosmos.mixin.world;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.everla.projectc.arkofcosmos.combat.CalamityVariants;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(WitherSkullBlock.class)
public class CalamityWitherMixin {
    @Unique private static final ThreadLocal<Boolean> ark$special=new ThreadLocal<>();
    @WrapOperation(method="checkSpawn",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/CarvedPumpkinBlock;clearPatternBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/pattern/BlockPattern$BlockPatternMatch;)V"))
    private static void ark$heads(Level level,BlockPattern.BlockPatternMatch match,Operation<Void> original) {
        boolean special=false;
        for(int x=0;x<3;x++) {
            var block=level.getBlockEntity(match.getBlock(x,0,0).getPos());
            if(block!=null&&block.getPersistentData().getBoolean(CalamityVariants.VARIANT))special=true;
        }
        ark$special.set(special);
        original.call(level,match);
    }
    @WrapOperation(method="checkSpawn",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean ark$spawn(Level level,Entity entity,Operation<Boolean> original) {
        if(Boolean.TRUE.equals(ark$special.get()))entity.getPersistentData().putBoolean(CalamityVariants.VARIANT,true);
        ark$special.remove();
        return original.call(level,entity);
    }
}
