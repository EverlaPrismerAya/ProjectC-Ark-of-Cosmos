package net.everla.projectc.arkofcosmos.antiMobBattle.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.util.*;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Independent world-space popup, retained after the victim has left the entity tracker. */
@Mod.EventBusSubscriber(modid = "arkofcosmos", value = Dist.CLIENT)
public final class ObliterationText {
    public static final String TEXT = "Oh my PCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
    private static final long DURATION = 3_000_000_000L;
    private static final Map<UUID, Popup> POPUPS = new LinkedHashMap<>();
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    private static ClientLevel world;
    private static TextureTarget mask;
    private static final Map<String, TextureTarget> MASKS = new HashMap<>();
    private static ShaderInstance shader;
    private static int textWidth;
    private record Popup(Vec3 position, long started, boolean procedureSword) {}
    private ObliterationText() {}

    public static void receive(UUID event, ResourceLocation dimension, Vec3 position, boolean procedureSword) {
        active();
        if (world == null || !world.dimension().location().equals(dimension) || SEEN.putIfAbsent(event, System.nanoTime()) != null) return;
        POPUPS.put(event, new Popup(position, System.nanoTime(), procedureSword));
    }

    public static boolean locksFrames() { return active() && POPUPS.values().stream().anyMatch(p -> !p.procedureSword); }

    public static boolean active() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            world = current;
            POPUPS.clear(); SEEN.clear();
        }
        long now = System.nanoTime();
        POPUPS.values().removeIf(p -> now - p.started >= DURATION);
        SEEN.values().removeIf(started -> now - started >= DURATION * 2);
        return world != null && !POPUPS.isEmpty();
    }

    @Mod.EventBusSubscriber(modid = "arkofcosmos", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class PopupShaders {
        @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
            MASKS.values().forEach(TextureTarget::destroyBuffers); MASKS.clear(); mask = null;
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("arkofcosmos", "anti_mob_battle_cosmic"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    instance -> shader = instance);
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active() || shader == null) return;
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        Vec3 camera = event.getCamera().getPosition();
        if (!OculusShaderCompat.deferForeground(() -> draw(view, camera))) draw(view, camera);
    }

    private static void prepareMask(String content) {
        mask = MASKS.get(content);
        if (mask != null) return;
        Minecraft mc = Minecraft.getInstance();
        var text = Component.literal(content).withStyle(ChatFormatting.BOLD);
        textWidth = mc.font.width(text);
        mask = new TextureTarget(textWidth + 4, mc.font.lineHeight + 4, false, Minecraft.ON_OSX);
        MASKS.put(content, mask);
        mask.setClearColor(0, 0, 0, 0);
        mask.clear(Minecraft.ON_OSX);
        var previousShader = RenderSystem.getShader();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack model = RenderSystem.getModelViewStack();
        model.pushPose(); model.setIdentity(); RenderSystem.applyModelViewMatrix();
        try {
            mask.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, mask.width, mask.height, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            var buffers = mc.renderBuffers().bufferSource();
            mc.font.drawInBatch(text, 2, 2, -1, false, new Matrix4f(), buffers, Font.DisplayMode.NORMAL, 0, 0xf000f0);
            buffers.endBatch();
        } finally {
            model.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void draw(Matrix4f view, Vec3 camera) {
        if (!active()) return;
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var previousShader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(1, TextureAtlas.LOCATION_BLOCKS);
            shader.safeGetUniform("yaw").set((float)Math.toRadians(mc.gameRenderer.getMainCamera().getYRot()));
            shader.safeGetUniform("pitch").set((float)Math.toRadians(mc.gameRenderer.getMainCamera().getXRot()));
            shader.safeGetUniform("externalScale").set(1f);
            shader.safeGetUniform("time").set((float)((System.nanoTime() / 1_000_000L % 20_000_000L) / 50.0));
            float[] uvs = new float[40];
            for (int i = 0; i < 10; i++) {
                var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath("arkofcosmos", "fx/cosmic_" + i));
                uvs[i * 4] = sprite.getU0(); uvs[i * 4 + 1] = sprite.getV0();
                uvs[i * 4 + 2] = sprite.getU1(); uvs[i * 4 + 3] = sprite.getV1();
            }
            shader.safeGetUniform("cosmicuvs").set(uvs);
            long now = System.nanoTime();
            for (Popup popup : POPUPS.values()) {
                prepareMask(popup.procedureSword ? "- Progress Terminated -" : TEXT);
                RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
                RenderSystem.setShader(() -> shader);
                RenderSystem.setShaderTexture(0, mask.getColorTextureId());
                RenderSystem.setShaderTexture(1, TextureAtlas.LOCATION_BLOCKS);
                shader.safeGetUniform("MaskTexel").set(1f / mask.width, 1f / mask.height);
                float age = (now - popup.started) / 1_000_000_000f;
                shader.safeGetUniform("opacity").set(Math.max(0, 1-age/3));
                Vec3 at = popup.position.subtract(camera).add(0, age / 3, 0);
                Matrix4f matrix = new Matrix4f(view).translate((float)at.x, (float)at.y, (float)at.z)
                        .rotate(mc.getEntityRenderDispatcher().cameraOrientation()).scale(-.025f, -.025f, .025f);
                float left = -mask.width / 2f, right = mask.width / 2f, top = -mask.height/2f, bottom = mask.height/2f;
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                buffer.vertex(matrix, left, top, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
                buffer.vertex(matrix, left, bottom, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
                buffer.vertex(matrix, right, bottom, 0).uv(1, 0).color(255, 255, 255, 255).endVertex();
                buffer.vertex(matrix, right, top, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
                BufferUploader.drawWithShader(buffer.end());
            }
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
}
