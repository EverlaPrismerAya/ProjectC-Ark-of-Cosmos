package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.calamity.combat.BiomeRules;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class BiomeBladeItem extends SwordItem {
    public BiomeBladeItem(){super(Tiers.NETHERITE,0,-2.4f,new Item.Properties().stacksTo(1).fireResistant());}
    private static int slot(ItemStack stack,String key){int v=stack.hasTag()&&stack.getTag().contains(key)?stack.getTag().getInt(key):-1;return v>=0&&v<8?v:-1;}
    public static int main(ItemStack s){return slot(s,"MendedMain");}
    public static int secondary(ItemStack s){int b=slot(s,"MendedSecondary");return b==main(s)?-1:b;}
    public static void set(ItemStack s,int a,int b){s.getOrCreateTag().putInt("MendedMain",a);s.getOrCreateTag().putInt("MendedSecondary",a==b?-1:b);}
    public static void swap(ItemStack s){set(s,secondary(s),main(s));}
    public static void attune(ItemStack s,int mode){int a=main(s),b=secondary(s);set(s,mode,b==mode?a:b);}
    public static void recoverEmpty(ItemStack s){if(main(s)<0&&secondary(s)>=0)set(s,secondary(s),-1);}
    @Override public com.google.common.collect.Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack s){
        return slot==EquipmentSlot.MAINHAND?com.google.common.collect.ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",BiomeRules.base(main(s))-1,AttributeModifier.Operation.ADDITION)):super.getAttributeModifiers(slot,s);
    }
    @Override public boolean isDamageable(ItemStack s){return false;}
    @Override public boolean onLeftClickEntity(ItemStack s,Player p,Entity e){return true;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return InteractionResultHolder.success(p.getItemInHand(h));}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){
        int a=main(s),b=secondary(s);
        lines.add(Component.translatable("biome.main",Component.translatable("biome.mode."+a)));
        lines.add(Component.translatable("biome.secondary",Component.translatable("biome.mode."+b)));
        for(String line:Component.translatable("biome.attack."+a).getString().split("\n"))lines.add(Component.literal(line).withStyle(style->style.withColor(a<0?0xA3A3A3:BiomeRules.COLORS[a])));
        lines.add(Component.translatable("biome.controls",Component.keybind("key.attack"),Component.keybind("key.use")).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
