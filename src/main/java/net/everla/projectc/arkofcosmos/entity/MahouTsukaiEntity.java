package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;

public final class MahouTsukaiEntity extends PathfinderMob implements net.minecraft.world.entity.monster.Enemy {
    public static final int MAX_PHASE = 3;
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(MahouTsukaiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TRANSITION=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TOTAL_HEALTH=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.FLOAT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    private boolean phaseRecovering;
    private long weaponInvulnerableUntil, nonPlayerInvulnerableUntil;
    private int healthWriteDepth, healthSyncDepth;
    private float vitality;
    private boolean vitalityReady;
    private final MahouPhaseOneAI phaseOne = new MahouPhaseOneAI(this);

    public MahouTsukaiEntity(EntityType<? extends MahouTsukaiEntity> type, Level level) {
        super(type, level);
        noPhysics=true;setNoGravity(true);
        setPersistenceRequired();
        setDropChance(EquipmentSlot.MAINHAND, 0);
        bossEvent.setDarkenScreen(false);
        bossEvent.setCreateWorldFog(false);
        vitality=super.getHealth();vitalityReady=true;
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0D).add(Attributes.MOVEMENT_SPEED, .2D).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override protected void registerGoals() {}
    public boolean phaseRecovering() { return phaseRecovering; }
    void weaponInvulnerable(int updates) { weaponInvulnerableUntil=Math.max(weaponInvulnerableUntil,level().getGameTime()+(updates+2)/3); }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(amount>0&&phaseTransitionTicks()>0)return false;
        if(amount>0 && phase()==1 && !phaseRecovering && level().getGameTime()<weaponInvulnerableUntil)return false;
        if(amount>0&&blocksNonPlayerDamage(source))return false;
        return super.hurt(source,amount);
    }
    @Override public boolean isInWall() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(PHASE, (byte) 1);entityData.define(TRANSITION,300);entityData.define(TOTAL_HEALTH,1f); }
    public int phase() { return entityData.get(PHASE); }

    public int phaseTransitionTicks(){return entityData.get(TRANSITION);}
    public float totalHealthProgress(){
        if(level().isClientSide)return entityData.get(TOTAL_HEALTH);
        float current=phaseRecovering?0:Math.max(0,getHealth()/getMaxHealth()-(phase()==1?.5f:phase()==2?.25f:0));
        float future=phase()==1?1.75f:phase()==2?1:0;
        return net.minecraft.util.Mth.clamp((current+future)/2.25f,0,1);
    }


    @Override public net.minecraft.network.chat.Component getName() {
        var custom = getCustomName();
        return (custom != null ? custom.copy() : net.minecraft.network.chat.Component.translatable(
                "entity.arkofcosmos.mahou_tsukai.phase_" + phase()))
                .withStyle(style -> style.withFont(net.everla.projectc.arkofcosmos.util.MahouNameStyle.FONT));
    }

    public float damageMultiplier() { return phase() == 1 ? .5f : phase() == 2 ? .67f : 1f; }

    public float bossBarProgress() {
        float threshold = phase() == 1 ? .5f : phase() == 2 ? .25f : 0f;
        return net.minecraft.util.Mth.clamp((getHealth() / getMaxHealth() - threshold) / (1f - threshold), 0f, 1f);
    }

    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        return super.getDamageAfterMagicAbsorb(source, amount) * damageMultiplier();
    }

    public boolean blocksNonPlayerDamage(DamageSource source) {
        return !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)
                && level().getGameTime()<nonPlayerInvulnerableUntil;
    }
    // Also used by the health-setter launch hook, including invokespecial callers.
    public boolean blocksHealthWrite(float health) {
        return vitalityReady && !level().isClientSide && healthWriteDepth==0 && health<getHealth()
                && (phaseTransitionTicks()>0 || level().getGameTime()<nonPlayerInvulnerableUntil);
    }
    @Override public float getHealth() {
        return vitalityReady&&!level().isClientSide?vitality:super.getHealth();
    }
    @Override public void setHealth(float health) {
        if(!vitalityReady||level().isClientSide){super.setHealth(health);return;}
        if(Float.isNaN(health)||blocksHealthWrite(health))return;
        float before=vitality;
        vitality=net.minecraft.util.Mth.clamp(health,0,getMaxHealth());
        if(healthWriteDepth==0 && vitality<before){
            nonPlayerInvulnerableUntil=level().getGameTime()+10;
            beginPhaseRecovery();
            if(phaseRecovering&&vitality<=0)vitality=1;
        }
        syncVitality();
    }
    private void syncVitality(){
        healthSyncDepth++;
        try { super.setHealth(vitality); }
        finally { healthSyncDepth--; }
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        if(vitalityReady&&!level().isClientSide&&healthSyncDepth==0&&DATA_HEALTH_ID.equals(key)){
            // Covers raw DataItem writes which bypass SynchedEntityData.set, but notify the entity.
            setHealth(entityData.get(DATA_HEALTH_ID));
            syncVitality();
        }
        super.onSyncedDataUpdated(key);
    }
    @Override protected void actuallyHurt(DamageSource source, float amount) {
        if(amount>0&&(phaseTransitionTicks()>0||blocksNonPlayerDamage(source)))return;
        float before=getHealth();
        healthWriteDepth++;
        try { super.actuallyHurt(source, amount); }
        finally { healthWriteDepth--; }
        if(getHealth()<before && !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))
            nonPlayerInvulnerableUntil=level().getGameTime()+10;
        // Evaluate actual health after armor, absorption and Forge damage events.
        beginPhaseRecovery();
        if (phaseRecovering && getHealth() <= 0) setHealth(1);
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
    }

    private void beginPhaseRecovery() {
        if (phase() >= MAX_PHASE || phaseRecovering) return;
        float threshold = phase() == 1 ? .5f : .25f;
        if (getHealth() < getMaxHealth() * threshold) phaseRecovering = true;
    }

    private void advancePhase(int next) {
        entityData.set(PHASE, (byte) next);
        phaseRecovering = false;
        if(next<=MAX_PHASE-1)entityData.set(TRANSITION,300);
        bossEvent.setProgress(1f);
        for (ServerPlayer player : bossEvent.getPlayers()) syncBossBar(player, next);
    }

    @Override public void aiStep() {
        if(!level().isClientSide)syncVitality();
        super.aiStep();
        if (!level().isClientSide && isAlive()) {
            if(phaseTransitionTicks()>0){
                if(!isNoAi())transitionRain();
                entityData.set(TRANSITION,phaseTransitionTicks()-1);
            }
            beginPhaseRecovery();
            if (phaseRecovering) {
                setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * .05f));
                if (getHealth() >= getMaxHealth()) advancePhase(phase() + 1);
            }
            entityData.set(TOTAL_HEALTH,totalHealthProgress());
            bossEvent.setProgress(bossBarProgress());
            bossEvent.setName(getDisplayName());
            if (!isNoAi()) phaseOne.tick();
        }
    }

    private void transitionRain(){
        int remaining=phaseTransitionTicks();
        if(remaining<40||remaining%10!=0)return;
        if((phase()==2||phase()==3)&&remaining%20==0){
            LivingEntity player=level().getNearestPlayer(getX(),getY(),getZ(),48,
                    e->e instanceof net.minecraft.world.entity.player.Player p&&MahouPhaseOneAI.valid(this,p));
            if(player==null&&MahouPhaseOneAI.valid(this,getTarget()))player=getTarget();
            if(player==null&&MahouPhaseOneAI.valid(this,getLastHurtByMob()))player=getLastHurtByMob();
            if(player!=null){
                double angle=random.nextDouble()*Math.PI*2;
                var at=position().add(Math.cos(angle)*12,12,Math.sin(angle)*12);
                for(int i=0;i<(phase()==3?5:1);i++) ElementsEntity.rainStar(this,player,at.add((random.nextDouble()-.5)*6,0,(random.nextDouble()-.5)*6));
            }
        }
        for(int i=0;i<(phase()==3?8:4);i++){
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*24;
            var top=position().add(Math.cos(angle)*radius,22,Math.sin(angle)*radius);
            var hit=level().clip(new net.minecraft.world.level.ClipContext(top,top.add(0,-54,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,this));
            var at=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?new net.minecraft.world.phys.Vec3(top.x,getY(),top.z):hit.getLocation();
            MahouAttackEntity.rain(this,at.add(0,.06,0),random.nextBoolean());
        }
    }

    private void syncBossBar(ServerPlayer player, int phase) {
        net.everla.projectc.arkofcosmos.network.ArkNetwork.bossBar(player, bossEvent.getId(), phase);
    }

    @Override public void remove(RemovalReason reason) {
        for (ServerPlayer player : bossEvent.getPlayers()) syncBossBar(player, 0);
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); syncBossBar(player, phase()); bossEvent.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossEvent.removePlayer(player); syncBossBar(player, 0); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        healthWriteDepth++;
        try { super.readAdditionalSaveData(tag); }
        finally { healthWriteDepth--; }
        entityData.set(PHASE, (byte) net.minecraft.util.Mth.clamp(tag.contains("Phase") ? tag.getByte("Phase") : 1, 1, MAX_PHASE));
        phaseRecovering = phase() < MAX_PHASE && tag.getBoolean("PhaseRecovering");
        // Minimal summon NBT is not a saved boss; do not erase its constructor's entrance.
        int transition=tag.contains("PhaseTransitionTicks")?tag.getInt("PhaseTransitionTicks"):
                !tag.hasUUID("UUID")&&!tag.contains("Phase")?300:0;
        entityData.set(TRANSITION,net.minecraft.util.Mth.clamp(transition,0,300));
        nonPlayerInvulnerableUntil=level().getGameTime()+net.minecraft.util.Mth.clamp(tag.getInt("NonPlayerInvulnerabilityTicks"),0,10);
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
        bossEvent.setName(getDisplayName());
        bossEvent.setProgress(bossBarProgress());
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
        tag.putBoolean("PhaseRecovering", phaseRecovering);
        tag.putInt("PhaseTransitionTicks",phaseTransitionTicks());
        tag.putInt("NonPlayerInvulnerabilityTicks",(int)Math.max(0,nonPlayerInvulnerableUntil-level().getGameTime()));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
