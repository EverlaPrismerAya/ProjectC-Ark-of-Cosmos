package net.everla.projectc.arkofcosmos;

public final class ArkCurvesCheck {
    public static void main(String[] args) {
        close(ArkCurves.swing(0), 0);
        close(ArkCurves.swing(.1f), .15f);
        close(ArkCurves.swing(.3f), .575f);
        close(ArkCurves.swing(.5f), 1);
        close(ArkCurves.swing(1), 1.2f);
        close(ArkCurves.swirl(.1f), .25f);
        close(ArkCurves.swirl(1), 1);
        close(ArkCurves.throwing(0), 1);
        close(ArkCurves.throwing(.35f), .8f);
        // The original jumps at .75; smoothing this discontinuity would change the attack.
        close(ArkCurves.throwing(.75f), 1);
        close(ArkCurves.throwing(1), 0);
        close(ArkCurves.throwScale(.5f), 1);
        close(ArkCurves.parryThrust(1), .9f);
        close(ArkCurves.blastThrust(1), 3.2f);
        close(ArkCurves.blastClose(.5f), 1);
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000f;
            if (!Float.isFinite(ArkCurves.swing(t)) || !Float.isFinite(ArkCurves.throwing(t))
                    || !Float.isFinite(ArkCurves.parryThrust(t))) throw new AssertionError("Nonfinite curve");
        }
        System.out.println("Ark curve checkpoints passed");
    }

    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > .00001f) throw new AssertionError(actual + " != " + expected);
    }
}
