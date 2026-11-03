package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.calamity.combat.TrueBiomeRules;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class TrueBiomeBladeItem extends SwordItem {
    public TrueBiomeBladeItem(){super(Tiers.NETHERITE,0,-2.4f,new Item.Properties().stacksTo(1).fireResistant());}
    public static int main(ItemStack s){return s.hasTag()?Math.floorMod(s.getTag().getInt("BiomeMain"),4):0;}
    public static int secondary(ItemStack s){int m=main(s),v=s.hasTag()&&s.getTag().contains("BiomeSecondary")?Math.floorMod(s.getTag().getInt("BiomeSecondary"),4):1;return v==m?(m+1)%4:v;}
    public static void select(ItemStack s,int slot,int mode){
        int a=main(s),b=secondary(s);
        if(slot==0){if(mode==b)b=a;a=mode;}else{if(mode==a)a=b;b=mode;}
        s.getOrCreateTag().putInt("BiomeMain",a);s.getOrCreateTag().putInt("BiomeSecondary",b);
    }
    @Override public com.google.common.collect.Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack s){
        if(slot!=EquipmentSlot.MAINHAND)return super.getAttributeModifiers(slot,s);
        return com.google.common.collect.ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",TrueBiomeRules.base(main(s))-1,AttributeModifier.Operation.ADDITION));
    }
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public boolean onLeftClickEntity(ItemStack stack,Player p,Entity e){return true;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return InteractionResultHolder.success(p.getItemInHand(h));}
    @Override public Component getName(ItemStack s){return Component.translatable(getDescriptionId()).withStyle(style->style.withColor(0xFF69B4));}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){
        int a=main(s),b=secondary(s);
        lines.add(Component.translatable("true_biome.main",Component.translatable("true_biome.mode."+a)).withStyle(style->style.withColor(TrueBiomeRules.COLOR[a])));
        for(String line:Component.translatable("true_biome.attack."+a).getString().split("\n"))lines.add(Component.literal(line).withStyle(style->style.withColor(TrueBiomeRules.COLOR[a])));
        lines.add(Component.translatable("true_biome.secondary",Component.translatable("true_biome.mode."+b)).withStyle(style->style.withColor(TrueBiomeRules.COLOR[b])));
        lines.add(Component.translatable("true_biome.passive."+b).withStyle(style->style.withColor(TrueBiomeRules.COLOR[b])));
        lines.add(Component.translatable("true_biome.controls",Component.keybind("key.attack"),Component.keybind("key.use")).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
