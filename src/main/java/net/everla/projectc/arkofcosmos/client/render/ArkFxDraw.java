package net.everla.projectc.arkofcosmos.client.render;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** XNA sprite/primitive semantics, including the original third texture coordinate. */
public final class ArkFxDraw {
    public static float[] rgba(float r, float g, float b, float a) { return new float[]{r,g,b,a}; }
    public static final VertexFormatElement UV3 = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 3);
    public static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.of("Position", DefaultVertexFormat.ELEMENT_POSITION,
            "Color", DefaultVertexFormat.ELEMENT_COLOR, "UV0", UV3));
    public static ShaderInstance spriteShader, trailShader;
    public static final Map<String, int[]> sizes = new HashMap<>();
    public static Matrix4f matrix;
    public static Vec3 camera;
    // Coordinates match the current draw call (world coordinates for particles, entity local otherwise).
    public static Vec3 fadeEye;
    public static void ownerView(int owner, Vec3 origin) {
        Minecraft mc = Minecraft.getInstance();
        fadeEye = mc.player != null && ownerFirstPerson(owner, mc.player.getId(),
                mc.options.getCameraType().isFirstPerson(), mc.getCameraEntity() == mc.player)
                ? mc.gameRenderer.getMainCamera().getPosition().subtract(origin) : null;
    }
    public static boolean ownerFirstPerson(int owner, int viewer, boolean firstPerson, boolean playerCamera) {
        return owner >= 0 && owner == viewer && firstPerson && playerCamera;
    }
    public static double nearOpacity(double distance) {
        double t = Math.max(0, Math.min(1, (distance - .5) / 2.5));
        return .12 + .88 * t * t * (3 - 2 * t);
    }
    public static double opacityAt(Vec3 point) { return fadeEye == null ? 1 : nearOpacity(point.distanceTo(fadeEye)); }
    public enum Blend { ALPHA, NONPREMULTIPLIED, ADDITIVE }
    private static Blend activeBlend=Blend.ALPHA;

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(shader(event,"fx_sprite"), shader -> spriteShader = shader);
        event.registerShader(shader(event,"fx_trail"), shader -> trailShader = shader);
        sizes.clear();
    }
    private static ShaderInstance shader(RegisterShadersEvent event,String name)throws IOException{
        return new ShaderInstance(event.getResourceProvider(),id(name),FORMAT){
            @Override public void apply(){
                super.apply();
                // ShaderInstance.apply applies its JSON BlendMode after begin(). Restore XNA's actual pass here.
                RenderSystem.enableBlend();
                RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
                RenderSystem.blendFunc(activeBlend==Blend.ALPHA?GlStateManager.SourceFactor.ONE:GlStateManager.SourceFactor.SRC_ALPHA,
                        activeBlend==Blend.ADDITIVE?GlStateManager.DestFactor.ONE:GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
        };
    }
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, name); }
    public static ResourceLocation texture(String name) { return id("textures/fx/" + name + ".png"); }
    public static int[] size(String name) {
        return sizes.computeIfAbsent(name, key -> {
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture(key)).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
    }
    public static void begin(String texture, Blend blend, boolean trail) {
        activeBlend=blend;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (blend == Blend.ALPHA) RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                blend == Blend.ADDITIVE ? GlStateManager.DestFactor.ONE : GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        ShaderInstance shader = trail ? trailShader : spriteShader;
        if (shader != null) {
            shader.safeGetUniform("NearFade").set(fadeEye == null ? 0f : 1f);
            shader.safeGetUniform("FadePremultiplied").set(blend == Blend.ALPHA ? 1f : 0f);
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture(texture));
        // GeneralParticleHandler uses PointClamp. Primitive textures use the original linear clamp.
        var image=Minecraft.getInstance().getTextureManager().getTexture(texture(texture));
        image.setFilter(trail,false);
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);image.bind();
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        if (trailShader != null && trailShader.getUniform("uTime") != null)
            trailShader.getUniform("uTime").set(time());
    }
    public static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
    public static void vertex(BufferBuilder buffer, Vec3 point, float[] color, float u, float v, float width) {
        Vec3 local = point.subtract(camera);
        buffer.vertex(matrix, (float)local.x, (float)local.y, (float)local.z).color(color[0], color[1], color[2], color[3]);
        buffer.putFloat(0, u); buffer.putFloat(4, v); buffer.putFloat(8, width);
        buffer.nextElement(); buffer.endVertex();
    }
    public static void sprite(String name, Vec3 position, AttackFrame plane, double angle, double sx, double sy,
                       double ox, double oy, float[] color, Blend blend) {
        int[] size = size(name);
        sprite(name, position, plane, angle, sx, sy, ox, oy, color, blend, 0, 0, size[0], size[1]);
    }
    public static void sprite(String name, Vec3 position, AttackFrame plane, double angle, double sx, double sy,
                       double ox, double oy, float[] color, Blend blend, int rx, int ry, int rw, int rh) {
        if (color[3] <= 0 || sx == 0 || sy == 0) return;
        begin(name, blend, false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, FORMAT);
        Vec3 x = plane.direction(angle).scale(sx / 16), y = plane.direction(angle + Math.PI / 2).scale(sy / 16);
        int[] size = size(name);
        float u0 = (float)rx / size[0], v0 = (float)ry / size[1], u1 = (float)(rx + rw) / size[0], v1 = (float)(ry + rh) / size[1];
        vertex(buffer, position.add(x.scale(-ox)).add(y.scale(-oy)), color, u0, v0, 1);
        vertex(buffer, position.add(x.scale(-ox)).add(y.scale(rh - oy)), color, u0, v1, 1);
        vertex(buffer, position.add(x.scale(rw - ox)).add(y.scale(rh - oy)), color, u1, v1, 1);
        vertex(buffer, position.add(x.scale(rw - ox)).add(y.scale(-oy)), color, u1, v0, 1);
        BufferUploader.drawWithShader(buffer.end());
        end();
    }
    public static void centered(String name, Vec3 position, AttackFrame plane, double rotation, double sx, double sy, float[] color, Blend blend) {
        int[] size = size(name);
        sprite(name, position, plane, rotation, sx, sy, size[0] / 2d, size[1] / 2d, color, blend);
    }

    public static float time(){
        Minecraft mc=Minecraft.getInstance();
        return mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20f%3600;
    }
    public static float[] hsl(float h,float s,float l,float a) {
        float q=l<.5f?l*(1+s):l+s-l*s, p=2*l-q;
        return rgba(Math.max(0,Math.min(1,hue(p,q,h+1f/3))),Math.max(0,Math.min(1,hue(p,q,h))),Math.max(0,Math.min(1,hue(p,q,h-1f/3))),a);
    }
    private static float hue(float p,float q,float t){ if(t<0)t++; if(t>1)t--; if(t<1f/6)return p+(q-p)*6*t; if(t<.5f)return q; if(t<2f/3)return p+(q-p)*(2f/3-t)*6; return p; }
}
