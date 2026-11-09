package net.everla.projectc.arkofcosmos.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.SatsukiFumoClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Monoliths use EntityRenderer, so RenderLivingEvent alone cannot replace their geometry. */
@Mixin(EntityRenderDispatcher.class)
public abstract class SatsukiFumoRenderMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <E extends Entity> void ark$fumo(EntityRenderer<E> renderer,E entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,Operation<Void> original){
        // Satsuki's native arrows are drawn as Bejeweled gem dust instead (also skips RF's arrow trail).
        if(net.everla.projectc.arkofcosmos.client.SatsukiArrowVisuals.hides(entity))return;
        if(!SatsukiFumoClient.render(entity,partial,pose,buffers,light))original.call(renderer,entity,yaw,partial,pose,buffers,light);
    }
}
