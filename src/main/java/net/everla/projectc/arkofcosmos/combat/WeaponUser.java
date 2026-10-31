package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Owner-dependent input/target policy, shared by the actual item and boss weapon entities. */
public final class WeaponUser {
    private WeaponUser() {}
    /** Follow vanilla ownership contracts, including modded servants and their projectiles. */
    public static boolean playerOwnedDamage(net.minecraft.world.damagesource.DamageSource source) {
        return playerOwned(source.getEntity()) || playerOwned(source.getDirectEntity());
    }
    private static boolean playerOwned(net.minecraft.world.entity.Entity entity) {
        var seen=new java.util.HashSet<net.minecraft.world.entity.Entity>();
        while(entity!=null && seen.add(entity)) {
            if(entity instanceof Player)return true;
            if(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile)entity=projectile.getOwner();
            else if(entity instanceof net.minecraft.world.entity.OwnableEntity owned)entity=owned.getOwner();
            else return false;
        }
        return false;
    }
    public static LivingEntity resolve(Level level, UUID uuid) {
        if(uuid==null)return null;
        if(level instanceof ServerLevel server && server.getEntity(uuid) instanceof LivingEntity living)return living;
        return level.getPlayerByUUID(uuid);
    }
    public static boolean active(LivingEntity owner) {
        return owner!=null && owner.isAlive() && !owner.isSpectator()
                && (!(owner instanceof MahouTsukaiEntity boss)||!boss.phaseRecovering()&&!boss.isNoAi());
    }
    public static Vec3 aim(LivingEntity owner) {
        if(owner instanceof Player player)return ArkCombat.aim(player);
        if(owner instanceof MahouTsukaiEntity boss && canHit(boss,boss.getTarget()))return boss.getTarget().getBoundingBox().getCenter();
        return owner.getBoundingBox().getCenter().add(owner.getLookAngle().scale(20));
    }
    public static boolean canHit(LivingEntity owner,LivingEntity target) {
        if(owner instanceof Player player)return ArkCombat.canHit(player,target);
        return target!=null && target!=owner && target.isAlive() && !target.isRemoved() && !owner.isAlliedTo(target)
                && (!(target instanceof Player player)||!player.isCreative()&&!player.isSpectator());
    }
    public static boolean enemy(LivingEntity owner,LivingEntity target) {
        return owner instanceof Player player?ArkCombat.enemy(player,target):canHit(owner,target);
    }
    public static LivingEntity nearest(LivingEntity owner,Vec3 from,double radius) {
        if(owner instanceof Player player)return ArkCombat.nearest(player,from,radius);
        if(owner instanceof MahouTsukaiEntity boss && canHit(boss,boss.getTarget())
                && from.distanceToSqr(boss.getTarget().getBoundingBox().getCenter())<radius*radius)return boss.getTarget();
        return null;
    }
    public static net.minecraft.world.damagesource.DamageSource source(net.minecraft.world.entity.Entity attack,LivingEntity owner) {
        var base=owner instanceof Player player?owner.damageSources().playerAttack(player):owner.damageSources().mobAttack(owner);
        return new net.minecraft.world.damagesource.DamageSource(base.typeHolder(),attack,owner);
    }
}
