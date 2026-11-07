package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers.Kind;
import net.everla.projectc.arkofcosmos.entity.CalamitasBrotherEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Vanilla illager model/texture reuse, without vanilla mob AI or global renderer changes. */
public final class CalamitasBrotherRenderer extends EntityRenderer<CalamitasBrotherEntity> {
    private static final ResourceLocation PILLAGER = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/pillager.png");
    private static final ResourceLocation VINDICATOR = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/vindicator.png");
    private final ModelPart pillager, vindicator;
    public CalamitasBrotherRenderer(EntityRendererProvider.Context context) {
        super(context); pillager = context.bakeLayer(ModelLayers.PILLAGER); vindicator = context.bakeLayer(ModelLayers.VINDICATOR); shadowRadius = .5f;
    }
    @Override public ResourceLocation getTextureLocation(CalamitasBrotherEntity entity) { return entity.kind() == Kind.CATACLYSM ? PILLAGER : VINDICATOR; }
    @Override public boolean shouldRender(CalamitasBrotherEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x,y,z))
                && frustum.isVisible(entity.getBoundingBox().inflate(CalamitasShieldRenderer.brotherRadius(entity)));
    }
    @Override public void render(CalamitasBrotherEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        ModelPart root = entity.kind() == Kind.CATACLYSM ? pillager : vindicator;
        root.getAllParts().forEach(ModelPart::resetPose);
        root.getChild("arms").visible = false;
        root.getChild("right_arm").visible = true; root.getChild("left_arm").visible = true;
        var combat = entity.view().getCompound("Combat");
        float swing = Mth.sin((float)(combat.getDouble("Counter") / 50 * Math.PI)) * .8f;
        root.getChild(combat.getBoolean("Hand") ? "right_arm" : "left_arm").xRot = -swing;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
        pose.scale(-1, -1, 1); pose.translate(0, -1.5, 0);
        root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))), light, OverlayTexture.NO_OVERLAY);
        pose.popPose(); CalamitasShieldRenderer.renderBrother(entity,partial,pose); super.render(entity, yaw, partial, pose, buffers, light);
    }
}
