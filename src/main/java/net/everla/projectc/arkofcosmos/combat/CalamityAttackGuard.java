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
    private static final StackWalker WALKER=StackWalker.getInstance();
    private CalamityAttackGuard() {}
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
