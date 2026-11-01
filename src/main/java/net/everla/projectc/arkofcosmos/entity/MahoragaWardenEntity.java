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
    private static final net.minecraft.world.effect.MobEffect[] ADAPTATION_EFFECTS={
        net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,net.minecraft.world.effect.MobEffects.REGENERATION,
        net.minecraft.world.effect.MobEffects.ABSORPTION,net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
        net.minecraft.world.effect.MobEffects.DAMAGE_BOOST};
    private final int[] effectUses=new int[5];
    private final long[] effectUntil=new long[5];
    private static final UUID ADAPTATION_ATTACK=UUID.fromString("d37cd057-429d-453e-a5d8-798c20fe9740");
    public boolean attacksPaused(){
        return bossId!=null&&level() instanceof ServerLevel server
                &&server.getEntity(bossId) instanceof MahouTsukaiEntity boss&&boss.fusionShield();
    }
    private void updateAdaptationAttributes(){
        long complete=adaptation.values().stream().filter(count->count==5).count();
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(Math.min(60,complete*5));
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(Math.min(30,complete*2));
        var attack=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        attack.removeModifier(ADAPTATION_ATTACK);
        if(complete>0)attack.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                ADAPTATION_ATTACK,"Full adaptation",Math.min(.75,complete*.05),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
    }
    private void grantAdaptationEffect(){
        double total=0;
        double[] weights=new double[5];
        for(int i=0;i<5;i++){
            if(effectUntil[i]>level().getGameTime()&&hasEffect(ADAPTATION_EFFECTS[i]))continue;
            total+=weights[i]=1.0/(1.0+effectUses[i]);
        }
        if(total==0)return;
        double roll=random.nextDouble()*total;
        for(int i=0;i<5;i++){
            if(weights[i]==0)continue;
            roll-=weights[i];
            if(roll<0){
                addEffect(new net.minecraft.world.effect.MobEffectInstance(ADAPTATION_EFFECTS[i],600,ADAPTATION_EFFECTS[i]==net.minecraft.world.effect.MobEffects.REGENERATION?random.nextInt(2):1+random.nextInt(3)));
                effectUses[i]++;effectUntil[i]=level().getGameTime()+600;return;
            }
        }
    }
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
        getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN,net.minecraft.util.Unit.INSTANCE);
        if(attacksPaused()){getNavigation().stop();return;}
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
    public float adaptationMultiplier(DamageSource source){return adaptation(sourceKey(source))>=5?.1f:1-adaptation(sourceKey(source))*.2f;}
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
        try{super.actuallyHurt(source,amount>0?amount*adaptationMultiplier(source):amount);}finally{healthWriteDepth--;}
        if(getHealth()<before&&!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))nonPlayerInvulnerableUntil=level().getGameTime()+10;
        if(count==5&&amount>0)grantAdaptationEffect();
        if(count<5&&getHealth()<before&&level().getGameTime()>=nextLearn&&(count>0||adaptation.size()<20)){
            adaptation.put(key,Math.min(5,count+1));updateAdaptationAttributes();nextLearn=level().getGameTime()+50;
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
    private boolean followingBoss(){
        return level()!=null&&!level().isClientSide&&bossId!=null&&level() instanceof ServerLevel server
                &&server.getEntity(bossId) instanceof MahouTsukaiEntity boss&&boss.distantOrbit();
    }
    @Override public void setPos(double x,double y,double z){
        // Ignore generic previous-tick position locks while the forced escape owns movement.
        if(followingBoss()&&x==xOld&&y==yOld&&z==zOld)return;
        super.setPos(x,y,z);
    }
    @Override public void aiStep(){
        if(!level().isClientSide)syncVitality();
        super.aiStep();
        if(!level().isClientSide&&bossId!=null&&level() instanceof ServerLevel server){
            var boss=server.getEntity(bossId);
            if(boss!=null&&!boss.isAlive())discard();

        }
    }
    void forceFollowBoss(MahouTsukaiEntity boss){
        if(!isAlive()||!boss.getUUID().equals(bossId))return;
        getNavigation().stop();
        getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        var delta=boss.position().subtract(position());
        if(delta.length()>4){
            var at=position().add(delta.normalize().scale(Math.min(.65,delta.length()-4)));
            super.setPos(at.x,at.y,at.z);
            hasImpulse=true;
        }
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);if(bossId!=null)tag.putUUID("Summoner",bossId);
        CompoundTag values=new CompoundTag();adaptation.forEach(values::putInt);tag.put("Adaptation",values);
        tag.putIntArray("AdaptationEffectUses",effectUses);
        for(int i=0;i<5;i++)tag.putInt("AdaptationEffectTicks"+i,(int)Math.max(0,effectUntil[i]-level().getGameTime()));
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
        int[] uses=tag.getIntArray("AdaptationEffectUses");
        for(int i=0;i<5;i++){
            effectUses[i]=i<uses.length?Math.max(0,uses[i]):0;
            effectUntil[i]=level().getGameTime()+net.minecraft.util.Mth.clamp(tag.getInt("AdaptationEffectTicks"+i),0,600);
        }
        updateAdaptationAttributes();
        getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN,net.minecraft.util.Unit.INSTANCE);
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
