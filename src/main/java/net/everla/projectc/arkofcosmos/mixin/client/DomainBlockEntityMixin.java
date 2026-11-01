package net.everla.projectc.arkofcosmos.mixin.client;

import net.everla.projectc.arkofcosmos.client.DomainEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class DomainBlockEntityMixin {
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true)
    private MultiBufferSource ark$blackBlockEntities(MultiBufferSource buffers){return DomainEffects.blackBlocks(buffers);}
}
