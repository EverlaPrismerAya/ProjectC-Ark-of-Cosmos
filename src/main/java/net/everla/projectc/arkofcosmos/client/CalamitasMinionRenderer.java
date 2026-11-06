package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.entity.CalamitasMinionEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Original heart and tendrils; a baked vanilla vex for the independent seeker. */
public final class CalamitasMinionRenderer extends EntityRenderer<CalamitasMinionEntity> {
    private static final String HEART = "entity/brimstone_heart";
    private static final ResourceLocation VEX = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/vex.png");
    private final ModelPart vex;
    public CalamitasMinionRenderer(EntityRendererProvider.Context context) {
        super(context); vex = context.bakeLayer(ModelLayers.VEX).getChild("root");
    }
    @Override public ResourceLocation getTextureLocation(CalamitasMinionEntity entity) {
        return entity.kind() == CalamitasMinionEntity.Kind.HEART ? ArkFxDraw.texture(HEART) : VEX;
    }
    @Override public boolean shouldRender(CalamitasMinionEntity entity, Frustum frustum, double x, double y, double z) {
        // Top endpoints can be on screen while the heart's own tiny AABB is not.
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x, y, z));
    }
    @Override public void render(CalamitasMinionEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.kind() == CalamitasMinionEntity.Kind.SEEKER) renderSeeker(entity, partial, pose, buffers);
        else {
            var state = entity.view();
            double pixels = state.getDouble("Pixels");
            if (pixels <= 0) return;
            Vec3 base = entity.getPosition(partial), center = base.add(0, entity.getBbHeight() / 2, 0);
            ArkFxDraw.matrix = pose.last().pose(); ArkFxDraw.camera = base; ArkFxDraw.fadeEye = null;
            double time = ((entity.level().getGameTime() % 72000) + partial) / 20d;
            for (Tag raw : state.getCompound("Connections").getList("Endpoints", Tag.TAG_COMPOUND)) {
                var endpoint = (net.minecraft.nbt.CompoundTag) raw;
                tendril(center, new Vec3(endpoint.getDouble("X"), endpoint.getDouble("Y"), endpoint.getDouble("Z")), pixels, time, entity.getId());
            }
            int[] size = ArkFxDraw.size(HEART); int frameHeight = size[1] / 6;
            int frame = Math.min(5, (int)Math.rint(Math.pow(Math.sin(time * 2.6 + entity.getId() * 1.3), 6) * 6));
            float opacity = (float)Math.min(1, (state.getInt("SourceAge") + partial * 3) * 42 / 255d);
            ArkFxDraw.litSprite(HEART, center, NativeSprites.billboard(), 0, 16 / pixels,
                    size[0] / 2d, frameHeight / 2d, new float[]{opacity, opacity, opacity, opacity},
                    0, frame * frameHeight, size[0], frameHeight, light, 0);
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }
    private void renderSeeker(CalamitasMinionEntity entity, float partial, PoseStack pose, MultiBufferSource buffers) {
        vex.getAllParts().forEach(ModelPart::resetPose);
        var body = vex.getChild("body");
        float age = entity.tickCount + partial, arm = Mth.cos(age * 5.5f * Mth.DEG_TO_RAD) * .1f;
        body.xRot = .15707964f;
        body.getChild("right_arm").zRot = Mth.PI / 5 + arm;
        body.getChild("left_arm").zRot = -Mth.PI / 5 - arm;
        var left = body.getChild("left_wing"); var right = body.getChild("right_wing");
        left.yRot = 1.0995574f + Mth.cos(age * .8f) * Mth.DEG_TO_RAD * 16.2f; right.yRot = -left.yRot;
        left.xRot = right.xRot = .47123888f; left.zRot = -.47123888f; right.zRot = .47123888f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
        pose.scale(-1, -1, 1); pose.translate(0, -1.501, 0);
        vex.render(pose, buffers.getBuffer(RenderType.entityTranslucent(VEX)), 15728880, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    private static void tendril(Vec3 from, Vec3 endpoint, double pixels, double time, int id) {
        if (ArkFxDraw.primitiveShader == null || endpoint.distanceToSqr(from) < 1e-12) return;
        if (OculusShaderCompat.defer(() -> tendril(from, endpoint, pixels, time, id))) return;
        Vec3 direction = endpoint.subtract(from).normalize();
        Vec3[] controls = {from, from.lerp(endpoint, .25), from.lerp(endpoint, .5), from.lerp(endpoint, .75), endpoint.add(direction.scale(18 / pixels))};
        Vec3[] points = new Vec3[41];
        for (int i = 0; i < 40; i++) {
            double t = i / 40d * 4; int n = (int)t; t -= n; double t2 = t * t, t3 = t2 * t;
            Vec3 a = controls[Math.max(0, n - 1)], b = controls[n], c = controls[n + 1], d = controls[Math.min(4, n + 2)];
            points[i] = a.scale(-.5*t3+t2-.5*t).add(b.scale(1.5*t3-2.5*t2+1)).add(c.scale(-1.5*t3+2*t2+.5*t)).add(d.scale(.5*t3-.5*t2));
        }
        points[40] = controls[4];
        Vec3 side = direction.cross(NativeSprites.billboard().normal());
        if (side.lengthSqr() < 1e-12) side = direction.cross(Math.abs(direction.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0));
        side = side.normalize();
        Vec3[][] edges = new Vec3[41][2]; float[][] colors = new float[41][];
        double length = controls[4].distanceTo(from);
        for (int point = 0; point < points.length; point++) {
            double completion = points[point].distanceTo(from) / length;
            double narrow = Math.pow(lerpValue(0,.16,completion) * lerpValue(1,.84,completion), 8);
            double width = 4 - 3*narrow + 3.2*Math.pow(Math.sin(time*2.6+id*1.3+completion),16);
            double color = lerpValue(0,.34,completion) * lerpValue(1.07,.66,completion); color = color*color*(3-2*color);
            // XNA Color multiplies/lerps quantize channels before premultiplied blending.
            float red = (int)((int)(97+158*color)*.425) / 255f, alpha = (int)((int)(178+77*color)*.425) / 255f;
            colors[point] = new float[]{red,0,0,alpha};
            Vec3 offset = side.scale(width / pixels);
            edges[point][0] = points[point].subtract(offset); edges[point][1] = points[point].add(offset);
        }
        ArkFxDraw.begin(HEART, ArkFxDraw.Blend.ALPHA, false);
        try {
            RenderSystem.setShader(() -> ArkFxDraw.primitiveShader);
            var buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.TRIANGLES, ArkFxDraw.FORMAT);
            int[] triangle = {0,1,2,2,1,3};
            for (int i = 0; i < 40; i++) for (int corner : triangle) {
                int index = i*2+corner, point = index/2;
                ArkFxDraw.vertex(buffer, edges[point][index%2], colors[point], 0,0,1);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally { ArkFxDraw.end(); }
    }
    private static double lerpValue(double from, double to, double value) { return Math.max(0, Math.min(1, (value-from)/(to-from))); }
}
