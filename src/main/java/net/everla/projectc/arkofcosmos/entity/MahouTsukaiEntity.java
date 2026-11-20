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

public final class MahouTsukaiEntity extends PathfinderMob implements net.minecraft.world.entity.monster.Enemy, net.everla.projectc.arkofcosmos.combat.ProtectedBoss {
    private static final java.util.Map<Level,java.util.Set<MahouTsukaiEntity>> LOADED=new java.util.concurrent.ConcurrentHashMap<>();
    public static java.util.Collection<MahouTsukaiEntity> loaded(Level level){return LOADED.getOrDefault(level,java.util.Set.of());}
    @Override public void onAddedToWorld(){super.onAddedToWorld();LOADED.computeIfAbsent(level(),k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(this);}
    @Override public void onRemovedFromWorld(){var entries=LOADED.get(level());if(entries!=null){entries.remove(this);if(entries.isEmpty())LOADED.remove(level(),entries);}super.onRemovedFromWorld();}
    public static final int MAX_PHASE = 3;
    public static final double FUSION_SHIELD_RADIUS = 3.0;
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(MahouTsukaiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TRANSITION=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TOTAL_HEALTH=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DEFEAT_TICKS=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    private java.util.UUID targetId;
    private net.minecraft.world.phys.AABB clientArenaBounds;
    private boolean mistilteinnEnraged;
    private java.util.UUID mistilteinnProvoker;
    private boolean defeatLooted;
    private java.util.UUID shikigami;
    private final MahouDialogue dialogue = new MahouDialogue();
    private static final EntityDataAccessor<Integer> SPELL=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.INT);
    public int spell(){return entityData.get(SPELL);}
    void spell(int value){entityData.set(SPELL,value);}
    public boolean fusionShield(){return spell()==3;}
    public boolean raisedHands(){return !defeating() && (phase()==3&&phaseTransitionTicks()>0||spell()==4);}
    void shikigamiDefeated(java.util.UUID id){if(id.equals(shikigami)){shikigami=null;}}
    @Override public boolean isAlliedTo(Entity other){
        return other instanceof MahouInfernumSummonEntity m&&m.ownedBy(this)||other instanceof MahoragaWardenEntity w&&getUUID().equals(w.bossId())||super.isAlliedTo(other);
    }
    private long nonPlayerInvulnerableUntil;
    private final DistantOrbitTrigger distantOrbitTrigger=new DistantOrbitTrigger();
    private long nonPlayerPressureUntil,combatSteps,observedCombatSteps;
    private Vec3 observedPosition;
    private Vec3 requestedMovement=Vec3.ZERO;
    private long positionRewoundAt=Long.MIN_VALUE;
    private LivingEntity pressureAttacker;
    private LivingEntity orbitAnchor;
    private int healthWriteDepth, healthSyncDepth, translatedDamageDepth, damageSettlementDepth;
    private float vitality;
    private final double[] recentDamage=new double[20];
    private long damageBudgetTick=Long.MIN_VALUE;
    private double damageBudgetUsed;
    private float damageHealthFloor=Float.NEGATIVE_INFINITY;
    private boolean vitalityReady;
    private final MahouInfernumAI infernum = new MahouInfernumAI(this);
    private static final EntityDataAccessor<CompoundTag> INFERNUM=SynchedEntityData.defineId(MahouTsukaiEntity.class,EntityDataSerializers.COMPOUND_TAG);
    public CompoundTag infernumView(){return entityData.get(INFERNUM);}
    void publishInfernum(CompoundTag view){view.putBoolean("Invulnerable",infernum.invulnerable());view.putUUID("BossBarId",bossEvent.getId());entityData.set(INFERNUM,view);}
    MahouInfernumAI infernum(){return infernum;}
    public int musicPhase(){return level().isClientSide?infernumView().getInt("Stage")>=2?2:1:infernum.stage>=2?2:1;}
    public boolean tailAttack(){return level().isClientSide?infernumView().getInt("Stage")==4:infernum.tailAttack();}
    public boolean mistilteinnEnraged(){return mistilteinnEnraged;}
    public net.minecraft.world.phys.AABB arenaBounds(){return infernum.arenaBounds();}

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
        return Mob.createMobAttributes().add(net.everla.projectc.arkofcosmos.ArkOfCosmos.MAHOU_MAX_HEALTH.get(), 1440d).add(Attributes.MOVEMENT_SPEED, .2D).add(Attributes.FOLLOW_RANGE, 128).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).add(Attributes.ARMOR,20.0D).add(Attributes.ARMOR_TOUGHNESS,8.0D);
    }

    public void applyConfiguredHealth() {
        var attribute=getAttribute(Attributes.MAX_HEALTH);
        double maximum=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.value("boss.mahou.health")*(mistilteinnEnraged?.01:1);
        if(attribute==null||attribute.getBaseValue()==maximum)return;
        float ratio=getHealth()/getMaxHealth();attribute.setBaseValue(maximum);
        healthWriteDepth++;
        try { setHealth(defeating()?1:ratio*getMaxHealth()); }
        finally { healthWriteDepth--; }
    }
    @Override public double getAttributeValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return super.getAttributeValue(attribute==Attributes.MAX_HEALTH?net.everla.projectc.arkofcosmos.ArkOfCosmos.MAHOU_MAX_HEALTH.get():attribute); }
    @Override public net.minecraft.world.entity.ai.attributes.AttributeInstance getAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return super.getAttribute(attribute==Attributes.MAX_HEALTH?net.everla.projectc.arkofcosmos.ArkOfCosmos.MAHOU_MAX_HEALTH.get():attribute); }
    @Override public double getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return super.getAttributeBaseValue(attribute==Attributes.MAX_HEALTH?net.everla.projectc.arkofcosmos.ArkOfCosmos.MAHOU_MAX_HEALTH.get():attribute); }
    @Override protected void registerGoals() {}
    @Override public void setTarget(LivingEntity target){super.setTarget(target);targetId=target==null?null:target.getUUID();}
    @Override public LivingEntity getTarget(){
        var target=super.getTarget();
        if(target==null && targetId!=null && level() instanceof net.minecraft.server.level.ServerLevel server
            && server.getEntity(targetId) instanceof LivingEntity remembered && MahouTargeting.valid(this,remembered))return remembered;
        return target;
    }
    public boolean phaseRecovering() { return false; }
    public boolean blocksProjectileTracking(){
        if(defeating()||isInvulnerable())return true;
        return level().isClientSide?infernumView().getBoolean("Invulnerable"):infernum.invulnerable();
    }
    public float outgoingDamageMultiplier() { return mistilteinnEnraged ? 6f : 1f; }
    void rageVolleyDialogue() { if(mistilteinnEnraged)dialogue.enragedVolley(this); }
    void brothersDialogue() { dialogue.summonBrothers(this); }
    public boolean enrageWithMistilteinn(net.minecraft.world.entity.player.Player attacker) {
        if(level().isClientSide||mistilteinnEnraged||!isAlive()||defeating())return false;
        mistilteinnEnraged=true;mistilteinnProvoker=attacker.getUUID();
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        applyConfiguredHealth();
        return true;
    }
    public boolean defeating() { return entityData.get(DEFEAT_TICKS) > 0; }
    public int defeatTicks() { return entityData.get(DEFEAT_TICKS); }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(Float.isNaN(amount))return false;
        if(amount>0){
            if(translatedDamageDepth==0)observeNonPlayerPressure(source);
            if(!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))amount=Math.min(amount,50f);
        }
        if (immuneDamage(source)) return false;
        if (defeating()) { holdDefeatHealth(); return false; }
        if(amount>0&&(infernum.invulnerable()))return false;
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
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(SPELL,0); entityData.define(PHASE, (byte) 1);entityData.define(TRANSITION,0);entityData.define(INFERNUM,new CompoundTag());entityData.define(TOTAL_HEALTH,1f);entityData.define(DEFEAT_TICKS,0); }
    public int phase() { return entityData.get(PHASE); }
    void presentationPhase(int value){
        if(phase()==value)return;
        entityData.set(PHASE,(byte)value);
        for(var player:bossEvent.getPlayers())syncBossBar(player,value);
    }

    public int phaseTransitionTicks(){return entityData.get(TRANSITION);}
    public float totalHealthProgress(){return net.minecraft.util.Mth.clamp(getHealth()/getMaxHealth(),0,1);}


    @Override public net.minecraft.network.chat.Component getName() {
        var custom = getCustomName();
        return (custom != null ? custom.copy() : net.minecraft.network.chat.Component.translatable(
                "entity.arkofcosmos.mahou_tsukai.phase_" + phase()))
                .withStyle(style -> style.withFont(net.everla.projectc.arkofcosmos.util.MahouNameStyle.FONT)
                        .withColor(net.everla.projectc.arkofcosmos.util.MahouNameStyle.MARKER_COLOR));
    }

    public float damageMultiplier(){return 1f;}

    public float bossBarProgress(){return totalHealthProgress();}

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
                && (infernum.invulnerable() || level().getGameTime()<nonPlayerInvulnerableUntil);
    }
    public float combatVitality(){return vitalityReady?vitality:-1;}
    public boolean redirectHealthWrite(float health){
        if(!vitalityReady||level().isClientSide||healthWriteDepth>0||healthSyncDepth>0)return false;
        if(Float.isNaN(health))return true;
        if(health<vitality){ordinaryDamage(damageSources().generic(),vitality-health);return true;}
        return false;
    }
    public boolean distantOrbit(){return level()!=null&&!level().isClientSide&&distantOrbitTrigger!=null&&distantOrbitTrigger.active(level().getGameTime());}
    private boolean presentThreat(LivingEntity entity){
        // A zero-health source can still execute attacks before its removal.
        return entity!=null&&entity!=this&&!entity.isRemoved()&&entity.level()==level()
                &&!isAlliedTo(entity)&&distanceToSqr(entity)<=128*128
                &&(!(entity instanceof net.minecraft.world.entity.player.Player player)||!player.isCreative()&&!player.isSpectator());
    }
    LivingEntity orbitTarget(){
        return MahouTargeting.valid(this,getTarget())?getTarget():presentThreat(orbitAnchor)?orbitAnchor:null;
    }
    private void observeNonPlayerPressure(DamageSource source){
        if(!level().isClientSide&&!defeating()
                &&!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))
        {
            nonPlayerPressureUntil=level().getGameTime()+40;
            if(source.getEntity() instanceof LivingEntity attacker&&attacker!=this)pressureAttacker=attacker;
        }
    }
    void monitorCombatStall(){
        if(isAlive()&&!defeating()&&!isNoAi()&&level().getGameTime()<nonPlayerPressureUntil&&observedCombatSteps==combatSteps)infernum.tick();
        observedCombatSteps=combatSteps;
    }
    public void ordinaryDamage(DamageSource source,float amount){
        if(!(amount>0)||Float.isNaN(amount))return;
        observeNonPlayerPressure(source);
        // Strip bypass tags while retaining ownership and projectile attribution.
        var ordinary=new DamageSource(damageSources().generic().typeHolder(),source.getDirectEntity(),source.getEntity());
        translatedDamageDepth++;
        try { hurt(ordinary,Math.min(amount,50f)); }
        finally { translatedDamageDepth--; }
    }
    @Override public float getHealth() {
        return vitalityReady&&!level().isClientSide?vitality:super.getHealth();
    }
    @Override public void setHealth(float health) {
        if(!vitalityReady||level().isClientSide){super.setHealth(health);return;}
        if(redirectHealthWrite(health)||Float.isNaN(health)||blocksHealthWrite(health))return;
        float before=vitality;
        // Reduce the final commit after weapon damage floors; loading and healing stay unchanged.
        if(mistilteinnEnraged&&damageSettlementDepth>0&&health<before)health=before-(before-health)*.01f;
        if(damageSettlementDepth>0&&healthSyncDepth==0&&health<before){
            long now=level().getGameTime();
            if(damageBudgetTick==Long.MIN_VALUE||now<damageBudgetTick||now-damageBudgetTick>=20){java.util.Arrays.fill(recentDamage,0);damageBudgetUsed=0;}
            else for(long tick=damageBudgetTick+1;tick<=now;tick++){int slot=Math.floorMod(tick,20);damageBudgetUsed-=recentDamage[slot];recentDamage[slot]=0;}
            damageBudgetTick=now;
            double loss=Math.min(before-Math.max(health,damageHealthFloor),Math.max(0,getMaxHealth()/60d-Math.max(0,damageBudgetUsed)));
            recentDamage[Math.floorMod(now,20)]+=loss;damageBudgetUsed+=loss;health=before-(float)loss;
        }
        if(health<before&&health<=0&&!defeating()&&infernum.interceptDeath())health=Math.min(before,1f);
        vitality=net.minecraft.util.Mth.clamp(Math.max(health,damageHealthFloor),0,getMaxHealth());
        if(healthWriteDepth==0 && vitality<before){
            nonPlayerInvulnerableUntil=level().getGameTime()+10;
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
        if(level().isClientSide && INFERNUM.equals(key)){
            var view=infernumView();
            var bounds=view.contains("Arena")?net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena.load(view.getCompound("Arena")).bounds():null;
            net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary.clientBoundsChanged(level(),clientArenaBounds,bounds,view.getList("ArenaPlayers",net.minecraft.nbt.Tag.TAG_INT_ARRAY));
            clientArenaBounds=bounds;
        }
    }
    @Override protected void actuallyHurt(DamageSource source, float amount) {
        if (defeating()) return;
        if(amount>0&&(infernum.invulnerable()||blocksNonPlayerDamage(source)))return;
        if(Float.isNaN(amount))return;
        float before=getHealth(),previousFloor=damageHealthFloor;
        if(amount>0&&!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)){
            amount=Math.min(amount,50f);
            damageHealthFloor=Math.max(previousFloor,before-50f);
        }
        healthWriteDepth++;damageSettlementDepth++;
        try { super.actuallyHurt(source, amount); }
        finally { healthWriteDepth--;damageSettlementDepth--;damageHealthFloor=previousFloor; }
        if(getHealth()<before && !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source))
            nonPlayerInvulnerableUntil=level().getGameTime()+10;
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
    }

    /** Called only by the player-originated /kill command hook. */
    public void removeByPlayerCommand() {
        clearCombatEntities();
        discard();
    }

    @Override public void awardKillScore(Entity victim,int score,DamageSource source) {
        super.awardKillScore(victim,score,source);
        if(!level().isClientSide&&mistilteinnEnraged&&source.getEntity()==this
                &&victim instanceof net.minecraft.world.entity.player.Player
                &&victim.getUUID().equals(mistilteinnProvoker)) {
            clearCombatEntities();
            discard();
        }
    }

    @Override public void die(DamageSource source) {
        if(!level().isClientSide&&vitality>0)return;
        if (!defeating() && !level().isClientSide) {
            beginDefeat(source);
            return;
        }
        super.die(source);
    }

    private void holdDefeatHealth(){
        healthWriteDepth++;
        try{setHealth(1);}finally{healthWriteDepth--;}
    }
    private void beginDefeat(DamageSource source) {
        dialogue.beginDefeat(this);
        holdDefeatHealth();
        entityData.set(DEFEAT_TICKS, 200);
        spell(0);
        noPhysics = false;
        setNoGravity(false);
        setNoAi(true);
        holdDefeatHealth();
        setDeltaMovement(getDeltaMovement().add(0, -.08, 0));
        clearCombatEntities();
        bossEvent.removeAllPlayers();
        if (!defeatLooted) {
            defeatLooted = true;
            net.minecraft.world.entity.player.Player killer = source.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p : (getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player p ? p : null);
            int count = 3 + random.nextInt(5);
            var stack = new net.minecraft.world.item.ItemStack(net.everla.projectc.arkofcosmos.ArkOfCosmos.AURIC_BAR.get(), count);
            if (killer != null && killer.level() == level() && !killer.isRemoved()) {
                // Drop the reward at the killer's feet so it remains a visible world drop.
                var reward = new net.minecraft.world.entity.item.ItemEntity(level(), killer.getX(), killer.getY(), killer.getZ(), stack);
                reward.setPickUpDelay(10);
                reward.setGlowingTag(true);
                level().addFreshEntity(reward);
            } else {
                var reward = spawnAtLocation(stack);
                if (reward != null) reward.setGlowingTag(true);
            }
        }
    }

    private void clearCombatEntities() {
        if (level().isClientSide) return;
        infernum.clear();
        level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.class, getBoundingBox().inflate(192), e -> e.ownerId() == getId()).forEach(GalaxiaEntity::discardAttack);
        level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.ElementsEntity.class, getBoundingBox().inflate(192), e -> e.ownerId() == getId()).forEach(ElementsEntity::discardAttack);
    }

    @Override public void setPos(double x,double y,double z){
        // xOld/yOld/zOld are the interpolation snapshot used by generic position locks.
        boolean previousPosition=x==xOld&&y==yOld&&z==zOld;
        if(previousPosition&&level()!=null&&!level().isClientSide){
            if(distantOrbit())return;
            if(position().distanceToSqr(x,y,z)>1e-6)positionRewoundAt=level().getGameTime();
        }
        super.setPos(x,y,z);
    }
    void forceCombatPosition(Vec3 position){super.setPos(position.x,position.y,position.z);}
    @Override public void aiStep() {
        // Defeat is synchronized; both sides must stop using the combat-only noclip movement.
        noPhysics = !defeating();
        if (defeating()) setNoGravity(false);
        if(!level().isClientSide)syncVitality();
        super.aiStep();
        if (defeating()) {
            if (!level().isClientSide) {
                dialogue.defeatTick(this);
                holdDefeatHealth();
                setNoGravity(false);
                if(onGround()) setDeltaMovement(Vec3.ZERO);
                else setDeltaMovement(getDeltaMovement().multiply(.92,1,.92));
                int remaining = defeatTicks() - 1;
                entityData.set(DEFEAT_TICKS, remaining);
                if (remaining <= 0) { dialogue.disappeared(this); remove(RemovalReason.DISCARDED); }
            }
            return;
        }
        if (!level().isClientSide && isAlive()) {
            if(!isNoAi()) {infernum.tick();dialogue.tick(this);combatSteps++;}
            entityData.set(TOTAL_HEALTH,totalHealthProgress());
            bossEvent.setProgress(bossBarProgress());bossEvent.setName(getName());
        }
    }
    void finishInfernum(){beginDefeat(damageSources().generic());}
    void retreatInfernum(){dialogue.disappeared(this);remove(RemovalReason.DISCARDED);}
    public float retreatOpacity(){var view=infernumView();return view.contains("RetreatOpacity")?view.getFloat("RetreatOpacity"):1;}


    private void syncBossBar(ServerPlayer player, int phase) {
        net.everla.projectc.arkofcosmos.network.ArkNetwork.bossBar(player, bossEvent.getId(), phase);
    }

    @Override public void remove(RemovalReason reason) {
        if(reason.shouldDestroy()&&!level().isClientSide)clearCombatEntities();
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
        mistilteinnEnraged=tag.getBoolean("MistilteinnEnraged");
        mistilteinnProvoker=tag.hasUUID("MistilteinnProvoker")?tag.getUUID("MistilteinnProvoker"):null;
        bossEvent.setColor(mistilteinnEnraged?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.WHITE);
        // Minimal summon NBT is not a saved boss; do not erase its constructor's entrance.
        int transition=tag.contains("PhaseTransitionTicks")?tag.getInt("PhaseTransitionTicks"):
                !tag.hasUUID("UUID")&&!tag.contains("Phase")?300:0;
        entityData.set(TRANSITION,net.minecraft.util.Mth.clamp(transition,0,300));
        entityData.set(DEFEAT_TICKS,net.minecraft.util.Mth.clamp(tag.getInt("DefeatTicks"),0,200));
        defeatLooted=tag.getBoolean("DefeatLooted")||defeating();
        nonPlayerInvulnerableUntil=level().getGameTime()+net.minecraft.util.Mth.clamp(tag.getInt("NonPlayerInvulnerabilityTicks"),0,10);
        infernum.load(tag);
        dialogue.load(tag);
        entityData.set(TRANSITION,0);
        applyConfiguredHealth();
        if(!tag.contains("Infernum") && tag.contains("Phase")){
            float oldRatio=net.minecraft.util.Mth.clamp(getHealth()/getMaxHealth(),0,1);
            float migrated=phase()==1?.75f+.25f*oldRatio:phase()==2?.45f+.30f*oldRatio:.25f+.20f*oldRatio;
            healthWriteDepth++;
            try{setHealth(getMaxHealth()*migrated);}finally{healthWriteDepth--;}
        }
        targetId=tag.hasUUID("InfernumTarget")?tag.getUUID("InfernumTarget"):null;
        entityData.set(TOTAL_HEALTH,totalHealthProgress());
        bossEvent.setName(getName());bossEvent.setProgress(bossBarProgress());
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        infernum.save(tag);
        dialogue.save(tag);
        if(targetId!=null)tag.putUUID("InfernumTarget",targetId);
        if(shikigami!=null)tag.putUUID("Shikigami",shikigami);else tag.remove("Shikigami");
        tag.putByte("Phase", (byte) phase());
        tag.putBoolean("MistilteinnEnraged", mistilteinnEnraged);
        if(mistilteinnProvoker!=null)tag.putUUID("MistilteinnProvoker",mistilteinnProvoker);
        tag.putInt("PhaseTransitionTicks",phaseTransitionTicks());
        tag.putInt("NonPlayerInvulnerabilityTicks",(int)Math.max(0,nonPlayerInvulnerableUntil-level().getGameTime()));
        tag.putInt("DefeatTicks", defeatTicks());
        tag.putBoolean("DefeatLooted",defeatLooted);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
