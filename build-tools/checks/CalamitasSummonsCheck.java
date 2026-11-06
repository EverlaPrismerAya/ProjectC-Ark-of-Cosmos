import java.util.Random;
import net.everla.projectc.arkofcosmos.entity.CalamitasSummons;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

public final class CalamitasSummonsCheck {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double value, double expected, String message) { require(Math.abs(value - expected) < 1e-8, message); }
    public static void main(String[] args) {
        Vec3 center = new Vec3(23, 80, -17), x = new Vec3(0, 0, 1);
        for (var kind : CalamitasSummons.Kind.values()) for (int seed = 0; seed < 100; seed++) {
            var batch = CalamitasSummons.begin(kind, center, x, 16, new Random(seed));
            var state = batch.save();
            require(CalamitasSummons.load(state).save().equals(state), "batch UUID/layout/RNG results changed on reload");
            var slots = state.getList("Slots", Tag.TAG_COMPOUND);
            require(slots.size() == (kind == CalamitasSummons.Kind.BROTHERS ? 2 : 10), "source summon count");
            require(!batch.complete() && batch.acceptedCount() == 0, "new batch accepted prematurely");
            for (int i = 0; i < slots.size(); i++) {
                var slot = slots.getCompound(i);
                if (kind == CalamitasSummons.Kind.HEARTS) {
                    near(slot.getDouble("X"), center.x, "locked heart plane");
                    near(slot.getDouble("Z"), center.z + (i % 2 == 0 ? -1 : 1) * (950 - i / 2 * 200) / 16d, "interleaved source heart x");
                    near(slot.getDouble("Y"), center.y + (812 - i / 2 * 100) / 16d, "source heart y including NPC half-height");
                    var endpoints = slot.getCompound("Connections").getList("Endpoints", Tag.TAG_COMPOUND);
                    require(endpoints.size() >= 2 && endpoints.size() <= 3, "source tendril count");
                    for (int j = 0; j < endpoints.size(); j++) {
                        var point = endpoints.getCompound(j);
                        near(point.getDouble("Y"), center.y + 1000 / 16d, "tendril anchored to fixed safeBox ceiling");
                        require(Math.abs(point.getDouble("Z") - center.z) <= 1000 / 16d + 1e-8, "tendril outside safeBox");
                    }
                } else if (kind == CalamitasSummons.Kind.BROTHERS)
                    near(slot.getDouble("Z"), center.z + (i == 0 ? 500 : -500) / 16d, "source brother cast position");
            }
        }
        System.out.println("PASS: 300 summon batch reloads, ten heart source positions, tendril counts/bounds and locked brother positions");
    }
}
