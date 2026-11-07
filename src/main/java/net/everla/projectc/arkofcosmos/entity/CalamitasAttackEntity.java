package net.everla.projectc.arkofcosmos.entity;

import java.util.UUID;
import net.everla.projectc.arkofcosmos.calamity.CalamitasSpace;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Native-damage projectiles derived from the pinned ordinary-world SCal source.
 * Velocity is blocks/source update, lifetimes count source updates, not MC ticks.
 */
public final class CalamitasAttackEntity extends Entity {
    public enum Kind { HELLBLAST, HELLBLAST_BARRAGE, DART, FIREBLAST, GIGABLAST, WAVE, MAELSTROM, SOUL, FIST, SLASH }
    private static final EntityDataAccessor<CompoundTag> VIEW = SynchedEntityData.defineId(CalamitasAttackEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final Kind kind;
    private UUID owner, target;
    private Vec3 velocity = Vec3.ZERO, planeX = new Vec3(1, 0, 0);
    private int age, remaining, variant = 2, still = 90;
    private long spawnedAt;
    private boolean splitChild;
    private long arenaFade = -1;
    private float arenaFadeOpacity;
    private boolean witchAttack;
    private long witchSession;
    private long witchFade = -1;
    private float witchFadeOpacity;
    private double pixels = 16, movement = 1, damage, dartDamage, maximumSpeed, speedAdd, speedLimit = 23, soulPhase;
    private float opacity, scale = 1;
    private boolean withinRange, ending, suppressSplit, invalidSave;
    private int hand;
    private Vec3 shootVelocity = Vec3.ZERO;
    private Vec3 orbAxis = new Vec3(0, 0, -1);
    private boolean orbPartnerAlive, slashExplode;
    private java.util.Map<net.minecraft.server.level.ServerPlayer, net.minecraft.nbt.ListTag> brotherSamples;

    public CalamitasAttackEntity(EntityType<? extends CalamitasAttackEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        spawnedAt = level.getGameTime();
        noPhysics = true;
        setNoGravity(true);
    }
    public static CalamitasAttackEntity spawn(LivingEntity boss, Kind kind, Vec3 position,
            Vec3 velocity, Vec3 planeX, double pixels, double damage, double dartDamage, double maximumSpeed, int variant) {
        var entity = CalamitasContent.ATTACKS.get(kind).get().create(boss.level());
        if (entity == null) throw new IllegalStateException("Cannot create Calamitas projectile");
        entity.owner = boss.getUUID();
        entity.witchAttack = boss instanceof BrimstoneWitchEntity;
        if(boss instanceof BrimstoneWitchEntity witch)entity.witchSession=witch.combatSession();
        if(entity.witchAttack) CalamitasBattleData.get((ServerLevel)boss.level()).begin(entity.owner);
        if (boss instanceof Mob mob && mob.getTarget() != null) entity.target = mob.getTarget().getUUID();
        entity.setPos(position);
        entity.velocity = velocity; entity.planeX = planeX;
        entity.pixels = pixels; entity.damage = damage; entity.dartDamage = dartDamage;
        entity.movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
        entity.soulPhase = entity.getId() * 1.1;
        entity.maximumSpeed = maximumSpeed; entity.variant = variant;
        entity.remaining = switch (kind) {
            case HELLBLAST -> 255; case HELLBLAST_BARRAGE -> 1500; case DART -> 690;
            case FIREBLAST -> 150; case GIGABLAST -> 120; case WAVE -> 1200; case MAELSTROM -> 36000; case SOUL -> 120;
            case FIST -> 1200; case SLASH -> 1500;
        };
        entity.opacity = kind == Kind.HELLBLAST || kind == Kind.DART ? 1 : 0;
        if (kind == Kind.MAELSTROM) entity.scale = .1f;
        var journal = CalamitasBattleData.get((ServerLevel) boss.level());
        if (!journal.add(entity.owner, entity.getUUID(), "attack")) { entity.discardAttack(); return entity; }
        entity.publish();
        if (!boss.level().addFreshEntity(entity)) {
            journal.forget(entity.owner, entity.getUUID());
            entity.discardAttack();
        } else CalamitasSimulation.joined(entity);
        return entity;
    }
    public static CalamitasAttackEntity spawnBrother(LivingEntity boss, Kind kind, Vec3 position,
            Vec3 velocity, Vec3 planeX, double pixels, double damage, int variant, int hand) {
        if (kind != Kind.FIST && kind != Kind.SLASH) throw new IllegalArgumentException("Brother projectile required");
        var entity = spawn(boss, kind, position, velocity, planeX, pixels, damage, damage, 0, variant);
        entity.hand = hand; entity.publish();
        return entity;
    }
    public Kind kind() { return kind; }
    public UUID ownerUUID() { return owner; }
    public CompoundTag view() { return entityData.get(VIEW); }
    @Override protected void defineSynchedData() { entityData.define(VIEW, new CompoundTag()); }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    @Override public void tick() {
        super.tick();
        if(witchAttack && level() instanceof ServerLevel server) {
            Entity npc=owner==null?null:server.getEntity(owner);
            if(npc instanceof BrimstoneWitchEntity witch && (!witch.fighting() || witchSession!=witch.combatSession() || distanceToSqr(witch)>4096)
                || npc==null && owner!=null && !CalamitasBattleData.get(server).active(owner)) fadeWitch();
            if(witchFade>=0) {
                opacity=witchFadeOpacity*Math.max(0,1-(server.getGameTime()-witchFade)/10f);
                setPos(position().add(velocity.scale(3))); publish();
                if(server.getGameTime()-witchFade>=10) discardAttack();
                return;
            }
        }
        // Resolve command-spawn ownership before choosing the clock. Otherwise the
        // first native tick and the level scheduler can both advance three frames.
        if (owner == null) simulate(0);
        if (!isRemoved()) simulate(level() instanceof ServerLevel server && CalamitasSimulation.managed(server, owner) ? 0 : 3);
    }
    void sourceStep() { simulate(1); }
    private void simulate(int frames) {
        if (!(level() instanceof ServerLevel server)) return;
        var journal = CalamitasBattleData.get(server);
        if (invalidSave) { discardAttack(); return; }
        if (owner == null) {
            LivingEntity commandOwner = server.getEntitiesOfClass(SupremeCalamitasEntity.class, getBoundingBox().inflate(256), Entity::isAlive)
                    .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (commandOwner == null) commandOwner = level().getNearestPlayer(this, 256);
            if (commandOwner == null) { discardAttack(); return; }
            owner = commandOwner.getUUID(); journal.begin(owner); journal.add(owner, getUUID(), "attack");
        }
        if (owner == null || !journal.active(owner)) { discardAttack(); return; }
        Entity loadedOwner = server.getEntity(owner);
        // A chunk unload is not an observed owner death. Do not advance/hit while unresolved.
        if (!(loadedOwner instanceof LivingEntity boss) || !boss.isAlive()) return;
        if (frames == 0) return;
        if (kind == Kind.SLASH || kind == Kind.FIST) brotherSamples = new java.util.LinkedHashMap<>();
        try {
            for (int sourceFrame = 0; sourceFrame < frames && !isRemoved(); sourceFrame++)
                for (int update = 0; update < updatesPerFrame() && !isRemoved(); update++)
                    step(boss);
        } finally {
            if (brotherSamples != null) {
                try {
                    for (var entry : brotherSamples.entrySet())
                        net.everla.projectc.arkofcosmos.network.ArkNetwork.playerEffect(entry.getKey(), brotherPacket(boss, entry.getValue()));
                } finally { brotherSamples = null; }
            }
        }
        // Managed one-frame calls are published together after all three frames.
        if (frames > 1 && !isRemoved()) publish();
    }
    private int updatesPerFrame() {
        return switch (kind) {
            case HELLBLAST_BARRAGE -> 2;
            case FIST -> variant == 0 || variant == 1 ? 3 : 1;
            case SLASH -> variant == 1 ? 3 : variant == 2 ? 2 : variant == 3 ? 6 : 1;
            default -> 1;
        };
    }

    public void fadeWitch() {
        if(witchFade<0) { witchFade=level().getGameTime(); witchFadeOpacity=opacity; suppressSplit=true; }
    }
    private LivingEntity target(LivingEntity boss) {
        if(witchAttack) return target!=null && level() instanceof ServerLevel server && server.getEntity(target) instanceof LivingEntity living && living.isAlive()?living:null;
        if (kind == Kind.GIGABLAST) {
            Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), 256,
                    e -> e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() && !boss.isAlliedTo(p));
            if (nearest != null) return nearest;
        }
        if (target != null && level() instanceof ServerLevel server
                && server.getEntity(target) instanceof LivingEntity living && living.isAlive()
                && (!(living instanceof Player p) || !p.isCreative() && !p.isSpectator())) return living;
        if (kind == Kind.MAELSTROM && boss instanceof Mob mob && mob.getTarget() != null) {
            target = mob.getTarget().getUUID(); return mob.getTarget();
        }
        return null;
    }

    private boolean arenaBoundary(LivingEntity boss, Vec3 next) {
        if (!(boss instanceof SupremeCalamitasEntity calamitas)) return false;
        var bounds = calamitas.arenaBounds();
        if (bounds == null) return false;
        int action = net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena.projectileBoundary(
                bounds, position(), next, collisionRadius(), pixels, terminalStraight(), splitChild,
                level().getGameTime() - spawnedAt);
        if (action == 2) { discardAttack(); return true; }
        if (arenaFade < 0 && action == 1) {
            arenaFade = level().getGameTime(); arenaFadeOpacity = opacity; suppressSplit = true;
        }
        if (arenaFade < 0) return false;
        opacity = arenaFadeOpacity * Math.max(0, 1 - (level().getGameTime() - arenaFade) / 5f);
        setPos(next);
        if (level().getGameTime() - arenaFade >= 5) discardAttack();
        return true;
    }
    private void step(LivingEntity boss) {
        if(witchAttack && (witchFade>=0 || distanceToSqr(boss)>4096)) { fadeWitch(); return; }
        if (arenaFade >= 0 && arenaBoundary(boss, position().add(velocity))) return;
        if (age == 0 && (kind == Kind.HELLBLAST || kind == Kind.FIREBLAST || kind == Kind.GIGABLAST)) flameSound();
        if (kind == Kind.MAELSTROM && age == 0) CalamitasContent.sound(this, "vortex_spawn");
        LivingEntity targetEntity = target(boss);
        Vec3 targetPosition = targetEntity == null ? null : targetEntity.getBoundingBox().getCenter();
        switch (kind) {
            case HELLBLAST -> {
                if (remaining < 51) opacity = Math.max(0, opacity - .02f);
                if (velocity.length() * pixels < 18 * movement) velocity = velocity.scale(1.03);
            }
            case HELLBLAST_BARRAGE -> opacity = remaining < 60 ? remaining / 60f : Mth.clamp(1 - (remaining - 1440) / 60f, 0, 1);
            case DART -> {
                if (velocity.length() < maximumSpeed) velocity = velocity.scale(Math.min(1.01, maximumSpeed / Math.max(1e-12, velocity.length())));
                if (remaining < 60) opacity = remaining / 60f;
                if (variant == 3) {
                    scale = .85f; if (remaining > 600) velocity = velocity.scale(1.015);
                    flameParticles(boss, 4);
                }
            }
            case WAVE -> {
                flameParticles(boss, 0);
                velocity = new Vec3(velocity.x, -5 * Math.sin((age + 1) / 5d) / pixels * movement, velocity.z);
                opacity = remaining < 60 ? remaining / 60f : Mth.clamp(1 - (remaining - 1140) / 30f, 0, 1);
            }
            case FIREBLAST, GIGABLAST -> fireball(boss, targetPosition);
            case MAELSTROM -> vortex(boss, targetPosition);
            case FIST -> fist(boss);
            case SLASH -> slash(boss);
            case SOUL -> {
                double phase = age / 27d + soulPhase;
                double horizontal = (Math.sin(phase) + Math.cos(Math.E * phase)) * 4;
                double t = Math.sin(age / 23d + soulPhase) * .5 + .5;
                t = t * t * (3 - 2 * t);
                Vec3 ideal = planeX.scale(horizontal).add(0, 3 + 6 * t, 0).scale(movement / pixels);
                velocity = velocity.lerp(ideal, .075);
                opacity = Mth.clamp(age / 15f, 0, 1) * Mth.clamp(remaining / 25f, 0, 1);
            }
        }
        Vec3 previous = position();
        Vec3 next = previous.add(velocity);
        if(witchAttack && next.distanceToSqr(boss.position())>4096) { fadeWitch(); return; }
        if (arenaBoundary(boss, next)) return;
        boolean canHit = kind == Kind.FIST ? opacity >= 1 || variant >= 3
                : kind == Kind.SLASH ? opacity >= 1 && variant < 4 || slashExplode && variant >= 4 : opacity >= 1;
        if (canHit && damage > 0) collide(boss, previous, next);
        if (isRemoved()) return;
        setPos(next);
        age++;
        if (--remaining <= 0) {
            impactSound();
            if (!suppressSplit && (kind == Kind.FIREBLAST || kind == Kind.GIGABLAST)) split(boss);
            discardAttack();
        }
    }
    private boolean brotherAlive(String role) {
        return CalamitasBattleData.get((ServerLevel)level()).living(owner, role) > 0;
    }
    private void fist(LivingEntity boss) {
        if (variant >= 3) {
            if (remaining > 240) {
                shootVelocity = velocity.scale(.4); remaining = 240; velocity = velocity.scale(5.5);
                orbAxis = CalamitasSpace.fistAxis(shootVelocity, planeX);
                orbPartnerAlive = brotherAlive("catastrophe");
            } else { brotherParticles(true, false, 3); velocity = velocity.scale(.945); }
            if (remaining == 205) brotherParticles(true, false, 4);
            if (remaining <= 200 && age % 3 == 0) {
                CalamitasContent.sound(this, "shot", position(), 1.2f, (float)Math.pow(2, .55));
                int count = orbPartnerAlive ? 1 : 3;
                for (int index = 0; index < count; index++) {
                    Vec3 ray = CalamitasSpace.fistRay(shootVelocity, orbAxis, index);
                    Vec3 offset = net.everla.projectc.arkofcosmos.calamity.CalamitasSpace.direction(random).scale(ray.length() * 1.5 * (5 + random.nextDouble() * 10));
                    Vec3 speed = ray.scale(Mth.clamp(age * .02, .1, 1.8) * (.75 + random.nextDouble() * .25));
                    spawnBrother(boss, Kind.FIST, position().add(offset), speed, planeX, pixels, damage, 0, random.nextInt(2));
                }
            }
            if (remaining == 1) brotherParticles(true, false, 1);
            brotherParticles(true, false, 0);
        } else {
            if (variant != 1) velocity = velocity.scale(1.0055);
            if (variant == 0) { scale = .8f; remaining = Math.min(remaining, 300); velocity = velocity.scale(1.006); }
            opacity = Mth.clamp(remaining / 12f, 0, 1) * Mth.clamp((1200 - remaining) / 7f, 0, 1);
        }
        if (!brotherAlive("cataclysm")) { remaining = 1; brotherParticles(true, false, 2); }
    }
    private void slash(LivingEntity boss) {
        int time = age + 1; // Source slash increments Time at the start; fists do so at the end.
        if (variant < 4) opacity = Mth.clamp(remaining / 8f, 0, 1) * Mth.clamp((1500 - remaining) / 8f, 0, 1);
        if (variant == 50) {
            remaining = Math.min(remaining, 30); opacity = 0;
            brotherParticles(false, false, 0);
            Vec3 ray = CalamitasSpace.unit(velocity, new Vec3(0, -1, 0));
            spawnBrother(boss, Kind.SLASH, position(), ray.scale(.1 / pixels * movement), planeX, pixels, damage, 3 + time, 5);
            return; // Source carriers bypass the brother-death check.
        }
        if (variant == 2) velocity = velocity.scale(1.0045);
        else if (variant == 3) {
            if (time > 30) velocity = velocity.scale(1.015);
            brotherParticles(false, false, 1);
        }
        else if (variant >= 4 && variant < 50) {
            if (remaining == 1500) {
                remaining = (hand == 5 ? 35 : 40) - (variant - 4);
                brotherParticles(false, false, -1);
            }
            else if (remaining == 1 && brotherAlive("catastrophe")) {
                slashExplode = true;
                brotherParticles(false, true, -1);
                CalamitasContent.sound(this, "brother_dash", position(), .65f, (float)Math.pow(2, .8));
            }
        }
        if (!brotherAlive("catastrophe")) { remaining = 1; brotherParticles(false, false, 3); }
    }

    private void brotherParticles(boolean fist, boolean burst, int dustMode) {
        // Carry the event independently of this short-lived projectile's removal packet.
        var sample = new CompoundTag(); sample.putBoolean("Slash", !fist && dustMode < 0);
        sample.putBoolean("SlashDust", !fist && dustMode >= 0);
        sample.putBoolean("FistDust", fist && dustMode < 3);
        sample.putBoolean("FistPulse", fist && dustMode == 3);
        sample.putBoolean("FistTelegraph", fist && dustMode == 4);
        if (fist && dustMode == 4) {
            sample.putBoolean("PartnerAlive", orbPartnerAlive); sample.putInt("Time", age);
            sample.putDouble("ShootX", shootVelocity.x); sample.putDouble("ShootY", shootVelocity.y); sample.putDouble("ShootZ", shootVelocity.z);
            sample.putDouble("OrbAxisX", orbAxis.x); sample.putDouble("OrbAxisY", orbAxis.y); sample.putDouble("OrbAxisZ", orbAxis.z);
        }
        if (fist) { sample.putDouble("PlaneX", planeX.x); sample.putDouble("PlaneZ", planeX.z); } sample.putInt("DustMode", dustMode);
        sample.putBoolean("Burst", burst); sample.putBoolean("Dash", hand != 5);
        sample.putInt("SourcePhase", CalamitasSimulation.projectilePhase());
        sample.putDouble("X", getX()); sample.putDouble("Y", getY()); sample.putDouble("Z", getZ());
        sample.putDouble("VX", velocity.x); sample.putDouble("VY", velocity.y); sample.putDouble("VZ", velocity.z);
        // Retain the old NEAR recipient decision at each original position, even
        // if a fast slash crosses the 256-block observation edge this source frame.
        for (var player : ((ServerLevel)level()).players())
            if (player.distanceToSqr(getX(), getY(), getZ()) < 256 * 256)
                brotherSamples.computeIfAbsent(player, ignored -> new net.minecraft.nbt.ListTag()).add(sample);
    }
    private CompoundTag brotherPacket(LivingEntity boss, net.minecraft.nbt.ListTag samples) {
        var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
        data.putInt("Owner", boss.getId()); data.putUUID("OwnerUUID", boss.getUUID());
        data.putDouble("Pixels", pixels); data.put("Samples", samples);
        return data;
    }

    private void fireball(LivingEntity boss, Vec3 targetPosition) {
        if (!withinRange) {
            opacity = suppressSplit ? Mth.clamp(remaining / 60f, 0, 1) : Mth.clamp(1 - (remaining - 130) / 20f, 0, 1);
            if (targetPosition != null) {
                Vec3 direction = targetPosition.subtract(position()).normalize();
                if (kind == Kind.FIREBLAST && distanceSource(targetPosition) > 40)
                    velocity = velocity.scale(79d / 80).add(direction.scale(13d / pixels / 80 * movement));
                else if (kind == Kind.GIGABLAST) {
                    velocity = CalamitasSpace.homeGigablast(velocity, targetPosition.subtract(position()));
                }
            }
            if (variant == 2 && random.nextBoolean()) {
                var sample = new CompoundTag(); sample.putBoolean("FireballTrail", true);
                sample.putBoolean("Giga", kind == Kind.GIGABLAST); sample.putFloat("Opacity", opacity);
                sample.putDouble("VX", velocity.x); sample.putDouble("VY", velocity.y); sample.putDouble("VZ", velocity.z);
                sendParticles(boss, sample, position());
            }
            if (remaining == 1 || targetPosition != null && distanceSource(targetPosition) < 224 && opacity == 1) {
                remaining = 60; withinRange = true;
            }
        }
        if (withinRange && !suppressSplit) {
            velocity = velocity.scale(.9);
            flameParticles(boss, 1);
            if (remaining <= 40) opacity = Math.max(0, opacity - .05f);
            if (remaining <= 30) { opacity = 0; velocity = Vec3.ZERO; }
            if (remaining == 30 || remaining == 15 || remaining == 8) {
                var sample = new CompoundTag(); sample.putBoolean("FireballBloom", true);
                sample.putBoolean("Giga", kind == Kind.GIGABLAST); sample.putInt("Remaining", remaining);
                sendParticles(boss, sample, position());
            }
        }
    }

    private void vortex(LivingEntity boss, Vec3 targetPosition) {
        if (age == 0) {
            var sample = new CompoundTag(); sample.putBoolean("VortexStart", true);
            sendParticles(boss, sample, position());
        }
        if (scale < 1.9f && remaining > 90) {
            if (scale < 1.5f) {
                var sample = new CompoundTag(); sample.putBoolean("VortexGrowth", true); sample.putFloat("VortexScale", scale);
                sendParticles(boss, sample, position());
            }
            scale += .01f;
        }
        if (speedAdd < speedLimit) speedAdd += .04;
        opacity = remaining < 90 ? remaining / 90f : Mth.clamp(1 - (remaining - 35910) / 90f, 0, 1);
        if (scale >= 1.9f) still--;
        if (still > 0) return;
        double inertia = 5 + speedAdd, speed = (2.9 + speedAdd * .25) * .8;
        var journal = CalamitasBattleData.get((ServerLevel) level());
        if (journal.living(owner, "heart") > 0 || journal.living(owner, "seeker") > 0) { inertia *= 1.5; speed *= .8; }
        if (targetPosition != null && distanceSource(targetPosition) > 160)
            velocity = velocity.scale((inertia - 1) / inertia)
                    .add(targetPosition.subtract(position()).normalize().scale(speed / pixels / inertia * movement));
        speedLimit = 15;
    }

    private double distanceSource(Vec3 other) { return position().distanceTo(other) * pixels; }
    private boolean terminalStraight() {
        return switch (kind) {
            case HELLBLAST, HELLBLAST_BARRAGE, DART -> true;
            case FIST -> variant < 3;
            case SLASH -> variant < 4;
            // Waves/souls change direction; fireballs, fist orbs and slash carriers emit attacks.
            default -> false;
        };
    }
    private double collisionRadius() {
        return switch (kind) {
            case MAELSTROM -> 170 * scale * opacity / pixels;
            case DART -> 10 * scale / pixels;
            case FIREBLAST -> 18 / pixels;
            case GIGABLAST -> 25 / pixels;
            case FIST -> (variant >= 3 ? 90 : 35 * scale) / pixels;
            case SLASH -> (hand == 5 ? 70 : variant >= 4 ? 100 : 43) / pixels;
            default -> 20 / pixels;
        };
    }
    private void collide(LivingEntity boss, Vec3 from, Vec3 to) {
        double radius = collisionRadius();
        AABB sweep = new AABB(from, to).inflate(radius);
        // Fireball impacts produce children: retain those collision events outside as well.
        if (kind != Kind.FIREBLAST && kind != Kind.GIGABLAST
                && boss instanceof SupremeCalamitasEntity calamitas) {
            var bounds = calamitas.arenaBounds();
            if (bounds != null && (sweep.maxX < bounds.minX || sweep.minX > bounds.maxX
                    || sweep.maxY < bounds.minY || sweep.minY > bounds.maxY
                    || sweep.maxZ < bounds.minZ || sweep.minZ > bounds.maxZ)) return;
        }
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, sweep,
                e -> (!witchAttack || e.getUUID().equals(target)) && e != boss && e.isAlive() && !boss.isAlliedTo(e) && !ownedAlly(e)
                    && (!(e instanceof Player p) || !p.isCreative() && !p.isSpectator()))) {
            if (!net.everla.projectc.arkofcosmos.calamity.CalamitasCollision.sweptSphereHits(from, to, radius, victim.getBoundingBox())) continue;
            double amount = damage;
            if (kind == Kind.MAELSTROM) {
                double distanceRatio = victim.getBoundingBox().getCenter().distanceToSqr(from) / (radius * radius);
                double sourceSpeed = victim.getDeltaMovement().length() * pixels / 3;
                double ratio = Math.max(distanceRatio, sourceSpeed * sourceSpeed / 625);
                // Preserve the source formula even though its explanatory comment suggests
                // the opposite speed relation. Native hurt replaces direct statLife writes.
                amount = Math.max(4, (int) (14 - 10 * ratio)) * damage;
            }
            if (!victim.hurt(damageSources().mobProjectile(this, boss), (float) amount)) continue;
            if (amount > 0) net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect.onHit(victim, kind);
            if (kind == Kind.MAELSTROM) vortexParticles(boss, victim.getBoundingBox().getCenter());
            if (kind == Kind.MAELSTROM && (age + 1) % 6 == 0)
                CalamitasContent.sound(this, "vortex_burn", victim.getBoundingBox().getCenter(), .25f, (float)Math.pow(2, .4));
            if (kind == Kind.FIREBLAST || kind == Kind.GIGABLAST) {
                impactSound();
                if (!suppressSplit) split(boss);
                discardAttack(); return;
            }
        }
    }
    private void vortexParticles(LivingEntity boss, Vec3 center) {
        var sample = new CompoundTag(); sample.putBoolean("VortexHit", true);
        sendParticles(boss, sample, center);
    }
    private void flameParticles(LivingEntity boss, int mode) {
        var sample = new CompoundTag(); sample.putBoolean("FlameDust", true); sample.putInt("FlameMode", mode);
        sample.putDouble("VX", velocity.x); sample.putDouble("VY", velocity.y); sample.putDouble("VZ", velocity.z);
        sendParticles(boss, sample, position());
    }
    private void sendParticles(LivingEntity boss, CompoundTag sample, Vec3 center) {
        sample.putInt("SourcePhase", CalamitasSimulation.projectilePhase());
        sample.putDouble("X", center.x); sample.putDouble("Y", center.y); sample.putDouble("Z", center.z);
        var samples = new net.minecraft.nbt.ListTag(); samples.add(sample);
        var data = new CompoundTag(); data.putString("Event", "calamitas_cast_sparks");
        data.putInt("Owner", boss.getId()); data.putUUID("OwnerUUID", boss.getUUID());
        data.putDouble("Pixels", pixels); data.put("Samples", samples);
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(this, data);
    }
    private boolean ownedAlly(Entity other) {
        return other instanceof CalamitasMinionEntity minion && owner.equals(minion.ownerUUID())
                || other instanceof CalamitasBrotherEntity brother && owner.equals(brother.ownerUUID())
                || other instanceof CalamitasSepulcherEntity worm && owner.equals(worm.ownerUUID());
    }
    private void impactSound() {
        if (kind == Kind.HELLBLAST) flameSound();
        if (kind == Kind.FIREBLAST || kind == Kind.GIGABLAST)
            CalamitasContent.sound(this, kind == Kind.FIREBLAST ? "fireblast_impact" : "gigablast_impact");
    }
    private void flameSound() {
        CalamitasContent.sound(this, "flame", position(), 1, (float)Math.pow(2, (random.nextFloat() - .5f) * .12f));
    }

    private void split(LivingEntity boss) {
        int count = kind == Kind.FIREBLAST ? 16 : 36;
        double speed = (kind == Kind.FIREBLAST ? 8 : 6.5) / pixels * movement;
        for (int index = 0; index < count; index++) {
            Vec3 direction = net.everla.projectc.arkofcosmos.calamity.CalamitasSpace.direction(random);
            var child=spawn(boss, Kind.DART, position(), direction.scale(speed), planeX, pixels, dartDamage,
                    dartDamage, speed * 1.5, variant);
            child.splitChild = true;
            if(witchAttack) child.target=target;
            child.publish();
        }
        if (variant == 2) flameParticles(boss, kind == Kind.FIREBLAST ? 2 : 3);
    }

    public void fadeForTransition(boolean includeVortex) {
        switch (kind) {
            case MAELSTROM -> { if (includeVortex) remaining = Math.min(remaining, 90); }
            case FIREBLAST, GIGABLAST -> { suppressSplit = true; remaining = Math.min(remaining, 15); }
            case HELLBLAST_BARRAGE, DART, WAVE -> remaining = Math.min(remaining, 60);
            default -> { }
        }
    }
    public void discardAttack() {
        ending = true;
        if (owner != null && level() instanceof ServerLevel server) CalamitasBattleData.get(server).forget(owner, getUUID());
        try { discard(); } finally { ending = false; }
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && !ending && remaining > 0
                && owner != null && level() instanceof ServerLevel server && CalamitasBattleData.get(server).active(owner)) return;
        super.remove(reason);
    }
    void publish() {
        var tag = new CompoundTag();
        addAdditionalSaveData(tag); entityData.set(VIEW, tag);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        if (target != null) tag.putUUID("Target", target);
        tag.putLong("WitchSession",witchSession);tag.putBoolean("WitchAttack",witchAttack); tag.putLong("WitchFade",witchFade); tag.putFloat("WitchFadeOpacity",witchFadeOpacity);
        tag.putLong("SpawnedAt", spawnedAt);
        tag.putBoolean("SplitChild", splitChild); tag.putLong("ArenaFade", arenaFade);
        tag.putFloat("ArenaFadeOpacity", arenaFadeOpacity);
        tag.putString("Kind", kind.name()); tag.putInt("Age", age); tag.putInt("Remaining", remaining);
        tag.putInt("Variant", variant); tag.putDouble("Pixels", pixels);
        tag.putDouble("Movement", movement);
        tag.putDouble("SoulPhase", soulPhase);
        tag.putDouble("VX", velocity.x); tag.putDouble("VY", velocity.y); tag.putDouble("VZ", velocity.z);
        tag.putDouble("PlaneX", planeX.x); tag.putDouble("PlaneZ", planeX.z);
        tag.putDouble("Damage", damage); tag.putDouble("DartDamage", dartDamage); tag.putDouble("MaximumSpeed", maximumSpeed);
        tag.putFloat("Opacity", opacity); tag.putFloat("Scale", scale); tag.putBoolean("WithinRange", withinRange);
        tag.putBoolean("SuppressSplit", suppressSplit); tag.putInt("Still", still);
        tag.putDouble("SpeedAdd", speedAdd); tag.putDouble("SpeedLimit", speedLimit);
        tag.putInt("Hand", hand); tag.putBoolean("OrbPartnerAlive", orbPartnerAlive); tag.putBoolean("SlashExplode", slashExplode);
        tag.putDouble("ShootX", shootVelocity.x); tag.putDouble("ShootY", shootVelocity.y); tag.putDouble("ShootZ", shootVelocity.z);
        tag.putDouble("OrbAxisX", orbAxis.x); tag.putDouble("OrbAxisY", orbAxis.y); tag.putDouble("OrbAxisZ", orbAxis.z);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        invalidSave = false;
        splitChild = tag.getBoolean("SplitChild"); arenaFade = tag.contains("ArenaFade") ? tag.getLong("ArenaFade") : -1;
        arenaFadeOpacity = tag.getFloat("ArenaFadeOpacity");
        witchSession=tag.getLong("WitchSession");witchAttack=tag.getBoolean("WitchAttack"); witchFade=tag.contains("WitchFade")?tag.getLong("WitchFade"):-1; witchFadeOpacity=tag.getFloat("WitchFadeOpacity");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        if (!tag.contains("Kind")) {
            setNoGravity(true);
            pixels = ProjectCCommonConfig.calamitasValue("pixels_per_block");
            movement = ProjectCCommonConfig.calamitasValue("movement_multiplier");
            velocity = getDeltaMovement().scale(1d / 3);
            remaining = switch (kind) {
                case HELLBLAST -> 255; case HELLBLAST_BARRAGE, SLASH -> 1500; case DART -> 690;
                case FIREBLAST -> 150; case GIGABLAST, SOUL -> 120; case WAVE, FIST -> 1200; case MAELSTROM -> 36000;
            };
            damage = dartDamage = ProjectCCommonConfig.calamitasDamage("dart", 80);
            if (kind == Kind.SOUL) damage = dartDamage = 0;
            scale = kind == Kind.MAELSTROM ? .1f : 1;
            maximumSpeed = 15 * movement / pixels;
            publish(); return;
        }
        age = Math.max(0, tag.getInt("Age")); remaining = tag.getInt("Remaining");
        variant = tag.getInt("Variant"); pixels = tag.getDouble("Pixels");
        spawnedAt = tag.contains("SpawnedAt") ? tag.getLong("SpawnedAt")
                : level().getGameTime() - age / (3L * updatesPerFrame());
        movement = tag.contains("Movement") ? tag.getDouble("Movement") : 1;
        soulPhase = tag.getDouble("SoulPhase");
        velocity = new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ"));
        planeX = new Vec3(tag.getDouble("PlaneX"), 0, tag.getDouble("PlaneZ"));
        damage = tag.getDouble("Damage"); dartDamage = tag.getDouble("DartDamage"); maximumSpeed = tag.getDouble("MaximumSpeed");
        opacity = tag.getFloat("Opacity"); scale = tag.getFloat("Scale"); withinRange = tag.getBoolean("WithinRange");
        suppressSplit = tag.getBoolean("SuppressSplit"); still = tag.getInt("Still");
        speedAdd = tag.getDouble("SpeedAdd"); speedLimit = tag.getDouble("SpeedLimit");
        hand = tag.getInt("Hand"); orbPartnerAlive = tag.getBoolean("OrbPartnerAlive"); slashExplode = tag.getBoolean("SlashExplode");
        shootVelocity = new Vec3(tag.getDouble("ShootX"), tag.getDouble("ShootY"), tag.getDouble("ShootZ"));
        orbAxis = tag.contains("OrbAxisX") ? new Vec3(tag.getDouble("OrbAxisX"), tag.getDouble("OrbAxisY"), tag.getDouble("OrbAxisZ"))
                : CalamitasSpace.fistAxis(shootVelocity, planeX);
        if (!kind.name().equals(tag.getString("Kind")) || pixels <= 0 || !Double.isFinite(pixels)
                || !Double.isFinite(movement) || movement <= 0 || !Double.isFinite(soulPhase) || !Double.isFinite(velocity.lengthSqr())
                || !Double.isFinite(damage) || damage < 0 || !Double.isFinite(dartDamage) || dartDamage < 0
                || remaining <= 0 || remaining > 36000 || !Float.isFinite(scale) || scale <= 0
                || !Float.isFinite(opacity) || !Double.isFinite(maximumSpeed) || !Double.isFinite(speedAdd)
                || !Double.isFinite(speedLimit) || !Double.isFinite(shootVelocity.lengthSqr()) || !Double.isFinite(planeX.lengthSqr())
                || Math.abs(planeX.lengthSqr() - 1) > 1e-6 || !Double.isFinite(orbAxis.lengthSqr())
                || Math.abs(orbAxis.lengthSqr() - 1) > 1e-6
                || kind == Kind.FIST && variant >= 3 && shootVelocity.lengthSqr() >= 1e-12
                    && Math.abs(orbAxis.dot(shootVelocity) / shootVelocity.length()) > 1e-6) { owner = null; invalidSave = true; }
        publish();
    }
}
