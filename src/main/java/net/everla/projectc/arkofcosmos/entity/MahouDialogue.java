package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** Server side boss dialogue broadcast within 128 blocks, localized on each client. */
final class MahouDialogue {
    private java.util.UUID target;
    private boolean firstStart, firstEnd, secondStart, phaseTwoHalf, thirdRain, vortex, summon, summonDead;
    private int defeatLine, lowQuarterLine, lowQuarterDelay;
    private boolean defeatedBefore, defeatRepeat;

    private ServerPlayer target(MahouTsukaiEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return null;
        ServerPlayer player = boss.getTarget() instanceof ServerPlayer p && p.isAlive() ? p
                : level.players().stream()
                .filter(p -> p.isAlive() && boss.distanceToSqr(p) <= 128 * 128)
                .min(java.util.Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if (player == null) return null;
        if (!player.getUUID().equals(target)) { target = player.getUUID(); defeatedBefore = player.getPersistentData().getBoolean("arkofcosmos_mahou_defeated"); }
        return player;
    }
    private void say(MahouTsukaiEntity boss, String key) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        Component message = Component.empty().append(boss.getName()).append(" ").append(Component.translatable(key)).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer recipient : level.players()) {
            if (boss.distanceToSqr(recipient) <= 128 * 128) recipient.sendSystemMessage(message);
        }
    }
    void tick(MahouTsukaiEntity boss) {
        if (boss.level().isClientSide || boss.defeating()) return;
        ServerPlayer player = target(boss);
        int left = boss.phaseTransitionTicks();
        if (left == 300) {
            if (boss.phase() == 1 && !firstStart) { firstStart=true; say(boss, defeatedBefore?"dialogue.arkofcosmos.mahou.first_rain_repeat":"dialogue.arkofcosmos.mahou.first_rain"); }
            else if (boss.phase() == 2 && !secondStart) { secondStart=true; say(boss, defeatedBefore?"dialogue.arkofcosmos.mahou.second_rain_repeat":"dialogue.arkofcosmos.mahou.second_rain"); }
            else if (boss.phase() == 3 && !thirdRain) { thirdRain=true; say(boss, defeatedBefore?"dialogue.arkofcosmos.mahou.third_rain_repeat":"dialogue.arkofcosmos.mahou.third_rain"); }
        }
        if (left == 1) {
            if (boss.phase()==1 && !firstEnd) { firstEnd=true; if (defeatedBefore || player != null && player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS)>0) say(boss, defeatedBefore?"dialogue.arkofcosmos.mahou.first_end_repeat":"dialogue.arkofcosmos.mahou.first_end"); }
        }
        if (boss.phase()==2 && boss.getHealth()<boss.getMaxHealth()*.5f && !phaseTwoHalf) { phaseTwoHalf=true; if(!defeatedBefore)say(boss,"dialogue.arkofcosmos.mahou.phase_two_half"); }
        if (boss.phase()==3 && boss.getHealth()<boss.getMaxHealth()*.25f && lowQuarterLine < (defeatedBefore ? 1 : 4) && --lowQuarterDelay<=0) {
            if (defeatedBefore) { say(boss,"dialogue.arkofcosmos.mahou.low_quarter_repeat"); lowQuarterLine=1; lowQuarterDelay=999999; return; }
            String[] lines={"dialogue.arkofcosmos.mahou.low_quarter_1","dialogue.arkofcosmos.mahou.low_quarter_2","dialogue.arkofcosmos.mahou.low_quarter_3","dialogue.arkofcosmos.mahou.low_quarter_4"};
            say(boss,lines[lowQuarterLine++]);
            lowQuarterDelay=20;
        }
    }
    void teleport(MahouTsukaiEntity boss){ if(!boss.level().isClientSide&&!defeatedBefore) say(boss,"dialogue.arkofcosmos.mahou.escape"); }
    void summoned(MahouTsukaiEntity boss){ if(!summon){summon=true;say(boss,defeatedBefore?"dialogue.arkofcosmos.mahou.summon_repeat":"dialogue.arkofcosmos.mahou.summon");} }
    void defeatedSummon(MahouTsukaiEntity boss){ if(!summonDead){summonDead=true;say(boss,defeatedBefore?"dialogue.arkofcosmos.mahou.summon_dead_repeat":"dialogue.arkofcosmos.mahou.summon_dead");} }
    void vortex(MahouTsukaiEntity boss){ if(!vortex){vortex=true;if(defeatedBefore)say(boss,"dialogue.arkofcosmos.mahou.vortex_repeat");} }
    void beginDefeat(MahouTsukaiEntity boss){ ServerPlayer p=target(boss); if(p==null)return; defeatRepeat=defeatedBefore; p.getPersistentData().putBoolean("arkofcosmos_mahou_defeated",true); defeatedBefore=true; defeatLine=0; }
    void defeatTick(MahouTsukaiEntity boss){ if(defeatLine<4&&boss.defeatTicks()%20==0)say(boss,"dialogue.arkofcosmos.mahou.defeat_"+(++defeatLine)+(defeatRepeat?"_repeat":"")); }
    void disappeared(MahouTsukaiEntity boss){ if(!boss.level().isClientSide)say(boss,"dialogue.arkofcosmos.mahou.disappear"); }
}
