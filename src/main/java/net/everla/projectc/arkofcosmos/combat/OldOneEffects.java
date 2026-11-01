package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class OldOneEffects {
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,ArkOfCosmos.MOD_ID);
    public enum Status {
        GRAVITY(false,0),THORNS(false,0),WARMTH(false,0),WET(false,0),WRATH(false,0),RAGE(false,0),INFERNO(false,0),TITAN(false,0),WARD(false,8),
        BRIMSTONE(true,0),GLACIAL(true,-10),PLAGUE(true,-4),HOLY_FIRE(true,0),ASTRAL(true,-6),DEPTH(true,0),TEMPORAL(true,0),FROSTBURN(true,0),CURSED(true,0),ICHOR(true,-20),DAYBREAK(true,0),CELLED(true,0);
        private final RegistryObject<MobEffect> effect;
        Status(boolean harmful,int armor){String name="old_one_"+name().toLowerCase(Locale.ROOT);effect=EFFECTS.register(name,()->{
            MobEffect e=new MobEffect(harmful?MobEffectCategory.HARMFUL:MobEffectCategory.BENEFICIAL,harmful?0x9454bf:0x68bccc){};
            if(armor!=0)e.addAttributeModifier(Attributes.ARMOR,UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),armor,AttributeModifier.Operation.ADDITION);
            return e;
        });}
        public MobEffect effect(){return effect.get();}
    }
    public static void register(IEventBus bus){Status.values();EFFECTS.register(bus);}
    public static boolean has(LivingEntity e,Status s){return e.hasEffect(s.effect());}
    public static void apply(LivingEntity e,Status s,int ticks){e.addEffect(new MobEffectInstance(s.effect(),ticks,0,false,false,true));}
    private static void vanilla(LivingEntity e,MobEffect effect){e.addEffect(new MobEffectInstance(effect,200,0,false,false,true));}
    public static void grant(Player p,OldOneEnvironment.Zone z){
        if(OldOneEnvironment.moons(p)){vanilla(p,MobEffects.DAMAGE_BOOST);vanilla(p,MobEffects.REGENERATION);vanilla(p,MobEffects.SATURATION);}
        switch(z){
            case ASTRAL->apply(p,Status.GRAVITY,200);
            case JUNGLE->apply(p,Status.THORNS,200);
            case SNOW->apply(p,Status.WARMTH,200);
            case BEACH->apply(p,Status.WET,200);
            case CORRUPT->apply(p,Status.WRATH,200);
            case CRIMSON->apply(p,Status.RAGE,200);
            case DUNGEON->vanilla(p,MobEffects.NIGHT_VISION);
            case DESERT,VORTEX->vanilla(p,MobEffects.DAMAGE_RESISTANCE);
            case MUSHROOM->vanilla(p,MobEffects.DIG_SPEED);
            case HELL->apply(p,Status.INFERNO,200);
            case HOLY->p.heal(2);
            case NEBULA,STARDUST->vanilla(p,MobEffects.DAMAGE_BOOST);
            case SOLAR->apply(p,Status.TITAN,200);
            case DEFAULT->apply(p,Status.WARD,200);
        }
    }
    public static void orb(LivingEntity t,OldOneEnvironment.Zone z){
        Status s=switch(z){case ASTRAL->Status.ASTRAL;case JUNGLE->Status.PLAGUE;case SNOW->Status.GLACIAL;case BEACH->Status.DEPTH;
            case DESERT,HOLY->Status.HOLY_FIRE;case MUSHROOM->Status.TEMPORAL;case HELL->Status.BRIMSTONE;case DUNGEON->Status.FROSTBURN;default->null;};
        if(s!=null)apply(t,s,400);else if(z==OldOneEnvironment.Zone.DEFAULT)t.addEffect(new MobEffectInstance(ArkOfCosmos.ARMOR_CRUNCH.get(),400,0,false,false,true));
    }
    public static boolean regenBlocked(LivingEntity e){return has(e,Status.BRIMSTONE)||has(e,Status.PLAGUE)||has(e,Status.HOLY_FIRE)||has(e,Status.ASTRAL)||has(e,Status.FROSTBURN)||has(e,Status.DAYBREAK)||has(e,Status.CELLED);}
    private static final TagKey<EntityType<?>> FREEZE_IMMUNE=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"old_one_freeze_immune"));
    private static final TagKey<EntityType<?>> SLOW_IMMUNE=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"old_one_slow_immune"));
    public static boolean movementImmune(LivingEntity e,boolean freeze){return !e.canChangeDimensions()||e.getType().is(freeze?FREEZE_IMMUNE:SLOW_IMMUNE)
            ||e instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity||e instanceof net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity;}
    private static final Map<LivingEntity,Vec3> FROZEN=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REFLECTING=ThreadLocal.withInitial(()->false);
    @SubscribeEvent public static void effect(MobEffectEvent.Applicable e){if(e.getEffectInstance().getEffect()==MobEffects.LEVITATION&&has(e.getEntity(),Status.GRAVITY))e.setResult(Event.Result.DENY);}
    @SubscribeEvent public static void falling(LivingFallEvent e){if(has(e.getEntity(),Status.GRAVITY)&&e.getEntity().hasEffect(MobEffects.SLOW_FALLING))e.setCanceled(true);}
    @SubscribeEvent public static void damage(LivingAttackEvent e){if(has(e.getEntity(),Status.WARMTH)&&e.getSource().is(net.minecraft.world.damagesource.DamageTypes.FREEZE))e.setCanceled(true);}
    @SubscribeEvent public static void knockback(LivingKnockBackEvent e){var attacker=e.getEntity().getLastHurtByMob();if(attacker!=null&&has(attacker,Status.TITAN))e.setStrength(e.getStrength()*1.5f);}
    @SubscribeEvent public static void critical(CriticalHitEvent e){if(e.isVanillaCritical()&&has(e.getEntity(),Status.RAGE))e.setDamageModifier(e.getDamageModifier()*1.1f);}
    @SubscribeEvent(priority=EventPriority.LOW) public static void hurt(LivingHurtEvent e){
        if(e.getSource().getEntity() instanceof LivingEntity attacker&&has(attacker,Status.WRATH))e.setAmount(e.getAmount()*1.1f);
        if(e.getSource().getDirectEntity()!=e.getSource().getEntity()||!(e.getSource().getEntity() instanceof LivingEntity attacker))return;
        if(e.getEntity() instanceof Player&&has(attacker,Status.TEMPORAL))e.setAmount(e.getAmount()*.5f);
    }
    @SubscribeEvent public static void reflect(LivingDamageEvent e){
        if(REFLECTING.get()||e.getAmount()<=0||e.getSource().getDirectEntity()!=e.getSource().getEntity()||!(e.getSource().getEntity() instanceof LivingEntity attacker)||attacker instanceof Player)return;
        LivingEntity victim=e.getEntity();double thorns=has(victim,Status.THORNS)?1d/3:0;if(has(victim,Status.WARD)&&thorns<1)thorns+=.2;
        if(thorns>0){REFLECTING.set(true);try{ArkDamage.hurtUnmitigated(attacker,attacker.damageSources().thorns(victim),(float)(e.getAmount()*thorns));}finally{REFLECTING.set(false);}}
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        LivingEntity e=event.getEntity();if(e.level().isClientSide||!e.isAlive())return;
        if(has(e,Status.GRAVITY))e.removeEffect(MobEffects.LEVITATION);
        if(has(e,Status.TEMPORAL)&&!movementImmune(e,false))for(int i=0;i<3;i++)e.setDeltaMovement(e.getDeltaMovement().scale(.5));
        if(has(e,Status.GLACIAL)&&!movementImmune(e,true)){FROZEN.putIfAbsent(e,e.position());e.setDeltaMovement(Vec3.ZERO);}else FROZEN.remove(e);
        if(e.tickCount%20!=0)return;
        if(has(e,Status.WARD))e.heal(.6f);
        // Old One life-loss statuses share Ark authority and all tag fallbacks.
        float loss=0;
        if(has(e,Status.BRIMSTONE))loss+=4;if(has(e,Status.PLAGUE))loss+=10;if(has(e,Status.HOLY_FIRE))loss+=5;
        if(has(e,Status.ASTRAL))loss+=7.5f;if(has(e,Status.FROSTBURN))loss+=1.6f;
        if(has(e,Status.DAYBREAK))loss+=20;
        // The weapon overrides Stardust Cell penetrate to 1. Its death leaves no attached cells,
        // so NPC.UpdateLifeRegen's celled multiplier is zero; it still cancels positive regen.
        if(loss>0)dot(e,e.damageSources().magic(),loss);
        if(has(e,Status.CURSED))dot(e,e.damageSources().magic(),2);
        if(has(e,Status.DEPTH))dot(e,e.damageSources().drown(),5);
        if(has(e,Status.INFERNO))for(var target:e.level().getEntitiesOfClass(LivingEntity.class,e.getBoundingBox().inflate(12.5),t->t.distanceToSqr(e)<=12.5*12.5&&e instanceof Player p&&ArkCombat.enemy(p,t))){target.setSecondsOnFire(2);dot(target,e.damageSources().inFire(),1);}
    }
    private static void dot(LivingEntity e,net.minecraft.world.damagesource.DamageSource source,float amount){ArkDamage.hurtUnmitigated(e,source,amount);}
    @SubscribeEvent public static void end(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)FROZEN.entrySet().removeIf(entry->{LivingEntity t=entry.getKey();if(t.isRemoved()||!t.isAlive()||!has(t,Status.GLACIAL))return true;t.setPos(entry.getValue());t.setDeltaMovement(Vec3.ZERO);t.hurtMarked=true;return false;});}
}
