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

/** Native Apollyon AI and death performance, with Calamitas-owned damage settlement. */
public final class SatsukiRinEntity extends Apostle implements SatsukiBoss,ApollyonCompatTarget {
    public static final int INTRO_TICKS=20;
    private static final EntityDataAccessor<Integer> INTRO=net.minecraft.network.syncher.SynchedEntityData.defineId(SatsukiRinEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(INTRO,0);}
    public int domainIntro(){return entityData.get(INTRO);}
    @Override protected void tickDeath(){
        if(domainIntro()<INTRO_TICKS){
            if(!level().isClientSide)entityData.set(INTRO,domainIntro()+1);
            return;
        }
        super.tickDeath();
    }
    private final SupremeCalamitasDynamicReduction dynamicReduction=new SupremeCalamitasDynamicReduction();
    private boolean vitalityReady,commandRemoval;
    private float vitality;
    private int damageDepth,syncDepth;
    private long nonPlayerInvulnerableUntil;
    private float previousDomainScale,domainScale;
    @Override public void tick(){
        previousDomainScale=domainScale;
        super.tick();
        domainScale=SatsukiDomain.scale(this);
        if(!level().isClientSide&&previousDomainScale==0&&domainScale>0)
            for(var player:level().players())if(player.distanceToSqr(this)<=128*128)
                player.displayClientMessage(Component.translatable("skill.arkofcosmos.satsuki_rin.fumo_shrine"),true);
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
    // Global GR/RF LivingEntity damage callbacks must see an ordinary mob during settlement.
    public boolean allTitlesApostle_1_20_1$isApollyon(){return damageDepth==0&&!commandRemoval;}
    @Override public boolean isInNether(){return true;}
    @Override public boolean arkofcosmos$isNetherApollyon(){return false;}
    @Override public void setCustomName(Component name){super.setCustomName(Component.translatable("entity.arkofcosmos.satsuki_rin"));}
    @Override public Component getName(){return Component.translatable("entity.arkofcosmos.satsuki_rin");}
    @Override public float getHealth(){return vitalityReady&&!level().isClientSide?vitality:entityData.get(DATA_HEALTH_ID);}
    public float revelaionfix$getApollyonHealth(){return getHealth();}
    public void revelaionfix$setApollyonHealth(float health){setHealth(health);}
    @Override public float combatVitality(){return vitalityReady?vitality:-1;}
    @Override public boolean isAlive(){return !isRemoved()&&getHealth()>0;}
    @Override public boolean isDeadOrDying(){return getHealth()<=0;}
    public boolean phaseInvulnerable(){return isSettingUpSecond();}
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
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit){
        super.dropCustomDeathLoot(source,looting,recentlyHit);
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
