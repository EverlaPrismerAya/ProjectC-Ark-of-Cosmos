package net.everla.projectc.arkofcosmos.item;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class ViolenceItem extends SwordItem {
    public static final float BASE_DAMAGE = 24.25f;
    public ViolenceItem() { super(Tiers.NETHERITE, 0, -2.4f, new Properties().stacksTo(1).fireResistant()); }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot,ItemStack stack) {
        if(slot!=net.minecraft.world.entity.EquipmentSlot.MAINHAND)return super.getAttributeModifiers(slot,stack);
        return com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
            new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",net.everla.projectc.arkofcosmos.ProjectCCommonConfig.damage("violence")-1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId(stack)).withStyle(s->s.withColor(0x6C2DC7)); }
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,net.minecraft.world.entity.Entity target) { return true; }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return InteractionResultHolder.success(player.getItemInHand(hand)); }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.appendTooltip(stack,lines);
        lines.add(Component.translatable("item.arkofcosmos.violence.flavor").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.arkofcosmos.violence.left",Component.keybind("key.attack")).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.arkofcosmos.violence.right",Component.keybind("key.use")).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.arkofcosmos.violence.aflame").withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }
}
