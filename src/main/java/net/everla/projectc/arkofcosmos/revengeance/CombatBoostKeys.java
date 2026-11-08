package net.everla.projectc.arkofcosmos.revengeance;

import com.mojang.blaze3d.platform.InputConstants;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public final class CombatBoostKeys {
    public static final KeyMapping RAGE = new KeyMapping("key.arkofcosmos.rage_button", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.gameplay");
    public static final KeyMapping ADRENALINE = new KeyMapping("key.arkofcosmos.adrenaline_button", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.gameplay");

    // Match the physical event independently: KeyMapping's down/click state can be shared by conflicting bindings.
    static void press(InputConstants.Key key) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.isSpectator()) return;
        boolean rage = RAGE.isActiveAndMatches(key);
        boolean adrenaline = ADRENALINE.isActiveAndMatches(key);
        if (rage || adrenaline) ArkNetwork.combatBoost(rage, adrenaline);
    }
    @SubscribeEvent public static void keyboard(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) press(InputConstants.getKey(event.getKey(), event.getScanCode()));
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) press(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }
    @Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            event.register(RAGE);
            event.register(ADRENALINE);
        }
    }
}
