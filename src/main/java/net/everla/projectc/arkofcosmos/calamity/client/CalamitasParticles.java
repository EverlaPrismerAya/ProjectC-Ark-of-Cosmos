package net.everla.projectc.arkofcosmos.calamity.client;

import java.util.ArrayList;
import java.util.List;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasSparks;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Reliable source-event batches cannot be overwritten by a newer entity snapshot. */
final class CalamitasParticles {
    private CalamitasParticles() {}
    private static final List<CompoundTag> pending = new ArrayList<>(), frame = new ArrayList<>();
    private static void slashDust(net.minecraft.world.entity.Entity boss, CompoundTag sample, double pixels, int mode, boolean fist) {
        var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
        var velocity = new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ"));
        var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
        java.util.function.Consumer<SupremeCalamitasSparks.SlashDust> emit = emitted -> {
            var dust = emitted.dust();
            var particle = ArkEffects.particle(dust.type() == 66 ? ArkFxParticle.Type.SOURCE_DUST_66 : ArkFxParticle.Type.SOURCE_DUST_279,
                    dust.position(), dust.velocity(), ArkEffects.rgb(emitted.color() >> 16 & 255, emitted.color() >> 8 & 255, emitted.color() & 255), dust.scale(), 100, plane);
            particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
        };
        ArkEffects.galaxiaOwner(boss.getId(), () -> {
            if (fist) SupremeCalamitasSparks.fistDust(mode, center,
                    new Vec3(sample.getDouble("PlaneX"), 0, sample.getDouble("PlaneZ")), pixels, ArkEffects.rng, emit);
            else SupremeCalamitasSparks.slashDust(mode, center, velocity, pixels, ArkEffects.rng, emit);
        });
    }
    static void receive(CompoundTag event) { pending.add(event.copy()); }
    static void clear() { pending.clear(); frame.clear(); }
    static void tick(int phase) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        if (net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED
                && net.everla.projectc.arkofcosmos.CalamitasContent.VULNERABILITY_HEX.isPresent()) {
            double pixels = net.everla.projectc.arkofcosmos.ProjectCCommonConfig.calamitasValue("pixels_per_block");
            for (var player : level.players()) {
                if (!player.isAlive() || !player.hasEffect(net.everla.projectc.arkofcosmos.CalamitasContent.VULNERABILITY_HEX.get())) continue;
                var spark = SupremeCalamitasSparks.hexSpark(player.getBoundingBox().getCenter(), pixels, ArkEffects.rng);
                var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                if (spark != null) ArkEffects.galaxiaOwner(player.getId(), () -> ArkEffects.particle(ArkFxParticle.Type.VOID_SPARK,
                        spark.position(), spark.velocity(), ArkEffects.rgb(spark.color() >> 16 & 255,
                                spark.color() >> 8 & 255, spark.color() & 255), spark.scale(), spark.lifetime(), plane).pixelScale = 16 / pixels);
                var dust = SupremeCalamitasSparks.hexDust(player.getBoundingBox().getCenter(), pixels, ArkEffects.rng);
                ArkEffects.galaxiaOwner(player.getId(), () -> {
                    boolean brimstone = dust.type() == 235;
                    var particle = ArkEffects.particle(brimstone ? ArkFxParticle.Type.SOURCE_DUST_235 : ArkFxParticle.Type.SOURCE_DUST_261,
                            dust.position(), dust.velocity(), brimstone ? new float[]{1, 1, 1, 0}
                                    : new float[]{230/255f, 230/255f, 230/255f, 115/255f}, dust.scale(), 100, plane);
                    particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                });
            }
        }
        if (phase == 0) { frame.clear(); frame.addAll(pending); pending.clear(); }
        for (var event : frame) {
            var boss = level.getEntity(event.getInt("Owner"));
            if (!(boss instanceof SupremeCalamitasEntity
                    || boss instanceof net.everla.projectc.arkofcosmos.entity.CalamitasBrotherEntity
                    || boss instanceof net.everla.projectc.arkofcosmos.entity.CalamitasMinionEntity
                    || boss instanceof net.everla.projectc.arkofcosmos.entity.CalamitasSepulcherEntity)
                    || !event.hasUUID("OwnerUUID") || !boss.getUUID().equals(event.getUUID("OwnerUUID")) || boss.isRemoved()) continue;
            var samples = event.getList("Samples", 10); int count = samples.size();
            for (int i = 0; i < count; i++) {
                var sample = samples.getCompound(i);
                if (sample.getBoolean("SeekerDeath")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var planeX = new Vec3(sample.getDouble("PlaneX"), 0, sample.getDouble("PlaneZ"));
                    var plane = new AttackFrame(planeX, new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.seekerDeathDust(center, planeX, pixels, ArkEffects.rng, emitted -> {
                        var dust = emitted.dust();
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_DUST_235, dust.position(), dust.velocity(),
                                new float[]{1, 1, 1, 0}, dust.scale(), 200, plane);
                        particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                        particle.noGravity = emitted.noGravity(); particle.dustFadeIn = emitted.fadeIn();
                        if (sample.hasUUID("TargetUUID")) {
                            particle.dustTargetId = sample.getInt("Target"); particle.dustTargetUUID = sample.getUUID("TargetUUID");
                            particle.dustTargetPosition = new Vec3(sample.getDouble("TargetX"), sample.getDouble("TargetY"), sample.getDouble("TargetZ"));
                        }
                    }));
                    continue;
                }
                if (sample.getBoolean("HeartTendrils")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var planeX = new Vec3(sample.getDouble("PlaneX"), 0, sample.getDouble("PlaneZ"));
                    var plane = new AttackFrame(planeX, new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> {
                        var endpoints = sample.getList("Endpoints", 10);
                        for (int endpoint = 0; endpoint < endpoints.size(); endpoint++) {
                            var point = endpoints.getCompound(endpoint);
                            SupremeCalamitasSparks.heartTendrilDust(center, new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")),
                                    planeX, pixels, ArkEffects.rng, dust -> {
                                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_DUST_5, dust.position(), dust.velocity(),
                                                new float[]{1, 1, 1, 1}, dust.scale(), 100, plane);
                                        particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                                    });
                        }
                    });
                    continue;
                }
                if (sample.getBoolean("FireballTrail")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var spark = SupremeCalamitasSparks.fireballTrail(sample.getBoolean("Giga"),
                            new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z")),
                            new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ")), pixels, ArkEffects.rng);
                    var color = ArkEffects.rgb(spark.color() >> 16 & 255, spark.color() >> 8 & 255, spark.color() & 255);
                    for (int channel = 0; channel < 4; channel++) color[channel] = (int)(Math.round(color[channel] * 255) * sample.getFloat("Opacity")) / 255f;
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> ArkEffects.particle(ArkFxParticle.Type.SOURCE_SPARK,
                            spark.position(), spark.velocity(), color, spark.scale(), spark.lifetime(), plane).pixelScale = 16 / pixels);
                    continue;
                }
                if (sample.getBoolean("FireballBloom")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.fireballBlooms(sample.getBoolean("Giga"), sample.getInt("Remaining"), center, light -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_BLOOM, center, Vec3.ZERO,
                                ArkEffects.rgb(light.color() >> 16 & 255, light.color() >> 8 & 255, light.color() & 255), .1f, light.lifetime(), plane);
                        particle.originalScale = .1f; particle.finalScale = light.scale(); particle.pixelScale = 16 / pixels;
                    }));
                    continue;
                }
                if (sample.getBoolean("VortexGrowth")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.vortexGrowth(sample.getFloat("VortexScale"), center, pixels, ArkEffects.rng, orb -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_ORB, orb.position(), orb.velocity(),
                                ArkEffects.rgb(orb.color() >> 16 & 255, orb.color() >> 8 & 255, orb.color() & 255), orb.scale(), orb.lifetime(), plane);
                        particle.pixelScale = 16 / pixels; particle.orbAdditive = true; particle.orbCenter = true;
                    }));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.vortexGrowthDust(sample.getFloat("VortexScale"), center, pixels, ArkEffects.rng, dust -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_DUST_235, dust.position(), dust.velocity(),
                                new float[]{1, 1, 1, 0}, dust.scale(), 100, plane);
                        particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                        particle.noGravity = true;
                    }));
                    continue;
                }
                if (sample.getBoolean("SeekerWarning")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var velocity = new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> {
                        if (sample.getBoolean("Pulse")) for (int layer = 0; layer < 2; layer++) {
                            var pulse = ArkEffects.particle(ArkFxParticle.Type.SOURCE_STATIC_PULSE, center, Vec3.ZERO,
                                    ArkEffects.rgb(255, 0, layer == 0 ? 0 : 76), layer == 0 ? .03f : .025f, 8, plane);
                            pulse.originalScale = pulse.scale; pulse.finalScale = .005f;
                            pulse.originalX = pulse.originalY = 2; pulse.rotation = pulse.previousRotation = 0; pulse.pixelScale = 16 / pixels;
                        }
                        var orb = SupremeCalamitasSparks.seekerWarning(center, velocity, Vec3.ZERO, ArkEffects.rng);
                        ArkEffects.particle(ArkFxParticle.Type.SOURCE_ORB, orb.position(), orb.velocity(),
                                ArkEffects.rgb(orb.color() >> 16 & 255, orb.color() >> 8 & 255, orb.color() & 255), orb.scale(), orb.lifetime(), plane).pixelScale = 16 / pixels;
                    });
                    continue;
                }
                if (sample.getBoolean("SeekerShotDust")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var velocity = new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.seekerShotDust(sample.getBoolean("Fired"), center, velocity, pixels, ArkEffects.rng, dust -> {
                        var particle = ArkEffects.particle(dust.type() == 60 ? ArkFxParticle.Type.SOURCE_DUST_60 : ArkFxParticle.Type.SOURCE_DUST_114,
                                dust.position(), dust.velocity(), new float[4], dust.scale(), 100, plane);
                        particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                    }));
                    continue;
                }
                if (sample.getBoolean("SepulcherBurst")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.sepulcherBlooms(center, light -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_BLOOM, center, Vec3.ZERO,
                                ArkEffects.rgb(light.color() >> 16 & 255, light.color() >> 8 & 255, light.color() & 255), .1f, light.lifetime(), plane);
                        particle.originalScale = .1f; particle.finalScale = light.scale(); particle.pixelScale = 16 / pixels;
                    }));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> {
                        var pulse = ArkEffects.particle(ArkFxParticle.Type.SOURCE_DIRECTIONAL_PULSE, center, Vec3.ZERO,
                                ArkEffects.rgb(255, 0, 0), 0, 25, plane);
                        pulse.originalScale = 0; pulse.finalScale = .9f;
                        pulse.originalX = pulse.originalY = 2;
                        pulse.rotation = pulse.previousRotation = 0; pulse.pixelScale = 16 / pixels;
                    });
                    continue;
                }
                if (sample.getBoolean("VortexStart")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.vortexStart(center, light -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_BLOOM, light.position(), Vec3.ZERO,
                                ArkEffects.rgb(light.color() >> 16 & 255, light.color() >> 8 & 255, light.color() & 255), light.scale(), light.lifetime(), plane);
                        particle.originalScale = light.scale(); particle.finalScale = 0; particle.pixelScale = 16 / pixels;
                    }));
                    continue;
                }
                if (sample.getBoolean("VortexHit")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.vortexHit(pixels, ArkEffects.rng, orb -> {
                        var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_ORB, center, orb.velocity(),
                                ArkEffects.rgb(orb.color() >> 16 & 255, orb.color() >> 8 & 255, orb.color() & 255), orb.scale(), 60, plane);
                        particle.pixelScale = 16 / pixels; particle.orbAdditive = orb.additive(); particle.orbCenter = orb.glowCenter();
                    }));
                    continue;
                }
                if (sample.getBoolean("FlameDust")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var velocity = new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    int flameMode = sample.getInt("FlameMode");
                    if (flameMode == 2 || flameMode == 3)
                        ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.fireballPoints(flameMode == 3, center, pixels, ArkEffects.rng, point -> {
                            var color = ArkEffects.rgb(point.color() >> 16 & 255, point.color() >> 8 & 255, point.color() & 255);
                            for (int channel = 0; channel < 4; channel++) color[channel] = (int)(color[channel] * 255 * .6f) / 255f;
                            var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_POINT, point.position(), point.velocity(), color, point.scale(), point.lifetime(), plane);
                            particle.pixelScale = 16 / pixels;
                        }));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.flameDust(sample.getInt("FlameMode"), center, velocity, pixels, ArkEffects.rng, dust -> {
                        var particle = ArkEffects.particle(dust.type() == 182 ? ArkFxParticle.Type.SOURCE_DUST_182 : dust.type() == 60 ? ArkFxParticle.Type.SOURCE_DUST_60 : ArkFxParticle.Type.SOURCE_DUST_114,
                                dust.position(), dust.velocity(), new float[4], dust.scale(), 100, plane);
                        particle.variant = dust.variant(); particle.spin = dust.horizontalSpeed(); particle.pixelScale = 16 / pixels;
                    }));
                    continue;
                }
                if (sample.getBoolean("FistPulse") || sample.getBoolean("FistTelegraph")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var center = new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z"));
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    ArkEffects.galaxiaOwner(boss.getId(), () -> {
                        if (sample.getBoolean("FistPulse")) SupremeCalamitasSparks.fistPulses(ArkEffects.rng, pulse -> {
                            var color = ArkEffects.rgb(pulse.color() >> 16 & 255, pulse.color() >> 8 & 255, pulse.color() & 255);
                            if (pulse.color() == 0xE5E5E5) color[3] = 229 / 255f;
                            var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_PULSE, center, Vec3.ZERO, color, pulse.scale(), 10, plane);
                            particle.originalScale = pulse.scale(); particle.finalScale = 0;
                            particle.rotation = particle.previousRotation = pulse.rotation(); particle.pixelScale = 16 / pixels;
                        });
                        else SupremeCalamitasSparks.fistTelegraph(sample.getBoolean("PartnerAlive"), center,
                                new Vec3(sample.getDouble("ShootX"), sample.getDouble("ShootY"), sample.getDouble("ShootZ")),
                                new Vec3(sample.getDouble("OrbAxisX"), sample.getDouble("OrbAxisY"), sample.getDouble("OrbAxisZ")), sample.getInt("Time"), spark -> {
                                    var color = new float[]{127 / 255f, 0, 63 / 255f, 127 / 255f};
                                    var particle = ArkEffects.particle(ArkFxParticle.Type.SOURCE_GLOW_SPARK, spark.position(), spark.velocity(), color, spark.scale(), spark.lifetime(), plane);
                                    particle.originalX = .9f; particle.originalY = .4f; particle.pixelScale = 16 / pixels;
                                });
                    });
                    continue;
                }
                if (sample.getBoolean("SlashDust") || sample.getBoolean("FistDust")) {
                    if (sample.getInt("SourcePhase") == phase) slashDust(boss, sample, event.getDouble("Pixels"), sample.getInt("DustMode"), sample.getBoolean("FistDust"));
                    continue;
                }
                if (sample.getBoolean("Slash")) {
                    if (sample.getInt("SourcePhase") != phase) continue;
                    double pixels = event.getDouble("Pixels");
                    var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                    if (sample.getBoolean("Burst")) slashDust(boss, sample, pixels, 2, false);
                    ArkEffects.galaxiaOwner(boss.getId(), () -> SupremeCalamitasSparks.slash(sample.getBoolean("Dash"), sample.getBoolean("Burst"),
                            new Vec3(sample.getDouble("X"), sample.getDouble("Y"), sample.getDouble("Z")),
                            new Vec3(sample.getDouble("VX"), sample.getDouble("VY"), sample.getDouble("VZ")), spark -> {
                                float amount = sample.getBoolean("Burst") ? .7f : .35f;
                                var color = ArkEffects.rgb(spark.color() >> 16 & 255, spark.color() >> 8 & 255, spark.color() & 255);
                                for (int channel = 0; channel < 4; channel++) color[channel] = (int)(Math.round(color[channel] * 255) * amount) / 255f;
                                ArkEffects.particle(spark.inward() ? ArkFxParticle.Type.VOID_SPARK : ArkFxParticle.Type.SOURCE_SPARK,
                                        spark.position(), spark.velocity(), color, spark.scale(), spark.lifetime(), plane).pixelScale = 16 / pixels;
                            }));
                    continue;
                }
                if (i < phase * count / 3 || i >= (phase + 1) * count / 3) continue;
                Vec3 cataclysm = new Vec3(sample.getDouble("X0"), sample.getDouble("Y0"), sample.getDouble("Z0"));
                Vec3 catastrophe = new Vec3(sample.getDouble("X1"), sample.getDouble("Y1"), sample.getDouble("Z1"));
                double pixels = event.getDouble("Pixels");
                var plane = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, -1, 0));
                ArkEffects.galaxiaOwner(boss.getId(), () -> {
                    SupremeCalamitasSparks.brotherLights(sample.getInt("Delay"),
                            new Vec3(sample.getDouble("BodyX"), sample.getDouble("BodyY"), sample.getDouble("BodyZ")), cataclysm, catastrophe,
                            pixels, light -> {
                                var particle = ArkEffects.particle(light.bloom() ? ArkFxParticle.Type.SOURCE_BLOOM : ArkFxParticle.Type.SOURCE_ORB,
                                        light.position(), Vec3.ZERO, ArkEffects.rgb(light.color() >> 16 & 255, light.color() >> 8 & 255, light.color() & 255),
                                        light.bloom() ? 0 : light.scale(), light.lifetime(), plane);
                                particle.finalScale = light.scale(); particle.pixelScale = 16 / pixels;
                            });
                    SupremeCalamitasSparks.brothers(sample.getInt("Delay"), cataclysm, catastrophe,
                            pixels, ArkEffects.rng, spark -> ArkEffects.particle(spark.inward() ? ArkFxParticle.Type.VOID_SPARK : ArkFxParticle.Type.SOURCE_SPARK,
                                    spark.position(), spark.velocity(), ArkEffects.rgb(spark.color() >> 16 & 255, spark.color() >> 8 & 255, spark.color() & 255),
                                    spark.scale(), spark.lifetime(), plane).pixelScale = 16 / pixels);
                });
            }
        }
    }
}
