package net.everla.projectc.arkofcosmos.calamity;

import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import net.minecraft.world.phys.Vec3;

/** Ordinary-world brothers Spark/VoidSpark emissions, Calamity 1a8cebd.
 * Directions expand into 3D; source counts, speed magnitudes and random ranges remain unchanged. */
public final class SupremeCalamitasSparks {
    private SupremeCalamitasSparks() {}
    public record Spark(boolean inward, Vec3 position, Vec3 velocity, float scale, int color, int lifetime) {}
    public record Orb(Vec3 velocity, float scale, int color, boolean additive, boolean glowCenter) {}
    public record Dust(int type, int variant, Vec3 position, Vec3 velocity, float scale, float horizontalSpeed) {}
    public record DeathDust(Dust dust, boolean noGravity, float fadeIn) {}
    /** Source NewDust(50,50): preserve native 2D speed distribution, extend direction and region to 3D. */
    public static void seekerDeathDust(Vec3 center, Vec3 planeX, double pixels, RandomGenerator random, Consumer<DeathDust> emit) {
        Vec3 depth = planeX.cross(new Vec3(0, 1, 0));
        for (int i = 0; i < 25; i++) {
            boolean noGravity = i >= 5 && (i - 5) % 2 == 0;
            float multiplier = i < 5 ? 3 : noGravity ? 5 : 2;
            Vec3 at = center.add(planeX.scale((random.nextInt(46) - 21) / pixels))
                    .add(0, -(random.nextInt(46) - 21) / pixels, 0)
                    .add(depth.scale((random.nextInt(46) - 21) / pixels));
            float vx = random.nextInt(-20, 21) * .1f, vy = random.nextInt(-20, 21) * .1f;
            Vec3 velocity = direction(random).scale(Math.hypot(vx, vy) * multiplier / pixels);
            float scale = (1 + random.nextInt(-20, 21) * .01f) * (noGravity ? 3 : 2), fadeIn = 0;
            if (i < 5 && random.nextBoolean()) { scale = .5f; fadeIn = 1 + random.nextInt(10) * .1f; }
            emit.accept(new DeathDust(new Dust(235, random.nextInt(3), at, velocity, scale, (float)(velocity.x * pixels)), noGravity, fadeIn));
        }
    }
    /** Variant 2 fireball death bursts; source radial offset and speed retain their shared direction. */
    public static void fireballPoints(boolean giga, Vec3 center, double pixels, RandomGenerator random, Consumer<Spark> emit) {
        for (int i = 0; i < (giga ? 25 : 18); i++) {
            Vec3 offset = direction(random).scale(Math.sqrt(2) * (giga ? 15 : 12) / pixels);
            emit.accept(new Spark(false, center.add(offset), offset.scale(random.nextFloat(.3f, 1f)),
                    giga ? 1.25f : 1.1f, random.nextBoolean() ? 0xFF007F : 0xFF0000, 15));
        }
    }
    /** Wave trail, fireball countdown, Fireblast burst, Gigablast burst, seeker/Sepulcher dart trail. */
    public static void flameDust(int mode, Vec3 center, Vec3 velocity, double pixels,
                                 RandomGenerator random, Consumer<Dust> emit) {
        if (mode < 0 || mode > 4) throw new IllegalArgumentException("Invalid flame Dust mode");
        if (mode == 4) {
            Vec3 offset = direction(random).scale(random.nextFloat() * 4 / pixels);
            Vec3 speed = velocity.scale(-.5 * random.nextFloat(.1f, .9f));
            emit.accept(new Dust(182, random.nextInt(3), center.add(offset), speed,
                    random.nextFloat(.2f, .6f), (float)(speed.x * pixels)));
            return;
        }
        int count = mode == 0 ? 1 : mode == 1 ? 2 : mode == 2 ? 18 : 25;
        for (int i = 0; i < count; i++) {
            int type = random.nextInt(3) == 0 ? 60 : 114;
            Vec3 speed = mode == 0 ? velocity.scale(random.nextFloat(.1f, .7f))
                    : direction(random).scale(Math.sqrt(2) * (mode == 1 ? 4 : mode == 2 ? 16 : 20)
                            * random.nextFloat(.5f, 1.3f) / pixels);
            float scale = random.nextFloat(mode == 0 || mode == 3 ? .9f : mode == 1 ? .7f : .75f,
                    mode == 2 ? 1.3f : 1.8f);
            emit.accept(new Dust(type, random.nextInt(3), center, speed, scale, (float)(speed.x * pixels)));
        }
    }
    public record SlashDust(Dust dust, int color, int alpha) {}
    public record Pulse(float scale, int color, float rotation) {}
    /** Three CustomPulse layers share the source's one random size per AI update. */
    public static void fistPulses(RandomGenerator random, Consumer<Pulse> emit) {
        float size = random.nextFloat(.8f, 1.2f);
        for (int i = 0; i < 3; i++) emit.accept(new Pulse((i < 2 ? 1.7f : 1.4f) * size,
                i < 2 ? 0xFF007F : 0xE5E5E5, random.nextFloat(-10, 10)));
    }
    public static void fistTelegraph(boolean partnerAlive, Vec3 center, Vec3 shootVelocity, Vec3 axis,
                                     int time, Consumer<Spark> emit) {
        for (int branch = 0; branch < (partnerAlive ? 1 : 3); branch++) {
            Vec3 velocity = CalamitasSpace.fistRay(shootVelocity, axis, branch).scale(Math.max(1, Math.min(1.8, time * .1)));
            for (int k = 0; k < 28; k++) emit.accept(new Spark(false, center.add(velocity.scale(9 + k * 11)),
                    velocity, .17f, 0xFF007F, 7));
        }
    }
    /** Non-zenith fist Dust: mode 0 orb trail, 1 final two rings, 2 brother-death cleanup. */
    public static void fistDust(int mode, Vec3 center, Vec3 planeX, double pixels,
                                RandomGenerator random, Consumer<SlashDust> emit) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid fist Dust mode");
        if (mode == 2) {
            slashDust(3, center, Vec3.ZERO, pixels, random,
                    dust -> emit.accept(new SlashDust(dust.dust(), 0xFF007F, 0)));
            return;
        }
        if (mode == 0) {
            Vec3 speed = direction(random).scale(Math.sqrt(2) * 14 * random.nextFloat(.1f, 2.5f) / pixels);
            emit.accept(new SlashDust(new Dust(279, random.nextInt(3), center.add(speed.scale(2)), speed,
                    random.nextFloat(.9f, 1.2f), (float)(speed.x * pixels)), 0xFF0000, 0));
            return;
        }
        for (int ring = 0; ring < 2; ring++) {
            float rotation = -.45f * random.nextFloat(.1f, 2.5f);
            for (int point = 0; point < 25; point++) {
                double angle = -Math.PI * .75 + Math.PI * 2 / 25 * point + rotation;
                // Source Y points down; both circles retain the locked attack plane.
                Vec3 ray = planeX.scale(Math.cos(angle)).add(0, -Math.sin(angle), 0);
                Vec3 speed = ray.scale((ring == 0 ? 9 : 7) / pixels);
                emit.accept(new SlashDust(new Dust(279, random.nextInt(3), center.add(ray.scale((ring == 0 ? 7 : 5) / pixels)),
                        speed, random.nextFloat(1.3f, 1.9f), (float)(speed.x * pixels)), 0xFF0000, 0));
            }
        }
    }
    /** Called once per source projectile AI update, including its extra updates.
     * mode 0: carrier, 1: acceleration, 2: trail burst, 3: brother-death cleanup. */
    public static void slashDust(int mode, Vec3 center, Vec3 velocity, double pixels,
                                 RandomGenerator random, Consumer<SlashDust> emit) {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("Invalid slash Dust mode");
        if (mode == 0 && !random.nextBoolean() || mode == 1 && random.nextInt(3) != 0) return;
        int count = mode == 2 ? 3 : mode == 3 ? 10 : 1;
        for (int i = 0; i < count; i++) {
            Vec3 at = center, speed;
            float scale;
            if (mode >= 2) {
                // The original (14,14)/(7,7) vectors keep their magnitude in 3D.
                double magnitude = Math.sqrt(2) * (mode == 2 ? 14 : 7)
                        * random.nextFloat(mode == 2 ? .1f : .8f, mode == 2 ? 2.5f : 1.2f);
                Vec3 radial = direction(random).scale(magnitude / pixels);
                at = at.add(radial.scale(mode == 2 ? 2 : 1));
                speed = mode == 2 ? radial : radial.scale(random.nextFloat(.2f, 1f));
                scale = random.nextFloat(mode == 2 ? 1.2f : 1.3f, mode == 2 ? 1.8f : 1.9f);
            } else {
                if (mode == 1) {
                    // NextVector2Circular has a uniform radius; extend direction only.
                    at = at.add(direction(random).scale(random.nextFloat() * 50 / pixels))
                            .subtract(velocity.lengthSqr() > 1e-16 ? velocity.normalize().scale(8.5 / pixels)
                                    : new Vec3(0, -8.5 / pixels, 0));
                }
                speed = velocity.scale(-random.nextFloat(mode == 0 ? .1f : .2f, mode == 0 ? 1.5f : 1.2f));
                scale = random.nextFloat(.5f, .7f);
            }
            var dust = new Dust(mode == 2 ? 279 : 66, random.nextInt(3), at, speed, scale, (float)(speed.x * pixels));
            emit.accept(new SlashDust(dust, mode == 3 ? 0x00FFFF : 0x00BFFF, mode == 0 ? 100 : 0));
        }
    }
    /** One native Dust per Hex source frame; rotate the source cone about world-up for depth. */
    public static Dust hexDust(Vec3 center, double pixels, RandomGenerator random) {
        Vec3 position = center.add(random.nextFloat(-10, 10) / pixels,
                random.nextFloat(-20, 20) / pixels, random.nextFloat(-10, 10) / pixels);
        int type = random.nextBoolean() ? 261 : 235, variant = random.nextInt(3);
        float scale = random.nextFloat(.4f, .5f), speed = random.nextFloat(.5f, 2.5f);
        double angle = random.nextDouble(-.6, .6), azimuth = random.nextDouble(Math.PI * 2);
        float horizontal = (float)(Math.sin(angle) * speed);
        Vec3 velocity = new Vec3(Math.cos(azimuth) * horizontal, Math.cos(angle) * speed,
                Math.sin(azimuth) * horizontal).scale(1 / pixels);
        return new Dust(type, variant, position, velocity, scale, horizontal);
    }
    /** Player Hex DrawEffects: one 50% VoidSpark per source step, with an upward velocity. */
    public static Spark hexSpark(Vec3 center, double pixels, RandomGenerator random) {
        if (!random.nextBoolean()) return null;
        // RandomDebuffVisualSpot rectangle extends to a box; X/Z share the source width.
        Vec3 position = center.add(random.nextFloat(-10, 10) / pixels,
                random.nextFloat(-20, 20) / pixels, random.nextFloat(-10, 10) / pixels);
        Vec3 velocity = new Vec3(0, random.nextFloat(6, 10) / pixels, 0);
        int lifetime = random.nextInt(18, 27);
        float scale = random.nextFloat(.015f, .045f);
        return new Spark(true, position, velocity, scale, random.nextBoolean() ? 0xFF0000 : 0xFF00A5, lifetime);
    }
    public static void vortexHit(double pixels, RandomGenerator random, Consumer<Orb> emit) {
        emit.accept(vortexOrb(pixels, random, random.nextBoolean() ? 0xFF0000 : 0xFF007F, true));
        if (random.nextBoolean()) emit.accept(vortexOrb(pixels, random, 0, false));
    }
    private static Orb vortexOrb(double pixels, RandomGenerator random, int color, boolean additive) {
        Vec3 velocity = CalamitasSpace.direction(random).scale(Math.sqrt(72) * random.nextFloat(.3f, 1.1f) / pixels);
        return new Orb(velocity, random.nextFloat(1.55f, 3.75f), color, additive, additive);
    }
    public static void slash(boolean dash, boolean burst, Vec3 center, Vec3 velocity, Consumer<Spark> emit) {
        if (burst) emit.accept(new Spark(true, center, velocity, dash ? 1.3f : .7f, 0x00FFFF, 9));
        else {
            emit.accept(new Spark(false, center, velocity, 5, 0x00BFFF, 25));
            if (dash) {
                emit.accept(new Spark(false, center.add(velocity.scale(50)), velocity, 5, 0x00BFFF, 25));
                emit.accept(new Spark(false, center.subtract(velocity.scale(50)), velocity, 5, 0x00BFFF, 25));
            }
        }
    }
    public static int inwardCount(int delay) {
        if (delay < 0 || delay >= 60) return 0;
        float power = delay == 0 ? 4 : (delay - 60f) / (20f - 60f);
        return (int)Math.ceil(1 + 5 * power);
    }
    public static void brothers(int delay, Vec3 cataclysm, Vec3 catastrophe, double pixels,
                                RandomGenerator random, Consumer<Spark> emit) {
        if (delay < 0 || delay >= 60 || !Double.isFinite(pixels) || pixels <= 0) return;
        for (int i = 0; i < inwardCount(delay); i++) {
            Vec3 offset = direction(random).scale(random.nextFloat(70, 150) * .04f * random.nextFloat(2, 13));
            emit.accept(new Spark(true, catastrophe.add(offset.scale(4.5 / pixels)), offset.scale(-.25 / pixels),
                    random.nextFloat(.08f, .16f) - delay * .002f, random.nextInt(5) == 0 ? 0x00BFFF : 0xFF0000, 9));
            emit.accept(new Spark(true, cataclysm.add(offset.scale(4.5 / pixels)), offset.scale(-.25 / pixels),
                    random.nextFloat(.08f, .16f) - delay * .002f, random.nextInt(5) == 0 ? 0x79154D : 0xFF0000, 9));
        }
        if (delay == 0) for (int i = 0; i < 30; i++) {
            emit.accept(outward(catastrophe, pixels, random, 0x00BFFF));
            emit.accept(outward(cataclysm, pixels, random, 0x50154D));
        }
    }
    /** Uniform 3D direction with the source circle's uniform radius and backward velocity. */
    public static Spark fireballTrail(boolean giga, Vec3 center, Vec3 velocity, double pixels, RandomGenerator random) {
        Vec3 offset = direction(random).scale(random.nextFloat() * (giga ? 30 : 20) / pixels);
        return new Spark(false, center.subtract(velocity).add(offset), velocity.scale(-random.nextFloat(.1f, 1f)),
                random.nextFloat(giga ? .5f : .35f, giga ? .75f : .6f), random.nextBoolean() ? 0xFF007F : 0xFF0000, 14);
    }
    public record Light(boolean bloom, Vec3 position, float scale, int color, int lifetime) {}
    public static void sepulcherBlooms(Vec3 center, Consumer<Light> emit) {
        emit.accept(new Light(true, center, .9f, 0xFF0000, 30));
        emit.accept(new Light(true, center, .8f, 0xFFFFFF, 30));
    }
    public static void vortexStart(Vec3 center, Consumer<Light> emit) {
        for (int i = 0; i < 2; i++) emit.accept(new Light(true, center, 1.45f, 0xFF004C, 120));
        emit.accept(new Light(true, center, 1.35f, 0xFFFFFF, 120));
    }
    public static Spark seekerWarning(Vec3 center, Vec3 shootVelocity, Vec3 npcVelocity, RandomGenerator random) {
        return new Spark(false, center.add(shootVelocity.scale(7)),
                shootVelocity.scale(random.nextFloat(.6f, 1.1f) * 1.8f).add(npcVelocity.scale(.5f)),
                random.nextFloat(.7f, .75f), random.nextBoolean() ? 0xFF004C : 0xFF0000, 15);
    }
    public static void seekerShotDust(boolean fired, Vec3 center, Vec3 shootVelocity, double pixels, RandomGenerator random, Consumer<Dust> emit) {
        for (int i = 0; i < (fired ? 5 : 10); i++) {
            Vec3 velocity;
            if (fired) {
                double angle = random.nextDouble(-1, 1);
                Vec3 aim = CalamitasSpace.unit(shootVelocity, new Vec3(0, 1, 0));
                velocity = aim.scale(Math.cos(angle)).add(CalamitasSpace.spreadAxis(aim, random).scale(Math.sin(angle)))
                        .scale(shootVelocity.length() * random.nextFloat(2.2f, 3.8f));
            } else velocity = direction(random).scale(Math.sqrt(18) * random.nextFloat(.5f, 1.3f) / pixels);
            emit.accept(new Dust(random.nextInt(3) == 0 ? 60 : 114, random.nextInt(3), center, velocity,
                    random.nextFloat(fired ? 1.8f : 1.3f, fired ? 2.1f : 2.4f), (float)(velocity.x * pixels)));
        }
    }
    /** Center-relative mapping accepted by the user; source counts, speed and signed scale retained. */
    /** NewDustDirect(4,4) clamps both dimensions to 5: native spawn offset is exactly (4,4). */
    public static void heartTendrilDust(Vec3 center, Vec3 endpoint, Vec3 planeX, double pixels,
            RandomGenerator random, Consumer<Dust> emit) {
        for (int i = 0; i < 70; i++) {
            Vec3 at = center.lerp(endpoint, i / 70f).add(planeX.scale(4 / pixels)).add(0, -4 / pixels, 0);
            Vec3 velocity = direction(random).scale(random.nextFloat() * 3 / pixels);
            emit.accept(new Dust(5, random.nextInt(3), at, velocity, random.nextFloat(1f, 1.4f), (float)(velocity.x * pixels)));
        }
    }
    public static void vortexGrowthDust(float vortexScale, Vec3 center, double pixels, RandomGenerator random, Consumer<Dust> emit) {
        for (int i = 0; i < 10; i++) {
            Vec3 velocity = direction(random).scale(Math.sqrt(1800) * random.nextFloat(.05f, 1.2f) / pixels);
            emit.accept(new Dust(235, random.nextInt(3), center, velocity,
                    random.nextFloat(1.7f, 2.8f) - vortexScale * 1.5f, (float)(velocity.x * pixels)));
        }
    }
    public static void vortexGrowth(float vortexScale, Vec3 center, double pixels, RandomGenerator random, Consumer<Spark> emit) {
        for (int i = 0; i < 3; i++) {
            Vec3 velocity = direction(random).scale(Math.sqrt(800) * random.nextFloat(.1f, 1.1f) / pixels);
            emit.accept(new Spark(false, center.add(velocity.scale(2 * (vortexScale * 5f))), velocity,
                    random.nextFloat(1.55f, 2.75f) - vortexScale * 1.5f, 0xFF007F, 60));
        }
    }
    public static void fireballBlooms(boolean giga, int remaining, Vec3 center, Consumer<Light> emit) {
        if (remaining == 30) for (int i = 0; i < 2; i++) emit.accept(new Light(true, center, giga ? .85f : .7f, 0x79154D, 30));
        if (remaining == 15) emit.accept(new Light(true, center, giga ? .8f : .65f, 0xFF0000, 15));
        if (remaining == 8) emit.accept(new Light(true, center, giga ? .7f : .5f, 0xFFFFFF, 8));
    }
    public static void brotherLights(int delay, Vec3 bodyCenter, Vec3 cataclysm, Vec3 catastrophe,
                                     double pixels, Consumer<Light> emit) {
        if (!Double.isFinite(pixels) || pixels <= 0) return;
        if (delay >= 60 && delay < 105) {
            float completion = (delay - 105f) / (60f - 105f);
            Vec3 axis = cataclysm.subtract(catastrophe).normalize(), down = new Vec3(0, -1000 / pixels, 0);
            // Source bodyWidth44/bodyHeight42 are constants, independent of the forcefield AABB.
            Vec3 circle = bodyCenter.add(0, -21 / pixels, 0);
            Vec3 left = circle.subtract(axis.scale((44 * .45f) / pixels)), right = circle.add(axis.scale((44 * .45f) / pixels));
            float scale = 2.8f - delay * .01f;
            emit.accept(new Light(false, catmull(left.add(down), left, catastrophe, catastrophe.add(down), completion), scale, 0xFF0000, 20));
            emit.accept(new Light(false, catmull(right.add(down), right, cataclysm, cataclysm.add(down), completion), scale, 0xFF0000, 20));
        }
        if (delay == 59) {
            emit.accept(new Light(true, catastrophe, 1.4f, 0x00BFFF, 59));
            emit.accept(new Light(true, catastrophe, 1, 0x00BFFF, 59));
            emit.accept(new Light(true, cataclysm, 1.4f, 0x79154D, 59));
            emit.accept(new Light(true, cataclysm, 1, 0x79154D, 59));
        }
    }
    private static Vec3 catmull(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float t) {
        double t2 = t * t, t3 = t2 * t;
        return b.scale(2).add(c.subtract(a).scale(t)).add(a.scale(2).subtract(b.scale(5)).add(c.scale(4)).subtract(d).scale(t2))
                .add(a.scale(-1).add(b.scale(3)).subtract(c.scale(3)).add(d).scale(t3)).scale(.5);
    }
    private static Spark outward(Vec3 at, double pixels, RandomGenerator random, int tint) {
        Vec3 velocity = direction(random).scale(Math.sqrt(50) * random.nextFloat(.5f, 1.5f) / pixels);
        return new Spark(false, at, velocity, random.nextFloat(1.2f, 1.45f), random.nextBoolean() ? tint : 0xFF0000, 35);
    }
    private static Vec3 direction(RandomGenerator random) {
        // A uniform sphere replaces the source circle; no extra particles or speed factor for depth.
        double y = random.nextDouble(-1, 1), angle = random.nextDouble(Math.PI * 2), horizontal = Math.sqrt(1 - y * y);
        return new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
    }
}
