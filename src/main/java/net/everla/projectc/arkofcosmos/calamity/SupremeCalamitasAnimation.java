package net.everla.projectc.arkofcosmos.calamity;

import net.minecraft.nbt.CompoundTag;

/** Source FindFrame clock; family IDs match the original seven six-frame strips. */
public final class SupremeCalamitasAnimation {
    public enum Family { UPWARD_DRAFT, FASTER_UPWARD_DRAFT, CASTING, BLAST_CAST, BLAST_PUNCH_CAST, OUTWARD_HAND_CAST, PUNCH_HAND_CAST }
    private Family family = Family.UPWARD_DRAFT;
    private double counter, shotTime;
    private float speed = .15f;
    private int frame, shootRate = 100;
    private boolean driven;

    public void step(SupremeCalamitasEncounter.Phase phase, int mode, int elapsed, boolean second, boolean worm,
                     double beforeShot, double afterShot) {
        family = Family.UPWARD_DRAFT; speed = .15f; driven = false;
        if (phase == SupremeCalamitasEncounter.Phase.BARRAGE || phase == SupremeCalamitasEncounter.Phase.HEART_CAST
                || phase == SupremeCalamitasEncounter.Phase.BROTHERS_CAST || phase == SupremeCalamitasEncounter.Phase.TRANSITION)
            family = Family.CASTING;
        else if (phase == SupremeCalamitasEncounter.Phase.COMBAT) {
            if (mode == 2 || mode == 0 && !second) family = Family.FASTER_UPWARD_DRAFT;
            else if (mode == 3) {
                family = second ? Family.PUNCH_HAND_CAST : Family.OUTWARD_HAND_CAST;
                if (second) speed = .245f;
            } else if (mode == 4) {
                if (!second) { family = Family.BLAST_CAST; speed = .175f; }
                else if (elapsed > 40 && (beforeShot + 1 > (worm ? 200 : 100) - 18 || beforeShot + 1 <= 15)) {
                    family = Family.BLAST_PUNCH_CAST; speed = 0;
                }
            }
        }
        shootRate = worm ? 200 : 100; shotTime = afterShot;
        driven = family == Family.BLAST_PUNCH_CAST && (shotTime > shootRate - 18 || shotTime <= 15);
        if (driven) frame = punchFrame(shotTime, shootRate);
        else { counter = (counter + speed) % 6; frame = (int)counter; }
    }
    public void idle() { step(SupremeCalamitasEncounter.Phase.COMBAT, -1, 0, false, false, 0, 0); }
    private static int punchFrame(double time, int rate) {
        return time > rate - 18 ? (int)(3 * Math.max(0, Math.min(1, (time - (rate - 18)) / 18)))
                : (int)(3 + 2 * Math.max(0, Math.min(1, time / 15)));
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Family", family.ordinal()); tag.putDouble("Counter", counter);
        tag.putFloat("Speed", speed); tag.putInt("Frame", frame); tag.putBoolean("Driven", driven);
        tag.putDouble("ShotTime", shotTime); tag.putInt("ShootRate", shootRate); return tag;
    }
    public void load(CompoundTag tag) {
        if (tag.isEmpty()) { family = Family.UPWARD_DRAFT; counter = shotTime = 0; speed = .15f; frame = 0; shootRate = 100; driven = false; return; }
        int f = tag.getInt("Family"), n = tag.getInt("Frame"), rate = tag.getInt("ShootRate");
        double c = tag.getDouble("Counter"), time = tag.getDouble("ShotTime"); float s = tag.getFloat("Speed");
        if (f < 0 || f >= Family.values().length || n < 0 || n > 5 || !Double.isFinite(c) || c < 0 || c >= 6
                || !Float.isFinite(s) || s < 0 || s > .245f || !Double.isFinite(time) || time < 0 || time > 281
                || rate != 100 && rate != 200) throw new IllegalArgumentException("Invalid Calamitas animation state");
        family = Family.values()[f]; counter = c; speed = s; frame = n; driven = tag.getBoolean("Driven"); shotTime = time; shootRate = rate;
    }
}
