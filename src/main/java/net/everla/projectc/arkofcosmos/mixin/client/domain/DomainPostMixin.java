package net.everla.projectc.arkofcosmos.mixin.client.domain;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.DomainEffects;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class DomainPostMixin {
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void ark$domainGray(float partial,long time,PoseStack pose,CallbackInfo ci){DomainEffects.grayscale(partial);}
}
