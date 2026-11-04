package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.calamity.combat.BrokenBiomeRules;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class BrokenBiomeBladeItem extends SwordItem {
    public BrokenBiomeBladeItem(){super(Tiers.NETHERITE,0,-2.4f,new Item.Properties().stacksTo(1).fireResistant());}
    private static int slot(ItemStack stack,String key){int v=stack.hasTag()&&stack.getTag().contains(key)?stack.getTag().getInt(key):-1;return v>=0&&v<5?v:-1;}
    // Read legacy stored attunement only when the main slot is empty.
    public static int main(ItemStack s){int a=slot(s,"BrokenMain");return a>=0?a:slot(s,"BrokenSecondary");}
    public static void select(ItemStack s,int mode){s.getOrCreateTag().putInt("BrokenMain",mode);s.getOrCreateTag().remove("BrokenSecondary");}
    @Override public Component getName(ItemStack s){return Component.translatable(getDescriptionId()).withStyle(style->style.withColor(0xFFC864));}
    @Override public com.google.common.collect.Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack s){
        return slot==EquipmentSlot.MAINHAND?com.google.common.collect.ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",BrokenBiomeRules.base(main(s))-1,AttributeModifier.Operation.ADDITION)):super.getAttributeModifiers(slot,s);
    }
    @Override public boolean isDamageable(ItemStack s){return false;}
    @Override public boolean onLeftClickEntity(ItemStack s,Player p,Entity e){return true;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return InteractionResultHolder.success(p.getItemInHand(h));}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){
        int a=main(s);
        lines.add(Component.translatable("broken_biome.main",Component.translatable("broken_biome.mode."+a)));
        for(String line:Component.translatable("broken_biome.attack."+a,Component.keybind("key.attack")).getString().split("\n"))lines.add(Component.literal(line).withStyle(style->style.withColor(a<0?0xA3A3A3:BrokenBiomeRules.COLORS[a])));
        for(String line:Component.translatable("broken_biome.controls",Component.keybind("key.attack"),Component.keybind("key.use")).getString().split("\n"))lines.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
