package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.combat.ArkCombat;
import net.everla.projectc.arkofcosmos.calamity.combat.ArkWeaponRules;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class ArkItem extends SwordItem {
    // A real sword category with zero uses: enchantability does not require a durability pool.
    private static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 9; }
        @Override public float getAttackDamageBonus() { return 0; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };
    public ArkItem() { super(TIER, (int)ArkCombat.BASE_DAMAGE - 1, 0, new Properties().stacksTo(1).fireResistant()); }
    @Override public boolean isEnchantable(ItemStack stack) { return stack.getCount() == 1; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment != Enchantments.UNBREAKING && enchantment != Enchantments.MENDING && enchantment.category.canEnchant(this);
    }
    @Override public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) { return true; }
    @Override public Component getName(ItemStack stack) {
        return Component.empty().append(Component.translatable(getDescriptionId(stack)).withStyle(style -> style.withColor(0x6C2DC7)));
    }
    public static int charge(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("ArkCharge") : 0; }
    public static void charge(ItemStack stack, int amount) { stack.getOrCreateTag().putInt("ArkCharge", Math.max(0, Math.min(ArkWeaponRules.MAX_CHARGE, amount))); }
    public static int cooldown(ItemStack stack) { return stack.hasTag() ? stack.getTag().getInt("ArkParryCooldown") : 0; }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            int remaining = ArkCombat.parryCooldown(player);
            if (cooldown(stack) != remaining) stack.getOrCreateTag().putInt("ArkParryCooldown", remaining);
        }
    }
    @Override public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        return com.google.common.collect.ImmutableMultimap.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", ArkCombat.BASE_DAMAGE - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
    // Both indicators are drawn by an item decorator, never by the vanilla durability bar.
    @Override public boolean isBarVisible(ItemStack stack) { return false; }
    @Override public boolean onEntitySwing(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return true; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) { return true; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Both attacks are driven by validated input packets, including clicks in empty space.
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        float wave = (float)Math.sin((System.nanoTime() / 1_000_000_000d) % 3600);
        for (String key : new String[]{"controls", "parry", "blast", "flavor"}) {
            int color = ArkWeaponRules.tooltipColor(key, wave);
            Component text = key.equals("blast")
                    ? Component.translatable("tooltip.arkofcosmos.blast", Component.keybind("key.arkofcosmos.dash"), Component.keybind("key.use"))
                    : Component.translatable("tooltip.arkofcosmos." + key);
            for (String line : text.getString().split("\n"))
                lines.add(Component.literal(line).withStyle(style -> style.withColor(color)));
        }
    }

}
