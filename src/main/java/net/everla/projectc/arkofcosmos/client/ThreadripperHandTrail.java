package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.item.ProcedureSwordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Camera-local history of the actual rendered blade, for local empty swings only. */
@Mod.EventBusSubscriber(modid = "arkofcosmos", value = Dist.CLIENT)
public final class ThreadripperHandTrail {
    private static final int LIFE = 5;
    private static final List<Sample> samples = new ArrayList<>();
    private static ClientLevel world;
    private static double start;
    private static ClientLevel flashWorld;
    private static double flashAt;
    private record Sample(Vec3 heel, Vec3 tip, double tick) {}

    private ThreadripperHandTrail() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || !event.isAttack() || !event.shouldSwingHand()
                || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.BLOCK
                || !ProcedureSwordItem.unrestricted(player) || player.isUsingItem()
                || !(player.getMainHandItem().getItem() instanceof ProcedureSwordItem)) return;
        flashWorld = mc.level;
        flashAt = mc.level.getGameTime() + mc.getFrameTime();
        if (!mc.options.getCameraType().isFirstPerson() || mc.hitResult.getType() != HitResult.Type.MISS) return;
        world = mc.level;
        start = flashAt;
        samples.clear();
    }

    static Runnable capture(PoseStack pose, ItemDisplayContext context) {
        var mc = Minecraft.getInstance();
        if (world == null || mc.level != world) { world = null; samples.clear(); return null; }
        double now = world.getGameTime() + mc.getFrameTime();
        if (now - start >= LIFE) { world = null; samples.clear(); return null; }
        if (mc.player == null || !ProcedureSwordItem.unrestricted(mc.player) || mc.player.isUsingItem()
                || context != (mc.player.getMainArm() == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) return null;
        Matrix4f transform = pose.last().pose();
        Sample head = new Sample(transform(transform, .48f, .48f), transform(transform, .95f, .95f), now);
        // Store at most three samples per tick; always use the live blade as the newest endpoint.
        if (samples.isEmpty() || now - samples.get(samples.size() - 1).tick() >= 0.34) samples.add(head);
        List<Sample> trail = new ArrayList<>(samples);
        if (trail.get(trail.size() - 1) != head) trail.add(head);
        if (trail.size() < 2) return null;
        return () -> draw(trail, now);
    }

    private static Vec3 transform(Matrix4f transform, float x, float y) {
        var p = transform.transformPosition(x, y, .5f, new Vector3f());
        return new Vec3(p.x, p.y, p.z);
    }

    static float tipFlash(ItemDisplayContext context) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != flashWorld || mc.player == null) return 0;
        boolean right = mc.player.getMainArm() == HumanoidArm.RIGHT;
        if (context != (right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                && context != (right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) return 0;
        double age = Math.max(0, mc.level.getGameTime() + mc.getFrameTime() - flashAt);
        return (float)Math.pow(Math.max(0, 1 - age / 3.6), 1.75);
    }

    static void glow(PoseStack pose, ItemDisplayContext context, float time, float tipFlash,
                     ProcedureSwordRenderer.BladeFace front, ProcedureSwordRenderer.BladeFace back) {
        boolean gui = context == ItemDisplayContext.GUI, ground = context == ItemDisplayContext.GROUND;
        float phase = (time % 48) / 48;
        float energy = (context.firstPerson() ? .63f : .46f) + .09f * (float)Math.cos(phase * Math.PI * 2);
        Matrix4f matrix = pose.last().pose();
        Vector3f eye = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrix).invert().transformPosition(new Vector3f());
        var face = gui || eye.z >= .5 ? front : back;
        if (face == null) return;
        drawItemFx(matrix, "item/procedure_sword", true, buffer -> {
            ThreadripperEffects.quad(buffer, face.center(), face.x().scale(1.25), face.y().scale(1.25),
                    ArkFxDraw.rgba(ground ? .54f : 1, 1, 1, ground ? .53f : gui ? .88f : 1), -13 - phase);
            if (gui || ground) return;
            Vec3 along = face.x().add(face.y()), across = face.x().subtract(face.y());
            Vec3 center = face.center().add(along.scale(.484));
            Vec3 longAxis = along.scale(.90);
            ThreadripperEffects.quad(buffer, center, longAxis, across.scale(.48),
                    ArkFxDraw.rgba(.31f, .72f, 1, energy), -8 - phase * .9f);
            ThreadripperEffects.quad(buffer, center.multiply(1, 1, 0).add(0, 0, .5), longAxis, new Vec3(0, 0, .32),
                    ArkFxDraw.rgba(.48f, .35f, 1, energy * .63f), -8 - phase * .9f);
            if (tipFlash > 0) {
                double flare = .44 + tipFlash * .40;
                ThreadripperEffects.quad(buffer, face.center().add(along.scale(.914)), along.scale(flare), across.scale(flare),
                        ArkFxDraw.rgba(.53f, .91f, 1, tipFlash), -10);
            }
        });
    }

    private static void draw(List<Sample> trail, double now) {
        drawItemFx(new Matrix4f(), "misc/white", false, buffer -> {
            float fade = (float)Math.max(0, 1 - (now - start) / LIFE);
            for (int i = 1; i < trail.size(); i++) {
                Sample a = trail.get(i - 1), b = trail.get(i);
                vertex(buffer, a, now, fade, false);
                vertex(buffer, a, now, fade, true);
                vertex(buffer, b, now, fade, true);
                vertex(buffer, b, now, fade, false);
            }
        });
    }

    private static void drawItemFx(Matrix4f matrix, String texture, boolean surface, Consumer<BufferBuilder> emit) {
        if (ArkFxDraw.riftShader == null) return;
        Matrix4f previousMatrix = ArkFxDraw.matrix;
        Vec3 previousCamera = ArkFxDraw.camera, previousEye = ArkFxDraw.fadeEye;
        ArkFxDraw.matrix = matrix; ArkFxDraw.camera = Vec3.ZERO; ArkFxDraw.fadeEye = null;
        ArkFxDraw.begin(texture, ArkFxDraw.Blend.ADDITIVE, false);
        // Bias rasterized depth, not the vertices: the outline stays on the baked blade face.
        boolean offsetWasEnabled = surface && GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float oldFactor = surface ? GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR) : 0;
        float oldUnits = surface ? GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS) : 0;
        try {
            if (surface) {
                RenderSystem.polygonOffset(0, -2);
                RenderSystem.enablePolygonOffset();
            }
            RenderSystem.setShader(() -> ArkFxDraw.riftShader);
            var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT);
            emit.accept(buffer);
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            if (surface) {
                RenderSystem.polygonOffset(oldFactor, oldUnits);
                if (!offsetWasEnabled) RenderSystem.disablePolygonOffset();
            }
            ArkFxDraw.end();
            ArkFxDraw.matrix = previousMatrix; ArkFxDraw.camera = previousCamera; ArkFxDraw.fadeEye = previousEye;
        }
    }

    private static void vertex(BufferBuilder buffer, Sample sample, double now, float fade, boolean heel) {
        float tail = (float)Math.max(0, 1 - (now - sample.tick()) / 2.6);
        float[] color = ArkFxDraw.rgba(.45f + .28f * (1 - tail), .43f + .46f * tail, 1, .37f * fade * tail * tail);
        ArkFxDraw.vertex(buffer, heel ? sample.heel() : sample.tip(), color, heel ? 0 : 1, (float)(1 - tail), -1);
    }
}
