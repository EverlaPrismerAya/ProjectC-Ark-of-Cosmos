package net.everla.projectc.arkofcosmos;

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
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** XNA sprite/primitive semantics, including the original third texture coordinate. */
final class ArkFxDraw {
    static float[] rgba(float r, float g, float b, float a) { return new float[]{r,g,b,a}; }
    static final VertexFormatElement UV3 = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 3);
    static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.of("Position", DefaultVertexFormat.ELEMENT_POSITION,
            "Color", DefaultVertexFormat.ELEMENT_COLOR, "UV0", UV3));
    static ShaderInstance spriteShader, trailShader;
    static final Map<String, int[]> sizes = new HashMap<>();
    static Matrix4f matrix;
    static Vec3 camera;
    // Coordinates match the current draw call (world coordinates for particles, entity local otherwise).
    static Vec3 fadeEye;
    static void ownerView(int owner, Vec3 origin) {
        Minecraft mc = Minecraft.getInstance();
        fadeEye = mc.player != null && ownerFirstPerson(owner, mc.player.getId(),
                mc.options.getCameraType().isFirstPerson(), mc.getCameraEntity() == mc.player)
                ? mc.gameRenderer.getMainCamera().getPosition().subtract(origin) : null;
    }
    static boolean ownerFirstPerson(int owner, int viewer, boolean firstPerson, boolean playerCamera) {
        return owner >= 0 && owner == viewer && firstPerson && playerCamera;
    }
    static double nearOpacity(double distance) {
        double t = Math.max(0, Math.min(1, (distance - .5) / 2.5));
        return .12 + .88 * t * t * (3 - 2 * t);
    }
    static double opacityAt(Vec3 point) { return fadeEye == null ? 1 : nearOpacity(point.distanceTo(fadeEye)); }
    enum Blend { ALPHA, NONPREMULTIPLIED, ADDITIVE }
    private static Blend activeBlend=Blend.ALPHA;

    static void register(RegisterShadersEvent event) throws IOException {
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
    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, name); }
    static ResourceLocation texture(String name) { return id("textures/fx/" + name + ".png"); }
    static int[] size(String name) {
        return sizes.computeIfAbsent(name, key -> {
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture(key)).open();
                 var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        });
    }
    static void begin(String texture, Blend blend, boolean trail) {
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
    static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
    static void vertex(BufferBuilder buffer, Vec3 point, float[] color, float u, float v, float width) {
        Vec3 local = point.subtract(camera);
        buffer.vertex(matrix, (float)local.x, (float)local.y, (float)local.z).color(color[0], color[1], color[2], color[3]);
        buffer.putFloat(0, u); buffer.putFloat(4, v); buffer.putFloat(8, width);
        buffer.nextElement(); buffer.endVertex();
    }
    static void sprite(String name, Vec3 position, AttackFrame plane, double angle, double sx, double sy,
                       double ox, double oy, float[] color, Blend blend) {
        int[] size = size(name);
        sprite(name, position, plane, angle, sx, sy, ox, oy, color, blend, 0, 0, size[0], size[1]);
    }
    static void sprite(String name, Vec3 position, AttackFrame plane, double angle, double sx, double sy,
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
    static void centered(String name, Vec3 position, AttackFrame plane, double rotation, double sx, double sy, float[] color, Blend blend) {
        int[] size = size(name);
        sprite(name, position, plane, rotation, sx, sy, size[0] / 2d, size[1] / 2d, color, blend);
    }

    static float time(){
        Minecraft mc=Minecraft.getInstance();
        return mc.level==null?0:(mc.level.getGameTime()+mc.getFrameTime())/20f%3600;
    }
    /** PrimitiveRenderer.AssignPoints/AssignVertices/AssignIndicesRectangleTrail, including its end samples. */
    static void trail(Vec3[] source, AttackFrame plane, PoseStack pose, float scale, boolean needle, Vec3 offset) {
        if(source.length<=4||trailShader==null)return;
        Vec3[] points=new Vec3[32]; points[0]=source[0].add(offset);points[31]=source[source.length-1].add(offset);
        for(int i=0;i<30;i++){
            double f=i/30d*(source.length-1);int n=(int)f;double t=f-n;
            Vec3 p1=source[n],p2=source[n+1],p0=n>0?source[n-1]:p1.scale(2).subtract(p2),p3=n<source.length-2?source[n+2]:p2.scale(2).subtract(p1);
            double t2=t*t,t3=t2*t;
            points[i+1]=p0.scale(-.5*t3+t2-.5*t).add(p1.scale(1.5*t3-2.5*t2+1)).add(p2.scale(-1.5*t3+2*t2+.5*t)).add(p3.scale(.5*t3-.5*t2)).add(offset);
        }
        begin("scarletdevilstreak",Blend.ALPHA,true);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.TRIANGLES,FORMAT);
        Matrix4f old=matrix;Vec3 oldCamera=camera;matrix=pose.last().pose();camera=Vec3.ZERO;
        Vec3[][] edges=new Vec3[32][2];float[][] colors=new float[32][];float[] widths=new float[32];
        float now=time();
        for(int i=0;i<32;i++){
            Vec3 tangent=(i==31?points[i].subtract(points[i-1]):points[i+1].subtract(points[i])).normalize();
            Vec3 side=plane.normal().cross(tangent).normalize();
            float completion=(i-1)/31f;
            float width=(float)((needle?14:22)*scale*Math.pow(1-completion,needle?2:3));
            widths[i]=width;edges[i][0]=points[i].subtract(side.scale(width/16));edges[i][1]=points[i].add(side.scale(width/16));
            float opacity=needle?.6f:1f;
            float fade=(float)ArkEffects.clamp((1-completion)/.36)*opacity;
            float pulse=(float)ArkEffects.lerp(.65,1,Math.cos(-now*3)*.5+.5);
            float[] color=ArkEffects.mix(needle?ArkEffects.CYAN:hsl(.55f,1,.8f,1),needle?ArkEffects.rgb(220,20,60):ArkEffects.rgb(175,238,238),Math.sin(completion*Math.PI*1.6-now*4)*.5+.5);
            colors[i]=ArkEffects.mul(ArkEffects.mix(ArkEffects.WHITE,color,pulse),fade);
        }
        // Calamity intentionally emits PositionsIndex - 2 segments.
        for(int i=0;i<30;i++)for(int index:new int[]{i*2,i*2+1,i*2+2,i*2+2,i*2+1,i*2+3}){
            int point=index/2,edge=index%2;float width=widths[point];
            vertex(buffer,edges[point][edge],colors[point],(point-1)/31f,.5f+(edge==0?-1:1)*width*.5f,width);
        }
        BufferUploader.drawWithShader(buffer.end());matrix=old;camera=oldCamera;end();
    }
    static float[] hsl(float h,float s,float l,float a) {
        float q=l<.5f?l*(1+s):l+s-l*s, p=2*l-q;
        return rgba(Math.max(0,Math.min(1,hue(p,q,h+1f/3))),Math.max(0,Math.min(1,hue(p,q,h))),Math.max(0,Math.min(1,hue(p,q,h-1f/3))),a);
    }
    private static float hue(float p,float q,float t){ if(t<0)t++; if(t>1)t--; if(t<1f/6)return p+(q-p)*6*t; if(t<.5f)return q; if(t<2f/3)return p+(q-p)*(2f/3-t)*6; return p; }
}
