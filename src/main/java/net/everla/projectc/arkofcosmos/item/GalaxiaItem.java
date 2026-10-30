package net.everla.projectc.arkofcosmos.item;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules;

public final class GalaxiaItem extends SwordItem {
    public GalaxiaItem() { super(Tiers.NETHERITE, 0, -2.4f, new Item.Properties().stacksTo(1).fireResistant()); }
    public static int mode(ItemStack stack) { return Math.floorMod(stack.getOrCreateTag().getInt("GalaxiaMode"),4); }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot,ItemStack stack) {
        if(slot!=net.minecraft.world.entity.EquipmentSlot.MAINHAND)return super.getAttributeModifiers(slot,stack);
        return com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.base(mode(stack))-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public Component getName(ItemStack stack) {
        return Component.empty().append(Component.translatable(getDescriptionId(stack))
                .withStyle(style -> style.withColor(GalaxiaRules.NAME_COLOR)));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) { return true; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        int attunement = mode(stack);
        int active = GalaxiaRules.tooltipColor(attunement);
        int passive = GalaxiaRules.passiveColor(attunement);
        int wave = GalaxiaRules.attunementColor(attunement, (System.nanoTime() / 1_000_000_000d) % 3600);
        lines.add(Component.translatable("galaxia.attunement", Component.translatable("galaxia.mode."+attunement))
                .withStyle(style -> style.withColor(wave)));
        addLines(lines, "galaxia.description."+attunement, active);
        addLines(lines, "galaxia.passive."+(attunement < 2 ? 0 : 1), passive);
        lines.add(Component.translatable("galaxia.controls", Component.keybind("key.attack"))
                .withStyle(style -> style.withColor(active)));
        addLines(lines, "galaxia.selection_controls", wave, Component.keybind("key.use"));
        addLines(lines, "galaxia.flavor", GalaxiaRules.NAME_COLOR);
    }
    private static void addLines(List<Component> lines, String key, int color, Object... args) {
        for (String line : Component.translatable(key, args).getString().split("\n"))
            lines.add(Component.literal(line).withStyle(style -> style.withColor(color)));
    }
}
