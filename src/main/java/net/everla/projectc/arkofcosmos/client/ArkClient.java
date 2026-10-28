package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.item.ArkItem;
import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.everla.projectc.arkofcosmos.calamity.client.ArkAttackRenderer;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.phys.Vec3;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class ArkClient {
    private ArkClient() {}
    private static boolean lastLeft, lastRight, lastAlt, wasHolding;
    private static int heartbeat;
    private static final KeyMapping DASH = new KeyMapping("key.arkofcosmos.dash", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.arkofcosmos");

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ArkEffects.tick();
        boolean active = mc.player != null && mc.screen == null && mc.isWindowActive() && ArkCombat.holding(mc.player);
        boolean left = active && mc.options.keyAttack.isDown();
        boolean right = active && mc.options.keyUse.isDown();
        boolean alt = active && DASH.isDown();
        send(left, right, alt, active);
    }

    @SubscribeEvent public static void effects(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if(event.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        if(Minecraft.getInstance().level==null)return;
        ArkEffects.render(event.getPoseStack(),event.getCamera().getPosition(),event.getPartialTick());
    }
    @SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        ArkEffects.camera(event.getCamera());
    }

    private static void send(boolean left, boolean right, boolean alt, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { lastLeft = lastRight = lastAlt = wasHolding = false; return; }
        if (left != lastLeft || right != lastRight || alt != lastAlt || active != wasHolding || active && ++heartbeat >= 10) {
            ArkNetwork.send(left, right, alt);
            lastLeft = left; lastRight = right; lastAlt = alt; wasHolding = active; heartbeat = 0;
        }
    }

    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ArkCombat.holding(mc.player) && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !ArkCombat.holding(mc.player)) return;
        boolean attack = mc.options.keyAttack.matchesMouse(event.getButton());
        boolean use = mc.options.keyUse.matchesMouse(event.getButton());
        boolean dash = DASH.matchesMouse(event.getButton());
        if (!attack && !use && !dash) return;
        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        // Deliver short clicks and releases between client ticks; also honor rebound keyboard controls in tick().
        boolean left = attack ? pressed : lastLeft;
        boolean right = use ? pressed : lastRight;
        boolean alt = dash ? pressed : DASH.isDown();
        send(left, right, alt, true);
    }

    @SubscribeEvent public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ArkCombat.holding(mc.player)) event.setCanceled(true);
    }

    @SubscribeEvent public static void playerPost(RenderPlayerEvent.Post event) {
        if (!ArkCombat.holding(event.getEntity())) return;
        if (event.getEntity().level().getEntitiesOfClass(ArkAttackEntity.class, event.getEntity().getBoundingBox().inflate(64),
                attack -> attack.ownerId() == event.getEntity().getId()).stream().findAny().isPresent()) return;
        Vec3 right = new Vec3(-Math.cos(Math.toRadians(event.getEntity().getYRot())), 0, -Math.sin(Math.toRadians(event.getEntity().getYRot())));
        Vec3 back = new Vec3(-right.z, 0, right.x);
        Vec3 origin = back.scale(.45).add(new Vec3(0, 1.05, 0));
        ArkAttackRenderer.renderHoverSword(event.getPoseStack(), event.getMultiBufferSource(), origin, right, 15728880);
    }


    @Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
            ArkFxDraw.register(event);
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(DASH);
        }
        @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent event) {
            event.register(ArkOfCosmos.ARK_OF_COSMOS.get(), (graphics, font, stack, x, y) -> {
                int charge = Math.round(13f * ArkItem.charge(stack) / 16);
                int cooldown = Math.round(13f * ArkItem.cooldown(stack) / 340);
                // Separate rows remain visible together in the hotbar and inventory.
                if (ArkItem.charge(stack) > 0) {
                    graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 2, y + 11, x + 15, y + 13, 0xFF000000);
                    float phase = (float)((System.nanoTime() / 1_000_000_000d % 4) / 4);
                    for (int pixel = 0; pixel < charge; pixel++) {
                        int color = net.minecraft.util.Mth.hsvToRgb((phase + pixel / 13f) % 1, 1, 1);
                        graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 2 + pixel, y + 11, x + 3 + pixel, y + 12, 0xFF000000 | color);
                    }
                }
                if (ArkItem.cooldown(stack) > 0) {
                    graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 2, y + 14, x + 15, y + 16, 0xFF000000);
                    graphics.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 2, y + 14, x + 2 + cooldown, y + 15, 0xFFFF9A2E);
                }
                return false;
            });
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ArkOfCosmos.ATTACK.get(), ArkAttackRenderer::new);
        }
    }
}
