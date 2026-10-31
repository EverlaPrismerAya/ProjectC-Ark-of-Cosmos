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
    public static ShaderInstance spriteShader, trailShader, tintShader, moonShader, forcefieldShader;
    /** Immutable per-draw BasicTint parameters, also captured by deferred Oculus draws. */
    public record Tint(float red, float green, float blue, float opacity) {}
    public static final Map<String, int[]> sizes = new HashMap<>();
    private static final Map<String, java.util.List<int[]>> contours = new HashMap<>();
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
        event.registerShader(shader(event,"galaxia_tint"), shader -> tintShader = shader);
        event.registerShader(shader(event,"mahou_moon"), shader -> moonShader = shader);
        event.registerShader(shader(event,"mahou_forcefield"), shader -> forcefieldShader = shader);
        sizes.clear();
        contours.clear();
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
    public static ResourceLocation texture(String name) { return id("textures/" + ((name.startsWith("galaxia/") || name.startsWith("elements/")) ? name : "fx/" + name) + ".png"); }
    public static int[] size(String name) {
        return sizes.computeIfAbsent(name, key -> {
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture(key)).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
    }
    public static void begin(String texture, Blend blend, boolean trail) {
        begin(texture, blend, trail, null);
    }
    private static void begin(String texture, Blend blend, boolean trail, Tint tint) {
        activeBlend=blend;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (blend == Blend.ALPHA) RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                blend == Blend.ADDITIVE ? GlStateManager.DestFactor.ONE : GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        ShaderInstance shader = tint != null ? tintShader : trail ? trailShader : spriteShader;
        if (tint != null && shader != null) {
            shader.safeGetUniform("uColor").set(tint.red, tint.green, tint.blue);
            shader.safeGetUniform("uOpacity").set(tint.opacity);
        }
        if (shader != null) {
            shader.safeGetUniform("AlphaCutoff").set(0f);
            shader.safeGetUniform("NearFade").set(fadeEye == null ? 0f : 1f);
            shader.safeGetUniform("FadePremultiplied").set(blend == Blend.ALPHA ? 1f : 0f);
            Vec3 eye=fadeEye==null?Vec3.ZERO:fadeEye.subtract(camera);
            shader.safeGetUniform("FadeEye").set((float)eye.x,(float)eye.y,(float)eye.z);
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

    /** Procedural lunar corona/gravity front. Uses the same deferred world/depth path as weapon FX. */
    public static void moon(Vec3 at, AttackFrame frame, double radius, float opacity, float time, boolean crescent) {
        if(radius<=0||opacity<=0)return;
        if(OculusShaderCompat.defer(()->moon(at,frame,radius,opacity,time,crescent)))return;
        begin("galaxia/hollowcirclesoftedge",Blend.ADDITIVE,false);
        try {
            moonShader.safeGetUniform("uTime").set(time);
            RenderSystem.setShader(()->moonShader);
            BufferBuilder buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,FORMAT);
            Vec3 x=frame.forward().scale(radius),y=frame.right().scale(radius);float[] color={1,1,1,opacity};float mode=crescent?1:0;
            vertex(buffer,at.subtract(x).subtract(y),color,0,0,mode);
            vertex(buffer,at.subtract(x).add(y),color,0,1,mode);
            vertex(buffer,at.add(x).add(y),color,1,1,mode);
            vertex(buffer,at.add(x).subtract(y),color,1,0,mode);
            BufferUploader.drawWithShader(buffer.end());
        } finally {end();}
    }
    /** Supreme Calamitas forcefield, through the same depth/deferred path as the weapons. */
    public static void forcefield(Vec3 at,AttackFrame frame,int phase,float healthRatio,float opacity,float time,boolean postMusicHit){
        if(OculusShaderCompat.defer(()->forcefield(at,frame,phase,healthRatio,opacity,time,postMusicHit)))return;
        begin("supreme_forcefield",Blend.ALPHA,false);
        try{
            RenderSystem.setShaderTexture(1,texture("supreme_noise"));
            float[] color=phase==1?new float[]{.35f,.65f,1}:phase==2?new float[]{.06f,.07f,.09f}:new float[]{.05f,.85f,.8f};
            float[] edge=phase==1?new float[]{.45f,.75f,1}:phase==2?new float[]{.24f,.26f,.3f}:new float[]{.4f,1,1};
            forcefieldShader.safeGetUniform("uColor").set(color[0],color[1],color[2]);
            forcefieldShader.safeGetUniform("uSecondaryColor").set(edge[0],edge[1],edge[2]);
            forcefieldShader.safeGetUniform("uHealthRatio").set(healthRatio);
            forcefieldShader.safeGetUniform("uPostMusicHit").set(postMusicHit?1f:0f);
            forcefieldShader.safeGetUniform("uRandom").set(java.util.concurrent.ThreadLocalRandom.current().nextFloat());
            forcefieldShader.safeGetUniform("uTime").set(time);
            forcefieldShader.safeGetUniform("uTintStrength").set(phase==2?.9f:.5f);
            RenderSystem.setShader(()->forcefieldShader);
            BufferBuilder buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,FORMAT);
            Vec3 x=frame.forward().scale(2.6),y=frame.right().scale(2.6);float[] rgba={1,1,1,opacity};
            vertex(buffer,at.subtract(x).subtract(y),rgba,0,0,1);vertex(buffer,at.subtract(x).add(y),rgba,0,1,1);
            vertex(buffer,at.add(x).add(y),rgba,1,1,1);vertex(buffer,at.add(x).subtract(y),rgba,1,0,1);
            BufferUploader.drawWithShader(buffer.end());
        }finally{end();}
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
        sprite(name,position,plane,angle,sx,sy,ox,oy,color,blend,rx,ry,rw,rh,null);
    }
    public static void sprite(String name, Vec3 position, AttackFrame plane, double angle, double sx, double sy,
                       double ox, double oy, float[] color, Blend blend, int rx, int ry, int rw, int rh, Tint tint) {
        if (color[3] <= 0 && color[0] <= 0 && color[1] <= 0 && color[2] <= 0 || sx == 0 || sy == 0) return;
        if (OculusShaderCompat.defer(() -> sprite(name, position, plane, angle, sx, sy,
                ox, oy, color, blend, rx, ry, rw, rh,tint))) return;
        begin(name, blend, false,tint);
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

    /** Same three-pixel extrusion and alpha contour as the Ark blade, using the FX material. */
    public static void extrudedBlade(String name, Vec3 position, AttackFrame plane, double angle, double scale,
                                     double ox, double oy, float[] color, Tint tint) {
        if (color[3] <= 0 || scale == 0) return;
        if (OculusShaderCompat.defer(() -> extrudedBlade(name, position, plane, angle, scale, ox, oy, color, tint))) return;
        int[] dimensions = size(name);
        var edges = contours.computeIfAbsent(name, key -> {
            java.util.List<int[]> result = new java.util.ArrayList<>();
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture(key)).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                for (int py = 0; py < image.getHeight(); py++) for (int px = 0; px < image.getWidth(); px++) {
                    if ((image.getPixelRGBA(px, py) >>> 24) == 0) continue;
                    if (px == 0 || (image.getPixelRGBA(px - 1, py) >>> 24) == 0) result.add(new int[]{px, py, 0});
                    if (px == image.getWidth() - 1 || (image.getPixelRGBA(px + 1, py) >>> 24) == 0) result.add(new int[]{px, py, 1});
                    if (py == 0 || (image.getPixelRGBA(px, py - 1) >>> 24) == 0) result.add(new int[]{px, py, 2});
                    if (py == image.getHeight() - 1 || (image.getPixelRGBA(px, py + 1) >>> 24) == 0) result.add(new int[]{px, py, 3});
                }
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
            return result;
        });
        Vec3 x = plane.direction(angle).scale(scale / 16), y = plane.direction(angle + Math.PI / 2).scale(scale / 16);
        Vec3 depth = plane.normal().scale(1.5 * scale / 16);
        begin(name, Blend.ALPHA, false, tint);
        // The solid blade must occlude later translucent terrain/cloud passes; transparent texels must not write depth.
        RenderSystem.depthMask(true);
        ShaderInstance bladeShader=tint==null?spriteShader:tintShader;
        if(bladeShader!=null)bladeShader.safeGetUniform("AlphaCutoff").set(.1f);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, FORMAT);
        for (int face = 0; face < 2; face++) {
            Vec3 origin = position.add(depth.scale(face == 0 ? 1 : -1));
            vertex(buffer, origin.add(x.scale(-ox)).add(y.scale(-oy)), color, 0, 0, 1);
            vertex(buffer, origin.add(x.scale(-ox)).add(y.scale(dimensions[1] - oy)), color, 0, 1, 1);
            vertex(buffer, origin.add(x.scale(dimensions[0] - ox)).add(y.scale(dimensions[1] - oy)), color, 1, 1, 1);
            vertex(buffer, origin.add(x.scale(dimensions[0] - ox)).add(y.scale(-oy)), color, 1, 0, 1);
        }
        for (int[] edge : edges) {
            int px = edge[0], py = edge[1], side = edge[2];
            double ax = px, ay = py, bx = px, by = py;
            if (side < 2) { ax += side; bx = ax; by++; }
            else { ay += side - 2; by = ay; bx++; }
            Vec3 a = position.add(x.scale(ax - ox)).add(y.scale(ay - oy));
            Vec3 b = position.add(x.scale(bx - ox)).add(y.scale(by - oy));
            float u = (px + .5f) / dimensions[0], v = (py + .5f) / dimensions[1];
            vertex(buffer, a.add(depth), color, u, v, 1);
            vertex(buffer, b.add(depth), color, u, v, 1);
            vertex(buffer, b.subtract(depth), color, u, v, 1);
            vertex(buffer, a.subtract(depth), color, u, v, 1);
        }
        BufferUploader.drawWithShader(buffer.end());
        end();
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
