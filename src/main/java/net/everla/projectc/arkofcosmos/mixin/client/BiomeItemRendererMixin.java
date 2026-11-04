package net.everla.projectc.arkofcosmos.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.BiomeItemEffects;
import net.everla.projectc.arkofcosmos.item.BiomeBladeItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class BiomeItemRendererMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void biome$inventory(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci){
        if(context==ItemDisplayContext.GUI&&stack.getItem() instanceof BiomeBladeItem)BiomeItemEffects.render(stack,pose,buffers);
    }
}
