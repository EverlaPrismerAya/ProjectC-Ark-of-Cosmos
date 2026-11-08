package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/** Retain ownership before allocating GL objects, including a partially created target. */
final class EffectRenderTarget extends RenderTarget {
    private EffectRenderTarget(boolean depth){super(depth);}
    static EffectRenderTarget create(int width,int height,boolean depth){
        RenderSystem.assertOnRenderThreadOrInit();
        var target=new EffectRenderTarget(depth);
        try {
            target.resize(width,height,Minecraft.ON_OSX);
            target.setClearColor(0,0,0,0);
            return target;
        } catch(RuntimeException|Error failure){
            target.destroyBuffers();
            throw failure;
        }
    }
}
