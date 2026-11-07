package net.everla.projectc.arkofcosmos.enchant;

import java.util.Map;
import java.util.WeakHashMap;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect;
import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Independent temporary timers; the potion is only a removable HUD indicator. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class WitchFlames {
    private WitchFlames() {}
    private static final class State { long flames,hex; }
    private static final Map<LivingEntity,State> STATES=new WeakHashMap<>();
    private static boolean showingFlames;
    public static boolean active(LivingEntity entity){return remaining(entity)>0;}
    public static int remaining(LivingEntity entity){var s=STATES.get(entity);return s==null?0:(int)Math.max(0,s.flames-entity.level().getGameTime());}
    public static boolean weakened(LivingEntity entity){var s=STATES.get(entity);return s!=null && (s.hex>entity.level().getGameTime() || entity instanceof Player && active(entity));}
    public static void apply(LivingEntity entity,int ticks){
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity || entity.level().isClientSide || !entity.isAlive() || ticks<=0 || protectedPlayer(entity))return;
        State s=STATES.computeIfAbsent(entity,k->new State());s.flames=Math.max(s.flames,entity.level().getGameTime()+ticks);
        armor(entity);WitchEnchanting.flames(entity,remaining(entity));
        showingFlames=true;
        try{entity.addEffect(new MobEffectInstance(CalamitasContent.VULNERABILITY_HEX.get(),remaining(entity),0,false,false,true));}
        finally{showingFlames=false;}
    }
    private static boolean protectedPlayer(LivingEntity e){return e instanceof Player p && (p.isCreative()||p.isSpectator());}
    @SubscribeEvent public static void potion(MobEffectEvent.Added event){
        var entity=event.getEntity();var effect=event.getEffectInstance();
        if(entity instanceof net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity || entity.level().isClientSide || showingFlames || !CalamitasContent.ENABLED || effect.getEffect()!=CalamitasContent.VULNERABILITY_HEX.get() || !entity.isAlive() || protectedPlayer(entity))return;
        State s=STATES.computeIfAbsent(entity,k->new State());
        // Commands and boss attacks both enter here; shorter refreshes cannot truncate a running timer.
        s.hex=effect.isInfiniteDuration()?Long.MAX_VALUE:Math.max(s.hex,entity.level().getGameTime()+effect.getDuration());armor(entity);
    }
    private static void armor(LivingEntity entity){
        var armor=entity.getAttribute(Attributes.ARMOR);if(armor==null)return;
        if(weakened(entity)){
            if(armor.getModifier(CalamitasHexEffect.ARMOR_MODIFIER)==null)armor.addTransientModifier(new AttributeModifier(CalamitasHexEffect.ARMOR_MODIFIER,"Witch hex",-.15,AttributeModifier.Operation.MULTIPLY_TOTAL));
        }else armor.removeModifier(CalamitasHexEffect.ARMOR_MODIFIER);
    }
    public static void clear(LivingEntity entity){
        if(STATES.remove(entity)!=null){armor(entity);WitchEnchanting.flames(entity,0);}
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var entity=event.getEntity();if(entity.level().isClientSide)return;
        // Migrate old enemy-only persisted timers without reviving them in a new life.
        entity.getPersistentData().remove("ArkWitchFlames");
        State s=STATES.get(entity);if(s==null)return;
        if(!entity.isAlive() || protectedPlayer(entity)){clear(entity);return;}
        long now=entity.level().getGameTime();
        if(s.flames>0 && s.flames<=now){s.flames=0;WitchEnchanting.flames(entity,0);}
        if(s.hex<=now)s.hex=0;
        if(s.flames==0 && s.hex==0){clear(entity);return;}
        armor(entity);
        if(entity.tickCount%20==0){
            float damage=active(entity)&&!(entity instanceof Player)?1332*.06f:active(entity)||!entity.hasEffect(MobEffects.FIRE_RESISTANCE)?3f:1.5f;
            if(damage>0)ArkDamage.hurtUnmitigated(entity,entity.damageSources().magic(),damage);
        }
    }
    @SubscribeEvent public static void track(PlayerEvent.StartTracking event){
        if(event.getTarget() instanceof LivingEntity entity && active(entity))WitchEnchanting.flamesTo(event.getEntity(),entity,remaining(entity));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event){if(!event.getEntity().level().isClientSide)clear(event.getEntity());}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event){if(!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity entity)clear(entity);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){clear(event.getOriginal());clear(event.getEntity());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity());}
}
