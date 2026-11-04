package net.everla.projectc.arkofcosmos.item;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
public final class OldOneItem extends SwordItem {
    private static final Tier TIER = new Tier() {
        public int getUses(){return 0;} public float getSpeed(){return 9;} public float getAttackDamageBonus(){return 0;}
        public int getLevel(){return 4;} public int getEnchantmentValue(){return 15;} public Ingredient getRepairIngredient(){return Ingredient.EMPTY;}
    };
    public OldOneItem(){super(TIER,19,0,new Item.Properties().stacksTo(1).fireResistant());}
    @Override public net.minecraft.network.chat.Component getName(ItemStack stack){
        return net.minecraft.network.chat.Component.empty().append(net.minecraft.network.chat.Component.translatable(getDescriptionId(stack)).withStyle(style->style.withColor(0x6C2DC7)));
    }
    @Override public boolean isEnchantable(ItemStack stack){return stack.getCount()==1;}
    @Override public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity){return true;}
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> lines, TooltipFlag flag) {
        net.everla.projectc.arkofcosmos.combat.InnateEnchantment.appendTooltip(stack, lines);
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.arkofcosmos.old_one_attack"));
    }
}
