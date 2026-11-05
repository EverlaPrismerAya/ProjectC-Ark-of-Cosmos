package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** An unremovable, world-tick healing ceiling; subsequent damage can only lower it. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class BossHealingLock {
    private static final String UNTIL="MahouHealingUntil", CAP="MahouHealingCap";
    private BossHealingLock(){}
    public static boolean active(LivingEntity target){
        return target.getEntityData().hasItem(LivingEntity.DATA_HEALTH_ID)
                &&target.getPersistentData().contains(CAP)
                &&!target.level().isClientSide
                &&target.level().getGameTime()<target.getPersistentData().getLong(UNTIL)
                &&!ArkDamage.protectedPlayer(target);
    }
    public static float health(Object target){
        return target instanceof LivingEntity living&&active(living)?living.getPersistentData().getFloat(CAP):-1;
    }
    public static void apply(LivingEntity target,LivingEntity owner){
        apply(target, owner, 40);
    }
    public static void apply(LivingEntity target,LivingEntity owner,int duration){
        apply(target, duration);
        ElementsCombat.applyResonance(target,owner,20);
    }
    public static void apply(LivingEntity target,int duration){
        float health=target.getHealth();
        target.getPersistentData().putFloat(CAP,health);
        target.getPersistentData().putLong(UNTIL,Math.max(target.getPersistentData().getLong(UNTIL),target.level().getGameTime()+duration));
    }
    public static boolean blockWrite(Object target,float value){
        if(!(target instanceof LivingEntity living)||!active(living))return false;
        float cap=health(living);
        return Float.isNaN(value)||value>cap;
    }
    /** Observe a completed getHealth call, never an arbitrary float filter or health offset read. */
    public static float filterHealth(float value,Object target){
        if(!(target instanceof LivingEntity living)||!active(living))return value;
        float cap=health(living);
        // Only the active store may lower the ceiling; a stale vanilla value is not another hit.
        if(ArkDamage.authoritative(living))return cap;
        if(Float.isNaN(value)||value>cap)return cap;
        value=Math.max(0,value);
        if(value<cap){
            living.getPersistentData().putFloat(CAP,value);
            synced(living,LivingEntity.DATA_HEALTH_ID);
            ArkDamage.syncHealingLock(living);
        }
        return value;
    }
    public static Object dataWrite(Object target,EntityDataAccessor<?> key,Object value){
        if(!(target instanceof LivingEntity living)||!ArkDamage.activeHealthData(living,key)
                ||!(value instanceof Float number)||!active(living))return value;
        return blockWrite(living,number)?health(living):value;
    }
    public static void synced(LivingEntity target,EntityDataAccessor<?> key){
        if(!ArkDamage.activeHealthData(target,key)||!active(target)||!target.getEntityData().hasItem(key))return;
        @SuppressWarnings("unchecked") var healthKey=(EntityDataAccessor<Float>)key;
        float value=target.getEntityData().get(healthKey);
        if(blockWrite(target,value))target.getEntityData().set(healthKey,health(target));
        else target.getPersistentData().putFloat(CAP,Math.max(0,value));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void heal(LivingHealEvent event){if(active(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event){
        var target=event.getEntity();if(!active(target))return;
        // Repair silent DataItem writes before expiry, so blocked healing cannot become deferred healing.
        synced(target,LivingEntity.DATA_HEALTH_ID);
        ArkDamage.syncHealingLock(target);
    }
}
