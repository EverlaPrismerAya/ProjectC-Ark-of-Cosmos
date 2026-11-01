package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.OldOneEntity;
import net.everla.projectc.arkofcosmos.item.OldOneItem;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class OldOneCombat {
    private static final Map<UUID,State> STATES = new HashMap<>();
    private static final class State { boolean left; int cooldown,idle; }
    private OldOneCombat() {}
    public static boolean holding(Player p) { return p.getMainHandItem().getItem() instanceof OldOneItem; }
    public static void input(ServerPlayer p, boolean left) {
        if (!holding(p) || !p.isAlive() || p.isSpectator()) { STATES.remove(p.getUUID()); return; }
        State s=STATES.computeIfAbsent(p.getUUID(), k->new State());
        boolean press=left&&!s.left; s.left=left; s.idle=0;
        if (press&&s.cooldown==0) attack(p,s);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        State s=STATES.get(p.getUUID()); if(s==null)return;
        s.cooldown=Math.max(0,s.cooldown-3);
        if(!holding(p)||!p.isAlive()||p.isSpectator()||++s.idle>40){s.left=false;return;}
        if(s.left&&s.cooldown==0)attack(p,s);
    }
    @SubscribeEvent public static void loot(net.minecraftforge.event.entity.living.LootingLevelEvent e){if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof OldOneEntity a)e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){STATES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void vanillaAttack(AttackEntityEvent e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    private static void attack(ServerPlayer p,State s){
        AttackFrame f=AttackFrame.capture(p); Vec3 c=p.getBoundingBox().getCenter(),target=aim(p);
        // Terraria screen X/Y are embedded in the captured attack plane. Y increases toward f.right().
        double tx=target.subtract(c).dot(f.forward()),ty=target.subtract(c).dot(f.right());
        OldOneEntity.create(p,OldOneEntity.Kind.SWORD,c,Vec3.ZERO,f);
        int beam=p.getRandom().nextInt(4);
        Vec3 straight=target.subtract(c).normalize();if(straight.lengthSqr()<1e-8)straight=f.forward();
        // Explicit source variant selection.
        OldOneEntity.Kind beamKind = switch (beam) {
            case 0 -> OldOneEntity.Kind.EON;
            case 1 -> OldOneEntity.Kind.EON_V2;
            case 2 -> OldOneEntity.Kind.EON_V3;
            default -> OldOneEntity.Kind.EON_V4;
        };
        OldOneEntity.create(p, beamKind,c,straight.scale(28d/16),f);
        double speed=22+p.getRandom().nextInt(8);
        for(int i=0;i<4;i++){
            // Source: (mouseX + centerX + width/2 - direction)/2, mountedCenterY - 100*i.
            double x=(tx+p.getBbWidth()/2-1d/16)/2, y=-100d*i/16;
            Vec3 origin=c.add(f.forward().scale(x)).add(f.right().scale(y));
            Vec3 delta=f.forward().scale(tx-x).add(f.right().scale(ty-y));
            Vec3 v=delta.lengthSqr()<1e-12?f.forward().scale(speed/16):delta.normalize().scale(speed/16);
            v=v.add(f.forward().scale((p.getRandom().nextInt(721)-360)*.02/16))
                    .add(f.right().scale((p.getRandom().nextInt(721)-360)*.02/16));
            OldOneEntity.orb(p,origin,v,f,p.getRandom().nextInt(3));
        }
        ArkOfCosmos.sound(p,"galaxia_item60",1);s.cooldown=15;
    }

    /** Crosshair ray, never the modern Cosmos cone auto-aim. Blocks occlude entity hits. */
    public static Vec3 aim(Player p){
        Vec3 start=p.getEyePosition(),end=start.add(p.getLookAngle().scale(20));
        var block=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        Vec3 best=block.getType()==HitResult.Type.MISS?end:block.getLocation();double distance=start.distanceToSqr(best);
        for(var t:p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(start,best).inflate(1),e->OldOneEntity.canHit(p,e))){
            var hit=t.getBoundingBox().clip(start,best);if(hit.isPresent()&&start.distanceToSqr(hit.get())<distance){best=hit.get();distance=start.distanceToSqr(best);}
        }return best;
    }
}
