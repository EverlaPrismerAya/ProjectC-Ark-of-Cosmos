package net.everla.projectc.arkofcosmos.item;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class FirstFractalItem extends SwordItem {
    public FirstFractalItem() { super(Tiers.NETHERITE, 0, -2.4f, new Properties().stacksTo(1).fireResistant()); }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public com.google.common.collect.Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack stack) {
        if(slot!=EquipmentSlot.MAINHAND)return super.getAttributeModifiers(slot,stack);
        return com.google.common.collect.ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",
                net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage("first_fractal")-1,AttributeModifier.Operation.ADDITION));
    }
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity target) { return true; }
    @Override public Component getName(ItemStack stack) { return super.getName(stack).copy().withStyle(s->s.withColor(0xFF2864)); }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.appendTooltip(stack,lines);
        lines.add(Component.translatable("first_fractal.use",Component.keybind("key.attack")).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
