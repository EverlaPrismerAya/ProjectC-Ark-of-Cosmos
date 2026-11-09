package net.everla.projectc.arkofcosmos.combat;
import net.minecraft.world.damagesource.DamageSource;
/** Vanilla damage entry points below Apostle's optional-mod defenses. */
public interface SatsukiDamageAccess {
    boolean ark$satsukiHurt(DamageSource source,float amount);
    void ark$satsukiActuallyHurt(DamageSource source,float amount);
}
