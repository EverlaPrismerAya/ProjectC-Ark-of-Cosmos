package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.entity.CalamitasAttackEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Display-only adapters. Goety supplies baked layers/textures, never projectile AI or damage. */
public final class CalamitasAttackRenderer extends EntityRenderer<CalamitasAttackEntity> {
    private static final ResourceLocation ARROW = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/projectiles/arrow.png");
    private final ModelPart blast, bolt, skull;
    public CalamitasAttackRenderer(EntityRendererProvider.Context context) {
        super(context);
        blast = context.bakeLayer(new ModelLayerLocation(goety("hell_blast"), "main"));
        bolt = context.bakeLayer(new ModelLayerLocation(goety("soul_bolt"), "main"));
        skull = context.bakeLayer(new ModelLayerLocation(goety("cursed_skull"), "main"));
    }
    private static ResourceLocation goety(String path) { return ResourceLocation.fromNamespaceAndPath("goety", path); }
    @Override public ResourceLocation getTextureLocation(CalamitasAttackEntity entity) {
        int age = entity.view().getInt("Age");
        return switch (entity.kind()) {
            case FIREBLAST, GIGABLAST -> goety("textures/entity/projectiles/hell_blast/hell_blast" + (age / 3 % 32 + 1) + ".png");
            case HELLBLAST, HELLBLAST_BARRAGE -> goety("textures/entity/servants/haunted_skull/attack/skull" + (age / 3 % 8 + 1) + ".png");
            case MAELSTROM -> ArkFxDraw.texture("calamitas_screamy_face");
            case SOUL -> goety("textures/entity/projectiles/soul_bolt/soul_bolt_" + (age / 3 % 3 + 1) + ".png");
            case FIST -> ARROW;
            case SLASH -> ArkFxDraw.texture(entity.view().getInt("Hand") == 0 ? "calamitas_slash_alt" : "calamitas_slash");
            default -> goety("textures/entity/projectiles/hell_bolt/bolt_" + (age / 3 % 3 + 1) + ".png");
        };
    }
    @Override public boolean shouldRender(CalamitasAttackEntity entity, Frustum frustum, double x, double y, double z) {
        // Model, slash afterimages and vortex extend far beyond the networking entity's .25-block AABB.
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x, y, z));
    }
    @Override public void render(CalamitasAttackEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var state = entity.view(); double pixels = state.getDouble("Pixels");
        if (pixels <= 0) return;
        Vec3 velocity = new Vec3(state.getDouble("VX"), state.getDouble("VY"), state.getDouble("VZ"));
        Vec3 direction = velocity.lengthSqr() > 1e-12 ? velocity.normalize() : new Vec3(0, 0, -1);
        float opacity = Mth.clamp(state.getFloat("Opacity"), 0, 1), scale = state.getFloat("Scale");
        if (entity.kind() == CalamitasAttackEntity.Kind.FIST && state.getInt("Variant") >= 3) opacity = 1;
        if (entity.kind() == CalamitasAttackEntity.Kind.MAELSTROM) {
            ArkFxDraw.matrix = pose.last().pose(); ArkFxDraw.camera = entity.getPosition(partial); ArkFxDraw.fadeEye = null;
            vortex(entity.getPosition(partial), NativeSprites.billboard(), pixels, scale, opacity, state.getInt("Remaining"),
                    ((entity.level().getGameTime() % 72000) + partial) / 20d);
        } else if (entity.kind() == CalamitasAttackEntity.Kind.SLASH) {
            ArkFxDraw.matrix = pose.last().pose(); ArkFxDraw.camera = entity.getPosition(partial); ArkFxDraw.fadeEye = null;
            Vec3 planeX = new Vec3(state.getDouble("PlaneX"), 0, state.getDouble("PlaneZ"));
            AttackFrame frame = new AttackFrame(planeX, new Vec3(0, -1, 0));
            if (state.getInt("Variant") < 4) {
                String texture = state.getInt("Hand") == 0 ? "calamitas_slash_alt" : "calamitas_slash";
                Vec3 at = entity.getPosition(partial).subtract(direction.scale(38 / pixels));
                for (int i = 0; i < 3; i++) whiteSlash(texture, at.subtract(velocity.scale(i * 4)), frame,
                        frame.angle(direction), 16 * scale / pixels, state.getInt("Age") / 7 % 4, opacity * (1 - i / 3f) * .5f, direction.dot(planeX) < 0);
                whiteSlash(texture, at, frame, frame.angle(direction), 16 * scale / pixels, state.getInt("Age") / 7 % 4, opacity, direction.dot(planeX) < 0);
            }
            // Source trail carriers are invisible; reliable events render their Spark/VoidSpark particles.
        } else if (opacity > 0) {
            pose.pushPose();
            if (entity.kind() == CalamitasAttackEntity.Kind.FIST) {
                pose.mulPose(new Quaternionf().rotationTo(1, 0, 0, (float)direction.x, (float)direction.y, (float)direction.z));
                float size = (float)((state.getInt("Variant") >= 3 ? 180 : 70 * scale) / pixels / .9);
                pose.scale(size, size, size); arrow(pose, buffers, opacity, light);
            } else {
                pose.mulPose(new Quaternionf().rotationTo(0, 0, -1, (float)direction.x, (float)direction.y, (float)direction.z));
                boolean large = entity.kind() == CalamitasAttackEntity.Kind.FIREBLAST || entity.kind() == CalamitasAttackEntity.Kind.GIGABLAST;
                boolean isSkull = entity.kind() == CalamitasAttackEntity.Kind.HELLBLAST || entity.kind() == CalamitasAttackEntity.Kind.HELLBLAST_BARRAGE;
                // Match the source hit core diameter; Goety blast/skull/bolt cores are 16/8/4 model pixels.
                double diameter = switch (entity.kind()) {
                    case FIREBLAST -> 36; case GIGABLAST -> 50; case DART -> 20; case SOUL -> 16; default -> 40;
                };
                float size = (float)(diameter / (large ? 1 : isSkull ? .5 : .25) / pixels * scale);
                pose.scale(-size, -size, size);
                ModelPart model = large ? blast : isSkull ? skull : bolt; model.getAllParts().forEach(ModelPart::resetPose);
                // Goety's original pivots offset their projectile origin; align the core with our synced center.
                if (isSkull) model.getChild("head").y = 4;
                else model.getChild("bolt").y = 0;
                if (large) model.getChild("bolt").z = 8.5f;
                model.render(pose, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))), 15728880, OverlayTexture.NO_OVERLAY, 1, 1, 1, opacity);
            }
            pose.popPose();
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }
    /** HellBall first, ten source zero-alpha vortex layers, then the opaque black bloom center. */
    private static void vortex(Vec3 at, AttackFrame face, double pixels, float scale, float opacity, int remaining, double globalTime) {
        if (opacity <= 0 || ArkFxDraw.hellBallShader == null) return;
        if (OculusShaderCompat.defer(() -> vortex(at, face, pixels, scale, opacity, remaining, globalTime), true)) return;
        String scream = "calamitas_screamy_face";
        ArkFxDraw.begin(scream, ArkFxDraw.Blend.ALPHA, false);
        try {
            var shader = ArkFxDraw.hellBallShader;
            shader.safeGetUniform("uTime").set(remaining / 60f * .24f);
            shader.safeGetUniform("uOpacity").set(opacity);
            shader.safeGetUniform("uShieldColor").set((int)(255 * opacity) / 255f, 0f, (int)(127 * opacity) / 255f);
            RenderSystem.setShader(() -> shader);
            var image = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(ArkFxDraw.texture(scream));
            image.setFilter(true, false); image.bind();
            RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL11.GL_REPEAT);
            RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL11.GL_REPEAT);
            int[] size = ArkFxDraw.size(scream);
            Vec3 x = face.forward().scale(size[0] * .715 * scale * opacity / pixels / 2), y = face.right().scale(size[1] * .715 * scale * opacity / pixels / 2);
            var out = Tesselator.getInstance().getBuilder(); out.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT);
            float[] white = {1,1,1,1};
            ArkFxDraw.vertex(out, at.subtract(x).subtract(y), white, 0,0,1);
            ArkFxDraw.vertex(out, at.subtract(x).add(y), white, 0,1,1);
            ArkFxDraw.vertex(out, at.add(x).add(y), white, 1,1,1);
            ArkFxDraw.vertex(out, at.add(x).subtract(y), white, 1,0,1);
            BufferUploader.drawWithShader(out.end());
        } finally { ArkFxDraw.end(); }
        for (int i=0;i<10;i++) {
            double angle = Math.PI * 2 * i / 3 + globalTime * Math.PI * 2;
            float black = Math.min(1, i * .2f), magenta = Math.min(1, i * .15f);
            // XNA Color.Lerp and scalar operators truncate at each operation; alpha is explicitly zero.
            int outerBlue = (int)(255 * magenta);
            float red = (int)((int)((int)(255 - 255 * black) * .5f) * opacity) / 255f;
            float blue = (int)((int)((int)(outerBlue - outerBlue * black) * .5f) * opacity) / 255f;
            Vec3 offset = face.direction(angle + globalTime * i / 16).scale(6 / pixels);
            double layerScale = scale * (1 - i * .05f) * opacity * 16 / pixels;
            ArkFxDraw.centered("calamitas_soul_vortex", at.add(offset), face, -angle + Math.PI / 2, layerScale, layerScale,
                    new float[]{red,0,blue,0}, ArkFxDraw.Blend.ALPHA);
        }
        float alpha = (int)(255 * opacity) / 255f;
        ArkFxDraw.centered("calamitas_large_bloom", at, face, 0, scale * .9 * opacity * 16 / pixels, scale * .9 * opacity * 16 / pixels,
                new float[]{0,0,0,alpha}, ArkFxDraw.Blend.ALPHA);
    }
    private static void whiteSlash(String texture, Vec3 at, AttackFrame plane, double angle, double scale, int frame, float opacity, boolean flip) {
        if (opacity <= 0 || ArkFxDraw.whiteSpriteShader == null) return;
        if (OculusShaderCompat.defer(() -> whiteSlash(texture, at, plane, angle, scale, frame, opacity, flip), true)) return;
        ArkFxDraw.begin(texture, ArkFxDraw.Blend.ALPHA, false);
        try {
            RenderSystem.setShader(() -> ArkFxDraw.whiteSpriteShader);
            int[] size = ArkFxDraw.size(texture); int height = size[1] / 4;
            Vec3 x = plane.direction(angle).scale(size[0] * scale / 32), y = plane.direction(angle + Math.PI / 2).scale(height * scale / 32 * (flip ? -1 : 1));
            float v0 = frame / 4f, v1 = (frame + 1) / 4f; float[] color = {1,1,1,opacity};
            var out = Tesselator.getInstance().getBuilder(); out.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT);
            ArkFxDraw.vertex(out, at.subtract(x).subtract(y), color, 0,v0,1);
            ArkFxDraw.vertex(out, at.subtract(x).add(y), color, 0,v1,1);
            ArkFxDraw.vertex(out, at.add(x).add(y), color, 1,v1,1);
            ArkFxDraw.vertex(out, at.add(x).subtract(y), color, 1,v0,1);
            BufferUploader.drawWithShader(out.end());
        } finally { ArkFxDraw.end(); }
    }
    /** Vanilla arrow mesh/UVs, with the source projectile's direction, scale and alpha. */
    private static void arrow(PoseStack pose, MultiBufferSource buffers, float alpha, int light) {
        pose.mulPose(Axis.XP.rotationDegrees(45)); pose.scale(.05625f, .05625f, .05625f); pose.translate(-4,0,0);
        var out = buffers.getBuffer(RenderType.entityTranslucent(ARROW));
        for (int side : new int[]{-1,1}) {
            arrowVertex(out,pose,-7,-2*side,-2,0,.15625f,side,0,0,alpha,light);
            arrowVertex(out,pose,-7,-2*side,2,.15625f,.15625f,side,0,0,alpha,light);
            arrowVertex(out,pose,-7,2*side,2,.15625f,.3125f,side,0,0,alpha,light);
            arrowVertex(out,pose,-7,2*side,-2,0,.3125f,side,0,0,alpha,light);
        }
        for (int i=0;i<4;i++) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
            arrowVertex(out,pose,-8,-2,0,0,0,0,1,0,alpha,light);
            arrowVertex(out,pose,8,-2,0,.5f,0,0,1,0,alpha,light);
            arrowVertex(out,pose,8,2,0,.5f,.15625f,0,1,0,alpha,light);
            arrowVertex(out,pose,-8,2,0,0,.15625f,0,1,0,alpha,light);
        }
    }
    private static void arrowVertex(VertexConsumer out, PoseStack pose, float x,float y,float z,float u,float v,float nx,float ny,float nz,float alpha,int light) {
        out.vertex(pose.last().pose(),x,y,z).color(1f,1f,1f,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),nx,ny,nz).endVertex();
    }
}
