package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.damagesource.DamageSource;

/** Boss-owned vitality exposed to the existing health and direct-damage launch hooks. */
public interface ProtectedBoss {
    float combatVitality();
    boolean redirectHealthWrite(float health);
    boolean blocksHealthWrite(float health);
    void ordinaryDamage(DamageSource source, float amount);
}
