package net.everla.projectc.arkofcosmos.mixin;

import java.util.Collection;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KillCommand.class)
abstract class KillCommandMixin {
    @Redirect(method="kill", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/Entity;kill()V"))
    private static void ark$playerKill(Entity target, CommandSourceStack source, Collection<? extends Entity> targets){
        // withEntity (/execute as) changes the executor, but preserves the original source.
        if(target instanceof MahouTsukaiEntity boss && source.source instanceof ServerPlayer)
            boss.removeByPlayerCommand();
        else target.kill();
    }
}
