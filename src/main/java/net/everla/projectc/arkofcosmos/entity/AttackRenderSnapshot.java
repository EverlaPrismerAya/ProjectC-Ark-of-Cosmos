package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.nbt.CompoundTag;

/** One immutable-by-convention render trajectory per client tick. */
public final class AttackRenderSnapshot {
    private CompoundTag state = new CompoundTag();

    public void initialize(CompoundTag incoming) {
        if (state.isEmpty()) state = incoming.copy();
    }

    public void tick(CompoundTag incoming, String clock) {
        boolean fresh = state.isEmpty() || incoming.getInt(clock) != state.getInt(clock);
        state = incoming.copy();
        // The top-level state is the endpoint. Never replay an unchanged trajectory.
        if (!fresh) state.remove("Samples");
    }

    public CompoundTag state() { return state; }

    public static float partial(CompoundTag state, float partial) {
        return state.getList("Samples", 10).size() < 2 ? 1 : partial;
    }
}
