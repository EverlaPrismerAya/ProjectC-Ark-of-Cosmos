import java.util.UUID;
import net.everla.projectc.arkofcosmos.entity.CalamitasBattleData;
import net.minecraft.nbt.CompoundTag;

public final class CalamitasBattleCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID heart = UUID.randomUUID(), otherHeart = UUID.randomUUID(), shot = UUID.randomUUID();
        var data = new CalamitasBattleData();
        data.begin(first); data.begin(second);
        require(data.add(first, heart, "heart") && data.add(second, otherHeart, "heart"), "members rejected");
        require(data.add(first, shot, "attack"), "attack rejected");
        require(data.living(first, "heart") == 1 && data.living(second, "heart") == 1, "battle counts mixed");
        UUID cancelled = UUID.randomUUID();
        require(data.add(first, cancelled, "seeker"), "pending summon rejected");
        data.cancelSummon(first, cancelled);
        require(data.living(first, "seeker") == 0 && data.add(first, cancelled, "seeker"), "cancelled summon cannot retry");
        data.cancelSummon(first, cancelled);
        // No entity lookup or chunk load is needed to keep an unresolved member alive.
        var load = CalamitasBattleData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        data = (CalamitasBattleData) load.invoke(null, data.save(new CompoundTag()));
        require(data.living(first, "heart") == 1, "unloaded heart lost at restart");
        UUID seeker = UUID.randomUUID(), cataclysm = UUID.randomUUID(), catastrophe = UUID.randomUUID();
        data.add(first, seeker, "seeker"); data.add(first, cataclysm, "cataclysm"); data.add(first, catastrophe, "catastrophe");
        data.reconcileSummons(first, java.util.Set.of(heart, seeker, cataclysm, catastrophe));
        require(data.living(first, "heart") == 1 && data.livingBrothers(first) == 2, "nearby living summons retired");
        data.reconcileSummons(first, java.util.Set.of(catastrophe));
        require(data.living(first, "seeker") == 0 && data.livingBrothers(first) == 1, "missing summons retained");
        data.reconcileSummons(first, java.util.Set.of());
        require(data.livingBrothers(first) == 0, "missing brothers still block transition");
        require(data.living(first, "heart") == 0 && data.living(second, "heart") == 1, "boss heart completion crossed ownership");
        require(data.living(first, "attack") == 1, "heart completion retired another role");
        data = (CalamitasBattleData) load.invoke(null, data.save(new CompoundTag()));
        require(data.confirmedDead(first, heart), "boss heart completion lost across restart");
        data.died(first, heart);
        require(data.living(first, "heart") == 0 && data.living(second, "heart") == 1, "death belongs to wrong battle");
        require(!data.add(first, heart, "heart"), "confirmed dead member resurrected by replay");
        data.forget(first, shot);
        require(data.living(first, "attack") == 0, "completed attack retained");
        data.end(first);
        require(!data.active(first) && data.active(second), "ending first battle affected second");
        require(!data.add(first, UUID.randomUUID(), "attack"), "ended battle accepted new attack");
        require(data.save(new CompoundTag()).getList("Battles", 10).size() == 1, "dead members retain ended battle forever");
        data = (CalamitasBattleData) load.invoke(null, data.save(new CompoundTag()));
        require(!data.active(first) && data.active(second), "ended marker lost across restart");
        data.forget(first, heart);
        require(data.save(new CompoundTag()).getList("Battles", 10).size() == 1, "fully cleaned battle retained");
        UUID brotherOwner = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), revived = UUID.randomUUID();
        data.begin(brotherOwner); data.add(brotherOwner, a, "cataclysm"); data.add(brotherOwner, b, "catastrophe");
        data.updateBrotherHealth(brotherOwner, a, .37);
        data = (CalamitasBattleData) load.invoke(null, data.save(new CompoundTag()));
        require(data.brotherHealth(brotherOwner, "cataclysm") == .37, "unloaded brother health snapshot lost");
        require(data.livingBrothers(brotherOwner) == 2 && data.brotherRevivalAvailable(brotherOwner), "initial brothers");
        data.died(brotherOwner, a); data.died(brotherOwner, b);
        var payload = new CompoundTag(); payload.putUUID("UUID", revived); payload.putString("Kind", "cataclysm");
        require(data.queueBrotherRevival(brotherOwner, b, payload), "one-time revival rejected");
        require(!data.queueBrotherRevival(brotherOwner, a, payload), "revival consumed twice");
        require(data.livingBrothers(brotherOwner) == 1 && !data.brotherRevivalAvailable(brotherOwner), "pending revival does not shield phase");
        data = (CalamitasBattleData) load.invoke(null, data.save(new CompoundTag()));
        require(data.pendingBrotherRevival(brotherOwner).equals(payload), "pending revival lost across restart");
        data.add(brotherOwner, revived, "cataclysm"); data.cancelSummon(brotherOwner, revived);
        require(data.livingBrothers(brotherOwner) == 1 && !data.completeBrotherRevival(brotherOwner, revived), "cancelled join cleared pending revival");
        require(data.add(brotherOwner, revived, "cataclysm") && data.completeBrotherRevival(brotherOwner, revived), "revival retry failed");
        require(data.livingBrothers(brotherOwner) == 1 && data.pendingBrotherRevival(brotherOwner) == null, "revival double counted");
        data.died(brotherOwner, revived);
        require(data.livingBrothers(brotherOwner) == 0 && !data.queueBrotherRevival(brotherOwner, revived, payload), "second revival allowed");
        data.end(brotherOwner);
        System.out.println("PASS: battle ownership, unresolved members, native death journal, restart and isolated cleanup");
    }
}
