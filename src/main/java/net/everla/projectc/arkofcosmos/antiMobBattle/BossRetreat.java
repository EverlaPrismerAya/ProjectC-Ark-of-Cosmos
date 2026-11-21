package net.everla.projectc.arkofcosmos.antiMobBattle;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** A living, non-combat exit, separate from normal defeat and command removal. */
public final class BossRetreat {
    public static final int DURATION = 60;
    private static final String AGE = "ArkAntiMobBattleRetreat";
    private BossRetreat() {}

    public static boolean active(LivingEntity boss) { return boss.getPersistentData().contains(AGE); }

    public static void begin(Mob boss) {
        if (boss.level().isClientSide || active(boss) || boss.isRemoved()) return;
        boss.getPersistentData().putInt(AGE, 0);
        boss.setTarget(null);
        boss.getNavigation().stop();
        boss.setNoAi(true);
        boss.setInvulnerable(true);
        boss.setNoGravity(true);
        boss.noPhysics = true;
        boss.setDeltaMovement(Vec3.ZERO);
        ArkOfCosmos.sound(boss, "fourth_calamity_death", 1);
    }

    /** Called instead of the Boss's combat tick, so target acquisition cannot resume. */
    public static boolean tick(Mob boss, Runnable finish) {
        if (boss.level().isClientSide || !active(boss)) return false;
        int age = boss.getPersistentData().getInt(AGE) + 1;
        boss.getPersistentData().putInt(AGE, age);
        boss.tickCount++;
        boss.xo = boss.xOld = boss.getX();
        boss.yo = boss.yOld = boss.getY();
        boss.zo = boss.zOld = boss.getZ();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setPos(boss.getX(), boss.getY() + .015 + .0005 * age * age, boss.getZ());
        boss.hasImpulse = true;
        if (age >= DURATION) finish.run();
        return true;
    }
}
