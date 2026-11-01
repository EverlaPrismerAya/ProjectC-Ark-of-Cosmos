package net.everla.projectc.arkofcosmos.combat.vanilla;

import java.util.*;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server-owned piercing and kinetic resolution, shared by players and living combatants. */
public final class SpearCombat {
    private static final Map<LivingEntity,State> STATES=new WeakHashMap<>();
    private static final class State {
        Vec3 position,motion=Vec3.ZERO;int tick;long lastJab=Long.MIN_VALUE;
        final Map<Integer,Integer> contacts=new HashMap<>();
    }
    public static void track(LivingEntity e) {
        State s=STATES.computeIfAbsent(e,k->new State());
        if(s.position!=null) s.motion=e.position().subtract(s.position);
        s.position=e.position();s.tick=e.tickCount;
        if(!e.isUsingItem()||!(e.getUseItem().getItem() instanceof SpearItem))s.contacts.clear();
    }
    public static Vec3 motion(Entity e) {
        if(!(e instanceof Player)&&e.isPassenger())e=e.getRootVehicle();
        if(e instanceof LivingEntity living) {
            State s=STATES.get(living);if(s!=null)return s.motion;
        }
        return new Vec3(e.getX()-e.xo,e.getY()-e.yo,e.getZ()-e.zo);
    }
    public static void start(LivingEntity e) {
        STATES.computeIfAbsent(e,k->new State()).contacts.clear();
        sound(e,(SpearItem)e.getUseItem().getItem(),"use");
    }
    public static void stop(LivingEntity e) { State s=STATES.get(e);if(s!=null)s.contacts.clear(); }
    public static boolean condition(int used,int until,double speed,double threshold,float factor) { return used<=until&&speed>=threshold*factor; }
    public static float kineticDamage(float base,double attacker,double target,float multiplier) { return base+Mth.floor(Math.max(0,attacker-target)*multiplier); }
    public static void kinetic(LivingEntity attacker,ItemStack stack,int used) {
        if(!(stack.getItem() instanceof SpearItem spear)||!attacker.isAlive())return;
        SpearMaterial m=spear.material;int active=used-m.delayTicks();if(active<0)return;
        State state=STATES.computeIfAbsent(attacker,k->new State());
        Vec3 look=attacker.getLookAngle();double speed=look.dot(motion(attacker).scale(20));
        float factor=attacker instanceof Player?1:.2f;boolean affected=false;
        for(Entity target:targets(attacker,true)) {
            Integer last=state.contacts.get(target.getId());if(last!=null&&attacker.tickCount-last<10)continue;
            state.contacts.put(target.getId(),attacker.tickCount);
            double relative=Math.max(0,speed-look.dot(motion(target).scale(20)));
            boolean dismount=condition(active,m.dismountTicks(),speed,m.dismountSpeed(),factor);
            boolean knockback=condition(active,m.knockbackTicks(),speed,m.knockbackSpeed(),factor);
            boolean damage=condition(active,m.damageTicks(),relative,m.relativeSpeed(),factor);
            if(dismount||knockback||damage)affected|=stab(attacker,target,stack,kineticDamage((float)attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),speed,speed-relative,m.multiplier()),damage,knockback,dismount);
        }
        if(affected){sound(attacker,spear,"hit");net.everla.projectc.arkofcosmos.network.ArkNetwork.spearFeedback(attacker);}
    }
    public static boolean jab(LivingEntity attacker) {
        if(attacker.level().isClientSide||!attacker.isAlive()||attacker.isSpectator()||!(attacker.getMainHandItem().getItem() instanceof SpearItem spear))return false;
        State s=STATES.computeIfAbsent(attacker,k->new State());long now=attacker.level().getGameTime();
        if(!(attacker instanceof Player)&&s.lastJab!=Long.MIN_VALUE&&now-s.lastJab<(int)(spear.material.duration()*20))return false;
        if(attacker instanceof Player player&&player.getAttackStrengthScale(0)<1)return false;
        s.lastJab=now;ItemStack stack=attacker.getMainHandItem();
        float damage=(float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for(Entity target:targets(attacker,false))stab(attacker,target,stack,damage,true,true,false);
        lunge(attacker,stack);attacker.swing(InteractionHand.MAIN_HAND,true);
        if(attacker instanceof Player player)player.resetAttackStrengthTicker();
        sound(attacker,spear,"attack");return true;
    }
    private static void sound(LivingEntity e,SpearItem spear,String action) { VanillaCombat.sound(e,"item."+(spear.material.name().equals("wooden")?"spear_wood":"spear")+"."+action,1,1); }
    private static void lunge(LivingEntity e,ItemStack stack) {
        int level=VanillaCombat.enchant(stack,VanillaCombat.LUNGE);
        if(level==0||e.isPassenger()||e.isFallFlying()||e.isInWater()||e instanceof Player p&&!p.isCreative()&&p.getFoodData().getFoodLevel()<7)return;
        // Local forward direction is normalized before removing its vertical component.
        Vec3 look=e.getLookAngle();e.push(look.x*.458f*level,0,look.z*.458f*level);VanillaCombat.syncMotion(e);
        stack.hurtAndBreak(1,e,a->a.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if(e instanceof Player p)p.causeFoodExhaustion(4*level);
        VanillaCombat.sound(e,"item.spear.lunge_"+Math.min(3,level),1,1);
    }
    public static boolean stab(LivingEntity attacker,Entity target,ItemStack stack,float base,boolean damage,boolean knockback,boolean dismount) {
        if(!canHit(attacker,target))return false;
        var source=VanillaCombat.source(attacker,"spear");
        float magic=target instanceof LivingEntity living?EnchantmentHelper.getDamageBonus(stack,living.getMobType()):0;
        float oldHealth=target instanceof LivingEntity living?living.getHealth():0;
        boolean hit=damage&&target.hurt(source,base+magic);
        Vec3 oldMovement=target.getDeltaMovement();
        if(knockback) {
            double power=.4f+attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK)+EnchantmentHelper.getKnockbackBonus(attacker);double yaw=attacker.getYRot()*Math.PI/180;
            if(target instanceof LivingEntity living)living.knockback(power,Math.sin(yaw),-Math.cos(yaw));
            else target.push(-Math.sin(yaw)*power,.1,Math.cos(yaw)*power);
            // Player movement is client-owned; forcing the server velocity back resets sprint jumps.
            if(!attacker.isFallFlying())attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(.6,1,.6));
            VanillaCombat.syncMotion(target);
            if(target instanceof net.minecraft.server.level.ServerPlayer){target.hurtMarked=false;target.setDeltaMovement(oldMovement);}
        }
        boolean dismounted=dismount&&target.isPassenger();if(dismounted)target.stopRiding();
        LivingEntity livingTarget=target instanceof LivingEntity living?living:target instanceof net.minecraft.world.entity.boss.EnderDragonPart part?part.parentMob:null;
        if((hit||knockback||dismounted)&&livingTarget!=null) {
            if(attacker instanceof Player player)stack.hurtEnemy(livingTarget,player);else stack.getItem().hurtEnemy(stack,livingTarget,attacker);
        }
        if(hit) {
            if(target instanceof LivingEntity living) {
                EnchantmentHelper.doPostHurtEffects(living,attacker);
                int fire=EnchantmentHelper.getFireAspect(attacker);if(fire>0)living.setSecondsOnFire(fire*4);
            }
            EnchantmentHelper.doPostDamageEffects(attacker,target);
            if(attacker instanceof Player player&&livingTarget!=null)player.awardStat(net.minecraft.stats.Stats.DAMAGE_DEALT,Math.round((oldHealth-livingTarget.getHealth())*10));
        }
        if(hit||knockback||dismounted){attacker.setLastHurtMob(target);if(attacker instanceof Player p)p.causeFoodExhaustion(.1f);return true;}
        return false;
    }
    private static boolean canHit(LivingEntity attacker,Entity e) {
        return e!=attacker&&e.isAlive()&&!e.isInvulnerable()&&(e.isPickable()||e instanceof net.minecraft.world.entity.Interaction)&&!e.isSpectator()&&!attacker.isPassengerOfSameVehicle(e)
            &&(!(attacker instanceof Player a)||!(e instanceof Player b)||a.canHarmPlayer(b));
    }
    private static boolean intersects(LivingEntity attacker,Entity target,Vec3 from,Vec3 to) {
        AABB box=target.getBoundingBox();
        if(box.contains(from)||box.clip(from,to).isPresent())return true;
        Optional<Vec3> margin=box.inflate(.125).clip(from,to);
        if(margin.isEmpty())return false;
        Vec3 center=box.getCenter();
        BlockHitResult wall=attacker.level().clip(new ClipContext(margin.get(),center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,attacker));
        return box.clip(margin.get(),wall.getType()==HitResult.Type.MISS?center:wall.getLocation()).isPresent();
    }
    public static List<Entity> targets(LivingEntity attacker,boolean sweep) {
        double factor=attacker instanceof Player?1:.5;double min=2*factor,max=(attacker instanceof Player p&&p.isCreative()?6.5:4.5)*factor;
        Vec3 look=attacker.getLookAngle(),eye=attacker.getEyePosition(),move=motion(attacker);
        Vec3 from=eye.add(look.scale(min)),to=eye.add(look.scale(max+Math.max(0,move.dot(look))));
        BlockHitResult wall=attacker.level().clip(new ClipContext(eye,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,attacker));
        if(wall.getType()!=HitResult.Type.MISS) {
            to=wall.getLocation();if(eye.distanceToSqr(to)<eye.distanceToSqr(from))return List.of();
        }
        final Vec3 end=to;AABB search=new AABB(from,end).inflate(1);if(sweep)search=search.expandTowards(move.scale(-1));
        List<Entity> hits=new ArrayList<>();
        for(Entity target:attacker.level().getEntities(attacker,search,e->canHit(attacker,e))) {
            boolean intersects=intersects(attacker,target,from,end);
            // Substeps cover a moving attack volume; each target is still resolved once per contact window.
            if(sweep&&!intersects) {
                int steps=Math.max(1,(int)Math.ceil(move.length()/.25));
                for(int i=1;i<=steps&&!intersects;i++) {
                    Vec3 offset=move.scale(-(double)i/steps);Vec3 origin=eye.add(offset),a=from.add(offset),b=end.add(offset);
                    BlockHitResult block=attacker.level().clip(new ClipContext(origin,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,attacker));
                    if(block.getType()!=HitResult.Type.MISS)b=block.getLocation();
                    if(origin.distanceToSqr(b)>=origin.distanceToSqr(a))intersects=intersects(attacker,target,a,b);
                }
            }
            if(intersects)hits.add(target);
        }
        hits.sort(Comparator.comparingDouble(e->e.distanceToSqr(attacker)));return hits;
    }
}

