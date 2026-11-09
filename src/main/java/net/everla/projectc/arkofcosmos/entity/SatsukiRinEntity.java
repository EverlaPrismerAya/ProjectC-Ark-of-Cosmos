package net.everla.projectc.arkofcosmos.entity;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import net.everla.projectc.arkofcosmos.SatsukiContent;
import net.everla.projectc.arkofcosmos.SatsukiConfig;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasDynamicReduction;
import net.everla.projectc.arkofcosmos.combat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;

/** Native Apollyon AI and death performance, with Calamitas-owned damage settlement. */
public final class SatsukiRinEntity extends Apostle implements SatsukiBoss,ApollyonCompatTarget {
    @Override protected SoundEvent getHurtSound(DamageSource source){return net.everla.projectc.arkofcosmos.SatsukiContent.HURT.get();}
    // Apostle plays getDeathSound() (pre-death) when dying starts and getTrueDeathSound() when the death routine ends.
    @Override protected SoundEvent getTrueDeathSound(){return net.everla.projectc.arkofcosmos.SatsukiContent.DEATH.get();}
    public static final int INTRO_TICKS=20;
    private static final EntityDataAccessor<Integer> INTRO=net.minecraft.network.syncher.SynchedEntityData.defineId(SatsukiRinEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHIELD=net.minecraft.network.syncher.SynchedEntityData.defineId(SatsukiRinEntity.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(INTRO,0);entityData.define(SHIELD,false);}
    /** Synced invulnerability (phase set-up or a living own monolith), drives the golden note shield. */
    public boolean shielded(){return entityData.get(SHIELD);}
    public int domainIntro(){return entityData.get(INTRO);}
    @Override protected void tickDeath(){
        if(domainIntro()<INTRO_TICKS){
            if(!level().isClientSide)entityData.set(INTRO,domainIntro()+1);
            return;
        }
        SatsukiDeathParticles.run(()->super.tickDeath());
    }
    private final SupremeCalamitasDynamicReduction dynamicReduction=new SupremeCalamitasDynamicReduction();
    private boolean vitalityReady,commandRemoval;
    private float vitality;
    private int damageDepth,syncDepth;
    private long nonPlayerInvulnerableUntil;
    private float previousDomainScale,domainScale;
    @Override public void tick(){
        previousDomainScale=domainScale;
        if(!level().isClientSide){
            // Select before super.tick() so the native Apostle goals can run with this target in the same tick.
            if(!isDeadOrDying()&&!validTarget(getTarget())){
                var target=level().getNearestPlayer(getX(),getY(),getZ(),TARGET_RANGE,e->e instanceof LivingEntity living&&validTarget(living));
                if(target!=null)setTarget(target);
            }
        }
        super.tick();
        if(!level().isClientSide){markArrows();monolithShield=!isDeadOrDying()&&hasMonolith();boolean shield=!isDeadOrDying()&&phaseInvulnerable();if(shield!=shielded())entityData.set(SHIELD,shield);}
        domainScale=SatsukiDomain.scale(this);
    }
    public static final double TARGET_RANGE=128;
    /** Creative, spectator, dead and far players are never kept; OdamaneHalo wearers stay legal targets. */
    public boolean validTarget(LivingEntity target){
        if(target==null||!target.isAlive()||target.isRemoved())return false;
        if(!(target instanceof net.minecraft.world.entity.player.Player p))return true;
        return !p.isCreative()&&!p.isSpectator()&&distanceToSqr(p)<=TARGET_RANGE*TARGET_RANGE;
    }
    // Native arrows stay authoritative; clients only receive a visual marker (owner/color) per arrow.
    private final java.util.Map<net.minecraft.world.entity.projectile.AbstractArrow,Integer> arrows=new java.util.IdentityHashMap<>();
    public void trackArrow(net.minecraft.world.entity.projectile.AbstractArrow arrow){
        arrows.computeIfAbsent(arrow,a->{int variant=getRandom().nextInt(7);markArrow(a,variant);return variant;});
    }
    private void markArrows(){
        arrows.keySet().removeIf(Entity::isRemoved);
        if(tickCount%10==0)arrows.forEach(this::markArrow);
    }
    private void markArrow(net.minecraft.world.entity.projectile.AbstractArrow arrow,int variant){
        var data=new net.minecraft.nbt.CompoundTag();data.putString("Event","satsuki_arrow");
        data.putUUID("Arrow",arrow.getUUID());data.putInt("Owner",getId());data.putInt("Variant",variant);
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(arrow,data);
    }
    /** Client-readable current attack (synced spell byte / bow use), drives the phase-two note circles. */
    public String currentAttack(){
        if(isSpellcasting()&&getSpellType()!=null)return getSpellType().name();
        return isUsingItem()||aboutToShoot()?"RANGED":"NONE";
    }
    public float domainScale(float partial){return net.minecraft.util.Mth.lerp(partial,previousDomainScale,domainScale);}
    public SatsukiRinEntity(EntityType<? extends SatsukiRinEntity> type,Level level){
        super(type,level);setPersistenceRequired();setConfigurableAttributes();vitality=getMaxHealth();vitalityReady=true;syncVitality();
    }
    public static AttributeSupplier.Builder createAttributes(){return Apostle.setCustomAttributes().add(SatsukiContent.MAX_HEALTH.get(),6666).add(Attributes.ARMOR,0).add(Attributes.ARMOR_TOUGHNESS,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override public double getAttributeValue(Attribute a){return super.getAttributeValue(a==Attributes.MAX_HEALTH?SatsukiContent.MAX_HEALTH.get():a);}
    @Override public double getAttributeBaseValue(Attribute a){return super.getAttributeBaseValue(a==Attributes.MAX_HEALTH?SatsukiContent.MAX_HEALTH.get():a);}
    @Override public AttributeInstance getAttribute(Attribute a){return super.getAttribute(a==Attributes.MAX_HEALTH?SatsukiContent.MAX_HEALTH.get():a);}
    @Override public void setConfigurableAttributes(){getAttribute(Attributes.MAX_HEALTH).setBaseValue(SatsukiConfig.value("health"));getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
    @Override public void applyConfiguredHealth(){
        float ratio=getMaxHealth()>0?getHealth()/getMaxHealth():1;
        setConfigurableAttributes();
        if(vitalityReady){vitality=net.minecraft.util.Mth.clamp(ratio*getMaxHealth(),0,getMaxHealth());syncVitality();}
        for(String key:new String[]{"armor_piercing","enchantment_piercing"}){
            var attribute=net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety_revelation",key.equals("armor_piercing")?"armor_penetration":key));
            var instance=attribute==null?null:getAttribute(attribute);
            if(instance!=null)instance.setBaseValue(1+SatsukiConfig.value(key)/100);
        }
    }
    public boolean allTitlesApostle_1_20_1$isApollyon(){return true;}
    @Override public boolean isInNether(){return true;}
    @Override public boolean isAlliedTo(Entity entity){return entity instanceof net.minecraft.world.entity.player.Player ? false : super.isAlliedTo(entity);}
    @Override public boolean arkofcosmos$isNetherApollyon(){return true;}
    @Override public void setCustomName(Component name){super.setCustomName(Component.translatable("entity.arkofcosmos.satsuki_rin"));}
    @Override public Component getName(){return Component.translatable("entity.arkofcosmos.satsuki_rin");}
    @Override public float getHealth(){return vitalityReady&&!level().isClientSide?vitality:entityData.get(DATA_HEALTH_ID);}
    public float revelaionfix$getApollyonHealth(){return getHealth();}
    public void revelaionfix$setApollyonHealth(float health){setHealth(health);}
    @Override public float combatVitality(){return vitalityReady?vitality:-1;}
    @Override public boolean isAlive(){return !isRemoved()&&getHealth()>0;}
    @Override public boolean isDeadOrDying(){return getHealth()<=0;}
    // Like Apollyon and its obsidian monoliths: invulnerable while one of her own monoliths (rendered as the Reimu Fumo) is alive.
    private boolean monolithShield;
    private boolean hasMonolith(){
        return !level().getEntitiesOfClass(com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith.class,getBoundingBox().inflate(TARGET_RANGE),
                m->m.isAlive()&&m.getTrueOwner()==this).isEmpty();
    }
    // "不老不死程度的能力" (GR title 0): every native heal() is four times as strong.
    @Override public void heal(float amount){
        if(amount>0&&!level().isClientSide&&title()==0)amount*=4;
        super.heal(amount);
    }
    /** GR title number (0 = 不老不死, 12 = 引发奇迹, 13 = 飞翔于空中). */
    public int title(){return ((z1gned.goetyrevelation.util.ApollyonAbilityHelper)(Object)this).allTitleApostle$getTitleNumber();}
    /** 引发奇迹 / 飞翔于空中: complete immunity outside casting animations (covers ArkDamage via the ProtectedBoss health guards). */
    private boolean unifiedGuard(){int t=title();return (t==12||t==13)&&!isSpellcasting()&&!isCasting();}
    public boolean phaseInvulnerable(){return isSettingUpSecond()||monolithShield||!isDeadOrDying()&&unifiedGuard();}
    private boolean blocksNonPlayerDamage(DamageSource source){return !WeaponUser.playerOwnedDamage(source)&&level().getGameTime()<nonPlayerInvulnerableUntil;}
    @Override public boolean blocksHealthWrite(float health){return vitalityReady&&!level().isClientSide&&damageDepth==0&&syncDepth==0&&health<vitality&&(phaseInvulnerable()||level().getGameTime()<nonPlayerInvulnerableUntil);}
    @Override public boolean redirectHealthWrite(float health){
        if(!vitalityReady||level().isClientSide||damageDepth>0||syncDepth>0)return false;
        if(!Float.isFinite(health))return true;
        if(health<vitality){ordinaryDamage(damageSources().generic(),vitality-health);return true;}
        return false;
    }
    @Override public void ordinaryDamage(DamageSource source,float amount){
        if(amount>0&&Float.isFinite(amount))hurt(new DamageSource(damageSources().generic().typeHolder(),source.getDirectEntity(),source.getEntity()),Math.min(amount,50));
    }
    private void syncVitality(){syncDepth++;try{entityData.set(DATA_HEALTH_ID,vitality);}finally{syncDepth--;}}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        if(vitalityReady&&!level().isClientSide&&syncDepth==0&&DATA_HEALTH_ID.equals(key)){setHealth(entityData.get(DATA_HEALTH_ID));syncVitality();}
        super.onSyncedDataUpdated(key);
    }
    @Override public void setHealth(float health){
        if(!Float.isFinite(health))return;
        if(!vitalityReady||level().isClientSide){entityData.set(DATA_HEALTH_ID,net.minecraft.util.Mth.clamp(health,0,getMaxHealth()));return;}
        if(redirectHealthWrite(health)||blocksHealthWrite(health))return;
        if(damageDepth>0&&syncDepth==0&&health<vitality){
            double loss=Math.max(0,vitality-Math.max(health,isSecondPhase()?0:getMaxHealth()/2));
            health=vitality-(float)(SatsukiConfig.value("dynamic_damage_reduction")!=0?dynamicReduction.apply(level().getGameTime(),loss):loss);
        }
        vitality=net.minecraft.util.Mth.clamp(health,0,getMaxHealth());syncVitality();
    }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){
        var id=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
        return effect.getEffect()!=net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE
                &&!net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety","iron_hide").equals(id)&&super.canBeAffected(effect);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(!(amount>0)||!Float.isFinite(amount)||phaseInvulnerable()||blocksNonPlayerDamage(source)||source.getEntity()!=null&&isAlliedTo(source.getEntity()))return false;
        if(!WeaponUser.playerOwnedDamage(source))amount=Math.min(amount,50);
        damageDepth++;
        try{
            boolean hit=((SatsukiDamageAccess)(Object)this).ark$satsukiHurt(source,amount);
            // Retaliate against a player attacker when the current target is missing or no longer legal.
            if(hit&&!level().isClientSide&&isAlive()&&source.getEntity() instanceof net.minecraft.world.entity.player.Player attacker
                    &&!validTarget(getTarget())&&validTarget(attacker))setTarget(attacker);
            if(hit&&getTarget()!=null&&source.getEntity() instanceof LivingEntity){
                increaseHitTime();
                if(isAlive()&&!isNoAi()&&getHitTimes()>=hitTimeTeleport())teleport();
            }
            return hit;
        }finally{damageDepth--;}
    }
    @Override protected void actuallyHurt(DamageSource source,float amount){
        if(phaseInvulnerable()||blocksNonPlayerDamage(source))return;
        boolean nonPlayer=!WeaponUser.playerOwnedDamage(source);float before=getHealth();damageDepth++;
        if(nonPlayer)amount=Math.min(amount,50);
        if(SatsukiConfig.value("hurt_limit")>0)amount=Math.min(amount,(float)SatsukiConfig.value("hurt_limit"));
        amount*=1-(float)SatsukiConfig.value("permanent_damage_reduction")/100;
        try{((SatsukiDamageAccess)(Object)this).ark$satsukiActuallyHurt(source,amount);}finally{damageDepth--;}
        if(nonPlayer&&getHealth()<before)nonPlayerInvulnerableUntil=level().getGameTime()+(long)SatsukiConfig.value("invulnerable_time");
    }
    public boolean protectRemoval(RemovalReason reason){return !commandRemoval&&vitalityReady&&!level().isClientSide&&getHealth()>0&&reason.shouldDestroy();}
    @Override public void removeByPlayerCommand(){commandRemoval=true;bossInfo.removeAllPlayers();remove(RemovalReason.DISCARDED);}
    @Override public void remove(RemovalReason reason){if(!protectRemoval(reason))super.remove(reason);}
    private boolean deathBurstSent;
    // Fixed Satsuki loot (arkofcosmos:entities/satsuki_rin); Apostle's unholy blood / night beacon are not dropped.
    public static final net.minecraft.resources.ResourceLocation LOOT=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","entities/satsuki_rin");
    @Override protected net.minecraft.resources.ResourceLocation getDefaultLootTable(){return LOOT;}
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit){
        if(!level().isClientSide&&!deathBurstSent){
            deathBurstSent=true;
            var packet=new net.minecraft.nbt.CompoundTag();packet.putString("Event","satsuki_death_burst");
            var center=getBoundingBox().getCenter();packet.putDouble("X",center.x);packet.putDouble("Y",center.y);packet.putDouble("Z",center.z);
            net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this,packet);
        }
    }
    @Override public void die(DamageSource source){if(vitalityReady&&getHealth()>0)return;super.die(source);}
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("SatsukiVitality",getHealth());tag.putInt("FumoIntro",domainIntro());}
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){
        vitalityReady=false;try{super.readAdditionalSaveData(tag);vitality=tag.contains("SatsukiVitality")?tag.getFloat("SatsukiVitality"):entityData.get(DATA_HEALTH_ID);}
        finally{vitalityReady=true;}
        vitality=Float.isFinite(vitality)?net.minecraft.util.Mth.clamp(vitality,0,getMaxHealth()):getMaxHealth();syncVitality();
        entityData.set(INTRO,net.minecraft.util.Mth.clamp(tag.getInt("FumoIntro"),0,INTRO_TICKS));
    }
}
