package net.everla.projectc.arkofcosmos.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Draw custom shaders after Oculus' world composite, retaining the scene's depth buffer. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class OculusShaderCompat {
    private static final List<Runnable> DEFERRED = new ArrayList<>();
    private static final List<Runnable> FOREGROUND = new ArrayList<>();
    private static final List<Runnable> HAND_ITEMS = new ArrayList<>();
    private static final PoseStack WORLD_POSE = new PoseStack();
    private static final Matrix4f MODEL_VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static Object api;
    private static Method shaderPack, shadowPass;
    private static boolean initialized, active, drawing, viewReady, backgroundQueued;

    private OculusShaderCompat() {}

    public static boolean shaderPackActive() { return active; }

    @SubscribeEvent
    public static void beginFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        DEFERRED.clear();
        FOREGROUND.clear();
        HAND_ITEMS.clear();
        viewReady = false;
        backgroundQueued = false;
        if (!initialized) {
            initialized = true;
            if (ModList.get().isLoaded("oculus")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    shaderPack = type.getMethod("isShaderPackInUse");
                    shadowPass = type.getMethod("isRenderingShadowPass");
                } catch (ReflectiveOperationException | LinkageError error) {
                    com.mojang.logging.LogUtils.getLogger().warn("Cannot initialize Ark Oculus compatibility", error);
                }
            }
        }
        active = query(shaderPack);
    }

    private static boolean query(Method method) {
        if (method == null) return false;
        try {
            return (boolean)method.invoke(api);
        } catch (ReflectiveOperationException error) {
            shaderPack = shadowPass = null;
            active = false;
            com.mojang.logging.LogUtils.getLogger().warn("Cannot query Oculus shader state", error);
            return false;
        }
    }

    /** Item poses/projections differ from the world camera, especially for first-person hands. */
    public static boolean deferItem(Runnable draw, boolean firstPerson) {
        if (!active || drawing) return false;
        if (query(shadowPass)) return true;
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Runnable replay = () -> {
            var previousShader = RenderSystem.getShader();
            var raster = new RasterState();
            RenderSystem.backupProjectionMatrix();
            var view = RenderSystem.getModelViewStack();
            view.pushPose();
            view.last().pose().set(modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            try { draw.run(); }
            finally {
                view.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
                RenderSystem.setShader(() -> previousShader);
                raster.close();
            }
        };
        if (firstPerson) HAND_ITEMS.add(replay);
        else DEFERRED.add(replay);
        return true;
    }

    @SubscribeEvent
    public static void beforeGui(net.minecraftforge.client.event.RenderGuiEvent.Pre event) { drawHandItems(); }

    @SubscribeEvent
    public static void endFrame(TickEvent.RenderTickEvent event) {
        // Hidden GUI frames may never dispatch RenderGuiEvent.
        if (event.phase == TickEvent.Phase.END) drawHandItems();
    }

    private static void drawHandItems() {
        if (HAND_ITEMS.isEmpty()) return;
        try { for (Runnable draw : HAND_ITEMS) draw.run(); }
        finally { HAND_ITEMS.clear(); }
    }

    /** Snapshot entity-local transforms before the dispatcher pops its mutable pose stack. */
    public static boolean defer(Runnable draw) { return defer(draw, false); }

    /** A retained-depth backdrop must precede particles even when Oculus has no shader pack. */
    public static void deferBackground(Runnable draw) {
        if (query(shadowPass)) return;
        if (defer(draw, true)) backgroundQueued = true;
        else draw.run();
    }

    public static boolean deferForeground(Runnable draw){
        if(drawing)return false;
        int first=DEFERRED.size();
        if(!defer(draw,true))return false;
        if(DEFERRED.size()>first)FOREGROUND.add(DEFERRED.remove(first));
        return true;
    }

    /** Entity FX that must follow clouds/translucency also use the retained-depth pass without a pack. */
    public static boolean defer(Runnable draw, boolean afterWorld) {
        if (!active && !afterWorld) return false;
        if (query(shadowPass)) return true;
        if (drawing) return false;
        // Standalone world draws (for example death popups) capture their own view and
        // can be queued before any ArkFxDraw entity context exists this frame.
        Matrix4f matrix = ArkFxDraw.matrix == null ? null : new Matrix4f(ArkFxDraw.matrix);
        Vec3 camera = ArkFxDraw.camera, eye = ArkFxDraw.fadeEye;
        DEFERRED.add(() -> {
            Matrix4f previousMatrix = ArkFxDraw.matrix;
            Vec3 previousCamera = ArkFxDraw.camera, previousEye = ArkFxDraw.fadeEye;
            ArkFxDraw.matrix = matrix; ArkFxDraw.camera = camera; ArkFxDraw.fadeEye = eye;
            try { draw.run(); }
            finally {
                ArkFxDraw.matrix = previousMatrix; ArkFxDraw.camera = previousCamera; ArkFxDraw.fadeEye = previousEye;
            }
        });
        return true;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || active && query(shadowPass)) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (!active && !backgroundQueued) {
                ArkEffects.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
            }
            // AFTER_LEVEL receives GameRenderer's projection pose, not this world pose.
            WORLD_POSE.last().pose().set(event.getPoseStack().last().pose());
            WORLD_POSE.last().normal().set(event.getPoseStack().last().normal());
            MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
            PROJECTION.set(event.getProjectionMatrix());
            viewReady = true;
        }
        if ((!active && DEFERRED.isEmpty() && FOREGROUND.isEmpty()) || !viewReady || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        mc.getMainRenderTarget().bindWrite(true);
        var shader = RenderSystem.getShader();
        var raster = new RasterState();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.last().pose().set(MODEL_VIEW);
        RenderSystem.applyModelViewMatrix();
        drawing = true;
        try {
            for (Runnable draw : DEFERRED) draw.run();
            if (active || backgroundQueued) {
                ArkEffects.render(WORLD_POSE, event.getCamera().getPosition(), event.getPartialTick());
            }
            for(Runnable draw:FOREGROUND)draw.run();
            net.everla.projectc.arkofcosmos.client.OdamaneHaloEffects.compositeFromOculus(event.getPartialTick());
        } finally {
            drawing = false;
            viewReady = false;
            backgroundQueued = false;
            DEFERRED.clear();
            FOREGROUND.clear();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShader(() -> shader);
            raster.close();
        }
    }
    // Deferred FX may use ArkFxDraw.end(), which resets culling and blend factors
    // to its defaults. Restore the caller's state after the entire replay.
    public static final class RasterState implements AutoCloseable {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int equation = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        @Override public void close() {
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(write);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendEquation(equation);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        }
    }

}
