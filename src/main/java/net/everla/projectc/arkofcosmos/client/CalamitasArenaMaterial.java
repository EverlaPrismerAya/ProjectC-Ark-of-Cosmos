package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/** Pinned two-layer screen-space Metaball composite. The caller supplies the projected wall mask.
 * sourceScreenX/Y are source-pixel camera offsets; mask and destination must have equal dimensions. */
public final class CalamitasArenaMaterial {
    private CalamitasArenaMaterial() {}

    public static void composite(RenderTarget mask, int rgb, double seconds, double sourceScreenX, double sourceScreenY) {
        composite(mask, mask, rgb, seconds, sourceScreenX, sourceScreenY);
    }
    public static void composite(RenderTarget mask, RenderTarget visible, int rgb, double seconds, double sourceScreenX, double sourceScreenY) {
        composite(mask,visible,rgb,seconds,sourceScreenX,sourceScreenY,false);
    }
    public static void compositeGhost(RenderTarget mask,RenderTarget visible,double seconds,double sourceScreenX,double sourceScreenY){composite(mask,visible,0x3D0602,seconds,sourceScreenX,sourceScreenY,true);}
    private static void composite(RenderTarget mask,RenderTarget visible,int rgb,double seconds,double sourceScreenX,double sourceScreenY,boolean ghost) {
        var shader = ArkFxDraw.arenaMetaballShader;
        if (shader == null) return;
        var previousShader = RenderSystem.getShader();
        int texture0 = RenderSystem.getShaderTexture(0), texture1 = RenderSystem.getShaderTexture(1);
        int texture2 = RenderSystem.getShaderTexture(2);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        try {
            // Reuse XNA's premultiplied AlphaBlend and the existing shader apply hook.
            ArkFxDraw.begin("calamitas_arena_smoke", ArkFxDraw.Blend.ALPHA, false);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, mask.getColorTextureId());
            RenderSystem.setShaderTexture(2, visible.getColorTextureId());
            shader.safeGetUniform("uScreenSize").set((float)mask.width, (float)mask.height);
            shader.safeGetUniform("EdgeColor").set(r, g, b, 1f);
            // Current camera offset already incorporates the source single-frame camera correction.
            shader.safeGetUniform("SingleFrameScreenOffset").set(0f, 0f);
            double time = seconds % 3600;
            for (int layer = 0; layer < (ghost?5:2); layer++) {
                String name = ghost?"gruesome_layer_"+(layer+1):layer == 0 ? "calamitas_arena_smoke" : "calamitas_arena_wave";
                var texture = ArkFxDraw.texture(name);
                var image = Minecraft.getInstance().getTextureManager().getTexture(texture);
                image.setFilter(true, false);
                RenderSystem.setShaderTexture(1, texture);
                RenderSystem.activeTexture(GL13.GL_TEXTURE1); image.bind();
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                int[] size = ArkFxDraw.size(name);
                shader.safeGetUniform("LayerSize").set((float)size[0], (float)size[1]);
                double x = layer == 0 ? Math.sin(time * .03) * 10 : 0;
                double y = layer == 0 ? Math.cos(time * .1) * 17 : time * .03;
                if(ghost){
                    switch(layer){
                        case 0->{x=time*.03;y=0;}
                        case 1->{double amount=Math.cos(time*.041)*2,angle=Math.cos(time*.08)*.97;x=amount*(Math.cos(angle)-Math.sin(angle));y=amount*(Math.sin(angle)+Math.cos(angle));}
                        case 2->{x=Math.cos(time*2.02)*.036;y=Math.sin(time*2.02)*.036+Math.cos(time*.161)*.5+.5;}
                        case 3->{x=-time*.04+Math.cos(time*1.89)*.03;y=Math.sin(time*1.89)*.03+noise(time*.187,time*.193,466920161)*.025;}
                        default->{x=time*.037+Math.cos(time*1.77)*.04725;y=Math.sin(time*1.77)*.04725+noise(time*.187,time*.193,577215664)*.05;}
                    }
                }
                shader.safeGetUniform("LayerOffset").set((float)(sourceScreenX / mask.width + x),
                        (float)(sourceScreenY / mask.height + y));
                shader.safeGetUniform("WaveLayer").set(ghost?0f:(float)layer);
                shader.safeGetUniform("LayerColor").set(ghost||layer == 0 ? 1f : r, ghost||layer == 0 ? 1f : g, ghost||layer == 0 ? 1f : b, 1f);
                RenderSystem.setShader(() -> shader);
                var out = Tesselator.getInstance().getBuilder(); out.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT);
                vertex(out, -1, -1, 0, 0); vertex(out, 1, -1, 1, 0);
                vertex(out, 1, 1, 1, 1); vertex(out, -1, 1, 0, 1);
                BufferUploader.drawWithShader(out.end());
            }
        } finally {
            ArkFxDraw.end();
            RenderSystem.setShaderTexture(0, texture0); RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShaderTexture(2, texture2);
            RenderSystem.setShader(() -> previousShader); RenderSystem.depthMask(write);
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equation, equationAlpha);
            RenderSystem.activeTexture(activeTexture);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
    private static void vertex(BufferBuilder out, float x, float y, float u, float v) {
        out.vertex(x, y, 0).color(1f, 1f, 1f, 1f);
        out.putFloat(0, u); out.putFloat(4, v); out.putFloat(8, 1);
        out.nextElement(); out.endVertex();
    }
    // CalamityUtils.PerlinNoise2D, two octaves, including the signed integer hash.
    private static double noise(double x,double y,int seed){x*=4;y*=4;int ix=(int)x,iy=(int)y;double fx=x-ix,fy=y-iy,sx=fx*fx*(3-2*fx),sy=fy*fy*(3-2*fy);double a=gradient(seed,ix,iy,fx,fy)*(1-sx)+gradient(seed,ix+1,iy,fx-1,fy)*sx,b=gradient(seed,ix,iy+1,fx,fy-1)*(1-sx)+gradient(seed,ix+1,iy+1,fx-1,fy-1)*sx;return a+(b-a)*sy;}
    private static double gradient(int seed,int x,int y,double dx,double dy){int hash=seed^(1619*x)^(31337*y);hash=hash*hash*hash*60493;hash=(hash>>13)^hash;double[][] directions={{-1,-1},{1,-1},{-1,1},{1,1},{0,-1},{-1,0},{0,1},{1,0}};var d=directions[hash&7];return dx*d[0]+dy*d[1];}
}
