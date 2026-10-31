package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Vanilla Warden brain and model with persistent, source-keyed damage adaptation. */
public final class MahoragaWardenEntity extends Warden {
    private UUID bossId;
    private final Map<String,Integer> adaptation=new HashMap<>();
    private long nextLearn;
    private long nonPlayerInvulnerableUntil;
    private int healthWriteDepth,healthSyncDepth;
    private float vitality;
    private boolean vitalityReady;
    private static final byte ADAPTED=61;
    private int wheelTurnStarted=-100;
    /** One full revolution in one second, only after a successful server adaptation. */
    public float wheelAngle(float partial){
        float progress=net.minecraft.util.Mth.clamp((tickCount-wheelTurnStarted+partial)/20f,0,1);
        return progress*(float)(Math.PI*2);
    }
    @Override public void handleEntityEvent(byte event){
        if(event==ADAPTED)wheelTurnStarted=tickCount;else super.handleEntityEvent(event);
    }
    public MahoragaWardenEntity(EntityType<? extends Warden> type,Level level){
        super(type,level);setPersistenceRequired();configureCombatAttributes();
        vitality=super.getHealth();vitalityReady=true;
    }
    private void configureCombatAttributes(){
        var speed=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.setBaseValue(.23*1.5*.85);
        var attack=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if(attack!=null)attack.setBaseValue(20);
    }
    @Override protected void customServerAiStep(){
        // Direct creation skips Warden.finalizeSpawn. Its cooldown refresher cannot
        // create a missing memory, leaving DIG ahead of FIGHT even with a target.
        getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN,net.minecraft.util.Unit.INSTANCE,1200L);
        var target=getTarget();
        if(target==null){
            target=level().getNearestPlayer(getX(),getY(),getZ(),35,this::canTargetEntity);
            if(target!=null){increaseAngerAt(target,150,false);setAttackTarget(target);}
        }else if(tickCount%20==0){
            // Eighty anger is exactly the fight threshold and decays below it.
            // Refresh anger without resetting the vanilla sonic-boom cooldown.
            increaseAngerAt(target,20,false);
        }
        super.customServerAiStep();
    }
    public UUID bossId(){return bossId;}
    void bind(MahouTsukaiEntity boss){bossId=boss.getUUID();setCustomName(net.minecraft.network.chat.Component.translatable("entity.arkofcosmos.mahoraga_warden"));}
    @Override protected boolean shouldDespawnInPeaceful(){return false;}
    @Override public boolean canTargetEntity(Entity other){return !(other instanceof MahouTsukaiEntity)&&super.canTargetEntity(other);}
    @Override public boolean isAlliedTo(Entity other){return other instanceof MahouTsukaiEntity||super.isAlliedTo(other);}
    static String sourceKey(DamageSource source){
        Entity attacker=source.getEntity();
        if(attacker instanceof Player p){
            // Include delayed child attacks: they must not inherit the player's newly held weapon.
            Entity direct=source.getDirectEntity();
            net.minecraft.world.item.Item weapon=direct instanceof GalaxiaEntity?net.everla.projectc.arkofcosmos.ArkOfCosmos.GALAXIA.get():
                    direct instanceof ElementsEntity?net.everla.projectc.arkofcosmos.ArkOfCosmos.ARK_OF_THE_ELEMENTS.get():
                    direct instanceof ArkAttackEntity?net.everla.projectc.arkofcosmos.ArkOfCosmos.ARK_OF_COSMOS.get():p.getMainHandItem().getItem();
            return "item:"+BuiltInRegistries.ITEM.getKey(weapon);
        }
        if(attacker!=null)return "entity:"+BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
        return "damage:"+source.typeHolder().unwrapKey().map(key->key.location().toString()).orElse(source.getMsgId());
    }
    public int adaptation(String key){return adaptation.getOrDefault(key,0);}
    public float adaptationMultiplier(DamageSource source){return Math.max(0,1-adaptation(sourceKey(source))*.2f);}
    public boolean blocksNonPlayerDamage(DamageSource source){
        return !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)&&level().getGameTime()<nonPlayerInvulnerableUntil;
    }
    public boolean blocksHealthWrite(float health){
        return vitalityReady&&!level().isClientSide&&healthWriteDepth==0&&health<getHealth()&&level().getGameTime()<nonPlayerInvulnerableUntil;
    }
    @Override public float getHealth(){
        return vitalityReady&&!level().isClientSide?vitality:super.getHealth();
    }
    @Override public void setHealth(float health){
        if(!vitalityReady||level().isClientSide){super.setHealth(health);return;}
        if(Float.isNaN(health)||blocksHealthWrite(health))return;
        float before=vitality;
        vitality=net.minecraft.util.Mth.clamp(health,0,getMaxHealth());
        if(healthWriteDepth==0&&vitality<before)nonPlayerInvulnerableUntil=level().getGameTime()+10;
        syncVitality();
    }
    private void syncVitality(){
        healthSyncDepth++;
        try{super.setHealth(vitality);}finally{healthSyncDepth--;}
    }
    @Override public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key){
        if(vitalityReady&&!level().isClientSide&&healthSyncDepth==0&&DATA_HEALTH_ID.equals(key)){
            setHealth(entityData.get(DATA_HEALTH_ID));syncVitality();
        }
        super.onSyncedDataUpdated(key);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.getEntity() instanceof MahouTsukaiEntity||source.getDirectEntity() instanceof MahouTsukaiEntity)return false;
        if(amount>0&&blocksNonPlayerDamage(source))return false;
        return super.hurt(source,amount);
    }
    @Override protected void actuallyHurt(DamageSource source,float amount){
        if(amount>0&&(source.getEntity() instanceof MahouTsukaiEntity||source.getDirectEntity() instanceof MahouTsukaiEntity||blocksNonPlayerDamage(source)))return;
        String key=sourceKey(source);int count=adaptation(key);
        float before=getHealth();
        healthWriteDepth++;
        try{super.actuallyHurt(source,amount>0?Math.max(1,amount*(1-count*.2f)):amount);}finally{healthWriteDepth--;}
        if(getHealth()<before&&!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))nonPlayerInvulnerableUntil=level().getGameTime()+10;
        if(count<5&&getHealth()<before&&level().getGameTime()>=nextLearn&&(count>0||adaptation.size()<20)){
            adaptation.put(key,Math.min(5,count+1));nextLearn=level().getGameTime()+50;
            level().broadcastEntityEvent(this,ADAPTED);
            level().playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE,1.5f,1f);
        }
    }
    @Override public void die(DamageSource source){
        super.die(source);
        if(level() instanceof ServerLevel server&&bossId!=null){
            if(server.getEntity(bossId) instanceof MahouTsukaiEntity boss)boss.shikigamiDefeated(getUUID());
            else ShikigamiDeaths.get(server).record(getUUID());
        }
    }
    @Override public void aiStep(){
        if(!level().isClientSide)syncVitality();
        super.aiStep();
        if(!level().isClientSide&&bossId!=null&&level() instanceof ServerLevel server){
            var boss=server.getEntity(bossId);
            if(boss!=null&&!boss.isAlive())discard();
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(bossId!=null)tag.putUUID("Summoner",bossId);
        CompoundTag values=new CompoundTag();adaptation.forEach(values::putInt);tag.put("Adaptation",values);
        tag.putInt("LearnCooldown",(int)Math.max(0,nextLearn-level().getGameTime()));
        tag.putInt("NonPlayerInvulnerabilityTicks",(int)Math.max(0,nonPlayerInvulnerableUntil-level().getGameTime()));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        healthWriteDepth++;
        try{super.readAdditionalSaveData(tag);}finally{healthWriteDepth--;}
        configureCombatAttributes();bossId=tag.hasUUID("Summoner")?tag.getUUID("Summoner"):null;
        nonPlayerInvulnerableUntil=level().getGameTime()+net.minecraft.util.Mth.clamp(tag.getInt("NonPlayerInvulnerabilityTicks"),0,10);
        adaptation.clear();var values=tag.getCompound("Adaptation");
        for(String key:values.getAllKeys())if(adaptation.size()<20)adaptation.put(key,net.minecraft.util.Mth.clamp(values.getInt(key),0,5));
        nextLearn=level().getGameTime()+Math.min(50,Math.max(0,tag.getInt("LearnCooldown")));
    }
}

/** Death receipts survive killing a summoned Warden while its boss chunk is unloaded. */
final class ShikigamiDeaths extends net.minecraft.world.level.saveddata.SavedData {
    private final Set<UUID> dead=new HashSet<>();
    static ShikigamiDeaths get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(ShikigamiDeaths::load,ShikigamiDeaths::new,"arkofcosmos_shikigami_deaths");}
    static ShikigamiDeaths load(CompoundTag tag){
        var data=new ShikigamiDeaths();for(var key:tag.getAllKeys())try{data.dead.add(UUID.fromString(key));}catch(IllegalArgumentException ignored){}return data;
    }
    void record(UUID id){dead.add(id);setDirty();}
    boolean consume(UUID id){if(!dead.remove(id))return false;setDirty();return true;}
    @Override public CompoundTag save(CompoundTag tag){for(UUID id:dead)tag.putBoolean(id.toString(),true);return tag;}
}
