package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.everla.projectc.arkofcosmos.entity.CalamitasSepulcherEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Each entity draws the complete baked vanilla ravager, including body and all legs. */
public final class CalamitasSepulcherRenderer extends EntityRenderer<CalamitasSepulcherEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/ravager.png");
    private final ModelPart root;
    public CalamitasSepulcherRenderer(EntityRendererProvider.Context context) {
        super(context); root = context.bakeLayer(ModelLayers.RAVAGER); shadowRadius = .5f;
    }
    @Override public ResourceLocation getTextureLocation(CalamitasSepulcherEntity entity) { return TEXTURE; }
    @Override public void render(CalamitasSepulcherEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var view = entity.view();
        float opacity = view.getFloat("Opacity");
        if (opacity > 0) {
            root.getAllParts().forEach(ModelPart::resetPose);
            float age = view.getInt("SourceAge") + partial * 3;
            float stride = Mth.cos(age * .12f) * .18f;
            root.getChild("right_hind_leg").xRot = stride;
            root.getChild("left_hind_leg").xRot = -stride;
            root.getChild("right_front_leg").xRot = -stride;
            root.getChild("left_front_leg").xRot = stride;
            pose.pushPose();
            pose.translate(0, entity.getBbHeight() / 2, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, entity.xRotO, entity.getXRot())));
            float scale = entity.getBbWidth() / 1.95f;
            pose.scale(-scale, -scale, scale);
            pose.translate(0, -.4, 0);
            root.render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, opacity);
            pose.popPose();
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
