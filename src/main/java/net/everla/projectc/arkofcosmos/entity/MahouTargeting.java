package net.everla.projectc.arkofcosmos.entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
/** Shared target filters also retire legacy weapon-owned attacks safely. */
final class MahouTargeting {
    private MahouTargeting() {}
    static boolean valid(MahouTsukaiEntity boss, LivingEntity target) {
        return target != null && target != boss && target.isAlive() && !target.isRemoved()
                && (!(target instanceof FourthCalamityEntity fourth) || !fourth.defeated())
                && target.level() == boss.level() && !boss.isAlliedTo(target) && boss.distanceToSqr(target) <= 128 * 128
                && (!(target instanceof Player p) || !p.isCreative() && !p.isSpectator());
    }
    static boolean holdAltitude(LivingEntity target){
        if(target==null||target.onGround())return false;
        Vec3 feet=target.position();
        BlockHitResult hit=target.level().clip(new ClipContext(feet,feet.add(0,-30.001,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,target));
        return hit.getType()==HitResult.Type.MISS||feet.y-hit.getLocation().y>=30-1e-6;
    }
}
