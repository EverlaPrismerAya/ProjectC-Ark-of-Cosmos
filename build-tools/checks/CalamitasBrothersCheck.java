import java.util.ArrayList;
import java.util.Random;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers.Kind;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers.Shot;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.everla.projectc.arkofcosmos.calamity.CalamitasCollision;

public final class CalamitasBrothersCheck {
    private static final Vec3 X = new Vec3(1, 0, 0), TARGET = new Vec3(100, 80, 60);
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void step(SupremeCalamitasBrothers ai, double hp, boolean partner, Random rng, ArrayList<Shot> shots) {
        ai.step(Vec3.ZERO, TARGET, new Vec3(2, 0, 1), X, hp, partner ? .8 : 0, partner, rng, shots::add);
    }
    public static void main(String[] args) {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        require(!CalamitasCollision.sweptSphereHits(new Vec3(-.8, -.8, .5), new Vec3(-.8, -.8, .5), 1, box), "inflated-box corner must not hit sphere");
        require(CalamitasCollision.sweptSphereHits(new Vec3(-20, .5, .5), new Vec3(20, .5, .5), .01, box), "fast sweep crosses box");
        require(CalamitasCollision.sweptSphereHits(new Vec3(-20, -1, .5), new Vec3(20, -1, .5), 1, box), "swept sphere tangent");
        require(!CalamitasCollision.sweptSphereHits(new Vec3(-20, -1.01, .5), new Vec3(20, -1.01, .5), 1, box), "near tangent miss");
        for (Kind kind : Kind.values()) {
            var ai = new SupremeCalamitasBrothers(kind, true);
            var shots = new ArrayList<Shot>();
            var rng = new Random(17);
            for (int frame = 0; frame < 80; frame++) step(ai, 1, false, rng, shots);
            require(shots.isEmpty() && ai.cachedPartnerAlive(), "initial 120-frame delay / cached partner");
            while (shots.isEmpty()) step(ai, 1, false, rng, shots);
            require(!ai.cachedPartnerAlive() && !ai.phase2(), "partner cache changes after ordinary emission");
            require(ai.healthCap(true) == 1 && ai.healthCap(false) == .65, "revival-dependent health cap");
            step(ai, 1, false, rng, shots);
            require(ai.phase2(), "partner loss strengthens next frame");
            // Native healing can cross the health threshold again: Phase2 is derived, not sticky.
            var healed = new SupremeCalamitasBrothers(kind, false);
            step(healed, .5, true, rng, shots); require(healed.phase2(), "60% health gate");
            step(healed, .8, true, rng, shots); require(!healed.phase2(), "healed brother retains source phase calculation");

            // Every frame's complete persisted state must resume identically, including
            // big-attack countdown, combo, cached partner, dash and hand orientation.
            for (int frame = 0; frame < 6000; frame++) {
                var restored = new SupremeCalamitasBrothers(kind, false);
                restored.load(ai.save());
                var left = new ArrayList<Shot>(); var right = new ArrayList<Shot>();
                step(ai, .4, false, new Random(frame), left);
                step(restored, .4, false, new Random(frame), right);
                require(left.equals(right) && ai.cues().equals(restored.cues()) && ai.save().equals(restored.save()), "brother NBT continuation frame " + frame);
                for (Shot shot : left) require(Double.isFinite(shot.velocity().length()) && shot.velocity().length() <= 90.000001, "invalid speed");
            }
            var tag = ai.save(); tag.putDouble("Counter", Double.NaN);
            try { ai.load(tag); throw new AssertionError("invalid saved counter accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        for (Kind kind : Kind.values()) {
            var roar = new SupremeCalamitasBrothers(kind, true);
            var emitted = new ArrayList<Shot>();
            step(roar, .5, true, new Random(1), emitted);
            require(roar.cues().size() == 1 && roar.cues().get(0).sound().equals("brother_roar"), "health enrage emits one roar during initial delay");
            var legacyTag = roar.save(); legacyTag.remove("EnrageRoar");
            var legacy = new SupremeCalamitasBrothers(kind, false); legacy.load(legacyTag);
            step(legacy, .5, true, new Random(1), emitted);
            require(legacy.cues().size() == 1, "legacy NBT defaults to an unplayed roar");
            var restore = new SupremeCalamitasBrothers(kind, false); restore.load(roar.save());
            require(restore.cues().isEmpty(), "NBT never restores transient cues");
            step(restore, .8, true, new Random(1), emitted); step(restore, .5, true, new Random(1), emitted);
            require(restore.cues().isEmpty(), "healing and crossing threshold again do not repeat roar");
            var loss = new SupremeCalamitasBrothers(kind, true); var lost = loss.save(); lost.putBoolean("PartnerAlive", false); loss.load(lost);
            step(loss, .5, false, new Random(1), emitted); require(loss.cues().isEmpty(), "partner loss alone skips health roar source branch");
        }
        var dash = new SupremeCalamitasBrothers(Kind.CATASTROPHE, true);
        var state = dash.save(); state.putInt("BigTimer", 0); state.putInt("Limit", 0);
        state.putDouble("Delay", 120); state.putDouble("Counter", 49); state.putBoolean("PartnerAlive", false);
        dash.load(state);
        var shots = new ArrayList<Shot>(); step(dash, .5, false, new Random(1), shots);
        Vec3 locked = dash.velocity(); require(Math.abs(locked.length() - 90) < 1e-8, "dash launch speed");
        for (int frame = 0; frame < 30; frame++) {
            dash.step(Vec3.ZERO, TARGET.scale(-1), Vec3.ZERO, X, .5, 0, false, new Random(frame), shots::add);
            require(dash.velocity().normalize().distanceTo(locked.normalize()) < 1e-8, "dash must retain launch direction");
        }
        require(shots.stream().filter(s -> s.variant() >= 4 && s.variant() < 50).count() == 30, "dash emits 30 trails");
        require(shots.stream().filter(s -> s.variant() == 50).count() == 2, "solo dash emits two ending carriers");
        System.out.println("PASS: brothers source delay, cached partner, health cap, healing, 12000 NBT/cue continuations, once-only health roar and locked 30-trail dash");
    }
}
