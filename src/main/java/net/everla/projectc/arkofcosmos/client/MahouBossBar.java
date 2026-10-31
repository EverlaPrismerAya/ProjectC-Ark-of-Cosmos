package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Replaces only the server boss-event IDs explicitly assigned to Mahou Tsukai. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class MahouBossBar {
    private static final Map<UUID, Integer> PHASES = new HashMap<>();
    private static final ResourceLocation BAR = id("textures/gui/boss_bar.png");
    private static final ResourceLocation[] STARS = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> id("textures/misc/cosmic/cosmic_" + i + ".png")).toArray(ResourceLocation[]::new);
    private static ShaderInstance shader;
    private static final int[][] STAR_FRAMES = new int[10][];

    private MahouBossBar() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, path); }

    public static void update(UUID id, int phase) {
        if (phase <= 0) PHASES.remove(id);
        else PHASES.put(id, Mth.clamp(phase, 1, 3));
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PHASES.clear(); }

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        // Use vanilla metadata parsing and retain each source frame's duration.
        for (int i = 0; i < STARS.length; i++) {
            var resource = event.getResourceProvider().getResourceOrThrow(STARS[i]);
            var metadata = resource.metadata().getSection(
                    net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER).orElseThrow();
            var frames = new java.util.ArrayList<Integer>();
            metadata.forEachFrame((index, duration) -> {
                for (int tick = 0; tick < duration; tick++) frames.add(index);
            });
            if (frames.isEmpty()) {
                try (var stream = resource.open(); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    for (int frame = 0; frame < image.getHeight() / image.getWidth(); frame++)
                        for (int tick = 0; tick < metadata.getDefaultFrameTime(); tick++) frames.add(frame);
                }
            }
            STAR_FRAMES[i] = frames.stream().mapToInt(Integer::intValue).toArray();
        }
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("boss_bar"),
                DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
    }

    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Integer phase = PHASES.get(event.getBossEvent().getId());
        if (phase == null) return;
        event.setCanceled(true);
        event.setIncrement(29);
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - 100, y = event.getY();
        float progress = Mth.clamp(event.getBossEvent().getProgress(), 0f, 1f);
        graphics.flush();
        var previous = RenderSystem.getShader();
        boolean depthTest = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        // GuiGraphics.flush() enables depth testing. HUD quads must not test/write world depth.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            // Dim the unfilled mask, then clip the full-width star field without stretching its UVs.
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, BAR);
            quad(graphics, x, y, 201, 48, .16f);
            if (shader != null) {
                shader.safeGetUniform("uTime").set((mc.level == null ? 0 : mc.level.getGameTime() + event.getPartialTick()) / 20f);
                RenderSystem.setShader(() -> shader);
                for (int i = 0; i < STARS.length; i++) {
                    shader.setSampler("Cosmic" + i, mc.getTextureManager().getTexture(STARS[i]).getId());
                    long tick = mc.level == null ? 0 : mc.level.getGameTime();
                    int[] frames = STAR_FRAMES[i];
                    shader.safeGetUniform("Frame" + i).set((float) frames[Math.floorMod(tick, frames.length)]);
                }
            }
            quad(graphics, x, y, progress <= 0 ? 0 : 3 + Math.round(195 * progress), 48, 1f);
            // Frame and phase ornament retain the texture's original colors.
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            quad(graphics, x, y, 201, (phase - 1) * 16, 1f);
        } finally {
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest();
            RenderSystem.setShader(() -> previous);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }
        var name = event.getBossEvent().getName();
        graphics.drawString(mc.font, name, graphics.guiWidth() / 2 - mc.font.width(name) / 2, y - 9, 0xFFFFFF);
    }

    private static void quad(GuiGraphics graphics, int x, int y, int width, int v, float brightness) {
        if (width <= 0) return;
        var pose = graphics.pose().last().pose();
        var out = Tesselator.getInstance().getBuilder();
        writeQuad(out, pose, x, y, width, v, brightness);
        BufferUploader.drawWithShader(out.end());
    }

    static void writeQuad(BufferBuilder out, org.joml.Matrix4f pose, int x, int y, int width, int v, float brightness) {
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Match GuiGraphics.innerBlit: GUI's inverted Y projection flips winding.
        out.vertex(pose, x, y, 0).uv(0, v / 256f).color(brightness, brightness, brightness, 1f).endVertex();
        out.vertex(pose, x, y + 16, 0).uv(0, (v + 16) / 256f).color(brightness, brightness, brightness, 1f).endVertex();
        out.vertex(pose, x + width, y + 16, 0).uv(width / 256f, (v + 16) / 256f).color(brightness, brightness, brightness, 1f).endVertex();
        out.vertex(pose, x + width, y, 0).uv(width / 256f, v / 256f).color(brightness, brightness, brightness, 1f).endVertex();
    }
}
