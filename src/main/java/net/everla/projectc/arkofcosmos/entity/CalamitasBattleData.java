package net.everla.projectc.arkofcosmos.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Calamitas-only ownership journal. Unloaded members remain alive until an observed
 * native death; ended battles also clean members which load after the boss is gone.
 */
public final class CalamitasBattleData extends SavedData {
    private final Map<UUID, Battle> battles = new HashMap<>();
    private final Map<UUID,UUID> conversions=new HashMap<>();
    private final Set<UUID> settlements=new HashSet<>();
    public boolean converted(UUID boss){return conversions.containsKey(boss);}
    public void converted(UUID boss,UUID npc){if(npc!=null && conversions.putIfAbsent(boss,npc)==null)setDirty();}
    public boolean settled(UUID boss){return settlements.contains(boss);}
    public void settle(UUID boss){if(settlements.add(boss))setDirty();}
    private boolean defeated;
    private boolean acceptanceMusicPlayed;
    public static boolean beginAcceptanceMusic(ServerLevel level, UUID owner) {
        var data = get(level.getServer().overworld());
        if (data.acceptanceMusicPlayed) return false;
        data.acceptanceMusicPlayed = true; data.setDirty();
        net.everla.projectc.arkofcosmos.network.ArkNetwork.calamitasMusic(owner, true);
        return true;
    }
    /** Progress is world-wide, while ownership journals remain dimension-local. */
    public static boolean defeated(ServerLevel level) { return get(level.getServer().overworld()).defeated; }
    public static void markDefeated(ServerLevel level) {
        var data = get(level.getServer().overworld());
        if (!data.defeated) { data.defeated = true; data.setDirty(); }
    }
    private static final class Battle {
        boolean ended;
        boolean brotherRevivalSpent;
        CompoundTag pendingBrother;
        final Map<UUID, Double> brotherHealth = new HashMap<>();
        final Map<UUID, String> members = new HashMap<>();
        final Set<UUID> dead = new HashSet<>();
    }
    public static CalamitasBattleData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CalamitasBattleData::load,
                CalamitasBattleData::new, "projectc_calamitas_battles");
    }
    public void begin(UUID owner) {
        if (!battles.containsKey(owner)) { battles.put(owner, new Battle()); setDirty(); }
    }
    public boolean active(UUID owner) {
        Battle battle = battles.get(owner);
        return battle != null && !battle.ended;
    }
    public boolean add(UUID owner, UUID member, String role) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended || battle.dead.contains(member)) return false;
        if (battle.members.putIfAbsent(member, role) == null) setDirty();
        return true;
    }
    public int living(UUID owner, String role) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended) return 0;
        int count = 0;
        for (var member : battle.members.entrySet())
            if (member.getValue().equals(role) && !battle.dead.contains(member.getKey())) count++;
        return count;
    }
    public void died(UUID owner, UUID member) {
        Battle battle = battles.get(owner);
        if (battle != null && battle.members.containsKey(member) && battle.dead.add(member)) setDirty();
    }
    public boolean brotherRevivalAvailable(UUID owner) {
        Battle battle = battles.get(owner);
        return battle != null && !battle.ended && !battle.brotherRevivalSpent;
    }
    /** Commit the one-use decision before attempting an entity join. A cancelled
     * join leaves this payload pending; the boss retries without consuming again. */
    public boolean queueBrotherRevival(UUID owner, UUID dyingMember, CompoundTag spawn) {
        if (!spawn.hasUUID("UUID")) throw new IllegalArgumentException("Revival requires a stable entity UUID");
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended || battle.brotherRevivalSpent
                || !battle.dead.contains(dyingMember)
                || !isBrother(battle.members.get(dyingMember))) return false;
        battle.brotherRevivalSpent = true;
        battle.pendingBrother = spawn.copy();
        setDirty();
        return true;
    }
    public CompoundTag pendingBrotherRevival(UUID owner) {
        Battle battle = battles.get(owner);
        return battle == null || battle.ended || battle.pendingBrother == null ? null : battle.pendingBrother.copy();
    }
    public boolean completeBrotherRevival(UUID owner, UUID acceptedMember) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended || battle.pendingBrother == null
                || !acceptedMember.equals(battle.pendingBrother.getUUID("UUID"))
                || !isBrother(battle.members.get(acceptedMember)) || battle.dead.contains(acceptedMember)) return false;
        battle.pendingBrother = null;
        setDirty();
        return true;
    }
    public int livingBrothers(UUID owner) {
        Battle battle = battles.get(owner);
        return battle == null || battle.ended ? 0 : living(owner, "cataclysm") + living(owner, "catastrophe")
                + (battle.pendingBrother == null ? 0 : 1);
    }
    private static boolean isBrother(String role) { return "cataclysm".equals(role) || "catastrophe".equals(role); }
    public void updateBrotherHealth(UUID owner, UUID member, double ratio) {
        if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) throw new IllegalArgumentException("Invalid brother health ratio");
        Battle battle = battles.get(owner);
        if (battle != null && !battle.ended && !battle.dead.contains(member) && isBrother(battle.members.get(member))
                && !Double.valueOf(ratio).equals(battle.brotherHealth.put(member, ratio))) setDirty();
    }
    /** Unresolved/unloaded partners retain their last observed health, rather than
     * being mistaken for a dead partner and accelerating the survivor. */
    public double brotherHealth(UUID owner, String role) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended || !isBrother(role)) return 0;
        for (var member : battle.members.entrySet())
            if (role.equals(member.getValue()) && !battle.dead.contains(member.getKey()))
                return battle.brotherHealth.getOrDefault(member.getKey(), 1d);
        return 0;
    }
    public boolean confirmedDead(UUID owner, UUID member) {
        Battle battle = battles.get(owner);
        return battle != null && battle.dead.contains(member);
    }
    public boolean knownMember(UUID owner, UUID member) {
        Battle battle = battles.get(owner);
        return battle != null && battle.members.containsKey(member);
    }
    public java.util.Set<UUID> members(UUID owner, boolean projectiles) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended) return java.util.Set.of();
        var ids = new java.util.HashSet<UUID>();
        battle.members.forEach((id, role) -> {
            if ("attack".equals(role) == projectiles && !battle.dead.contains(id)) ids.add(id);
        });
        return ids;
    }
    /** Heart-controlled retirement also records unloaded worm slots. They must
     * disappear when loaded rather than keep the boss shielded indefinitely. */
    public void retireWorms(UUID owner) {
        Battle battle = battles.get(owner);
        if (battle == null || battle.ended) return;
        boolean changed = false;
        for (var member : battle.members.entrySet())
            if (("worm".equals(member.getValue()) || "worm_segment".equals(member.getValue()))
                    && battle.dead.add(member.getKey())) changed = true;
        if (changed) setDirty();
    }
    /** A cancelled entity join is neither a living summon nor a native death. */
    public void cancelSummon(UUID owner, UUID member) {
        Battle battle = battles.get(owner);
        if (battle != null && !battle.dead.contains(member) && battle.members.remove(member) != null) {
            battle.brotherHealth.remove(member); setDirty();
        }
    }
    public void forget(UUID owner, UUID member) {
        Battle battle = battles.get(owner);
        if (battle == null) return;
        if (battle.ended || "attack".equals(battle.members.get(member))) {
            battle.members.remove(member);
            battle.dead.remove(member);
            battle.brotherHealth.remove(member);
            if (battle.ended && battle.members.isEmpty()) battles.remove(owner);
            setDirty();
        }
    }
    public void end(UUID owner) {
        Battle battle = battles.get(owner);
        if (battle == null) return;
        battle.ended = true;
        battle.pendingBrother = null;
        battle.brotherHealth.clear();
        // Dead mobs may already have completed their native removal before the
        // encounter ends; no entity remains to acknowledge their cleanup later.
        battle.dead.forEach(battle.members::remove);
        battle.dead.clear();
        if (battle.members.isEmpty()) battles.remove(owner);
        setDirty();
    }
    private static CalamitasBattleData load(CompoundTag root) {
        var data = new CalamitasBattleData();
        data.defeated = root.getBoolean("Defeated");
        data.acceptanceMusicPlayed = root.getBoolean("AcceptanceMusicPlayed");
        for(Tag raw:root.getList("WitchConversions",Tag.TAG_COMPOUND)){CompoundTag entry=(CompoundTag)raw;if(entry.hasUUID("Boss")&&entry.hasUUID("NPC")){UUID boss=entry.getUUID("Boss");data.conversions.put(boss,entry.getUUID("NPC"));if(entry.getBoolean("Settled"))data.settlements.add(boss);}}
        for (Tag raw : root.getList("Battles", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("Owner")) continue;
            var battle = new Battle();
            battle.ended = tag.getBoolean("Ended");
            battle.brotherRevivalSpent = tag.getBoolean("BrotherRevivalSpent");
            if (battle.brotherRevivalSpent && !battle.ended && tag.contains("PendingBrother", Tag.TAG_COMPOUND))
                battle.pendingBrother = tag.getCompound("PendingBrother").copy();
            for (Tag entry : tag.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) entry;
                if (!member.hasUUID("Id")) continue;
                UUID id = member.getUUID("Id");
                battle.members.put(id, member.getString("Role"));
                if (member.getBoolean("Dead")) battle.dead.add(id);
                if (member.contains("BrotherHealth", Tag.TAG_DOUBLE)) {
                    double ratio = member.getDouble("BrotherHealth");
                    if (Double.isFinite(ratio) && ratio >= 0 && ratio <= 1) battle.brotherHealth.put(id, ratio);
                }
            }
            data.battles.put(tag.getUUID("Owner"), battle);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        var conversionsTag=new ListTag();conversions.forEach((boss,npc)->{var entry=new CompoundTag();entry.putUUID("Boss",boss);entry.putUUID("NPC",npc);entry.putBoolean("Settled",settlements.contains(boss));conversionsTag.add(entry);});root.put("WitchConversions",conversionsTag);
        root.putBoolean("Defeated", defeated);
        root.putBoolean("AcceptanceMusicPlayed", acceptanceMusicPlayed);
        var entries = new ListTag();
        battles.forEach((owner, battle) -> {
            var tag = new CompoundTag();
            tag.putUUID("Owner", owner); tag.putBoolean("Ended", battle.ended);
            tag.putBoolean("BrotherRevivalSpent", battle.brotherRevivalSpent);
            if (battle.pendingBrother != null) tag.put("PendingBrother", battle.pendingBrother.copy());
            var members = new ListTag();
            battle.members.forEach((id, role) -> {
                var member = new CompoundTag(); member.putUUID("Id", id);
                member.putString("Role", role); member.putBoolean("Dead", battle.dead.contains(id));
                if (battle.brotherHealth.containsKey(id)) member.putDouble("BrotherHealth", battle.brotherHealth.get(id));
                members.add(member);
            });
            tag.put("Members", members); entries.add(tag);
        });
        root.put("Battles", entries);
        return root;
    }
}
