package net.everla.projectc.arkofcosmos.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.everla.projectc.arkofcosmos.client.DomainEffects;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class DomainWorldMixin {
    @Unique private float[] ark$domainColor;
    @Inject(method="renderChunkLayer",at=@At("HEAD"))
    private void ark$blackBlocks(CallbackInfo ci){
        if(DomainEffects.inside()){
            ark$domainColor=RenderSystem.getShaderColor().clone();
            RenderSystem.setShaderColor(0,0,0,1);
        }
    }
    @Inject(method="renderChunkLayer",at=@At("RETURN"))
    private void ark$restoreBlocks(CallbackInfo ci){
        if(ark$domainColor!=null){
            RenderSystem.setShaderColor(ark$domainColor[0],ark$domainColor[1],ark$domainColor[2],ark$domainColor[3]);
            ark$domainColor=null;
        }
    }
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void ark$domainFrame(CallbackInfo ci){DomainEffects.beginFrame();}
}
