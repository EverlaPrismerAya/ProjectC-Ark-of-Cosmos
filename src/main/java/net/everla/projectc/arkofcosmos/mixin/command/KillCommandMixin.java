package net.everla.projectc.arkofcosmos.mixin.command;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import java.util.Collection;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(KillCommand.class)
abstract class KillCommandMixin {
    @WrapOperation(method="kill", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;kill()V"))
    private static void ark$playerKill(Entity target, Operation<Void> original, CommandSourceStack source, Collection<? extends Entity> targets){
        // withEntity (/execute as) changes the executor, but preserves the original source.
        if(target instanceof MahouTsukaiEntity boss && source.source instanceof ServerPlayer)
            boss.removeByPlayerCommand();
        else original.call(target);
    }
}
