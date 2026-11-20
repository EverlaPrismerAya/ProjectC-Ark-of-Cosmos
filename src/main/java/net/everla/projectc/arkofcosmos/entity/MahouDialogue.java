package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.everla.projectc.arkofcosmos.combat.MahouProgress;

/** Server side boss dialogue broadcast within 128 blocks, localized on each client. */
final class MahouDialogue {
    private boolean firstStart, firstEnd, secondStart, phaseTwoHalf, thirdRain, vortex;
    private int defeatLine, lowQuarterLine, lowQuarterDelay;
    private boolean defeatRepeat;

    private boolean defeatedBefore(MahouTsukaiEntity boss) {
        return boss.level() instanceof ServerLevel level && level.players().stream().anyMatch(
                p -> boss.distanceToSqr(p) <= 128 * 128
                        && MahouProgress.defeated(p));
    }

    private ServerPlayer target(MahouTsukaiEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return null;
        ServerPlayer player = boss.getTarget() instanceof ServerPlayer p && p.isAlive() ? p
                : level.players().stream()
                .filter(p -> p.isAlive() && boss.distanceToSqr(p) <= 128 * 128)
                .min(java.util.Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if (player == null) return null;
        return player;
    }
    private void say(MahouTsukaiEntity boss, String key) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        Component message = Component.empty().append(boss.getName()).append(" ").append(Component.translatable(key)).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer recipient : level.players()) {
            if (boss.distanceToSqr(recipient) <= 128 * 128) recipient.sendSystemMessage(message);
        }
    }
    void summonBrothers(MahouTsukaiEntity boss) {
        say(boss, defeatedBefore(boss)?"dialogue.arkofcosmos.mahou.brothers_repeat":"dialogue.arkofcosmos.mahou.brothers");
    }
    void tick(MahouTsukaiEntity boss) {
        if (boss.level().isClientSide || boss.defeating()) return;
        ServerPlayer player = target(boss);
        var ai=boss.infernum();
        if (ai.entrance>0 || ai.attack==net.everla.projectc.arkofcosmos.calamity.MahouInfernumEncounter.Attack.PhaseTransition) {
            if (ai.stage == 0 && !firstStart) { firstStart=true; say(boss, defeatedBefore(boss)?"dialogue.arkofcosmos.mahou.first_rain_repeat":"dialogue.arkofcosmos.mahou.first_rain"); }
            else if (ai.stage == 1 && !secondStart) { secondStart=true; say(boss, defeatedBefore(boss)?"dialogue.arkofcosmos.mahou.second_rain_repeat":"dialogue.arkofcosmos.mahou.second_rain"); }
            else if (ai.stage >= 2 && !thirdRain) { thirdRain=true; say(boss, defeatedBefore(boss)?"dialogue.arkofcosmos.mahou.third_rain_repeat":"dialogue.arkofcosmos.mahou.third_rain"); }
        }
        if (ai.entrance==0 && firstStart) {
            if (boss.phase()==1 && !firstEnd) { firstEnd=true; if (defeatedBefore(boss) || player != null && player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS)>0) say(boss, defeatedBefore(boss)?"dialogue.arkofcosmos.mahou.first_end_repeat":"dialogue.arkofcosmos.mahou.first_end"); }
        }
        if (boss.phase()==2 && boss.getHealth()<boss.getMaxHealth()*.5f && !phaseTwoHalf) { phaseTwoHalf=true; if(!defeatedBefore(boss))say(boss,"dialogue.arkofcosmos.mahou.phase_two_half"); }
        if (boss.phase()==3 && boss.getHealth()<boss.getMaxHealth()*.25f && lowQuarterLine < (defeatedBefore(boss) ? 1 : 4) && --lowQuarterDelay<=0) {
            if (defeatedBefore(boss)) { say(boss,"dialogue.arkofcosmos.mahou.low_quarter_repeat"); lowQuarterLine=1; lowQuarterDelay=999999; return; }
            String[] lines={"dialogue.arkofcosmos.mahou.low_quarter_1","dialogue.arkofcosmos.mahou.low_quarter_2","dialogue.arkofcosmos.mahou.low_quarter_3","dialogue.arkofcosmos.mahou.low_quarter_4"};
            say(boss,lines[lowQuarterLine++]);
            lowQuarterDelay=20;
        }
    }
    void enragedVolley(MahouTsukaiEntity boss){
        String[] lines={"red","blue","fusion","purple","blue_dash","domain"};
        say(boss,"dialogue.arkofcosmos.mahou.rage_"+lines[boss.getRandom().nextInt(lines.length)]);
    }
    void beginDefeat(MahouTsukaiEntity boss){ defeatRepeat=defeatedBefore(boss); ServerPlayer p=target(boss); if(p==null)return; MahouProgress.markDefeated(p); defeatLine=0; }
    void defeatTick(MahouTsukaiEntity boss){ if(defeatLine<4&&boss.defeatTicks()%20==0)say(boss,"dialogue.arkofcosmos.mahou.defeat_"+(++defeatLine)+(defeatRepeat?"_repeat":"")); }
    void disappeared(MahouTsukaiEntity boss){ if(!boss.level().isClientSide)say(boss,"dialogue.arkofcosmos.mahou.disappear"); }
    void save(net.minecraft.nbt.CompoundTag root){
        var t=new net.minecraft.nbt.CompoundTag();boolean[] flags={firstStart,firstEnd,secondStart,phaseTwoHalf,thirdRain,vortex,false,false,defeatRepeat};
        for(int i=0;i<flags.length;i++)t.putBoolean("Flag"+i,flags[i]);t.putInt("DefeatLine",defeatLine);t.putInt("QuarterLine",lowQuarterLine);t.putInt("QuarterDelay",lowQuarterDelay);root.put("MahouDialogue",t);
    }
    void load(net.minecraft.nbt.CompoundTag root){
        var t=root.getCompound("MahouDialogue");firstStart=t.getBoolean("Flag0");firstEnd=t.getBoolean("Flag1");secondStart=t.getBoolean("Flag2");phaseTwoHalf=t.getBoolean("Flag3");thirdRain=t.getBoolean("Flag4");vortex=t.getBoolean("Flag5");defeatRepeat=t.getBoolean("Flag8");defeatLine=t.getInt("DefeatLine");lowQuarterLine=t.getInt("QuarterLine");lowQuarterDelay=t.getInt("QuarterDelay");
    }
}
