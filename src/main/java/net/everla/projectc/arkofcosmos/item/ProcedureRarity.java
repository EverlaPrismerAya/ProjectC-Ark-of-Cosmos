package net.everla.projectc.arkofcosmos.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;

public final class ProcedureRarity {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("arkofcosmos", "procedure_rainbow");
    public static final Rarity RAINBOW = Rarity.create("ARK_PROCEDURE_RAINBOW", style -> style.withColor(currentColor(0)).withFont(FONT));
    private ProcedureRarity() {}
    public static int currentColor(long offset) {
        return java.awt.Color.HSBtoRGB(Math.floorMod(System.currentTimeMillis()+offset,6000L)/6000f,.85f,1f)&0xffffff;
    }
    public static MutableComponent number() { return Component.literal("ℵ₀").withStyle(s -> s.withFont(FONT)); }
}
