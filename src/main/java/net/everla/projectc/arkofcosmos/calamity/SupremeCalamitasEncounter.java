package net.everla.projectc.arkofcosmos.calamity;

/** Server-side encounter gates for the ordinary-world death-mode port (Calamity 1a8cebd).
 * Each advance is one source frame. The entity supplies battle-owned living add counts;
 * unloaded or unresolved adds must not be reported as dead.
 */
public final class SupremeCalamitasEncounter {
    public enum Phase { BARRAGE, HEART_CAST, COMBAT, BROTHERS_WARNING, BROTHERS_CAST, BROTHERS, TRANSITION, ACCEPTANCE, FINISHED }
    public enum Event {
        NONE, BARRAGE_ENDED, BARRAGE_STARTED, HEART_CAST_STARTED, SUMMON_HEARTS,
        BROTHERS_WARNING, BROTHERS_CAST_STARTED, SUMMON_BROTHERS, SECOND_STAGE,
        SUMMON_SEEKERS, DESPAIR_6, DESPAIR_4, DESPAIR_2, SURRENDER,
        ACCEPTANCE_TEXT_1, ACCEPTANCE_TEXT_2, ACCEPTANCE_TEXT_3, FINISHED
    }
    public static final int HEART_CAST_FRAMES = 75;
    public static final int BROTHERS_WARNING_FRAMES = 15;
    public static final int BROTHERS_CAST_FRAMES = 150;
    public static final int ACCEPTANCE_FRAMES = 1200;
    public static final int SECOND_STAGE_TRANSITION_FRAMES = 200;
    private Phase phase = Phase.BARRAGE;
    private int round = 1;
    private int phaseFrame;
    private int heartSummons;
    private boolean brothersSummoned;
    private boolean seekersSummoned;
    private boolean secondStage;
    private int despair;

    public Phase phase() { return phase; }
    public int round() { return round; }
    public int phaseFrame() { return phaseFrame; }
    public int heartSummons() { return heartSummons; }
    public boolean secondStage() { return secondStage; }
    public boolean seekersSummoned() { return seekersSummoned; }
    public int despair() { return despair; }

    public record State(Phase phase, int round, int phaseFrame, int heartSummons,
                        boolean brothersSummoned, boolean seekersSummoned,
                        boolean secondStage, int despair) {}

    public State save() {
        return new State(phase, round, phaseFrame, heartSummons, brothersSummoned,
                seekersSummoned, secondStage, despair);
    }

    public void restore(State state) {
        if (state == null || state.phase == null || state.round < 1 || state.round > 5
                || state.phaseFrame < 0 || state.phaseFrame > limit(state.phase)
                || state.heartSummons < 0 || state.heartSummons > 2
                || state.despair < 0 || state.despair > 3
                || (state.secondStage && !state.brothersSummoned)
                || (state.heartSummons == 2 && state.round != 5))
            throw new IllegalArgumentException("Invalid Calamitas encounter state");
        phase = state.phase;
        round = state.round;
        phaseFrame = state.phaseFrame;
        heartSummons = state.heartSummons;
        brothersSummoned = state.brothersSummoned;
        seekersSummoned = state.seekersSummoned;
        secondStage = state.secondStage;
        despair = state.despair;
    }

    private static int limit(Phase phase) {
        return switch (phase) {
            case BARRAGE -> SupremeCalamitasBarrage.DURATION - 1;
            case HEART_CAST -> HEART_CAST_FRAMES - 1;
            case BROTHERS_WARNING -> BROTHERS_WARNING_FRAMES - 1;
            case BROTHERS_CAST -> BROTHERS_CAST_FRAMES - 1;
            case ACCEPTANCE -> ACCEPTANCE_FRAMES - 1;
            case TRANSITION -> SECOND_STAGE_TRANSITION_FRAMES - 1;
            default -> 0;
        };
    }

    /** Advance after simulating this frame's attacks, so the last barrage frame still fires. */
    public Event advance(double health, double maximumHealth, int livingHearts, int livingBrothers) {
        return advance(health, maximumHealth, livingHearts, livingBrothers, false);
    }
    public Event advance(double health, double maximumHealth, int livingHearts, int livingBrothers, boolean rematch) {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth < 100
                || health <= 0 || livingHearts < 0 || livingBrothers < 0)
            throw new IllegalArgumentException("Invalid encounter input");
        switch (phase) {
            case FINISHED: return Event.NONE;
            case BARRAGE:
                if (++phaseFrame < SupremeCalamitasBarrage.DURATION) return Event.NONE;
                enter(Phase.COMBAT);
                return Event.BARRAGE_ENDED;
            case HEART_CAST:
                if (++phaseFrame < HEART_CAST_FRAMES) return Event.NONE;
                // This is tied to the cast, not the stale upstream <=8% health check.
                heartSummons++;
                enter(Phase.COMBAT);
                return Event.SUMMON_HEARTS;
            case BROTHERS_WARNING:
                if (++phaseFrame < BROTHERS_WARNING_FRAMES) return Event.NONE;
                enter(Phase.BROTHERS_CAST);
                return Event.BROTHERS_CAST_STARTED;
            case BROTHERS_CAST:
                if (++phaseFrame < BROTHERS_CAST_FRAMES) return Event.NONE;
                brothersSummoned = true;
                enter(Phase.BROTHERS);
                return Event.SUMMON_BROTHERS;
            case BROTHERS:
                if (livingBrothers > 0) return Event.NONE;
                secondStage = true;
                enter(Phase.TRANSITION);
                return Event.SECOND_STAGE;
            case TRANSITION:
                if (++phaseFrame == SECOND_STAGE_TRANSITION_FRAMES) enter(Phase.COMBAT);
                return Event.NONE;
            case ACCEPTANCE:
                phaseFrame++;
                if (phaseFrame >= (rematch ? ACCEPTANCE_FRAMES - 720 : ACCEPTANCE_FRAMES)) {
                    enter(Phase.FINISHED);
                    return Event.FINISHED;
                }
                if (rematch) return Event.NONE;
                return switch (ACCEPTANCE_FRAMES - phaseFrame) {
                    case 900 -> Event.ACCEPTANCE_TEXT_1;
                    case 600 -> Event.ACCEPTANCE_TEXT_2;
                    case 300 -> Event.ACCEPTANCE_TEXT_3;
                    default -> Event.NONE;
                };
            default: break;
        }
        // A completed forced phase is committed before allowing the next health trigger.
        if (heartSummons == 0) {
            enter(Phase.HEART_CAST);
            return Event.HEART_CAST_STARTED;
        }
        if (livingHearts > 0) return Event.NONE;
        double ratio = health / maximumHealth;
        if (round == 1 && ratio <= .75 || round == 2 && ratio <= .5)
            return beginBarrage();
        if (round >= 3 && !brothersSummoned) {
            enter(Phase.BROTHERS_WARNING);
            return Event.BROTHERS_WARNING;
        }
        if (round == 3 && ratio <= .3) return beginBarrage();
        if (round >= 4 && ratio <= .2 && !seekersSummoned) {
            seekersSummoned = true;
            return Event.SUMMON_SEEKERS;
        }
        if (round == 4 && ratio <= .1) return beginBarrage();
        if (round == 5 && heartSummons == 1) {
            enter(Phase.HEART_CAST);
            return Event.HEART_CAST_STARTED;
        }
        if (round == 5 && heartSummons == 2) {
            // Queue mandatory dialogue even if an externally altered health value crossed
            // multiple thresholds. Normal damage is held at the next gate by the entity.
            if (despair == 0 && ratio <= .06) { despair = 1; return Event.DESPAIR_6; }
            if (despair == 1 && ratio <= .04) { despair = 2; return Event.DESPAIR_4; }
            if (despair == 2 && ratio <= .02) { despair = 3; return Event.DESPAIR_2; }
            if (health < 2 && despair == 3) {
                enter(Phase.ACCEPTANCE);
                return Event.SURRENDER;
            }
        }
        return Event.NONE;
    }

    private Event beginBarrage() {
        round++;
        enter(Phase.BARRAGE);
        return Event.BARRAGE_STARTED;
    }

    private void enter(Phase next) { phase = next; phaseFrame = 0; }

    public boolean invulnerable(int livingHearts) {
        return invulnerable(livingHearts, 0);
    }

    public boolean invulnerable(int livingHearts, int livingSeekers) {
        return phase != Phase.COMBAT || livingHearts > 0 || heartSummons == 0
                || secondStage && livingSeekers > 0
                || round == 5 && heartSummons < 2;
    }

    /** Native damage may reach this value, but cannot skip a mandatory health gate.
     * This does not own health: the entity still uses vanilla armor/events/absorption.
     * Absolute 1 HP permits the upstream <2 HP acceptance condition at any configured max.
     */
    public double damageFloor(double maximumHealth) {
        if (!Double.isFinite(maximumHealth) || maximumHealth < 100)
            throw new IllegalArgumentException("Calamitas max health must be at least 100");
        double ratio;
        if (round == 1) ratio = .75;
        else if (round == 2) ratio = .5;
        else if (round == 3) ratio = .3;
        else if (!seekersSummoned) ratio = .2;
        else if (round == 4) ratio = .1;
        else ratio = switch (despair) { case 0 -> .06; case 1 -> .04; case 2 -> .02; default -> 0; };
        return Math.max(1, maximumHealth * ratio);
    }
}
