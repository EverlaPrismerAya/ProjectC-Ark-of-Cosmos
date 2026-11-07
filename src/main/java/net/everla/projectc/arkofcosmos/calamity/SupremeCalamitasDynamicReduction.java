package net.everla.projectc.arkofcosmos.calamity;

/** One-second rolling budget, fed only damage ready to be committed to the boss. */
public final class SupremeCalamitasDynamicReduction {
    private final double[] incoming = new double[20], applied = new double[20];
    private long lastTick = Long.MIN_VALUE;
    private double incomingTotal, appliedTotal;

    public double apply(long tick, double damage) {
        if (!Double.isFinite(damage) || damage < 0) throw new IllegalArgumentException("Invalid damage");
        if (lastTick == Long.MIN_VALUE || tick < lastTick || tick - lastTick >= 20) {
            java.util.Arrays.fill(incoming, 0);
            java.util.Arrays.fill(applied, 0);
            incomingTotal = appliedTotal = 0;
        } else {
            for (long time = lastTick + 1; time <= tick; time++) {
                int slot = Math.floorMod(time, 20);
                incomingTotal -= incoming[slot]; appliedTotal -= applied[slot];
                incoming[slot] = applied[slot] = 0;
            }
        }
        lastTick = tick;
        incomingTotal = Math.max(0, incomingTotal) + damage;
        appliedTotal = Math.max(0, appliedTotal);
        double budget = incomingTotal <= 160 ? incomingTotal : 160 + 160 * Math.log(incomingTotal / 160);
        double result = Math.max(0, Math.min(damage, budget - appliedTotal));
        int slot = Math.floorMod(tick, 20);
        incoming[slot] += damage; applied[slot] += result; appliedTotal += result;
        return result;
    }
}
