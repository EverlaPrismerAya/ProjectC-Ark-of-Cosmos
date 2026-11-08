package net.everla.projectc.arkofcosmos.combat;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.*;

public final class WeaponAttributeCheck {
    private static AttributeModifier modifier(double value, AttributeModifier.Operation operation) {
        return new AttributeModifier(UUID.randomUUID(), "check", value, operation);
    }
    private static void equal(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-6) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        var attribute = new RangedAttribute("check", 1, 0, 2048);
        var attack = new AttributeInstance(attribute, ignored -> {});
        var weapon = modifier(19, AttributeModifier.Operation.ADDITION);
        var held = List.of(weapon);
        attack.addTransientModifier(weapon);
        equal(PlayerWeaponDamage.factor(attack, held, held), 1);
        var rage = modifier(.35, AttributeModifier.Operation.MULTIPLY_BASE);
        var adrenaline = modifier(2.45, AttributeModifier.Operation.MULTIPLY_BASE);
        attack.addTransientModifier(rage);
        equal(PlayerWeaponDamage.factor(attack, held, held), 1.35);
        attack.removeModifier(rage);
        attack.addTransientModifier(adrenaline);
        equal(PlayerWeaponDamage.factor(attack, held, held), 3.45);
        attack.addTransientModifier(rage);
        equal(PlayerWeaponDamage.factor(attack, held, held), 3.8);
        // Additive attack bonuses and external total multipliers remain intact.
        attack.addTransientModifier(modifier(5, AttributeModifier.Operation.ADDITION));
        attack.addTransientModifier(modifier(.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        equal(PlayerWeaponDamage.factor(attack, held, held), 25 * 3.8 * 1.5 / 20);
        // Switching to another weapon cannot replace the launching weapon's base.
        attack.removeModifier(weapon);
        var other = new AttributeModifier(weapon.getId(), "other weapon", 99, AttributeModifier.Operation.ADDITION);
        attack.addTransientModifier(other);
        equal(PlayerWeaponDamage.factor(attack, List.of(other), held), 25 * 3.8 * 1.5 / 20);
        equal(attack.getValue(), 105 * 3.8 * 1.5);
        System.out.println("Weapon attribute checks passed (base, rage, adrenaline, stacking, external bonuses, weapon switch).");
    }
}
