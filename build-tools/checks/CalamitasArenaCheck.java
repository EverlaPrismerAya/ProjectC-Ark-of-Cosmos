import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasArena;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBarrage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CalamitasArenaCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1e-8, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) throws Exception {
        AABB projectileArena = new AABB(-10, -10, -10, 10, 10, 10);
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
            Vec3 wall = new Vec3(axis == 0 ? sign * 12 : 0, axis == 1 ? sign * 12 : 0, axis == 2 ? sign * 12 : 0);
            Vec3 outward = wall.scale(1.1), inward = wall.scale(.9);
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, Vec3.ZERO, wall, 1, 16, true, false, 100) == 0, "preserve crossing sweep");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, inward, 1, 16, true, false, 100) == 0, "preserve incoming shots");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, outward, 3, 16, true, false, 100) == 0, "preserve radius overlapping arena");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, outward, 1, 16, true, false, 0) == 1, "fade separating straight shot");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, outward, 1, 16, false, false, 100) == 0, "preserve curving shots and emitters");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, outward, 1, 16, true, true, 19) == 0, "split grace period");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall, outward, 1, 16, true, true, 20) == 1, "split grace expires at 20 MC ticks");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall.scale(20), outward.scale(20), 1, 16, true, false, 0) == 2, "retire separating straight shot beyond outer wall");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall.scale(20), inward.scale(20), 1, 16, true, false, 100) == 0, "preserve distant incoming shots");
            require(SupremeCalamitasArena.projectileBoundary(projectileArena, wall.scale(20), outward.scale(20), 1, 16, false, false, 100) == 0, "no arbitrary range cap for returning shots");
        }
        CalamitasBarrageCheck.main(args);
        CalamitasEncounterCheck.main(args);
        CalamitasBattleCheck.main(args);
        CalamitasCombatCheck.main(args);
        CalamitasBrothersCheck.main(args);
        CalamitasSummonsCheck.main(args);
        CalamitasSepulcherCheck.main(args);
        CalamitasShieldCheck.main(args);
        CalamitasAnimationCheck.main(args);
        CalamitasSpaceCheck.main(args);
        CalamitasArenaCollisionCheck.main(args);
        CalamitasArenaShrinkCheck.main(args);
        CalamitasArenaAppearanceCheck.main(args);
        CalamitasArenaVisualCheck.main(args);
        CalamitasSparksCheck.main(args);
        CalamitasCastLightCheck.main(args);
        CalamitasSlashDustCheck.main(args);
        CalamitasFistDustCheck.main(args);
        CalamitasFlameDustCheck.main(args);
        Vec3 center = new Vec3(23, 80, -39);
        var arena = new SupremeCalamitasArena(center, 16);
        AABB player = new AABB(center.add(-.3, 0, -.3), center.add(.3, 1.8, .3));
        for (int frame = 0; frame < 2000; frame++) arena.step(false, false, false, false, false, player);
        near(arena.bounds().maxX, 85.5, "ordinary arena right");
        near(arena.bounds().minY, 17.5, "ordinary arena down");
        for (int frame = 0; frame < 2000; frame++) arena.step(true, false, false, false, false, player);
        near(arena.bounds().minY, 64.375, "first worm arena floor");
        near(arena.bounds().maxZ, 32.875, "worm depth uses horizontal range");
        for (int frame = 0; frame < 2000; frame++) arena.step(true, false, false, true, false, player);
        near(arena.bounds().minY, 33.125, "second worm arena floor");
        for (int frame = 0; frame < 2000; frame++) arena.step(false, true, false, true, false, player);
        near(arena.bounds().maxY, 173.75, "seeker height");
        near(arena.bounds().maxX, 76.125, "seeker width");
        for (int frame = 0; frame < 2000; frame++) arena.step(false, false, true, true, false, player);
        near(arena.bounds().maxX, 106.125, "brother width");
        near(arena.bounds().maxY, 133.125, "brother height");

        arena.beginBarrage(new Vec3(0, 99, 1), 381);
        var shot = new SupremeCalamitasBarrage.Shot(SupremeCalamitasBarrage.Kind.HELLBLAST, 1000, -1000, -4, 0);
        var emission = arena.map(shot, center);
        near(emission.position().z, 23.5, "source horizontal maps into captured plane");
        near(emission.position().y, 142.5, "Terraria down maps to Minecraft down");
        near(emission.velocity().length(), .25, "speed is per source step, without extra tick multiplier");
        near(emission.velocity().z, -.25, "locked horizontal velocity");
        for (int index = 0; index < 300; index++) {
            var loaded = SupremeCalamitasArena.load(arena.save());
            require(arena.map(shot, center).equals(loaded.map(shot, center)), "depth distribution changed after reload");
        }
        var saved = arena.save();
        var frozen = SupremeCalamitasArena.load(saved);
        for (int frame = 0; frame < 2000; frame++) arena.step(false, false, false, true, true, player);
        require(arena.map(shot, center).equals(frozen.map(shot, center)), "barrage depth changed mid-round");
        near(arena.bounds().minY, 75.4375, "acceptance floor is source 73px below origin");
        near(arena.bounds().maxY, 105, "acceptance ceiling");

        AABB far = player.move(1000, 0, 0);
        for (int frame = 0; frame < 200; frame++) arena.step(false, false, false, true, false, far);
        near(arena.enrageSpeed(), 2, "enrage is capped");
        for (int frame = 0; frame < 200; frame++) arena.step(false, false, false, true, false, player);
        near(arena.enrageSpeed(), 1, "enrage recovers inside arena");
        saved.putDouble("AttackX", Double.NaN);
        try {
            SupremeCalamitasArena.load(saved);
            throw new AssertionError("invalid saved plane accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: 3D arena extents, locked plane/depth, NBT continuation, enrage and source units");
    }
}
