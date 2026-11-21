package net.everla.projectc.arkofcosmos.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.everla.projectc.arkofcosmos.calamity.client.ArkEffects;
import net.everla.projectc.arkofcosmos.client.render.ArkFxDraw;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** World-space cut snapshots outlive the entities that produced them. */
public final class ThreadripperEffects {
    private static final int FULL_PURGES = 12, MAX_PURGES = 48, MAX_MELEE = 64;
    private static final List<Cut> melee = new ArrayList<>(), purges = new ArrayList<>();
    private static ClientLevel world;
    private static long lastThunder = Long.MIN_VALUE;
    private record Cut(Vec3 at, Vec3 landing, AttackFrame frame, double radius, int owner, int seed, long start, boolean purge) {
        int life() { return purge ? 24 : 12; }
        int impact() { return purge ? 5 + seed % 3 : 0; }
    }

    private ThreadripperEffects() {}

    public static void tick(ClientLevel level) {
        if (world != level) { world = level; melee.clear(); purges.clear(); lastThunder = Long.MIN_VALUE; }
        if (level == null) return;
        long now = level.getGameTime();
        melee.removeIf(cut -> now - cut.start() >= cut.life());
        purges.removeIf(cut -> now - cut.start() >= cut.life());
        // Cascading targets share one nearby thunder cue, rather than each playing a loud sample.
        if (!purges.isEmpty() && (lastThunder == Long.MIN_VALUE || now - lastThunder >= 6)) {
            var mc = Minecraft.getInstance();
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            Cut nearest = null;
            double distance = 48 * 48;
            for (Cut cut : purges) {
                if (now - cut.start() != cut.impact()) continue;
                double d = eye.distanceToSqr(cut.landing());
                if (d < distance) { distance = d; nearest = cut; }
            }
            if (nearest != null) {
                Vec3 point = nearest.landing();
                level.playLocalSound(point.x, point.y, point.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.32f, .70f, false);
                lastThunder = now;
            }
        }
    }

    public static void receive(CompoundTag data) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        tick(level);
        if (level == null || !level.dimension().location().toString().equals(data.getString("Dimension"))) return;
        Vec3 at = ArkAttackEntity.vector(data, "At");
        Vec3 direction = ArkAttackEntity.vector(data, "F");
        Vec3 right = ArkAttackEntity.vector(data, "R");
        double size = Math.max(data.getDouble("Width"), data.getDouble("Height"));
        if (!finite(at) || !finite(direction) || !finite(right) || !Double.isFinite(size) || size <= 0) return;
        if (direction.cross(right).lengthSqr() < .01) return;
        boolean purge = data.getBoolean("Purge");
        double radius = Mth.clamp(size * .64, .65, 3.2);
        Vec3 landing = at;
        if (purge) {
            double height = data.getDouble("Height");
            if (!Double.isFinite(height)) return;
            Vec3 feet = at.add(0, -Math.max(0, height) * .5, 0);
            var surface = mc.player == null ? null : level.clip(new ClipContext(at, feet.add(0, -4, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            landing = (surface != null && surface.getType() == HitResult.Type.BLOCK ? surface.getLocation() : feet).add(0, .045, 0);
        }
        Cut cut = new Cut(at, landing, new AttackFrame(direction, right), radius, data.getInt("Owner"),
                data.getInt("Seed") & 255, level.getGameTime(), purge);
        var list = purge ? purges : melee;
        list.add(cut);
        if (!purge && mc.player != null && mc.player.getId() == cut.owner()
                && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player)
            ArkEffects.addShake(2.65);
        if (purge && list.size() > MAX_PURGES) {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            list.remove(list.stream().max(Comparator.comparingDouble(c -> c.at().distanceToSqr(eye))).orElseThrow());
        } else if (!purge && list.size() > MAX_MELEE) list.remove(0);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public static void render(Vec3 camera, float partial) {
        if (world == null || world != Minecraft.getInstance().level || melee.isEmpty() && purges.isEmpty()) return;
        var nearest = new ArrayList<>(purges);
        nearest.sort(Comparator.comparingDouble(c -> c.at().distanceToSqr(camera)));
        double now = world.getGameTime() + partial;
        boolean procedural = ArkFxDraw.riftShader != null;
        if (!procedural && ArkFxDraw.spriteShader == null) return;
        ArkFxDraw.begin("misc/white", ArkFxDraw.Blend.ADDITIVE, false);
        try {
            if (procedural) RenderSystem.setShader(() -> ArkFxDraw.riftShader);
            var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, ArkFxDraw.FORMAT);
            for (Cut cut : melee) draw(buffer, cut, now, procedural, procedural);
            for (int i = 0; i < nearest.size(); i++) draw(buffer, nearest.get(i), now, procedural, procedural && i < FULL_PURGES);
            var mesh = buffer.endOrDiscardIfEmpty();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } finally { ArkFxDraw.end(); }
    }

    private static void draw(BufferBuilder buffer, Cut cut, double now, boolean procedural, boolean detailed) {
        double age = Math.max(0, now - cut.start());
        double delay = cut.impact();
        double t = (age - delay) / (cut.life() - delay);
        if (t >= 1) return;
        var f = cut.frame();
        Vec3 up = f.normal();
        double angle = .48 + (cut.seed() % 19 - 9) * .035;
        Vec3 axis = f.right().scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        Vec3 side = axis.cross(f.forward()).scale(.86).add(f.forward().scale(.51));
        double radius = cut.radius();
        ArkFxDraw.ownerView(cut.owner(), Vec3.ZERO);
        float visibility = (float)ArkFxDraw.opacityAt(cut.at());
        float strikeVisibility = .20f + .80f * visibility;
        ArkFxDraw.fadeEye = null; // Opacity is applied per cut; the shared shader batch needs no eye uniform.
        if (cut.purge() && procedural) judgement(buffer, cut, age, strikeVisibility, detailed);
        if (!detailed) {
            double sinceImpact = age - delay;
            if (sinceImpact < 0 || sinceImpact >= 6) return;
            float alpha = (float)(.90 * strikeVisibility * Math.pow(1 - sinceImpact / 6, 2));
            quad(buffer, cut.at(), axis.scale(radius * 1.30), side.scale(.020 + radius * .017), rgba(.58f, .94f, 1, alpha), procedural ? -2 : 1);
            return;
        }
        if (t < 0) return;
        float alpha = (float)(visibility * (1 - Mth.smoothstep(Mth.clamp((t - .61) / .39, 0, 1))));
        float clock = riftClock(cut.seed(), t);
        // Start at full reach; only the wake expands. A bright narrow core leads the slower tear.
        double length = radius * (1.78 + .22 * Math.min(1, t * 9));
        double bloom = (age - delay) / (cut.purge() ? 8.0 : 6.2);
        if (bloom < 1) {
            float energy = strikeVisibility * (float)Math.pow(1 - bloom, 1.65);
            double spread = radius * (1.1 + 1.35 * (1 - Math.pow(1 - bloom, 3)));
            // Two intersecting local planes catch oblique views without glowing through terrain.
            quad(buffer, cut.at(), axis.scale(spread * 1.33), side.scale(spread * .72),
                    rgba(.44f, .91f, 1, energy), -10);
            quad(buffer, cut.at(), axis.scale(spread), axis.cross(side).scale(spread * .59),
                    rgba(.68f, .31f, 1, energy * .83f), -10);
        }
        double prism = (age - delay) / (cut.purge() ? 11.0 : 8.0);
        if (prism < 1) {
            double ease = 1 - Math.pow(1 - prism, 2.6);
            float opacity = strikeVisibility * (float)Math.sqrt(1 - prism);
            double front = radius * (.90 + 2.43 * ease);
            Vec3 prismRight = axis.scale(.88).add(side.scale(.475));
            Vec3 prismUp = side.scale(.88).subtract(axis.scale(.475));
            quad(buffer, cut.at().add(f.forward().scale(radius * ease * .30)), prismRight.scale(front), prismUp.scale(front * .72),
                    rgba(1, 1, 1, opacity), -11 - (float)prism);
        }
        double snap = (age - delay) / 3.6;
        if (snap < 1) {
            float hit = (float)(strikeVisibility * (1 - snap) * (1 - snap));
            quad(buffer, cut.at().add(f.forward().scale(-.015)), axis.scale(radius * 2.06),
                    side.scale(radius * .24), rgba(.66f, .92f, 1, hit), -6);
            double spread = radius * (.56 + 1.29 * (1 - (1 - snap) * (1 - snap)));
            quad(buffer, cut.at(), axis.scale(spread), side.scale(spread * .48),
                    rgba(.41f, .78f, 1, strikeVisibility * (float)(1 - snap) * .83f), -4 - (float)snap);
        }
        quad(buffer, cut.at(), axis.scale(length), side.scale(radius * .48), rgba(.67f, .91f, 1, alpha), clock);
        if (cut.purge() && t > .10) {
            double second = (t - .10) / .90;
            Vec3 crossed = f.right().scale(.53).subtract(up.scale(.848)).normalize();
            Vec3 crossSide = crossed.cross(f.forward()).scale(.88).subtract(f.forward().scale(.475));
            quad(buffer, cut.at().add(f.forward().scale(.025)), crossed.scale(length * .92), crossSide.scale(radius * .38),
                    rgba(.93f, .55f, 1, alpha * .82f), riftClock((cut.seed() + 61) & 255, second));
        }
        double dust = (t - .07) / .93;
        if (dust <= 0) return;
        double throwDistance = 1 - Math.pow(1 - dust, 3);
        for (int i = 0; i < (cut.purge() ? 12 : 9); i++) {
            double phase = cut.seed() * .194 + i * 2.39996;
            Vec3 burst = axis.scale(Math.cos(phase)).add(side.scale(Math.sin(phase) * .65));
            Vec3 at = cut.at().add(burst.scale(radius * (.24 + throwDistance * (1.27 + .25 * (i % 3)))))
                    .add(f.forward().scale(radius * throwDistance * (i % 3 - 1) * .28));
            double size = (.022 + .012 * (i % 4)) * Math.sqrt(radius) * (1 - dust * .64);
            float a = (float)(visibility * Math.min(1, dust * 16) * (1 - dust) * .86);
            boolean pixel = i % 3 == 0;
            Vec3 sparkAxis = burst.normalize(), sparkSide = axis.cross(side).cross(sparkAxis);
            quad(buffer, at, sparkAxis.scale(size * (pixel ? 1 : 1.9 + 3.1 * (1 - dust))), sparkSide.scale(size),
                    i % 2 == 0 ? rgba(.48f, .94f, 1, a) : rgba(.86f, .39f, 1, a), pixel ? -2 : -3);
        }
    }

    private static void judgement(BufferBuilder buffer, Cut cut, double age, float visibility, boolean detailed) {
        double impactAge = age - cut.impact();
        double groundRadius = cut.radius();
        double height = 19 + cut.seed() % 6 + groundRadius * 1.6;
        Vec3 ground = cut.landing();
        Vec3 sky = ground.add(0, height, 0);
        if (impactAge < 4.6) {
            double descent = Math.min(1, age / cut.impact());
            double above = height * (1 - descent * descent);
            double tail = Math.min(height - above, 8 + 12 * descent);
            if (impactAge >= 0) tail *= 1 - impactAge / 5;
            double width = groundRadius * (detailed ? 1.26 : .77) * (0.40 + .60 * descent);
            float energy = (float)(visibility * (impactAge < 0 ? .63 + .37 * descent : 1 - impactAge / 5));
            if (tail > .05) {
                Vec3 center = ground.add(0, above + tail * .5, 0);
                Vec3 vertical = new Vec3(0, tail * .5, 0);
                quad(buffer, center, vertical, new Vec3(width, 0, 0), rgba(.53f, .88f, 1, energy), -15 - (float)descent);
                quad(buffer, center, vertical, new Vec3(0, 0, width), rgba(.67f, .46f, 1, energy), -15 - (float)descent);
            }
            if (detailed && impactAge < 0) {
                double seal = groundRadius * (2.6 - descent * .9);
                quad(buffer, sky, new Vec3(seal, 0, 0), new Vec3(0, 0, seal),
                        rgba(1, 1, 1, visibility * .85f), -11 - (float)descent);
            }
        }
        if (!detailed || impactAge < 0 || impactAge >= 12) return;
        double travel = impactAge / 12;
        double spread = groundRadius * (1.25 + 3.5 * (1 - Math.pow(1 - travel, 2.3)));
        float wave = (float)(visibility * Math.pow(1 - travel, .68));
        quad(buffer, ground.add(0, .035, 0), new Vec3(spread, 0, 0), new Vec3(0, 0, spread),
                rgba(1, 1, 1, wave), -11 - (float)travel);
        if (impactAge < 4) {
            double blast = groundRadius * (1.6 + .42 * impactAge);
            float flare = (float)(visibility * (1 - impactAge / 4));
            Vec3 vertical = new Vec3(0, blast, 0);
            quad(buffer, ground.add(0, groundRadius * .36, 0), new Vec3(blast, 0, 0), vertical, rgba(.44f, .86f, 1, flare), -10);
            quad(buffer, ground.add(0, groundRadius * .36, 0), new Vec3(0, 0, blast), vertical, rgba(.66f, .34f, 1, flare * .8f), -10);
        }
    }

    // Pack the seed and normalized lifetime into the same per-vertex value for one world draw.
    private static float riftClock(int seed, double age) { return seed * 2 + .25f + (float)age * .5f; }

    private static float[] rgba(float r, float g, float b, float a) { return ArkFxDraw.rgba(r, g, b, a); }

    static void quad(BufferBuilder buffer, Vec3 at, Vec3 x, Vec3 y, float[] color, float parameter) {
        ArkFxDraw.vertex(buffer, at.subtract(x).subtract(y), color, 0, 0, parameter);
        ArkFxDraw.vertex(buffer, at.subtract(x).add(y), color, 0, 1, parameter);
        ArkFxDraw.vertex(buffer, at.add(x).add(y), color, 1, 1, parameter);
        ArkFxDraw.vertex(buffer, at.add(x).subtract(y), color, 1, 0, parameter);
    }
}
