package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;

/** Structural launch hooks: no optional-mod classes or identifiers. */
public final class BossDamageBridge {
    private BossDamageBridge() {}

    public static boolean damage(Object target, Object source, float amount) {
        if (!(target instanceof MahouTsukaiEntity boss) || boss.combatVitality()<0 || boss.level().isClientSide
                || !(source instanceof DamageSource damage)) return false;
        boss.ordinaryDamage(damage,amount);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean offset(Object target, Object key, float value) {
        if (!(target instanceof MahouTsukaiEntity boss) || boss.combatVitality()<0 || boss.level().isClientSide
                || !(key instanceof EntityDataAccessor<?>)) return false;
        var accessor=(EntityDataAccessor<Float>)key;
        if(!boss.getEntityData().hasItem(accessor))return false;
        // Consume the persistent ceiling, including rejected hits, so it cannot reappear on reads.
        boss.getEntityData().set(accessor,0f);
        if(value<0)boss.ordinaryDamage(boss.damageSources().generic(),-value);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean readOffset(Object target,Object key){
        if(!(target instanceof MahouTsukaiEntity boss)||boss.combatVitality()<0||boss.level().isClientSide
                ||!(key instanceof EntityDataAccessor<?> accessor)||!boss.getEntityData().hasItem(accessor))return false;
        return offset(target,key,boss.getEntityData().get((EntityDataAccessor<Float>)key));
    }
}
