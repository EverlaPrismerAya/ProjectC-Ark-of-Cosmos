package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class CalamityAttackGuard {
    public static final String OWNER="ArkFourthOwner";
    public static final ThreadLocal<FourthCalamityEntity> PLACING=new ThreadLocal<>();
    public static final ThreadLocal<FourthCalamityEntity> DETONATING=new ThreadLocal<>();
    private static final StackWalker WALKER=StackWalker.getInstance();
    private CalamityAttackGuard() {}
    public static FourthCalamityEntity damageOwner(net.minecraft.world.damagesource.DamageSource source) {
        for(Entity entity:new Entity[]{source.getEntity(),source.getDirectEntity()}) {
            if(entity instanceof FourthCalamityEntity boss)return boss;
            if(entity instanceof Projectile projectile&&projectile.getOwner() instanceof FourthCalamityEntity boss)return boss;
            if(entity!=null&&entity.getPersistentData().hasUUID(OWNER)&&entity.level() instanceof ServerLevel level
                &&level.getEntity(entity.getPersistentData().getUUID(OWNER)) instanceof FourthCalamityEntity boss)return boss;
        }
        if(source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return DETONATING.get()!=null?DETONATING.get():PLACING.get();
        return null;
    }
    public static boolean hurt(Entity target,net.minecraft.world.damagesource.DamageSource source,float amount) {
        FourthCalamityEntity boss=damageOwner(source);
        return boss!=null&&target!=boss&&target instanceof net.minecraft.world.entity.LivingEntity living
            ?WeaponDamage.hurt(living,source,amount):target.hurt(source,amount);
    }
    public static final ThreadLocal<Boolean> FULL_KNOCKBACK=new ThreadLocal<>();
    public static void knockback(net.minecraft.world.entity.LivingEntity victim,double strength,double x,double z,Entity attacker) {
        Boolean previous=FULL_KNOCKBACK.get();
        if(attacker instanceof FourthCalamityEntity)FULL_KNOCKBACK.set(true);
        try { victim.knockback(strength,x,z);if(attacker instanceof FourthCalamityEntity)net.everla.projectc.arkofcosmos.combat.vanilla.VanillaCombat.syncMotion(victim); }
        finally { if(previous==null)FULL_KNOCKBACK.remove();else FULL_KNOCKBACK.set(previous); }
    }
    private static FourthCalamityEntity crystalOwner(net.minecraftforge.event.level.ExplosionEvent event) {
        Entity source=event.getExplosion().getDirectSourceEntity();
        if(source instanceof EndCrystal&&DETONATING.get()!=null)return DETONATING.get();
        if(source instanceof EndCrystal&&source.getPersistentData().hasUUID(OWNER)&&event.getLevel() instanceof ServerLevel level
            &&level.getEntity(source.getPersistentData().getUUID(OWNER)) instanceof FourthCalamityEntity boss)return boss;
        return null;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
    public static void explosionStart(net.minecraftforge.event.level.ExplosionEvent.Start event) {
        FourthCalamityEntity boss=crystalOwner(event);
        if(boss!=null&&event.isCanceled())boss.actions.crystalExplosion((EndCrystal)event.getExplosion().getDirectSourceEntity(),true);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void explosionDetonated(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        FourthCalamityEntity boss=crystalOwner(event);
        if(boss!=null) {
            boss.actions.crystalExplosion((EndCrystal)event.getExplosion().getDirectSourceEntity(),false);
            var target=boss.getTarget();
            if(target!=null&&event.getAffectedEntities().contains(target)
                &&net.minecraft.world.level.Explosion.getSeenPercent(event.getExplosion().getPosition(),target)>0)boss.combat.crystalBlast(target);
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
    public static void interaction(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        FourthCalamityEntity boss=PLACING.get();
        if(boss!=null&&(event.isCanceled()||event.getUseItem()==net.minecraftforge.eventbus.api.Event.Result.DENY))
            boss.actions.interactionDenied("PROTECTION_DENIED");
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
    public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        FourthCalamityEntity boss=PLACING.get();
        if(boss!=null&&event.isCanceled())boss.actions.interactionDenied("PROTECTION_DENIED");
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide)return;
        Entity entity=event.getEntity();
        FourthCalamityEntity boss=entity instanceof Projectile projectile&&projectile.getOwner() instanceof FourthCalamityEntity owner?owner:
            entity instanceof EndCrystal?PLACING.get():null;
        if(boss!=null)entity.getPersistentData().putUUID(OWNER,boss.getUUID());
    }
    public static boolean owned(Entity entity,FourthCalamityEntity boss) {
        return entity.getPersistentData().hasUUID(OWNER)&&entity.getPersistentData().getUUID(OWNER).equals(boss.getUUID());
    }
    public static boolean blockRemoval(Entity entity,Entity.RemovalReason reason) {
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.PlayerWindCharge charge&&charge.ending())return false;
        if(reason!=Entity.RemovalReason.DISCARDED||!(entity.level() instanceof ServerLevel level)||!entity.getPersistentData().hasUUID(OWNER)
            ||!(entity instanceof Projectile||entity instanceof EndCrystal))return false;
        Entity owner=level.getEntity(entity.getPersistentData().getUUID(OWNER));
        if(!(owner instanceof FourthCalamityEntity boss)||!boss.isAlive())return false;
        // Only a native projectile's own lifecycle may discard a marked attack. Mod cleaners have no such caller.
        return WALKER.walk(frames->frames.filter(frame->!frame.getClassName().equals(Entity.class.getName())
            &&!frame.getClassName().equals(CalamityAttackGuard.class.getName()))
            .findFirst().map(frame->!frame.getClassName().startsWith("net.minecraft.world.entity.projectile.")).orElse(true));
    }
}
