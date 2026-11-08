package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Apply player attributes once at impact; stored projectile damage stays unscaled. */
public final class PlayerWeaponDamage {
    private PlayerWeaponDamage() {}

    public static float scale(LivingEntity owner, ItemStack weapon, float damage) {
        if (!(owner instanceof Player) || damage <= 0) return damage;
        return (float)(damage * factor(owner.getAttribute(Attributes.ATTACK_DAMAGE),
                owner.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE),
                weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)));
    }

    static double factor(AttributeInstance original, java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeModifier> held,
                         java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeModifier> launched) {
        var attack = new AttributeInstance(original.getAttribute(), ignored -> {});
        attack.replaceFrom(original);
        // Use the launching weapon even if the player has since changed their main hand.
        for (var modifier : held)
            attack.removeModifier(modifier.getId());
        var baseline = new AttributeInstance(original.getAttribute(), ignored -> {});
        baseline.setBaseValue(1);
        for (var modifier : launched) {
            attack.removeModifier(modifier.getId());
            attack.addTransientModifier(modifier);
            baseline.addTransientModifier(modifier);
        }
        double base = baseline.getValue();
        return base > 0 ? attack.getValue() / base : 1;
    }
}
