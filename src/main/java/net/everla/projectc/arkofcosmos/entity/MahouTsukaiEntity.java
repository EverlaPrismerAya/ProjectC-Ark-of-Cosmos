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
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;

public final class MahouTsukaiEntity extends PathfinderMob implements net.minecraft.world.entity.monster.Enemy {
    public static final int MAX_PHASE = 3;
    public static final double FUSION_SHIELD_RADIUS = 3.0;
    private static final double RAIN_RADIUS = 24;
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(MahouTsukaiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TRANSITION=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TOTAL_HEALTH=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DEFEAT_TICKS=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    private boolean phaseRecovering;
    private boolean defeatLooted;
    private java.util.UUID shikigami;
    private boolean shikigamiSummoned;
    private final MahouDialogue dialogue = new MahouDialogue();
    private static final EntityDataAccessor<Integer> SPELL=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    public int spell(){return entityData.get(SPELL);}
    void spell(int value){entityData.set(SPELL,value);}
    public boolean fusionShield(){return spell()==3;}
    public boolean raisedHands(){return !defeating() && (phase()==3&&phaseTransitionTicks()>0||spell()==4);}
    public boolean shikigamiAlive(){
        if(shikigami==null||!(level() instanceof net.minecraft.server.level.ServerLevel server))return false;
        if(ShikigamiDeaths.get(server).consume(shikigami)){shikigami=null;return false;}
        var entity=server.getEntity(shikigami);
        // Unloaded summons remain authoritative until loaded or their death is observed.
        return entity==null||entity.isAlive();
    }
    private void summonShikigami(){
        if(phase()!=3||shikigamiSummoned||getHealth()>=getMaxHealth()*.5f)return;
        var summon=net.everla.projectc.arkofcosmos.ArkOfCosmos.MAHORAGA.get().create(level());
        if(summon==null)return;
        summon.bind(this);summon.setPos(position().add(4,0,0));
        if(level().addFreshEntity(summon)){shikigami=summon.getUUID();shikigamiSummoned=true;dialogue.summoned(this);if(getHealth()<=0)setHealth(1);}
    }
    void shikigamiDefeated(java.util.UUID id){if(id.equals(shikigami)){shikigami=null;dialogue.defeatedSummon(this);}}
    @Override public boolean isAlliedTo(Entity other){
        return other instanceof MahoragaWardenEntity w&&getUUID().equals(w.bossId())||super.isAlliedTo(other);
    }
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
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0D).add(Attributes.MOVEMENT_SPEED, .2D).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).add(Attributes.ARMOR,20.0D).add(Attributes.ARMOR_TOUGHNESS,8.0D);
    }

    @Override protected void registerGoals() {}
    public boolean phaseRecovering() { return phaseRecovering; }
    public boolean defeating() { return entityData.get(DEFEAT_TICKS) > 0; }
    public int defeatTicks() { return entityData.get(DEFEAT_TICKS); }
    void weaponInvulnerable(int updates) { weaponInvulnerableUntil=Math.max(weaponInvulnerableUntil,level().getGameTime()+(updates+2)/3); }
    @Override public boolean hurt(DamageSource source,float amount) {
        if (immuneDamage(source)) return false;
        if (defeating()) { setHealth(1); return false; }
        if(amount>0&&(phaseTransitionTicks()>0||shikigamiAlive()))return false;
        if(amount>0 && phase()==1 && !phaseRecovering && level().getGameTime()<weaponInvulnerableUntil)return false;
        if(amount>0&&blocksNonPlayerDamage(source))return false;
        return super.hurt(source,amount);
    }

    private boolean immuneDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_DROWNING)) return true;
        String id = source.getMsgId();
        return "poison".equals(id) || "wither".equals(id) || "witherSkull".equals(id)
                || "magic".equals(id) && hasEffect(net.minecraft.world.effect.MobEffects.POISON);
    }
    @Override public boolean isInWall() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(SPELL,0); entityData.define(PHASE, (byte) 1);entityData.define(TRANSITION,300);entityData.define(TOTAL_HEALTH,1f);entityData.define(DEFEAT_TICKS,0); }
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
                .withStyle(style -> style.withFont(net.everla.projectc.arkofcosmos.util.MahouNameStyle.FONT)
                        .withColor(net.everla.projectc.arkofcosmos.util.MahouNameStyle.MARKER_COLOR));
    }

    public float damageMultiplier() { return phase() == 1 ? .5f : phase() == 2 ? .67f : 1f; }

    public float bossBarProgress() {
        float threshold = phase() == 1 ? .5f : phase() == 2 ? .25f : 0f;
        return net.minecraft.util.Mth.clamp((getHealth() / getMaxHealth() - threshold) / (1f - threshold), 0f, 1f);
    }

    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        float multiplier = .75f;
        if (source.is(DamageTypeTags.IS_FIRE) || net.everla.projectc.arkofcosmos.combat.ElementsCombat.isResonanceDamage(this)) multiplier *= 1.15f;
        return super.getDamageAfterMagicAbsorb(source, amount) * damageMultiplier() * multiplier;
    }

    public boolean blocksNonPlayerDamage(DamageSource source) {
        return !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)
                && level().getGameTime()<nonPlayerInvulnerableUntil;
    }
    // Also used by the health-setter launch hook, including invokespecial callers.
    public boolean blocksHealthWrite(float health) {
        return vitalityReady && !level().isClientSide && healthWriteDepth==0 && health<getHealth()
                && (phaseTransitionTicks()>0 || shikigamiAlive() || level().getGameTime()<nonPlayerInvulnerableUntil);
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
            summonShikigami();
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
        if (defeating()) return;
        if(amount>0&&(phaseTransitionTicks()>0||shikigamiAlive()||blocksNonPlayerDamage(source)))return;
        float before=getHealth();
        healthWriteDepth++;
        try { super.actuallyHurt(source, amount); }
        finally { healthWriteDepth--; }
        if(getHealth()<before && !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))
            nonPlayerInvulnerableUntil=level().getGameTime()+10;
        // Evaluate actual health after armor, absorption and Forge damage events.
        beginPhaseRecovery();
        summonShikigami();
        if (phaseRecovering && getHealth() <= 0) setHealth(1);
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
    }

    @Override public void die(DamageSource source) {
        if (phase() == MAX_PHASE && !defeating() && !level().isClientSide) {
            beginDefeat(source);
            return;
        }
        super.die(source);
    }

    private void beginDefeat(DamageSource source) {
        dialogue.beginDefeat(this);
        setHealth(1);
        entityData.set(DEFEAT_TICKS, 200);
        phaseRecovering = false;
        spell(0);
        noPhysics = false;
        setNoGravity(false);
        setNoAi(true);
        setHealth(1);
        setDeltaMovement(getDeltaMovement().add(0, -.08, 0));
        clearCombatEntities();
        bossEvent.removeAllPlayers();
        if (!defeatLooted) {
            defeatLooted = true;
            net.minecraft.world.entity.player.Player killer = source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : (getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player p ? p : null);
            int count = 3 + random.nextInt(5);
            var stack = new net.minecraft.world.item.ItemStack(net.everla.projectc.arkofcosmos.ArkOfCosmos.AURIC_BAR.get(), count);
            if (killer != null && killer.level() == level() && !killer.isRemoved()) killer.getInventory().placeItemBackInInventory(stack);
            else spawnAtLocation(stack);
        }
    }

    private void clearCombatEntities() {
        if (level().isClientSide) return;
        level().getEntitiesOfClass(MahouAttackEntity.class, getBoundingBox().inflate(192), e -> e.ownedBy(this)).forEach(Entity::discard);
        level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.class, getBoundingBox().inflate(192), e -> e.ownerId() == getId()).forEach(Entity::discard);
        level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.ElementsEntity.class, getBoundingBox().inflate(192), e -> e.ownerId() == getId()).forEach(Entity::discard);
    }

    private void beginPhaseRecovery() {
        if (phase() >= MAX_PHASE || phaseRecovering) return;
        float threshold = phase() == 1 ? .5f : .25f;
        if (getHealth() < getMaxHealth() * threshold) phaseRecovering = true;
    }

    private void advancePhase(int next) {
        entityData.set(PHASE, (byte) next);
        phaseRecovering = false;
        if(next<=MAX_PHASE)entityData.set(TRANSITION,300);
        bossEvent.setProgress(1f);
        for (ServerPlayer player : bossEvent.getPlayers()) syncBossBar(player, next);
    }

    @Override public void aiStep() {
        if(!level().isClientSide)syncVitality();
        super.aiStep();
        if (defeating()) {
            if (!level().isClientSide) {
                dialogue.defeatTick(this);
                setHealth(1);
                setNoGravity(false);
                if(!onGround() && getDeltaMovement().y<=0){
                    var floor=level().clip(new net.minecraft.world.level.ClipContext(
                            position(),new Vec3(getX(),level().getMinBuildHeight(),getZ()),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                    if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){
                        setPos(getX(),floor.getLocation().y,getZ());
                        setDeltaMovement(Vec3.ZERO);
                    }
                }
                if(onGround()) setDeltaMovement(Vec3.ZERO);
                else setDeltaMovement(getDeltaMovement().multiply(.92,1,.92).add(0,-.08,0));
                int remaining = defeatTicks() - 1;
                entityData.set(DEFEAT_TICKS, remaining);
                if (remaining <= 0) { dialogue.disappeared(this); remove(RemovalReason.DISCARDED); }
            }
            return;
        }
        if (!level().isClientSide && isAlive()) {
            if(phase()==3&&!isNoAi()){
                var vortices=level().getEntitiesOfClass(MahouAttackEntity.class,getBoundingBox().inflate(160),e->e.kind()==MahouAttackEntity.VOID_VORTEX&&e.ownedBy(this));
                if(vortices.isEmpty()){dialogue.vortex(this);MahouAttackEntity.voidVortex(this,position().add(0,6,16));}
            }
            if(phaseTransitionTicks()>0){
                if(!isNoAi())transitionRain();
            }
            // transitionRain acquires the locked player before dialogue checks run.
            dialogue.tick(this);
            if (phaseTransitionTicks() > 0) entityData.set(TRANSITION,phaseTransitionTicks()-1);
            beginPhaseRecovery();
            summonShikigami();
            if (phaseRecovering) {
                setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * .05f));
                if (getHealth() >= getMaxHealth()) advancePhase(phase() + 1);
            }
            entityData.set(TOTAL_HEALTH,totalHealthProgress());
            bossEvent.setProgress(bossBarProgress());
            bossEvent.setName(getName());
            if (!isNoAi()) phaseOne.tick();
        }
    }

    private void transitionRain(){
        int remaining=phaseTransitionTicks();
        LivingEntity locked=getTarget();
        boolean validLock=locked!=null&&locked!=this&&locked.isAlive()&&!locked.isRemoved()&&locked.level()==level()
                &&!isAlliedTo(locked)&&EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(locked);
        if (!validLock) {
            LivingEntity player=level().getNearestPlayer(getX(),getY(),getZ(),64,
                    e->e instanceof net.minecraft.world.entity.player.Player p&&MahouPhaseOneAI.valid(this,p));
            if (player != null) { setTarget(player); locked=player; validLock=true; }
        }
        // The rain covers a horizontal circle; a locked target can leave even
        // beyond normal acquisition range. Check every tick, including the tail.
        if(validLock){
            double dx=locked.getX()-getX(),dz=locked.getZ()-getZ();
            if(dx*dx+dz*dz>RAIN_RADIUS*RAIN_RADIUS){
                dialogue.teleport(this);
                teleportTo(locked.getX(),locked.getBoundingBox().maxY+6,locked.getZ());
                getNavigation().stop();setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
        }
        if(remaining<40||remaining%10!=0)return;
        if((phase()==2||phase()==3)&&remaining%20==0){
            LivingEntity player=validLock?locked:level().getNearestPlayer(getX(),getY(),getZ(),48,
                    e->e instanceof net.minecraft.world.entity.player.Player p&&MahouPhaseOneAI.valid(this,p));
            if(player==null&&MahouPhaseOneAI.valid(this,getTarget()))player=getTarget();
            if(player==null&&MahouPhaseOneAI.valid(this,getLastHurtByMob()))player=getLastHurtByMob();
            if(player!=null){
                setTarget(player);
                double angle=random.nextDouble()*Math.PI*2;
                var at=position().add(Math.cos(angle)*12,12,Math.sin(angle)*12);
                ElementsEntity.rainStar(this,player,at);
            }
        }
        Vec3 rainCenter=validLock?locked.position():position();
        for(int i=0;i<(phase()==3?8:4);i++){
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*RAIN_RADIUS;
            var top=rainCenter.add(Math.cos(angle)*radius,22,Math.sin(angle)*radius);
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
        // The universal Ark damage path may request KILLED after die() returns.
        // Keep the Boss alive until its own defeat timer reaches zero.
        if (defeating() && reason != RemovalReason.DISCARDED) return;
        if(reason.shouldDestroy()&&shikigami!=null&&level() instanceof net.minecraft.server.level.ServerLevel server){
            var summon=server.getEntity(shikigami);if(summon!=null)summon.discard();
        }
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
        shikigami=tag.hasUUID("Shikigami")?tag.getUUID("Shikigami"):null;
        shikigamiSummoned=tag.getBoolean("ShikigamiSummoned");
        phaseRecovering = phase() < MAX_PHASE && tag.getBoolean("PhaseRecovering");
        // Minimal summon NBT is not a saved boss; do not erase its constructor's entrance.
        int transition=tag.contains("PhaseTransitionTicks")?tag.getInt("PhaseTransitionTicks"):
                !tag.hasUUID("UUID")&&!tag.contains("Phase")?300:0;
        entityData.set(TRANSITION,net.minecraft.util.Mth.clamp(transition,0,300));
        entityData.set(DEFEAT_TICKS,net.minecraft.util.Mth.clamp(tag.getInt("DefeatTicks"),0,200));
        nonPlayerInvulnerableUntil=level().getGameTime()+net.minecraft.util.Mth.clamp(tag.getInt("NonPlayerInvulnerabilityTicks"),0,10);
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
        bossEvent.setName(getName());
        bossEvent.setProgress(bossBarProgress());
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(shikigami!=null)tag.putUUID("Shikigami",shikigami);else tag.remove("Shikigami");
        tag.putBoolean("ShikigamiSummoned",shikigamiSummoned);
        tag.putByte("Phase", (byte) phase());
        tag.putBoolean("PhaseRecovering", phaseRecovering);
        tag.putInt("PhaseTransitionTicks",phaseTransitionTicks());
        tag.putInt("NonPlayerInvulnerabilityTicks",(int)Math.max(0,nonPlayerInvulnerableUntil-level().getGameTime()));
        tag.putInt("DefeatTicks", defeatTicks());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
