package net.everla.projectc.arkofcosmos.entity;

import java.util.Random;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.everla.projectc.arkofcosmos.calamity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Native-health ordinary-world encounter host. All authoritative simulation is
 * server-side; the compound view is a client rendering snapshot, not health authority. */
public final class SupremeCalamitasEntity extends Monster implements net.everla.projectc.arkofcosmos.combat.ProtectedBoss {
    private static final java.util.Map<Level, java.util.Set<SupremeCalamitasEntity>> LOADED = new java.util.concurrent.ConcurrentHashMap<>();
    public static java.util.Collection<SupremeCalamitasEntity> loaded(Level level) { return LOADED.getOrDefault(level, java.util.Set.of()); }
    static void forgetLevel(Level level) { LOADED.remove(level); }
    @Override public void onAddedToWorld() {
        super.onAddedToWorld();
        LOADED.computeIfAbsent(level(), key -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(this);
    }
    @Override public void onRemovedFromWorld() {
        var bosses = LOADED.get(level());
        if (bosses != null) { bosses.remove(this); if (bosses.isEmpty()) LOADED.remove(level(), bosses); }
        super.onRemovedFromWorld();
    }
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(SupremeCalamitasEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final SupremeCalamitasEncounter encounter = new SupremeCalamitasEncounter();
    private final SupremeCalamitasCombat combat = new SupremeCalamitasCombat();
    private final SupremeCalamitasAnimation animation = new SupremeCalamitasAnimation();
    private final net.minecraft.nbt.ListTag castSparks = new net.minecraft.nbt.ListTag();
    private final SupremeCalamitasBarrage barrage = new SupremeCalamitasBarrage();
    private final SupremeCalamitasShield shield = new SupremeCalamitasShield();
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.arkofcosmos.supreme_calamitas"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private SupremeCalamitasArena arena;
    private net.minecraft.world.phys.AABB clientArenaBounds;
    private CalamitasSummons pending, brotherCast;
    private Vec3 velocity = Vec3.ZERO, combatPlane = new Vec3(1, 0, 0);
    private double pixels = 16, movement = 1;
    public static final byte RUMBLE_CUE = 100;
    private int sourceAge, shieldHurtDelay, rumbleCue;
    public int rumbleCue() { return rumbleCue; }
    @Override public void handleEntityEvent(byte event) {
        if (event == RUMBLE_CUE) { if (level().isClientSide) rumbleCue++; }
        else super.handleEntityEvent(event);
    }
    private float opacity = 1;
    private boolean initialized, protectionBoost, finished, framePaused;
    private boolean vitalityReady;
    private float vitality;
    private int healthWriteDepth, healthSyncDepth;
    private float damageHealthFloor = Float.NEGATIVE_INFINITY;
    private long nonPlayerInvulnerableUntil;
    private java.util.UUID targetId;

    public SupremeCalamitasEntity(EntityType<? extends SupremeCalamitasEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true); setPersistenceRequired(); xpReward = 0;
        vitality = super.getHealth(); vitalityReady = true;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(CalamitasContent.MAX_HEALTH.get(), 2300)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 256);
    }
    @Override public double getAttributeValue(Attribute attribute) { return super.getAttributeValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public AttributeInstance getAttribute(Attribute attribute) { return super.getAttribute(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override public double getAttributeBaseValue(Attribute attribute) { return super.getAttributeBaseValue(attribute == Attributes.MAX_HEALTH ? CalamitasContent.MAX_HEALTH.get() : attribute); }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(VIEW, new CompoundTag()); }
    public CompoundTag view() { return entityData.get(VIEW); }
    /** Same authoritative six planes feed movement and the eventual arena renderer. */
    public net.minecraft.world.phys.AABB arenaBounds() {
        if (!isAlive() || isRemoved() || finished) return null;
        if (level().isClientSide) return "FINISHED".equals(view().getString("Phase")) ? null : clientArenaBounds;
        return arena == null ? null : arena.bounds();
    }
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (shield == null) return super.getDimensions(pose);
        if (level().isClientSide) {
            var snapshot = view(); var state = snapshot.getCompound("Shield"); double units = snapshot.getDouble("Pixels");
            if (units <= 0 || !state.contains("HitboxWidth")) return super.getDimensions(pose);
            return net.minecraft.world.entity.EntityDimensions.fixed((float)(state.getInt("HitboxWidth") / units), (float)(state.getInt("HitboxHeight") / units));
        }
        return initialized ? net.minecraft.world.entity.EntityDimensions.fixed((float)(shield.hitboxWidth() / pixels), (float)(shield.hitboxHeight() / pixels)) : super.getDimensions(pose);
    }
    private void refreshShieldDimensions(boolean keepCenter) {
        var size = getDimensions(getPose());
        if (size.width == getBbWidth() && size.height == getBbHeight()) return;
        Vec3 center = getBoundingBox().getCenter();
        refreshDimensions();
        if (keepCenter) setPos(center.x, center.y - getBbHeight() / 2, center.z);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (vitalityReady && !level().isClientSide && healthSyncDepth == 0 && DATA_HEALTH_ID.equals(key)) {
            setHealth(entityData.get(DATA_HEALTH_ID)); syncVitality();
        }
        super.onSyncedDataUpdated(key);
        if (shield != null && level().isClientSide && VIEW.equals(key)) {
            refreshShieldDimensions(false);
            var tag = view();
            clientArenaBounds = tag.contains("Arena") ? SupremeCalamitasArena.load(tag.getCompound("Arena")).bounds() : null;
        }
    }
    public boolean protectionBoost() { return protectionBoost; }
    public SupremeCalamitasEncounter.Phase phase() { return encounter.phase(); }
    @Override public void setTarget(LivingEntity target) { super.setTarget(target); targetId = target == null ? null : target.getUUID(); }
    @Override public LivingEntity getTarget() {
        var target = super.getTarget();
        // OdamaneHalo can clear the vanilla target slot; retain this encounter's valid player target.
        if (target == null && targetId != null && level() instanceof ServerLevel server
                && server.getEntity(targetId) instanceof Player player && validTarget(player)
                && net.everla.projectc.arkofcosmos.combat.HaloCompat.equipped(player, "halo_of_the_end")) return player;
        return target;
    }
    public static SupremeCalamitasEntity spawn(ServerLevel level, Vec3 position, LivingEntity target) {
        var boss = CalamitasContent.BOSS.get().create(level);
        if (boss == null) return null;
        boss.setPos(position); boss.setTarget(target); boss.initialize();
        if (!level.addFreshEntity(boss)) { boss.endBattle(false); return null; }
        return boss;
    }
    private Vec3 horizontalAim() {
        Vec3 direction = getTarget() == null ? combatPlane : getTarget().position().subtract(position());
        direction = new Vec3(direction.x, 0, direction.z);
        return CalamitasSpace.unit(direction, combatPlane);
    }
    private void initialize() {
        if (initialized || !(level() instanceof ServerLevel server)) return;
        initialized = true;
        pixels = ProjectCCommonConfig.calamitasValue("pixels_per_block");
        movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(100, 1150000 * ProjectCCommonConfig.calamitasValue("health_multiplier")));
        getAttribute(Attributes.ARMOR).setBaseValue(100 * ProjectCCommonConfig.calamitasValue("armor_multiplier"));
        setHealth(getMaxHealth());
        arena = new SupremeCalamitasArena(getBoundingBox().getCenter(), pixels);
        combatPlane = horizontalAim(); arena.beginBarrage(combatPlane, random.nextLong());
        CalamitasBattleData.get(server).begin(getUUID()); publish();
    }
    private boolean validTarget(LivingEntity target) {
        return target != null && target != this && target.isAlive() && target.level() == level() && !isAlliedTo(target)
                && (!(target instanceof Player p) || !p.isCreative() && !p.isSpectator());
    }
    @Override public void tick() {
        if (vitalityReady && !level().isClientSide) syncVitality();
        castSparks.clear();
        setDeltaMovement(Vec3.ZERO); super.tick();
        simulate(0);
    }
    boolean sourceStep() {
        if (shieldHurtDelay > 0) shieldHurtDelay--;
        framePaused = false; simulate(1); return !framePaused;
    }
    private void simulate(int frames) {
        if (!(level() instanceof ServerLevel server) || isDeadOrDying() || finished) return;
        initialize();
        var journal = CalamitasBattleData.get(server);
        if (!journal.active(getUUID())) { endBattle(false); return; }
        if (getTarget() == null && targetId != null && server.getEntity(targetId) instanceof LivingEntity loadedTarget) setTarget(loadedTarget);
        if (!validTarget(getTarget()) || distanceToSqr(getTarget()) > 200 * 200) {
            Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), 256,
                    e -> e instanceof Player p && validTarget(p));
            if (nearest != null || !validTarget(getTarget())) setTarget(nearest);
        }
        Random frameRandom = new Random(random.nextLong());
        for (int frame = 0; frame < frames && !finished; frame++) {
            if (!validTarget(getTarget())) {
                animation.idle();
                shield.beginFrame(); refreshShieldDimensions(true);
                opacity *= .935f; shield.despawn(opacity); fadeAttacks(false);
                velocity = new Vec3(0, 4 * (1 - Math.sin(Math.PI * opacity)), 0);
                setPos(position().add(velocity.scale(movement / pixels)));
                if (opacity <= 25 / 255f) endBattle(false);
                continue;
            }
            if (pending != null) {
                if (!pending.retry(this)) { framePaused = true; break; }
                pending = null;
            }
            CalamitasBrotherEntity.retryRevival(this);
            if (sourceAge == 0) say("SCalSummonText");
            sourceAge++;
            int hearts = journal.living(getUUID(), "heart"), seekers = journal.living(getUUID(), "seeker");
            if (hearts == 0 && journal.living(getUUID(), "worm") > 0) {
                // A head whose chunk is unloaded cannot run its own retirement
                // check. Commit every owned worm slot, with loaded effects once.
                for (var id : journal.members(getUUID(), false))
                    if (server.getEntity(id) instanceof CalamitasSepulcherEntity worm) worm.retire(true);
                journal.retireWorms(getUUID());
            }
            int brothers = journal.livingBrothers(getUUID());
            arena.stepAppearance((double)getHealth() / getMaxHealth(),
                    encounter.phase() == SupremeCalamitasEncounter.Phase.ACCEPTANCE && encounter.phaseFrame() > 0);
            arena.step(journal.living(getUUID(), "worm") > 0, seekers > 0, brothers > 0,
                    encounter.round() >= 4, encounter.phase() == SupremeCalamitasEncounter.Phase.ACCEPTANCE,
                    () -> getTarget().getBoundingBox(), (previous, proposed) ->
                            net.everla.projectc.arkofcosmos.combat.CalamitasArenaBoundary.shrink(this, previous, proposed));
            protectionBoost = !arena.bounds().intersects(getTarget().getBoundingBox());
            shield.step(encounter, combat, getHealth() / getMaxHealth(), getTarget().getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()), combatPlane);
            refreshShieldDimensions(true);
            var animationPhase = encounter.phase();
            int animationMode = combat.mode(), animationElapsed = combat.elapsed();
            double animationShot = combat.shotTimer();
            boolean animationSecond = encounter.secondStage(), animationWorm = journal.living(getUUID(), "worm") > 0;
            switch (encounter.phase()) {
                case BARRAGE -> {
                    velocity = velocity.scale(.95);
                    barrage.step(encounter.round(), arena.enrageSpeed(), frameRandom, shot -> {
                        var mapped = arena.map(shot, getTarget().getBoundingBox().getCenter());
                        var kind = switch (shot.kind()) {
                            case HELLBLAST -> CalamitasAttackEntity.Kind.HELLBLAST_BARRAGE;
                            case FIREBLAST -> CalamitasAttackEntity.Kind.FIREBLAST;
                            case GIGABLAST -> CalamitasAttackEntity.Kind.GIGABLAST;
                            case WAVE -> CalamitasAttackEntity.Kind.WAVE;
                            case MAELSTROM -> CalamitasAttackEntity.Kind.MAELSTROM;
                        };
                        emit(kind, mapped.position(), mapped.velocity().scale(movement), arena.attackX(), 0, 2);
                    });
                    if (encounter.round() == 4 && barrage.maelstromTelegraph() == 180)
                        CalamitasContent.sound(this, "vortex_warning", getTarget().getBoundingBox().getCenter(), 1, (float)Math.pow(2, .3));
                }
                case COMBAT -> {
                    if (combat.mode() == -1) combatPlane = horizontalAim();
                    combat.step(getBoundingBox().getCenter().scale(pixels), getTarget().getBoundingBox().getCenter().scale(pixels),
                            getTarget().getBbWidth() * pixels / 2, combatPlane, encounter.secondStage(), journal.living(getUUID(), "worm") > 0,
                            Math.min(1, getHealth() / getMaxHealth()), arena.enrageSpeed(), frameRandom, shot -> emit(
                                CalamitasAttackEntity.Kind.valueOf(shot.kind().name()), shot.position().scale(1 / pixels),
                                shot.velocity().scale(movement / pixels), shot.planeX(), shot.maximumSpeed() * movement / pixels, shot.variant()));
                    velocity = combat.velocity();
                    if (combat.cue() != null) CalamitasContent.sound(this, combat.cue().name().toLowerCase(java.util.Locale.ROOT));
                    if (combat.mode() == 2 && combat.elapsed() == 0) shield.lockDash(velocity);
                }
                case HEART_CAST -> velocity = velocity.scale(.94);
                case BROTHERS_CAST -> {
                    velocity = Vec3.ZERO;
                    int delay = SupremeCalamitasEncounter.BROTHERS_CAST_FRAMES - encounter.phaseFrame() - 1;
                    if (delay < 105 && brotherCast != null) {
                        var sample = new CompoundTag(); sample.putInt("Age", sourceAge); sample.putInt("Delay", delay);
                        Vec3 body = getBoundingBox().getCenter(); sample.putDouble("BodyX", body.x); sample.putDouble("BodyY", body.y); sample.putDouble("BodyZ", body.z);
                        for (int slot = 0; slot < 2; slot++) {
                            Vec3 center = brotherCast.center(slot); sample.putDouble("X" + slot, center.x);
                            sample.putDouble("Y" + slot, center.y); sample.putDouble("Z" + slot, center.z);
                        }
                        castSparks.add(sample);
                        while (castSparks.size() > 3) castSparks.remove(0);
                    }
                }
                case BROTHERS_WARNING, BROTHERS -> velocity = Vec3.ZERO;
                case TRANSITION, FINISHED -> velocity = Vec3.ZERO;
                case ACCEPTANCE -> velocity = new Vec3(velocity.x * .965,
                        velocity.y > -8 ? velocity.y - .165 : velocity.y, velocity.z * .965);
            }
            animation.step(animationPhase, animationMode, animationElapsed, animationSecond, animationWorm, animationShot, combat.shotTimer());
            Vec3 before = getBoundingBox().getCenter();
            if (encounter.phase() == SupremeCalamitasEncounter.Phase.ACCEPTANCE) {
                // Source gravity runs at 60 Hz; use native block collision for each
                // source step without also applying vanilla 20 Hz gravity.
                noPhysics = false;
                Vec3 step = velocity.scale(movement / pixels), old = position();
                move(net.minecraft.world.entity.MoverType.SELF, step);
                Vec3 actual = position().subtract(old);
                velocity = new Vec3(Math.abs(actual.x - step.x) > 1e-7 ? 0 : velocity.x,
                        Math.abs(actual.y - step.y) > 1e-7 ? 0 : velocity.y,
                        Math.abs(actual.z - step.z) > 1e-7 ? 0 : velocity.z);
            } else setPos(position().add(velocity.scale(movement / pixels)));
            Vec3 aim = getTarget().getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            setYRot((float)Math.toDegrees(Math.atan2(-aim.x, aim.z))); yBodyRot = getYRot(); yHeadRot = getYRot();
            if (encounter.phase() == SupremeCalamitasEncounter.Phase.COMBAT && combat.contact()) contact(before, getBoundingBox().getCenter());
            handle(encounter.advance(Math.max(1, getHealth()), getMaxHealth(), hearts, brothers, CalamitasBattleData.defeated(server)), frameRandom);
            if (encounter.phase() == SupremeCalamitasEncounter.Phase.ACCEPTANCE && encounter.phaseFrame() > 0)
                CalamitasBattleData.beginAcceptanceMusic(server, getUUID());
            if (encounter.phase() == SupremeCalamitasEncounter.Phase.BARRAGE
                    && encounter.phaseFrame() == SupremeCalamitasBarrage.RUMBLE_FRAME)
                level().broadcastEntityEvent(this, RUMBLE_CUE);
            if (encounter.phase() == SupremeCalamitasEncounter.Phase.TRANSITION && encounter.phaseFrame() == 100)
                CalamitasContent.sound(this, "transition_burst", getBoundingBox().getCenter(), 1.2f, 1);
        }
        bossBar.setProgress(Math.max(0, Math.min(1, getHealth() / getMaxHealth())));
        publish();
    }
    void sendCastSparks() {
        if (castSparks.isEmpty()) return;
        var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
        data.putInt("Owner", getId()); data.putUUID("OwnerUUID", getUUID()); data.putDouble("Pixels", pixels);
        data.put("Samples", castSparks.copy());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.effect(this, data);
        castSparks.clear();
    }
    private void emit(CalamitasAttackEntity.Kind kind, Vec3 position, Vec3 speed, Vec3 plane, double maximum, int variant) {
        String skill = switch (kind) {
            case HELLBLAST, HELLBLAST_BARRAGE -> "hellblast"; case DART -> "dart";
            case FIREBLAST -> "fireblast"; case GIGABLAST -> "gigablast"; case WAVE -> "wave"; case MAELSTROM -> "maelstrom";
            default -> throw new IllegalArgumentException("Invalid boss projectile");
        };
        int base = switch (kind) { case GIGABLAST -> 105; case DART -> 80; case WAVE -> 85; case MAELSTROM -> 1; default -> 95; };
        CalamitasAttackEntity.spawn(this, kind, position, speed, plane, pixels,
                ProjectCCommonConfig.calamitasDamage(skill, base), ProjectCCommonConfig.calamitasDamage("dart", 80), maximum, variant);
    }
    private void contact(Vec3 from, Vec3 to) {
        double halfWidth = getBbWidth() / 2, halfHeight = getBbHeight() / 2;
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, to).inflate(halfWidth, halfHeight, halfWidth), this::validTarget)) {
            var expanded = victim.getBoundingBox().inflate(halfWidth, halfHeight, halfWidth);
            if (expanded.contains(from) || expanded.contains(to) || expanded.clip(from, to).isPresent())
                victim.hurt(damageSources().mobAttack(this), (float)ProjectCCommonConfig.calamitasDamage("contact", 210));
        }
    }
    private void handle(SupremeCalamitasEncounter.Event event, Random random) {
        String line = switch (event) {
            case BARRAGE_STARTED -> "SCalBH" + encounter.round() + "Text";
            case SUMMON_HEARTS -> encounter.heartSummons() == 1 ? "SCalStartText" : "SCalSepulcher2Text";
            case SUMMON_BROTHERS -> "SCalBrothersText";
            case SECOND_STAGE -> "SCalPhase2Text";
            case SUMMON_SEEKERS -> "SCalSeekerRingText";
            case DESPAIR_6 -> "SCalDesparationText1";
            case DESPAIR_4 -> "SCalDesparationText2";
            case DESPAIR_2 -> "SCalDesparationText3";
            case SURRENDER -> "SCalDesparationText4";
            case ACCEPTANCE_TEXT_1 -> "SCalAcceptanceText1";
            case ACCEPTANCE_TEXT_2 -> "SCalAcceptanceText2";
            case ACCEPTANCE_TEXT_3 -> "SCalAcceptanceText3";
            default -> null;
        };
        if (line != null) say(line);
        switch (event) {
            case BARRAGE_STARTED -> arena.beginBarrage(horizontalAim(), random.nextLong());
            case BARRAGE_ENDED -> { CalamitasContent.sound(this, "barrage_end"); fadeAttacks(false); combat.pauseAttacks(60); }
            case HEART_CAST_STARTED -> CalamitasContent.sound(getTarget(), "heart_cast");
            case BROTHERS_CAST_STARTED -> brotherCast = CalamitasSummons.begin(CalamitasSummons.Kind.BROTHERS, getBoundingBox().getCenter(), combatPlane, pixels, random);
            case SUMMON_HEARTS -> {
                CalamitasContent.sound(getTarget(), "sepulcher_spawn");
                pending = CalamitasSummons.begin(CalamitasSummons.Kind.HEARTS, arena.origin(), combatPlane, pixels, random);
            }
            case SUMMON_BROTHERS -> {
                pending = brotherCast; brotherCast = null;
                float pitch = (float)Math.pow(2, -.2);
                CalamitasContent.sound(this, "cataclysm_spawn", pending.center(0), 1, pitch);
                CalamitasContent.sound(this, "catastrophe_spawn", pending.center(1), 1, pitch);
            }
            case SUMMON_SEEKERS -> {
                CalamitasContent.sound(this, "seeker_cast", getBoundingBox().getCenter(), 1, (float)Math.pow(2, (this.random.nextFloat() - .5f) * .12f));
                CalamitasContent.sound(getTarget(), "seeker_impact");
                pending = CalamitasSummons.begin(CalamitasSummons.Kind.SEEKERS, getBoundingBox().getCenter(), combatPlane, pixels, random);
            }
            case SECOND_STAGE -> { velocity = Vec3.ZERO; fadeAttacks(true); }
            case SURRENDER -> {
                fadeAttacks(true);
                Vec3 facing = horizontalAim();
                Vec3 center = arena.origin().add(0, 30 / pixels, 0);
                setPos(center.x, center.y - getBbHeight() / 2, center.z);
                // The original sprite faces left with spriteDirection=+1, so
                // its +10 impulse travels away from the player.
                velocity = facing.scale(-10).add(0, 7, 0);
                noPhysics = false;
            }
            case FINISHED -> endBattle(true);
            default -> { }
        }
    }
    private void say(String line) {
        boolean rematch = CalamitasBattleData.defeated((ServerLevel)level());
        var text = Component.translatable("dialogue.arkofcosmos.calamitas." + line + (rematch ? "Rematch" : ""))
                .withStyle(style -> style.withColor(0xFFA500));
        ((ServerLevel)level()).getServer().getPlayerList().broadcastSystemMessage(text, false);
    }
    private void fadeAttacks(boolean vortex) {
        for (var entity : ((ServerLevel)level()).getAllEntities())
            if (entity instanceof CalamitasAttackEntity attack && getUUID().equals(attack.ownerUUID())) attack.fadeForTransition(vortex);
    }
    /** Administrative cleanup and natural acceptance use the same isolated ownership end. */
    public void endBattle(boolean accepted) {
        if (!cleanupBattle()) return;
        if (!accepted) net.everla.projectc.arkofcosmos.network.ArkNetwork.calamitasMusic(getUUID(), false);
        if (accepted) { CalamitasBattleData.markDefeated((ServerLevel)level()); setHealth(0); super.die(damageSources().genericKill()); }
        else discard();
    }
    private boolean cleanupBattle() {
        if (finished || !(level() instanceof ServerLevel server)) return false;
        finished = true; CalamitasBattleData.get(server).end(getUUID()); bossBar.removeAllPlayers();
        var ownedSnapshot = new java.util.ArrayList<Entity>(); server.getAllEntities().forEach(ownedSnapshot::add);
        for (Entity entity : ownedSnapshot) {
            if (entity instanceof CalamitasAttackEntity attack && getUUID().equals(attack.ownerUUID())) attack.discardAttack();
            else if (entity instanceof CalamitasMinionEntity minion && getUUID().equals(minion.ownerUUID())) minion.discardMinion();
            else if (entity instanceof CalamitasBrotherEntity brother && getUUID().equals(brother.ownerUUID())) brother.discardBrother();
            else if (entity instanceof CalamitasSepulcherEntity worm && getUUID().equals(worm.ownerUUID())) worm.retire(false);
        }
        return true;
    }
    @Override public void remove(RemovalReason reason) {
        if (protectRemoval(reason)) return;
        if (initialized && !finished && !level().isClientSide
                && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED || reason == RemovalReason.CHANGED_DIMENSION)
                && cleanupBattle()) net.everla.projectc.arkofcosmos.network.ArkNetwork.calamitasMusic(getUUID(), false);
        if (bossBar != null) bossBar.removeAllPlayers();
        super.remove(reason);
    }
    @Override public boolean isAlliedTo(Entity other) {
        return other instanceof CalamitasMinionEntity minion && getUUID().equals(minion.ownerUUID())
                || other instanceof CalamitasBrotherEntity brother && getUUID().equals(brother.ownerUUID())
                || other instanceof CalamitasSepulcherEntity worm && getUUID().equals(worm.ownerUUID())
                || super.isAlliedTo(other);
    }
    @Override protected void playHurtSound(DamageSource source) {
        if (level().isClientSide || shieldHurtDelay > 0) return;
        shieldHurtDelay = 5 + random.nextInt(3);
        if (!isSilent()) CalamitasContent.sound(this, "shield_hurt");
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (Float.isNaN(amount) || damageProtected() || blocksNonPlayerDamage(source)
                || source.getEntity() != null && isAlliedTo(source.getEntity())) return false;
        if (!net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)) amount = Math.min(amount, 50f);
        return super.hurt(source, amount);
    }
    /** Phase protection, excluding the brief non-player hit cooldown. */
    public boolean phaseInvulnerable() {
        return level().isClientSide ? view().getBoolean("Invulnerable") : damageProtected();
    }
    private boolean damageProtected() {
        return finished || !(level() instanceof ServerLevel server) || pending != null
                || CalamitasBattleData.get(server).living(getUUID(), "worm") > 0
                || encounter.invulnerable(CalamitasBattleData.get(server).living(getUUID(), "heart"), CalamitasBattleData.get(server).living(getUUID(), "seeker"));
    }
    private boolean blocksNonPlayerDamage(DamageSource source) {
        return !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source)
                && level().getGameTime() < nonPlayerInvulnerableUntil;
    }
    @Override public float combatVitality() { return vitalityReady ? vitality : -1; }
    @Override public float getHealth() { return vitalityReady && !level().isClientSide ? vitality : super.getHealth(); }
    @Override public boolean blocksHealthWrite(float health) {
        return vitalityReady && !level().isClientSide && healthWriteDepth == 0 && !finished && health < vitality
                && (damageProtected() || level().getGameTime() < nonPlayerInvulnerableUntil);
    }
    @Override public boolean redirectHealthWrite(float health) {
        if (!vitalityReady || level().isClientSide || healthWriteDepth > 0 || healthSyncDepth > 0 || finished) return false;
        if (Float.isNaN(health)) return true;
        if (health < vitality) { ordinaryDamage(damageSources().generic(), vitality - health); return true; }
        return false;
    }
    @Override public void ordinaryDamage(DamageSource source, float amount) {
        if (!(amount > 0) || Float.isNaN(amount)) return;
        hurt(new DamageSource(damageSources().generic().typeHolder(), source.getDirectEntity(), source.getEntity()), Math.min(amount, 50f));
    }
    private void syncVitality() {
        healthSyncDepth++;
        try { super.setHealth(vitality); } finally { healthSyncDepth--; }
    }
    @Override public void setHealth(float health) {
        if (!vitalityReady || level().isClientSide) { super.setHealth(health); return; }
        if (redirectHealthWrite(health) || Float.isNaN(health) || blocksHealthWrite(health)) return;
        if (!finished && encounter != null && health < vitality && healthWriteDepth == 0) health = Math.max(health, (float)encounter.damageFloor(getMaxHealth()));
        vitality = net.minecraft.util.Mth.clamp(Math.max(health, damageHealthFloor), 0, getMaxHealth());
        syncVitality();
    }
    @Override protected void actuallyHurt(DamageSource source, float amount) {
        if (Float.isNaN(amount) || damageProtected() || blocksNonPlayerDamage(source)) return;
        float before = getHealth(), previousFloor = damageHealthFloor;
        boolean nonPlayer = !net.everla.projectc.arkofcosmos.combat.WeaponUser.playerOwnedDamage(source);
        damageHealthFloor = Math.max(previousFloor, (float)encounter.damageFloor(getMaxHealth()));
        if (nonPlayer) { amount = Math.min(amount, 50f); damageHealthFloor = Math.max(damageHealthFloor, before - 50f); }
        healthWriteDepth++;
        try { super.actuallyHurt(source, amount); }
        finally { healthWriteDepth--; damageHealthFloor = previousFloor; }
        if (nonPlayer && getHealth() < before) nonPlayerInvulnerableUntil = level().getGameTime() + 10;
    }
    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        double dr = protectionBoost && encounter.phase() != SupremeCalamitasEncounter.Phase.ACCEPTANCE ? .9999 : encounter.round() == 5 ? .3 : .25;
        return (float)(super.getDamageAfterMagicAbsorb(source, damage) * (1 - dr));
    }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide && vitalityReady && !finished) return;
        super.die(source);
    }
    public boolean protectingCombat() { return !level().isClientSide && vitalityReady && !finished; }
    public boolean protectRemoval(RemovalReason reason) { return protectingCombat() && reason.shouldDestroy(); }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); net.everla.projectc.arkofcosmos.network.ArkNetwork.fourthBossBar(player, bossBar.getId(), 4, 1); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); net.everla.projectc.arkofcosmos.network.ArkNetwork.fourthBossBar(player, bossBar.getId(), 0, 0); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    private CompoundTag encounterTag() {
        var s = encounter.save(); var tag = new CompoundTag();
        tag.putString("Phase", s.phase().name()); tag.putInt("Round", s.round()); tag.putInt("Frame", s.phaseFrame()); tag.putInt("Hearts", s.heartSummons());
        tag.putBoolean("Brothers", s.brothersSummoned()); tag.putBoolean("Seekers", s.seekersSummoned()); tag.putBoolean("Second", s.secondStage()); tag.putInt("Despair", s.despair()); return tag;
    }
    private void publish() {
        if (arena == null || !(level() instanceof ServerLevel server)) return;
        var tag = encounterTag(); tag.putInt("SourceAge", sourceAge); tag.put("Arena", arena.save()); tag.put("Combat", combat.save()); tag.put("Shield", shield.save()); tag.put("Animation", animation.save());
        tag.putBoolean("Invulnerable", damageProtected());
        if (validTarget(getTarget())) {
            var target = getTarget(); var center = target.getBoundingBox().getCenter();
            tag.putUUID("Target", target.getUUID()); tag.putInt("TargetId", target.getId());
            tag.putDouble("TargetX", center.x); tag.putDouble("TargetY", center.y); tag.putDouble("TargetZ", center.z);
        }
        tag.putDouble("Pixels", pixels);
        tag.putFloat("Opacity", opacity); tag.putBoolean("ProtectionBoost", protectionBoost); tag.putBoolean("PendingSummon", pending != null); entityData.set(VIEW, tag);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("Initialized", initialized); tag.putBoolean("Finished", finished);
        if (!initialized) return;
        tag.put("CalamitasEncounter", encounterTag()); tag.put("CalamitasArena", arena.save()); tag.put("CalamitasCombat", combat.save()); tag.put("CalamitasShield", shield.save()); tag.put("CalamitasAnimation", animation.save());
        tag.putInt("BarrageFrames", barrage.completedFrames()); tag.putInt("BarrageGate", barrage.shotGate()); tag.putInt("VortexTelegraph", barrage.maelstromTelegraph());
        tag.putInt("SourceAge", sourceAge); tag.putInt("ShieldHurtDelay", shieldHurtDelay); tag.putDouble("Movement", movement); tag.putFloat("Opacity", opacity); tag.putBoolean("ProtectionBoost", protectionBoost);
        tag.putInt("NonPlayerInvulnerabilityTicks", (int)Math.max(0, nonPlayerInvulnerableUntil - level().getGameTime()));
        tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
        tag.putDouble("PlaneX", combatPlane.x); tag.putDouble("PlaneZ", combatPlane.z);
        if (targetId != null) tag.putUUID("CalamitasTarget", targetId);
        if (pending != null) tag.put("PendingSummon", pending.save()); if (brotherCast != null) tag.put("BrotherCast", brotherCast.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        castSparks.clear();
        healthWriteDepth++;
        try { super.readAdditionalSaveData(tag); } finally { healthWriteDepth--; }
        setNoGravity(true); initialized = tag.getBoolean("Initialized"); finished = tag.getBoolean("Finished");
        nonPlayerInvulnerableUntil = level().getGameTime() + net.minecraft.util.Mth.clamp(tag.getInt("NonPlayerInvulnerabilityTicks"), 0, 10);
        if (!initialized) return;
        var s = tag.getCompound("CalamitasEncounter");
        encounter.restore(new SupremeCalamitasEncounter.State(SupremeCalamitasEncounter.Phase.valueOf(s.getString("Phase")), s.getInt("Round"), s.getInt("Frame"),
                s.getInt("Hearts"), s.getBoolean("Brothers"), s.getBoolean("Seekers"), s.getBoolean("Second"), s.getInt("Despair")));
        noPhysics = encounter.phase() != SupremeCalamitasEncounter.Phase.ACCEPTANCE;
        arena = SupremeCalamitasArena.load(tag.getCompound("CalamitasArena")); pixels = arena.pixelsPerBlock();
        animation.load(tag.getCompound("CalamitasAnimation")); combat.load(tag.getCompound("CalamitasCombat")); shield.load(tag.getCompound("CalamitasShield")); barrage.restore(tag.getInt("BarrageFrames"), tag.getInt("BarrageGate"), tag.getInt("VortexTelegraph"));
        sourceAge = tag.getInt("SourceAge"); shieldHurtDelay = tag.getInt("ShieldHurtDelay"); movement = tag.getDouble("Movement"); opacity = tag.getFloat("Opacity"); protectionBoost = tag.getBoolean("ProtectionBoost");
        velocity = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ")); combatPlane = new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ"));
        pending = tag.contains("PendingSummon") ? CalamitasSummons.load(tag.getCompound("PendingSummon")) : null;
        targetId = tag.hasUUID("CalamitasTarget") ? tag.getUUID("CalamitasTarget") : null;
        brotherCast = tag.contains("BrotherCast") ? CalamitasSummons.load(tag.getCompound("BrotherCast")) : null;
        if (sourceAge < 0 || shieldHurtDelay < 0 || shieldHurtDelay > 7 || !Double.isFinite(movement) || movement <= 0 || !Float.isFinite(opacity) || opacity < 0 || opacity > 1
                || !Double.isFinite(velocity.lengthSqr()) || !Double.isFinite(combatPlane.lengthSqr()) || Math.abs(combatPlane.lengthSqr() - 1) > 1e-6)
            throw new IllegalArgumentException("Invalid Calamitas boss save");
        int expected = (encounter.round() - 1) * SupremeCalamitasBarrage.DURATION
                + (encounter.phase() == SupremeCalamitasEncounter.Phase.BARRAGE ? encounter.phaseFrame() : SupremeCalamitasBarrage.DURATION);
        if (barrage.completedFrames() != expected || getMaxHealth() < 100) throw new IllegalArgumentException("Calamitas phase and barrage clocks disagree");
        refreshShieldDimensions(false); publish();
    }
}
