package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Boss outgoing damage policy, independent of Ark weapon compatibility and multipliers. */
public final class BossDamage {
    private BossDamage() {}

    public static boolean owns(DamageSource source) {
        return source.getEntity() instanceof MahouTsukaiEntity || CalamityAttackGuard.damageOwner(source) != null;
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        // WeaponDamage reserves the existing shared victim cooldown and native hurt context.
        if (!WeaponDamage.handling(target, source)) return WeaponDamage.hurt(target, source, amount);
        if (!ArkDamage.validHit(target, amount)) return false;
        if (ArkDamage.nativeTarget(target)) return target.hurt(source, amount);
        boolean fourth = CalamityAttackGuard.damageOwner(source) != null;
        float damage = fourth ? mitigated(target, source, amount) : mahouDamage(target, source, amount);
        return ArkDamage.applyDamage(target, source, damage, true);
    }

    /** Apply protection enchantments after Mahou's armor/resistance floor. */
    private static float mahouDamage(LivingEntity target, DamageSource source, float amount) {
        float reduced = CombatRules.getDamageAfterAbsorb(amount, target.getArmorValue(),
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) reduced *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * .2f);
        reduced = Math.max(amount * .35f, reduced);
        int protection = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);
        return protection > 0 ? CombatRules.getDamageAfterMagicAbsorb(reduced, protection) : reduced;
    }

    /** Fourth Calamity retains source-specific vanilla mitigation and no damage floor. */
    private static float mitigated(LivingEntity target, DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR))
            amount = CombatRules.getDamageAfterAbsorb(amount, target.getArmorValue(),
                    (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) return amount;
        var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE))
            amount = Math.max(amount * (25 - (resistance.getAmplifier() + 1) * 5) / 25f, 0);
        if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return amount;
        int protection = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);
        return protection > 0 ? CombatRules.getDamageAfterMagicAbsorb(amount, protection) : amount;
    }
}
