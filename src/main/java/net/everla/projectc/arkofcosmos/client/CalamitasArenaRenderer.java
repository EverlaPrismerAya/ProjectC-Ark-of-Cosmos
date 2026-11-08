package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.client.CalamitasArenaVisual;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.everla.projectc.arkofcosmos.client.render.OculusShaderCompat;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Six world-space wall surfaces, projected through the pinned screen-space metaball material.
 * Geometry never writes world blocks or the main depth buffer. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID, value=Dist.CLIENT)
public final class CalamitasArenaRenderer {
    private static final Map<UUID, CalamitasArenaVisual> ARENAS = new LinkedHashMap<>();
    private static Level level;
    private static RenderTarget mask, visible;
    private static void releaseTargets(){
        if(mask!=null){mask.destroyBuffers();mask=null;}
        if(visible!=null){visible.destroyBuffers();visible=null;}
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
    private CalamitasArenaRenderer() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var current = Minecraft.getInstance().level;
        if (current != level) {
            ARENAS.clear(); level = current;
            releaseTargets();
        }
        if (current == null) return;
        if (Minecraft.getInstance().isPaused()) return;
        var alive = new HashSet<UUID>();
        for (var boss : SupremeCalamitasEntity.loaded(current)) {
            var bounds = boss.arenaBounds(); double pixels = boss.view().getDouble("Pixels");
            if (bounds == null || pixels <= 0) continue;
            alive.add(boss.getUUID());
            ARENAS.compute(boss.getUUID(), (id, old) -> old == null || old.retiring()
                    ? new CalamitasArenaVisual(id.getMostSignificantBits() ^ id.getLeastSignificantBits()) : old)
                    .snapshot(bounds, pixels, boss.view().getCompound("Arena").getInt("BorderColor"));
        }
        ARENAS.forEach((id, arena) -> {
            if (!alive.contains(id)) arena.retire();
            for (int source = 0; source < 3; source++) arena.step();
        });
        ARENAS.values().removeIf(CalamitasArenaVisual::empty);
        if(ARENAS.isEmpty()&&(mask!=null||visible!=null))releaseTargets();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || ARENAS.isEmpty()) return;
        ArkFxDraw.matrix = event.getPoseStack().last().pose();
        ArkFxDraw.camera = event.getCamera().getPosition(); ArkFxDraw.fadeEye = null;
        double time = (Minecraft.getInstance().level.getGameTime() % 72000 + event.getPartialTick()) / 20d;
        // Queue before entity FX so translucent attacks are drawn over their arena backdrop.
        OculusShaderCompat.deferBackground(() -> draw(time));
    }

    private static void draw(double time) {
        var mc = Minecraft.getInstance(); var main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;
        if (mask == null || visible == null || mask.width != main.width || mask.height != main.height) {
            releaseTargets();
            try {
                mask = EffectRenderTarget.create(main.width,main.height,false);
                visible = EffectRenderTarget.create(main.width,main.height,true);
            } catch(RuntimeException|Error failure){releaseTargets();throw failure;}
            finally {main.bindWrite(true);}
        }
        var shader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor().clone();
        int texture0 = RenderSystem.getShaderTexture(0);
        int src = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dst = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean write = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (var arena : ARENAS.values()) {
                mask.clear(Minecraft.ON_OSX); mask.bindWrite(true); project(arena, false);
                visible.clear(Minecraft.ON_OSX); visible.copyDepthFrom(main); visible.bindWrite(true); project(arena, true);
                main.bindWrite(true);
                decorations(arena, true);
                CalamitasArenaMaterial.composite(mask, visible, arena.color(), time,
                        ArkFxDraw.camera.x * arena.pixels(), -ArkFxDraw.camera.y * arena.pixels());
                decorations(arena, false);
            }
        } finally {
            main.bindWrite(true); RenderSystem.setShader(() -> shader); RenderSystem.depthMask(write);
            RenderSystem.setShaderTexture(0, texture0); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void project(CalamitasArenaVisual arena, boolean depth) {
        RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.disableBlend();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (arena.currentStrip() != null || arena.previousStrip() != null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            var out = Tesselator.getInstance().getBuilder(); out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            // The source static strips occupy [5, thickness-1] source pixels outside the logical boundary.
            strip(out, arena.previousStrip());
            strip(out, arena.currentStrip());
            BufferUploader.drawWithShader(out.end());
        }
        if (arena.particles().isEmpty()) return;
        ArkFxDraw.begin("calamitas_arena_circle", ArkFxDraw.Blend.ALPHA, false);
        if (!depth) RenderSystem.disableDepthTest();
        var face = NativeSprites.billboard(); var out = Tesselator.getInstance().getBuilder();
        out.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT); float[] white = {1, 1, 1, 1};
        for (var particle : arena.particles()) {
            double radius = particle.size / arena.pixels() / 2;
            Vec3 x = face.forward().scale(radius), y = face.right().scale(radius), p = particle.center;
            ArkFxDraw.vertex(out, p.subtract(x).subtract(y), white, 0, 0, 1);
            ArkFxDraw.vertex(out, p.subtract(x).add(y), white, 0, 1, 1);
            ArkFxDraw.vertex(out, p.add(x).add(y), white, 1, 1, 1);
            ArkFxDraw.vertex(out, p.add(x).subtract(y), white, 1, 0, 1);
        }
        BufferUploader.drawWithShader(out.end()); ArkFxDraw.end();
    }

    private static void strip(BufferBuilder out, CalamitasArenaVisual.Strip strip) {
        if (strip == null || strip.thickness() <= 6) return;
        box(out, strip.bounds().inflate(5 / strip.pixels()), 1, 1, 1, 1);
        box(out, strip.bounds().inflate((strip.thickness() - 1) / strip.pixels()), 1, 1, 1, 1);
    }

    private static void decorations(CalamitasArenaVisual arena, boolean band) {
        if (arena.thickness() < 4) return;
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.enableBlend(); RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var out = Tesselator.getInstance().getBuilder(); out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = (arena.color() >> 16 & 255) / 255f, g = (arena.color() >> 8 & 255) / 255f, b = (arena.color() & 255) / 255f;
        if (band) box(out, arena.bounds(), 0, 0, 0, 38 / 255f);
        else {
            // The guide marks the collision planes, not the outer edge of the
            // source's 2000-pixel decorative wall band.
            frame(out, arena.bounds(), 1 / arena.pixels(), r, g, b, 1);
        }
        BufferUploader.drawWithShader(out.end());
    }
    private static void frame(BufferBuilder out, AABB a, double half, float r, float g, float b, float alpha) {
        for (int axis = 0; axis < 3; axis++) for (int u = 0; u < 2; u++) for (int v = 0; v < 2; v++) {
            double[] lo = {a.minX, a.minY, a.minZ}, hi = {a.maxX, a.maxY, a.maxZ};
            int j = (axis + 1) % 3, k = (axis + 2) % 3;
            double x = u == 0 ? lo[j] : hi[j], y = v == 0 ? lo[k] : hi[k];
            lo[j] = x - half; hi[j] = x + half; lo[k] = y - half; hi[k] = y + half;
            box(out, new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]), r * alpha, g * alpha, b * alpha, alpha);
        }
    }
    private static void box(BufferBuilder out, AABB a, float r, float g, float b, float alpha) {
        for (int axis = 0; axis < 3; axis++) for (int side = 0; side < 2; side++) {
            int j = (axis + 1) % 3, k = (axis + 2) % 3;
            double[] lo = {a.minX, a.minY, a.minZ}, hi = {a.maxX, a.maxY, a.maxZ};
            for (int corner : new int[]{0, 1, 3, 2}) {
                double[] p = new double[3]; p[axis] = side == 0 ? lo[axis] : hi[axis];
                p[j] = (corner & 1) == 0 ? lo[j] : hi[j]; p[k] = (corner & 2) == 0 ? lo[k] : hi[k];
                Vec3 camera = ArkFxDraw.camera;
                out.vertex(ArkFxDraw.matrix, (float)(p[0] - camera.x), (float)(p[1] - camera.y), (float)(p[2] - camera.z)).color(r, g, b, alpha).endVertex();
            }
        }
    }
}
