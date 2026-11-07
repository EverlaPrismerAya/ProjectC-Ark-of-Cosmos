import java.util.ArrayList;
import java.util.List;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Event;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasEncounter.Phase;

public final class CalamitasEncounterCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        runEncounter(2300, true);
        runEncounter(100, false);
        runEncounter(4444, false);
        runEncounter(1234.5678f, false);
        floatDespairGates();
        secondSummonAtTenPercent();
        rematchAcceptance();
        System.out.println("PASS: mandatory phases under burst damage, both brothers, casts, surrender, save/restore");
    }

    private static void rematchAcceptance() {
        var encounter = new SupremeCalamitasEncounter();
        encounter.restore(new SupremeCalamitasEncounter.State(Phase.ACCEPTANCE, 5, 0, 2,
                true, true, true, 3));
        for (int frame = 1; frame <= 480; frame++) {
            var resumed = new SupremeCalamitasEncounter();
            resumed.restore(encounter.save());
            encounter = resumed;
            Event event = encounter.advance(1, 2300, 0, 0, true);
            require(event == (frame == 480 ? Event.FINISHED : Event.NONE),
                    "rematch ends at720 remaining without first-battle acceptance dialogue");
        }
        require(encounter.phase() == Phase.FINISHED, "rematch completed");
    }

    private static void runEncounter(double maxHealth, boolean externalBurst) {
        var encounter = new SupremeCalamitasEncounter();
        var events = new ArrayList<Event>();
        int hearts = 0, brothers = 0, heartLife = 0, brotherLife = 0;
        int[] barrageFrames = new int[5];
        int castFrames = 0, brotherCastFrames = 0, warningFrames = 0, acceptanceFrames = 0, transitionFrames = 0;
        double health = maxHealth;
        for (int frame = 0; frame < 12000 && encounter.phase() != Phase.FINISHED; frame++) {
            // Round-trip every intermediate phase as an NBT-facing snapshot would do.
            var loaded = new SupremeCalamitasEncounter();
            loaded.restore(encounter.save());
            require(loaded.save().equals(encounter.save()), "save round-trip");
            if (hearts > 0 && --heartLife == 0) hearts = 0;
            if (brothers > 0) {
                brotherLife--;
                if (brotherLife == 100) brothers = 1;
                if (brotherLife == 0) brothers = 0;
            }
            switch (encounter.phase()) {
                case BARRAGE -> barrageFrames[encounter.round() - 1]++;
                case HEART_CAST -> castFrames++;
                case BROTHERS_WARNING -> warningFrames++;
                case BROTHERS_CAST -> brotherCastFrames++;
                case ACCEPTANCE -> acceptanceFrames++;
                case TRANSITION -> transitionFrames++;
                default -> { }
            }
            if (externalBurst) health = 1;
            else if (!encounter.invulnerable(hearts)) health = (float)encounter.damageFloor(maxHealth);
            Event event = encounter.advance(health, maxHealth, hearts, brothers);
            Event afterReload = loaded.advance(health, maxHealth, hearts, brothers);
            require(event == afterReload && loaded.save().equals(encounter.save()), "resume diverged at " + frame);
            if (event != Event.NONE) events.add(event);
            if (event == Event.SUMMON_HEARTS) { hearts = 10; heartLife = 200; }
            if (event == Event.SUMMON_BROTHERS) { brothers = 2; brotherLife = 200; }
            if (event == Event.SECOND_STAGE) require(brothers == 0, "one surviving brother must hold phase one");
            if (encounter.phase() == Phase.BROTHERS) require(encounter.invulnerable(hearts), "brother shield");
        }
        require(encounter.phase() == Phase.FINISHED, "encounter did not finish");
        require(events.equals(List.of(Event.BARRAGE_ENDED, Event.HEART_CAST_STARTED, Event.SUMMON_HEARTS,
                Event.BARRAGE_STARTED, Event.BARRAGE_ENDED, Event.BARRAGE_STARTED, Event.BARRAGE_ENDED,
                Event.BROTHERS_WARNING, Event.BROTHERS_CAST_STARTED, Event.SUMMON_BROTHERS,
                Event.SECOND_STAGE, Event.BARRAGE_STARTED, Event.BARRAGE_ENDED, Event.SUMMON_SEEKERS,
                Event.BARRAGE_STARTED, Event.BARRAGE_ENDED, Event.HEART_CAST_STARTED, Event.SUMMON_HEARTS,
                Event.DESPAIR_6, Event.DESPAIR_4, Event.DESPAIR_2, Event.SURRENDER,
                Event.ACCEPTANCE_TEXT_1, Event.ACCEPTANCE_TEXT_2, Event.ACCEPTANCE_TEXT_3, Event.FINISHED)),
                "missing, repeated or reordered mandatory event: " + events);
        for (int frames : barrageFrames) require(frames == 900, "barrage duration changed");
        require(castFrames == 150 && warningFrames == 15 && brotherCastFrames == 150,
                "cast durations changed");
        require(acceptanceFrames == 1200, "acceptance duration changed");
        require(transitionFrames == 200, "second-stage transition must last two 100-frame halves");
        for (int frame = 0; frame < 20; frame++)
            require(encounter.advance(1, maxHealth, 0, 0) == Event.NONE, "duplicate settlement");
    }

    private static void floatDespairGates() {
        for (float maximum : new float[]{100, 4444, 1234.5678f, 9000.3f}) {
            double[] fractions = {.06, .04, .02};
            Event[] expected = {Event.DESPAIR_6, Event.DESPAIR_4, Event.DESPAIR_2};
            for (int index = 0; index < fractions.length; index++) {
                var encounter = new SupremeCalamitasEncounter();
                encounter.restore(new SupremeCalamitasEncounter.State(Phase.COMBAT, 5, 0, 2, true, true, true, index));
                float floor = (float)encounter.damageFloor(maximum);
                require(encounter.advance(Math.nextUp(floor), maximum, 0, 0) == Event.NONE, "dialogue triggered above native float floor");
                require(encounter.advance(floor, maximum, 0, 0) == expected[index], "float floor stalled at " + maximum + " * " + fractions[index]);
            }
        }
    }
    private static void secondSummonAtTenPercent() {
        var encounter = new SupremeCalamitasEncounter();
        encounter.restore(new SupremeCalamitasEncounter.State(Phase.COMBAT, 5, 0, 1, true, true, true, 0));
        require(encounter.advance(230, 2300, 0, 0) == Event.HEART_CAST_STARTED,
                "second Sepulcher must start at end of fifth barrage, not <=8%");
        for (int frame = 1; frame <= 75; frame++)
            require(encounter.advance(230, 2300, 0, 0) == (frame == 75 ? Event.SUMMON_HEARTS : Event.NONE),
                    "heart cast timing");
        for (int frame = 0; frame < 200; frame++)
            require(encounter.advance(230, 2300, 0, 0) == Event.NONE, "second summon repeated above 8%");
        require(encounter.heartSummons() == 2, "second summon flag not committed");
        require(encounter.invulnerable(0, 1), "living seekers must shield phase-two boss");
        require(encounter.advance(2, 2300, 0, 0) == Event.DESPAIR_6, "first low-health dialogue");
        require(encounter.advance(2, 2300, 0, 0) == Event.DESPAIR_4, "second low-health dialogue");
        require(encounter.advance(2, 2300, 0, 0) == Event.DESPAIR_2, "third low-health dialogue");
        require(encounter.advance(2, 2300, 0, 0) == Event.NONE, "2 HP is not acceptance");
        require(encounter.advance(1.99, 2300, 0, 0) == Event.SURRENDER, "<2 HP is acceptance");
    }
}
