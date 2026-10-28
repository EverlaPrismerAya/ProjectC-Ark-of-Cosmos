package net.everla.projectc.arkofcosmos.calamity.util;

/** Attack curves transcribed from Calamity Team's local DLL; see reference/ATTACK-SPEC.md. */
public final class ArkCurves {
    private ArkCurves() {}

    public enum Ease { LINEAR, SINE_IN, SINE_OUT, SINE_BUMP, POLY_IN, POLY_OUT, POLY_IN_OUT, EXP_OUT, CIRC_IN }
    public record Segment(Ease ease, float start, float height, float shift, int degree) {
        Segment(Ease ease, float start, float height, float shift) { this(ease, start, height, shift, 1); }
    }

    private static final Segment[] SWING = {
            new Segment(Ease.EXP_OUT, 0, 0, .15f),
            new Segment(Ease.POLY_IN_OUT, .1f, .15f, .85f, 3),
            new Segment(Ease.LINEAR, .5f, 1, .2f)};
    private static final Segment[] SWIRL = {
            new Segment(Ease.SINE_IN, 0, 0, .25f), new Segment(Ease.SINE_OUT, .1f, .25f, .75f)};
    private static final Segment[] THROW = {
            new Segment(Ease.POLY_IN, 0, 1, -.2f, 3),
            new Segment(Ease.LINEAR, .35f, .8f, 0), new Segment(Ease.SINE_IN, .75f, 1, -1)};
    private static final Segment[] PARRY_THRUST = {
            new Segment(Ease.SINE_BUMP, 0, .2f, -.05f),
            new Segment(Ease.POLY_IN_OUT, .2f, .2f, .8f, 2),
            new Segment(Ease.CIRC_IN, .7f, 1, -.1f)};
    private static final Segment[] PARRY_CLOSE = {
            new Segment(Ease.SINE_BUMP, 0, 0, -.15f),
            new Segment(Ease.POLY_IN, .3f, 0, 1, 4), new Segment(Ease.LINEAR, .5f, 1, 0)};
    private static final Segment[] BLAST_THRUST = {
            new Segment(Ease.SINE_BUMP, 0, .2f, -.1f), new Segment(Ease.POLY_OUT, .3f, .2f, 3, 3)};
    private static final Segment[] BLAST_CLOSE = {
            new Segment(Ease.SINE_BUMP, 0, 0, -.15f),
            new Segment(Ease.POLY_IN, .35f, 0, 1, 4), new Segment(Ease.LINEAR, .5f, 1, 0)};

    public static float swing(float t) { return piecewise(t, SWING); }
    public static float swirl(float t) { return piecewise(t, SWIRL); }
    public static float throwing(float t) { return piecewise(t, THROW); }
    public static float throwScale(float t) { return ease(Ease.SINE_BUMP, clamp(t), 1); }
    public static float parryThrust(float t) { return piecewise(t, PARRY_THRUST); }
    public static float parryClose(float t) { return piecewise(t, PARRY_CLOSE); }
    public static float blastThrust(float t) { return piecewise(t, BLAST_THRUST); }
    public static float blastClose(float t) { return piecewise(t, BLAST_CLOSE); }

    public static float clamp(float t) { return Math.max(0, Math.min(1, t)); }
    public static float lerp(float from, float to, float t) { return from + (to - from) * t; }

    public static float piecewise(float progress, Segment... segments) {
        progress = clamp(progress);
        for (int i = 0; i < segments.length; i++) {
            Segment s = segments[i];
            float end = i + 1 == segments.length ? 1 : segments[i + 1].start;
            if (progress < s.start || (i + 1 < segments.length && progress >= end)) continue;
            return s.height + ease(s.ease, (progress - s.start) / (end - s.start), s.degree) * s.shift;
        }
        return 0;
    }

    private static float ease(Ease type, float t, int degree) {
        return switch (type) {
            case LINEAR -> t;
            case SINE_IN -> 1 - (float)Math.cos(t * (float)Math.PI / 2);
            case SINE_OUT -> (float)Math.sin(t * (float)Math.PI / 2);
            case SINE_BUMP -> (float)Math.sin(t * (float)Math.PI);
            case POLY_IN -> (float)Math.pow(t, degree);
            case POLY_OUT -> 1 - (float)Math.pow(1 - t, degree);
            case POLY_IN_OUT -> t < .5f ? (float)Math.pow(2, degree - 1) * (float)Math.pow(t, degree)
                    : 1 - (float)Math.pow(-2 * t + 2, degree) / 2;
            case EXP_OUT -> t == 1 ? 1 : 1 - (float)Math.pow(2, -10 * t);
            case CIRC_IN -> 1 - (float)Math.sqrt(1 - Math.pow(t, 2));
        };
    }
}
