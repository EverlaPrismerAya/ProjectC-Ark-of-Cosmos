package net.everla.projectc.arkofcosmos.combat.vanilla;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/** 26.1.2 impulse impact height and 40-tick grace, without granting global fall immunity. */
public final class ImpulseFall {
    private static final String HEIGHT="aocImpulseY",GRACE="aocImpulseGrace";
    public static void mark(LivingEntity e,boolean keepLower) {
        CompoundTag d=e.getPersistentData();double y=e.getY();
        if(keepLower&&d.contains(HEIGHT))y=Math.min(y,d.getDouble(HEIGHT));
        d.putDouble(HEIGHT,y);d.putInt(GRACE,40);
    }
    public static void endGrace(LivingEntity e) { e.getPersistentData().putInt(GRACE,0); }
    public static boolean hasProtection(LivingEntity e) { return e.getPersistentData().contains(HEIGHT); }
    public static void tick(LivingEntity e) {
        CompoundTag d=e.getPersistentData();int grace=d.getInt(GRACE);if(grace>0)d.putInt(GRACE,grace-1);
        if(d.contains(HEIGHT)&&(e.isInWater()||e.onClimbable()||e.isFallFlying()||e.onGround()&&grace==0))clear(e);
    }
    private static void clear(LivingEntity e) { e.getPersistentData().remove(HEIGHT);e.getPersistentData().remove(GRACE); }
    public static void fall(LivingFallEvent event) {
        LivingEntity e=event.getEntity();CompoundTag d=e.getPersistentData();if(!d.contains(HEIGHT))return;
        float height=(float)Math.min(event.getDistance(),d.getDouble(HEIGHT)-e.getY());
        event.setDistance(Math.max(0,height));if(height<=0||d.getInt(GRACE)==0||height>3)clear(e);
    }
}
