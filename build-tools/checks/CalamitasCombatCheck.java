import java.util.ArrayList;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasCombat;
import net.minecraft.world.phys.Vec3;

public final class CalamitasCombatCheck {
    private static final Vec3 ORIGIN = Vec3.ZERO, TARGET = new Vec3(100, 80, 60), X = new Vec3(1, 0, 0);
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void step(SupremeCalamitasCombat combat, boolean second, boolean worm,
            Random rng, ArrayList<SupremeCalamitasCombat.Shot> shots) {
        combat.step(ORIGIN, TARGET, 5, X, second, worm, .5, 1, rng, shots::add);
    }
    public static void main(String[] args) {
        var combat = new SupremeCalamitasCombat();
        var shots = new ArrayList<SupremeCalamitasCombat.Shot>();
        var rng = new Random(17);
        for (int i = 0; i < 90; i++) step(combat, false, false, rng, shots);
        require(shots.isEmpty(), "hover strict >90 gate");
        step(combat, false, false, rng, shots);
        require(!shots.isEmpty(), "hover shoots on frame91");
        require(combat.cue() != null, "hover has one source sound cue per attack");
        step(combat, false, false, rng, shots);
        require(combat.cue() == null, "sound cue does not repeat on following frame");
        var slowed = new SupremeCalamitasCombat(); shots.clear();
        for (int i = 0; i < 180; i++) step(slowed, false, true, rng, shots);
        require(shots.isEmpty(), "worm halves hover shot timer");
        step(slowed, false, true, rng, shots);
        require(!shots.isEmpty(), "worm hover shoots frame181");

        var state = combat.save(); state.putInt("Mode", 1); state.putInt("Elapsed", 0); combat.load(state);
        step(combat, false, false, rng, shots);
        require(combat.cue() == SupremeCalamitasCombat.Cue.DASH, "dash cue on launch frame");
        Vec3 locked = combat.velocity();
        require(Math.abs(locked.length() - 34) < 1e-9 && combat.contact(), "launch speed/contact");
        for (int i = 0; i < 24; i++) combat.step(ORIGIN, new Vec3(-500, 0, -400), 5, X, false, false, .5, 1, rng, shots::add);
        require(combat.velocity().equals(locked) && combat.contact(), "dash never tracks moved target");
        step(combat, false, false, rng, shots);
        require(!combat.contact() && combat.velocity().length() < locked.length(), "dash contact ends frame25");
        for (int i = 25; i < 70; i++) step(combat, false, false, rng, shots);
        require(combat.mode() == 1, "first stage repeats charge");
        step(combat, false, false, rng, shots);
        for (int i = 0; i < 70; i++) step(combat, false, false, rng, shots);
        require(combat.mode() == -1, "first stage exactly two charges");

        state = combat.save(); state.putInt("Mode", 3); state.putInt("Elapsed", 0); state.putDouble("SideTimer", 0); combat.load(state);
        shots.clear();
        for (int i = 0; i < 480; i++) step(combat, false, false, rng, shots);
        require(shots.size() == 23, "first side barrage excludes terminal frame480");
        state.putInt("Mode", 3); combat.load(state); shots.clear();
        for (int i = 0; i < 300; i++) step(combat, true, false, rng, shots);
        require(shots.size() == 12, "last stage side barrage count");

        combat = new SupremeCalamitasCombat(); rng = new Random(91);
        for (int frame = 0; frame < 5000; frame++) {
            var restored = new SupremeCalamitasCombat(); restored.load(combat.save());
            var a = new ArrayList<SupremeCalamitasCombat.Shot>(); var b = new ArrayList<SupremeCalamitasCombat.Shot>();
            long seed = rng.nextLong();
            step(combat, frame > 2500, frame % 11 < 3, new Random(seed), a);
            step(restored, frame > 2500, frame % 11 < 3, new Random(seed), b);
            require(combat.save().equals(restored.save()) && a.equals(b) && combat.cue() == restored.cue(), "combat NBT resume frame" + frame);
            for (var shot : a) if (shot.kind() == SupremeCalamitasCombat.Kind.DART) {
                require(Math.abs(shot.velocity().length() - 10) < 1e-8 && shot.variant() == 4, "fan magnitude/variant");
            }
        }
        combat = new SupremeCalamitasCombat();
        state = combat.save(); state.putInt("Pause", 200); state.putDouble("ShotTimer", 90);
        combat.load(state); shots.clear();
        step(combat, false, false, new Random(17), shots);
        require(shots.isEmpty() && combat.cue() != null, "source sound survives first-stage attack pause without spawning shots");
        System.out.println("Calamitas ordinary combat timing, locked dash, counts and 5000 NBT resume frames passed");
    }
}
