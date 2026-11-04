package net.everla.projectc.arkofcosmos.mixin.world;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;

@Mixin(BeaconMenu.class)
public class BeaconSummonMixin {
    @Shadow @Final private Container beacon;
    @Shadow @Final private ContainerLevelAccess access;
    @Inject(method="updateEffects",at=@At("HEAD"),cancellable=true)
    private void ark$summon(Optional<MobEffect> primary,Optional<MobEffect> secondary,CallbackInfo ci) {
        var stack=beacon.getItem(0);
        if(!stack.is(ArkOfCosmos.ASHES_OF_CALAMITY.get())&&!stack.is(ArkOfCosmos.AURIC_BAR.get()))return;
        access.execute((level,pos)-> {
            if(level.isClientSide||!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEACON))return;
            var boss=stack.is(ArkOfCosmos.ASHES_OF_CALAMITY.get())?ArkOfCosmos.MAHOU_TSUKAI.get().create(level):ArkOfCosmos.THE_PLAYER.get().create(level);
            if(boss==null)return;
            boss.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0);
            if(level.addFreshEntity(boss))beacon.removeItem(0,1);
        });
        ci.cancel();
    }
}
