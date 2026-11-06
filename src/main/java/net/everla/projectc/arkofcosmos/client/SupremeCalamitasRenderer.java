package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Baked slim player, including jacket/hat/sleeves/trousers; no player skin service. */
public final class SupremeCalamitasRenderer extends EntityRenderer<SupremeCalamitasEntity> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "textures/entity/supreme_calamitas.png");
    private final ModelPart root;
    public SupremeCalamitasRenderer(EntityRendererProvider.Context context) { super(context); root = context.bakeLayer(ModelLayers.PLAYER_SLIM); shadowRadius = .4f; }
    @Override public ResourceLocation getTextureLocation(SupremeCalamitasEntity entity) { return SKIN; }
    @Override public boolean shouldRender(SupremeCalamitasEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        double pixels = entity.view().getDouble("Pixels");
        // Largest source quad: immune smear 156px * 1.35 * 1.5, including its diagonal.
        return pixels > 0 ? frustum.isVisible(entity.getBoundingBox().inflate(156 * 1.35 * 1.5 / Math.sqrt(2) / pixels))
                : super.shouldRender(entity, frustum, x, y, z);
    }
    private void arm(String name, String sleeve, float x, float z) {
        ModelPart part = root.getChild(name); part.xRot = x; part.zRot = z; root.getChild(sleeve).copyFrom(part);
    }
    @Override public void render(SupremeCalamitasEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        root.getAllParts().forEach(ModelPart::resetPose);
        var view = entity.view(); String phase = view.getString("Phase");
        float time = view.getInt("SourceAge") + partial * 3;
        float idle = Mth.sin(time * .04f) * .04f;
        var animation = view.getCompound("Animation");
        int family = animation.getInt("Family"), frame = animation.getInt("Frame");
        float cycle = Mth.sin(frame * Mth.TWO_PI / 6);
        // Human poses follow the source strips; the rigid Slim skeleton cannot
        // reproduce sprite cloth deformation or bend an elbow.
        switch (family) {
            case 1 -> { arm("left_arm", "left_sleeve", .12f + cycle * .06f, -.14f); arm("right_arm", "right_sleeve", .12f - cycle * .06f, .14f); }
            case 2 -> { arm("left_arm", "left_sleeve", -.12f + cycle * .04f, -.38f); arm("right_arm", "right_sleeve", -.12f - cycle * .04f, .38f); }
            case 3 -> { arm("left_arm", "left_sleeve", -.85f + cycle * .08f, -.18f); arm("right_arm", "right_sleeve", -.85f - cycle * .08f, .18f); }
            case 4 -> {
                float punch = switch (frame) { case 1 -> -.35f; case 2 -> -.65f; case 3 -> -1.55f; case 4 -> -1.4f; case 5 -> -.75f; default -> 0; };
                arm("right_arm", "right_sleeve", punch, .12f); arm("left_arm", "left_sleeve", -.2f, -.15f);
            }
            case 5 -> { arm("right_arm", "right_sleeve", -1.05f + cycle * .12f, .3f + cycle * .15f); arm("left_arm", "left_sleeve", idle, -.1f); }
            case 6 -> { arm("right_arm", "right_sleeve", -.8f - cycle * .55f, .14f); arm("left_arm", "left_sleeve", -.15f, -.12f); }
            default -> { arm("left_arm", "left_sleeve", idle, -.08f); arm("right_arm", "right_sleeve", -idle, .08f); }
        }
        if (phase.equals("ACCEPTANCE") || phase.equals("FINISHED")) {
            root.getChild("head").xRot = .35f;
            arm("left_arm", "left_sleeve", .2f, -.04f); arm("right_arm", "right_sleeve", .2f, .04f);
        }
        root.getChild("hat").copyFrom(root.getChild("head"));
        root.getChild("jacket").copyFrom(root.getChild("body"));
        root.getChild("left_pants").copyFrom(root.getChild("left_leg")); root.getChild("right_pants").copyFrom(root.getChild("right_leg"));
        float opacity = view.contains("Opacity") ? view.getFloat("Opacity") : 1;
        pose.pushPose();
        pose.translate(0, (entity.getBbHeight() - 1.8) / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
        pose.scale(-1, -1, 1); pose.translate(0, -1.5, 0);
        root.render(pose, buffers.getBuffer(RenderType.entityTranslucent(SKIN)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, opacity);
        pose.popPose(); CalamitasShieldRenderer.render(entity, partial, pose); super.render(entity, yaw, partial, pose, buffers, light);
    }
}
