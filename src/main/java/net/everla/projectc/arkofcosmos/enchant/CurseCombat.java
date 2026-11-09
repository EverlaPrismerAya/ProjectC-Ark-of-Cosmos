package net.everla.projectc.arkofcosmos.enchant;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.everla.projectc.arkofcosmos.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Source curse formulas and ownership snapshots shared by native and Ark damage paths. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class CurseCombat {
    private CurseCombat() {}
    private static final ThreadLocal<Boolean> PERIODIC=ThreadLocal.withInitial(()->false);
    private static final String SNAP="ArkWitchSnapshot", OWNER="ArkWitchOwner";
    private static final Map<Player,State> STATES=new WeakHashMap<>();
    private static final class State { long used=-1000,portal,hitUse=-1,sourceTick=-1,flameSelfHit=-20; ItemStack sourceItem=ItemStack.EMPTY; double withering; int withered,hold; }
    private static State state(Player p){return STATES.computeIfAbsent(p,k->{State s=new State();var tag=p.getPersistentData().getCompound("ArkWitchState");s.portal=tag.getLong("Portal");s.withering=tag.getDouble("Damage");s.withered=tag.getInt("Withered");s.hold=tag.getInt("Hold");return s;});}
    private static void save(Player p){State s=state(p);var tag=new CompoundTag();tag.putLong("Portal",s.portal);tag.putDouble("Damage",s.withering);tag.putInt("Withered",s.withered);tag.putInt("Hold",s.hold);p.getPersistentData().put("ArkWitchState",tag);}
    public static void selfDamage(Player p,double source){
        if(p.isCreative()||p.isSpectator())return;
        boolean previous=PERIODIC.get();PERIODIC.set(true);
        try{ArkDamage.hurtUnmitigated(p,p.damageSources().magic(),(float)(CurseMath.health(source,p.getMaxHealth())));}finally{PERIODIC.set(previous);}
    }
    public static void heal(Player p,double source){p.heal((float)(CurseMath.health(source,p.getMaxHealth())));}
    public static Player owner(Entity entity){
        Set<Entity> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(entity!=null && seen.add(entity)){
            if(entity instanceof Player p)return p;
            if(entity.getPersistentData().hasUUID(OWNER) && entity.level() instanceof ServerLevel server)return server.getPlayerByUUID(entity.getPersistentData().getUUID(OWNER));
            if(entity instanceof Projectile projectile)entity=projectile.getOwner();
            else if(entity instanceof OwnableEntity owned)entity=owned.getOwner();else return null;
        }
        return null;
    }
    private static LivingEntity customOwner(Entity e){
        if(e instanceof ViolenceEntity a)return a.owner();
        if(e instanceof OldOneEntity a)return a.owner();if(e instanceof ArkAttackEntity a)return a.curseOwner();if(e instanceof GalaxiaEntity a)return a.curseOwner();
        if(e instanceof ElementsEntity a)return a.curseOwner();if(e instanceof AncientsEntity a)return a.curseOwner();
        if(e instanceof FracturedEntity a)return a.curseOwner();if(e instanceof BrokenBiomeEntity a)return a.curseOwner();
        if(e instanceof BiomeEntity a)return a.curseOwner();if(e instanceof TrueBiomeEntity a)return a.curseOwner();return null;
    }
    private static ItemStack customWeapon(Entity e){
        if(e instanceof ViolenceEntity a)return a.weapon();
        if(e instanceof OldOneEntity a)return a.weapon();if(e instanceof ArkAttackEntity a)return a.weapon();if(e instanceof GalaxiaEntity a)return a.weapon();
        if(e instanceof ElementsEntity a)return a.weapon();if(e instanceof AncientsEntity a)return a.weapon();
        if(e instanceof FracturedEntity a)return a.weapon();if(e instanceof BrokenBiomeEntity a)return a.weapon();
        if(e instanceof BiomeEntity a)return a.weapon();if(e instanceof TrueBiomeEntity a)return a.weapon();return ItemStack.EMPTY;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void capture(EntityJoinLevelEvent event){
        Entity e=event.getEntity(); if(event.getLevel().isClientSide || event.loadedFromDisk() || e instanceof Player || e instanceof WitchCurseEntity)return;
        var data=e.getPersistentData();if(data.getBoolean("ArkWitchCaptured"))return;
        LivingEntity custom=customOwner(e);Player p=custom instanceof Player player?player:owner(e);
        if(p==null || !(e instanceof Projectile || e instanceof LivingEntity && e instanceof OwnableEntity || custom!=null))return;
        // Every owned attack is marked, even if unenchanted, so later weapon swaps cannot add a curse.
        data.putBoolean("ArkWitchCaptured",true);data.putUUID(OWNER,p.getUUID());
        ItemStack item=custom==null?(p.isUsingItem()?p.getUseItem():state(p).sourceTick==p.level().getGameTime()?state(p).sourceItem:ItemStack.EMPTY):customWeapon(e);
        if(e instanceof Projectile projectile && projectile.getOwner()!=null && projectile.getOwner().getPersistentData().getBoolean("ArkWitchCaptured")){
            var parent=projectile.getOwner().getPersistentData();
            if(parent.contains(SNAP))data.put(SNAP,parent.getCompound(SNAP).copy());
            data.putLong("ArkWitchBorn",parent.getLong("ArkWitchBorn"));return;
        }
        if(Curse.of(item)==Curse.TAINTED && (e instanceof Projectile || custom!=null)){
            use(p,p.getMainHandItem());
            event.setCanceled(true);
            if(e instanceof net.minecraft.world.entity.projectile.ThrownTrident trident && !p.getAbilities().instabuild) {
                ItemStack returned=item.copy();returned.setCount(1);if(!p.getInventory().add(returned))p.drop(returned,false);
            }
            return;
        }
        if(Curse.of(item)!=null){
            if(custom==null)use(p,item);
            else if(p.getMainHandItem().is(item.getItem()) && Curse.of(p.getMainHandItem())==Curse.of(item)){
                use(p,p.getMainHandItem());if(Curse.of(item)==Curse.EPHEMERAL)item.getOrCreateTag().putFloat("ArkWitchCharge",p.getMainHandItem().getOrCreateTag().getFloat("ArkWitchCharge"));
            }
            data.put(SNAP,item.save(new CompoundTag()));
            data.putLong("ArkWitchBorn",e.level().getGameTime());
            if(e instanceof LivingEntity && e instanceof OwnableEntity && Curse.of(item)==Curse.HELLBOUND)data.putBoolean("ArkWitchMinion",true);
        }
    }
    public static ItemStack weapon(DamageSource source){
        Entity direct=source.getDirectEntity();if(direct instanceof BejeweledBoltEntity bolt)return bolt.weapon();if(direct instanceof WitchCurseEntity)return ItemStack.EMPTY;
        if(direct!=null && direct.getPersistentData().getBoolean("ArkWitchCaptured"))
            return direct.getPersistentData().contains(SNAP)?ItemStack.of(direct.getPersistentData().getCompound(SNAP)):ItemStack.EMPTY;
        if(direct==source.getEntity() && direct instanceof Player p)return p.getMainHandItem();
        return ItemStack.EMPTY;
    }
    public static double smooth(double a,double b,double t){return CurseMath.smooth(a,b,t);}
    public static float modify(LivingEntity victim,DamageSource source,float amount){
        Player p=owner(source.getEntity());if(p==null)return amount;
        ItemStack item=weapon(source);Curse curse=Curse.of(item);if(curse==null)return amount;
        double factor=1,d=p.distanceTo(victim)*16;
        switch(curse){
            case OBLATORY -> {if(CalamitasContent.ENABLED && WitchSouls.active(p))factor=1.25;}
            case RESENTFUL -> factor=CurseMath.resentful(d/16);
            case BLOODTHIRSTY -> factor=CurseMath.bloodthirsty(d/16);
            case EPHEMERAL -> factor=CurseMath.ephemeral(item.getOrCreateTag().getFloat("ArkWitchCharge"));
            case WITHERING -> {if(state(p).withered>0)factor=1.6;}
            case HELLBOUND -> {
                Entity direct=source.getDirectEntity();long born=direct==null?0:direct.getPersistentData().getLong("ArkWitchBorn");
                factor=CurseMath.hellbound((p.level().getGameTime()-born)*3d);
            }
            case TAINTED -> {if(source.getDirectEntity()!=source.getEntity())return 0;}
            default -> {}
        }
        return (float)(amount*factor);
    }
    public static void hit(LivingEntity victim,DamageSource source,float damage){
        Player p=owner(source.getEntity());if(p==null || damage<=0)return;
        Curse curse=Curse.of(weapon(source));if(curse==null)return;
        if(curse==Curse.WITHERING)state(p).withering+=damage*400/p.getMaxHealth();
        if(curse==Curse.AFLAME && victim.isAlive() && CalamitasContent.ENABLED){
            WitchFlames.apply(victim,40);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void nativeHurt(LivingHurtEvent event){event.setAmount(modify(event.getEntity(),event.getSource(),event.getAmount()));}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void nativeDamage(LivingDamageEvent event){
        if(event.getEntity() instanceof WitchCurseEntity actor)event.setAmount(actor.capDamage(event.getAmount()));
        hit(event.getEntity(),event.getSource(),event.getAmount());
        if(event.getEntity() instanceof Player p)event.setAmount(incoming(p,event.getAmount()));
    }
    public static float incoming(Player p,float amount){
        if(!CalamitasContent.ENABLED || PERIODIC.get())return amount;
        if(amount>0){
            State s=state(p);
            if(Curse.of(p.getMainHandItem())==Curse.PERSECUTED)WitchCurseEntity.portal(p);
            if(s.withering>0){double ratio=CurseMath.withering(s.withering);amount*=(float)(1-Math.max(0,ratio));s.withering=0;s.withered=360;if(CalamitasContent.ENABLED)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(CalamitasContent.WITCH_WITHERED.get(),360));}
        }
        return amount;
    }
    public static void use(Player p,ItemStack item){
        if(p.level().isClientSide || p.isSpectator())return;
        State s=state(p);long now=p.level().getGameTime();Curse selected=Curse.of(item);if(selected==null || s.hitUse==now)return;s.hitUse=now;s.used=now;
        Curse curse=Curse.of(item);if(curse==null)return;
        if(curse==Curse.TAINTED)p.getPersistentData().putLong("ArkWitchArmSwing",now*3+25);
        if(curse==Curse.EPHEMERAL){double frames=60/Math.max(.1,p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
            item.getOrCreateTag().putFloat("ArkWitchCharge",(float)CurseMath.discharge(item.getOrCreateTag().getFloat("ArkWitchCharge"),frames));}
        if(curse==Curse.INDIGNANT)WitchCurseEntity.demon(p);
        if(curse==Curse.TRAITOROUS && CalamitasContent.ENABLED && WitchSouls.active(p) && WitchSouls.ratio(p)<.25 && !WitchCurseEntity.exists(p,WitchCurseEntity.Kind.MANA)){
            int capacity=WitchSouls.capacity(p);int taken=WitchSouls.drain(p);
            if(taken>0)WitchCurseEntity.spawn(p,WitchCurseEntity.Kind.MANA,p.getEyePosition(),(float)(taken*80d*.06),0);
        }
    }
    public static void source(Player p,ItemStack item){if(!p.level().isClientSide){state(p).sourceItem=item;state(p).sourceTick=p.level().getGameTime();}}
    @SubscribeEvent public static void melee(AttackEntityEvent event){source(event.getEntity(),event.getEntity().getMainHandItem());use(event.getEntity(),event.getEntity().getMainHandItem());}
    @SubscribeEvent public static void using(LivingEntityUseItemEvent.Start event){if(event.getEntity() instanceof Player p){source(p,event.getItem());use(p,event.getItem());}}
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickItem event){source(event.getEntity(),event.getItemStack());}
    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event){source(event.getEntity(),event.getItemStack());}
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event){source(event.getEntity(),event.getItemStack());}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(!CalamitasContent.ENABLED || event.phase!=TickEvent.Phase.END || !(event.player.level() instanceof ServerLevel))return;
        Player p=event.player;if(!p.isAlive())return;State s=state(p);long now=p.level().getGameTime();
        for(ItemStack item:p.getInventory().items)if(Curse.of(item)==Curse.EPHEMERAL)
            item.getOrCreateTag().putFloat("ArkWitchCharge",Math.min(1600,item.getOrCreateTag().getFloat("ArkWitchCharge")+3));
        Curse curse=Curse.of(p.getMainHandItem());
        if(p.isUsingItem() && now%Math.max(1,(int)(20/Math.max(.1,p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))))==0)use(p,p.getUseItem());
        if(curse==Curse.AFLAME && now-s.flameSelfHit>=20 && !p.isCreative() && !p.isSpectator()){
            s.flameSelfHit=now;
            selfDamage(p,7);
        }
        if(s.withered>0){if(CalamitasContent.ENABLED && !p.hasEffect(CalamitasContent.WITCH_WITHERED.get()))p.addEffect(new net.minecraft.world.effect.MobEffectInstance(CalamitasContent.WITCH_WITHERED.get(),s.withered));s.withered--;double loss=0;for(int frame=0;frame<3;frame++){s.hold+=curse==Curse.WITHERING?1:-1;if(s.hold<0)s.hold=0;else loss+=Math.floor(5*Math.pow(1.5,s.hold/87d))/120;}if(loss>0)selfDamage(p,loss);}else s.hold=0;
        if(++s.portal>=600){s.portal=0;if(curse==Curse.PERSECUTED)WitchCurseEntity.portal(p);}
        if(curse==Curse.LECHEROUS && !WitchCurseEntity.exists(p,WitchCurseEntity.Kind.ORB))WitchCurseEntity.spawn(p,WitchCurseEntity.Kind.ORB,p.getEyePosition().add(p.getLookAngle().scale(8)),0,0);
        save(p);
        if(curse==Curse.TAINTED){for(int hand=0;hand<2;hand++)if(!WitchCurseEntity.armExists(p,hand))WitchCurseEntity.spawn(p,WitchCurseEntity.Kind.ARM,p.getEyePosition(),(float)(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*5),hand);}
    }
    @SubscribeEvent public static void minions(LivingEvent.LivingTickEvent event){
        LivingEntity entity=event.getEntity();if(!(entity.level() instanceof ServerLevel level))return;

        if(!entity.getPersistentData().getBoolean("ArkWitchMinion"))return;
        Player p=owner(entity);if(p==null)return;
        long time=level.getGameTime()-entity.getPersistentData().getLong("ArkWitchBorn");
        if(time<800 && ((time*3)%40>=37) && entity.getRandom().nextInt(12)==0)WitchCurseEntity.explode(p,entity.position(),2000*.06f,108/32d);
        if(time>=800){WitchCurseEntity.explode(p,entity.position(),6000*.06f,5);entity.getPersistentData().remove("ArkWitchMinion");entity.kill();}
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){Curse curse=Curse.of(event.getItemStack());if(curse!=null){event.getToolTip().add(net.minecraft.network.chat.Component.translatable(curse.key()).withStyle(net.minecraft.ChatFormatting.GOLD));event.getToolTip().add(net.minecraft.network.chat.Component.translatable(curse.key()+".description").withStyle(net.minecraft.ChatFormatting.GRAY));}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){save(event.getEntity());STATES.remove(event.getEntity());}
}
