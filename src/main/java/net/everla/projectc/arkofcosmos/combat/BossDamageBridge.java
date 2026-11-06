package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;

/** Structural launch hooks: no optional-mod classes or identifiers. */
public final class BossDamageBridge {
    private BossDamageBridge() {}

    public static boolean damage(Object target, Object source, float amount) {
        if (!(target instanceof LivingEntity entity) || !(target instanceof ProtectedBoss boss) || boss.combatVitality()<0 || entity.level().isClientSide
                || !(source instanceof DamageSource damage)) return false;
        boss.ordinaryDamage(damage,amount);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean offset(Object target, Object key, float value) {
        if (!(target instanceof LivingEntity entity) || !(target instanceof ProtectedBoss boss) || boss.combatVitality()<0 || entity.level().isClientSide
                || !(key instanceof EntityDataAccessor<?>)) return false;
        var accessor=(EntityDataAccessor<Float>)key;
        if(!entity.getEntityData().hasItem(accessor))return false;
        // Consume the persistent ceiling, including rejected hits, so it cannot reappear on reads.
        entity.getEntityData().set(accessor,0f);
        if(value<0)boss.ordinaryDamage(entity.damageSources().generic(),-value);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean readOffset(Object target,Object key){
        if(!(target instanceof LivingEntity entity)||!(target instanceof ProtectedBoss boss)||boss.combatVitality()<0||entity.level().isClientSide
                ||!(key instanceof EntityDataAccessor<?> accessor)||!entity.getEntityData().hasItem(accessor))return false;
        return offset(target,key,entity.getEntityData().get((EntityDataAccessor<Float>)key));
    }
}
