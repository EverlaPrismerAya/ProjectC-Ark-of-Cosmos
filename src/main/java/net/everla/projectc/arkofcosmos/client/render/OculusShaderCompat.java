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

/** Draw custom shaders after Oculus' world composite, retaining the scene's depth buffer. */
@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class OculusShaderCompat {
    private static final List<Runnable> DEFERRED = new ArrayList<>();
    private static final PoseStack WORLD_POSE = new PoseStack();
    private static final Matrix4f MODEL_VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static Object api;
    private static Method shaderPack, shadowPass;
    private static boolean initialized, active, drawing, viewReady;

    private OculusShaderCompat() {}

    public static boolean shaderPackActive() { return active; }

    @SubscribeEvent
    public static void beginFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        DEFERRED.clear();
        viewReady = false;
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

    /** Snapshot entity-local transforms before the dispatcher pops its mutable pose stack. */
    public static boolean defer(Runnable draw) {
        if (!active) return false;
        if (query(shadowPass)) return true;
        if (drawing) return false;
        Matrix4f matrix = new Matrix4f(ArkFxDraw.matrix);
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
            if (!active) {
                ArkEffects.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
            } else {
                // AFTER_LEVEL receives GameRenderer's projection pose, not this world pose.
                WORLD_POSE.last().pose().set(event.getPoseStack().last().pose());
                WORLD_POSE.last().normal().set(event.getPoseStack().last().normal());
                MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
                PROJECTION.set(event.getProjectionMatrix());
                viewReady = true;
            }
        }
        if (!active || !viewReady || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        mc.getMainRenderTarget().bindWrite(true);
        var shader = RenderSystem.getShader();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.last().pose().set(MODEL_VIEW);
        RenderSystem.applyModelViewMatrix();
        drawing = true;
        try {
            for (Runnable draw : DEFERRED) draw.run();
            ArkEffects.render(WORLD_POSE, event.getCamera().getPosition(), event.getPartialTick());
        } finally {
            drawing = false;
            viewReady = false;
            DEFERRED.clear();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShader(() -> shader);
            ArkFxDraw.end();
        }
    }
}
