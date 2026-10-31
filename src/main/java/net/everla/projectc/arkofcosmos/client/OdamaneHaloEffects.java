package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local halo-only blur/rewind/color chain; never replaces Minecraft's active post processor. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class OdamaneHaloEffects extends RenderType {
    private static PostChain chain;
    private static boolean pending, prepared, failed;
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "shaders/post/odamane.json");
    private static final OutputStateShard OUTPUT = new OutputStateShard("ark_odamane_target", () -> {
        var mc = Minecraft.getInstance();
        var target = chain.getTempTarget("final");
        target.copyDepthFrom(mc.getMainRenderTarget());
        target.bindWrite(false);
    }, () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));
    private static final RenderType HALO = create("ark_odamane_halo", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER).setTextureState(new TextureStateShard(OdamaneHaloLayer.TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(OUTPUT).createCompositeState(false));
    private static final TransparencyStateShard GLOW_BLEND = new TransparencyStateShard("ark_halo_glow", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
    private static final RenderType GLOW = create("ark_odamane_glow", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(OdamaneHaloLayer.TEXTURE, false, false))
                    .setTransparencyState(GLOW_BLEND).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .setDepthTestState(EQUAL_DEPTH_TEST).setOutputState(ITEM_ENTITY_TARGET).createCompositeState(true));

    private OdamaneHaloEffects() { super("ark_halo", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {}); }
    static RenderType glowType() { return GLOW; }

    public static void reset() {
        if (chain != null) chain.close();
        chain = null; failed = pending = prepared = false;
    }

    static RenderType targetType() {
        var mc = Minecraft.getInstance();
        if (!failed && chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), EFFECT);
                chain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            } catch (java.io.IOException | com.google.gson.JsonSyntaxException error) {
                failed = true;
                if (chain != null) { chain.close(); chain = null; }
                com.mojang.logging.LogUtils.getLogger().error("Cannot load Odamane halo effect", error);
            } finally { mc.getMainRenderTarget().bindWrite(true); }
        }
        if (chain == null) return RenderType.energySwirl(OdamaneHaloLayer.TEXTURE, 1, 1);
        var target = chain.getTempTarget("final");
        if (target.width != mc.getWindow().getWidth() || target.height != mc.getWindow().getHeight())
            chain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (!prepared) {
            target = chain.getTempTarget("final");
            target.setClearColor(0, 0, 0, 0);target.clear(Minecraft.ON_OSX);
            mc.getMainRenderTarget().bindWrite(true);
            prepared = true;
        }
        pending = true;
        return HALO;
    }

    @SubscribeEvent public static void beginHaloFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) pending = prepared = false;
    }

    @SubscribeEvent public static void compositeHalo(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) composite(event.getPartialTick());
    }

    static void composite(float partialTick) {
        if (!pending || chain == null) return;
        var mc = Minecraft.getInstance();
        try {
            chain.process(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            compositeTarget();
        } finally {
            pending = false;
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();
        }
    }

    private static void compositeTarget() {
        var mc = Minecraft.getInstance();
        var target = chain.getTempTarget("final");
        int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
        var shader = mc.gameRenderer.blitShader;
        shader.setSampler("DiffuseSampler", target.getColorTextureId());
        if (shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0, 0, -2000));
        if (shader.PROJECTION_MATRIX != null)
            shader.PROJECTION_MATRIX.set(new Matrix4f().setOrtho(0, width, height, 0, 1000, 3000));
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        // blitToScreen applies the blit shader AFTER caller blend state, disabling blending.
        // Apply first, then add halo RGB over the world; preserve destination alpha.
        shader.apply();
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            var out = Tesselator.getInstance().getBuilder();
            float u = (float) target.viewWidth / target.width, v = (float) target.viewHeight / target.height;
            out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            out.vertex(0, height, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
            out.vertex(width, height, 0).uv(u, 0).color(255, 255, 255, 255).endVertex();
            out.vertex(width, 0, 0).uv(u, v).color(255, 255, 255, 255).endVertex();
            out.vertex(0, 0, 0).uv(0, v).color(255, 255, 255, 255).endVertex();
            BufferUploader.draw(out.end());
        } finally {
            shader.clear();
        }
    }

}
