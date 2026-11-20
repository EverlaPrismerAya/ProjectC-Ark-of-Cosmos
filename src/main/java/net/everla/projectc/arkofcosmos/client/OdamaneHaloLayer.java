package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.vertex.PoseStack;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Boss-only layer: independent of player Curios slots and GR classes. */
public final class OdamaneHaloLayer extends RenderLayer<MahouTsukaiEntity, PlayerModel<MahouTsukaiEntity>> {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "textures/entity/odamane_halo.png");
    private final OdamaneHaloModel halo = new OdamaneHaloModel(OdamaneHaloModel.createLayer().bakeRoot());

    public OdamaneHaloLayer(RenderLayerParent<MahouTsukaiEntity, PlayerModel<MahouTsukaiEntity>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, MahouTsukaiEntity boss,
                                 float swing, float amount, float partialTick, float age, float yaw, float pitch) {
        if (boss.isInvisible()) return;
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.matrix=pose.last().pose();
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.camera=net.minecraft.world.phys.Vec3.ZERO;
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.fadeEye=null;
        var captured=new PoseStack();captured.last().pose().set(pose.last().pose());captured.last().normal().set(pose.last().normal());
        if(OculusShaderCompat.deferForeground(()->draw(captured,boss,partialTick)))return;
        draw(captured,boss,partialTick);
    }
    private void draw(PoseStack pose,MahouTsukaiEntity boss,float partialTick){
        boolean shaderPack = OculusShaderCompat.shaderPackActive();
        boolean lowHealth = boss.bossBarProgress() < .5f;
        halo.animate(OdamaneHaloModel.rotation(boss.getName().getString().length(), Blaze3D.getTime()));
        pose.pushPose();
        try {
            // Reference applies the Halo pivot once here and again when rendering the part.
            halo.halo.translateAndRotate(pose);
            pose.translate(0, shaderPack ? .3 : 0, -.5);
            RenderType type = lowHealth ? shaderPack ? OdamaneHaloEffects.swirlType()
                    : OdamaneHaloEffects.targetType() : OdamaneHaloEffects.baseType();
            var immediate = Minecraft.getInstance().renderBuffers().bufferSource();
            halo.renderToBuffer(pose, immediate.getBuffer(type), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, boss.retreatOpacity());
            immediate.endBatch(type);
            if (shaderPack) {
                float alpha = lowHealth ? 2f : 1f + Mth.abs(Mth.sin((float) (Blaze3D.getTime() * 10 / 44.5))) / 1.5f + .1f;
                RenderType glow = OdamaneHaloEffects.glowType();
                halo.renderToBuffer(pose, immediate.getBuffer(glow), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha*boss.retreatOpacity());
                immediate.endBatch(glow);
            }
        } finally { pose.popPose(); }
    }
}
