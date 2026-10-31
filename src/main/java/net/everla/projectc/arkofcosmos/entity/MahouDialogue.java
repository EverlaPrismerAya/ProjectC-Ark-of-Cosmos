package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Server side, target-only boss dialogue. The text itself is localized on the client. */
final class MahouDialogue {
    private java.util.UUID target;
    private boolean firstStart, firstEnd, secondStart, phaseTwoHalf, thirdRain, vortex, summon, summonDead;
    private int defeatLine, lowQuarterLine, lowQuarterDelay;
    private boolean defeatedBefore, defeatRepeat;

    private ServerPlayer target(MahouTsukaiEntity boss) {
        if (!(boss.getTarget() instanceof ServerPlayer player) || !player.isAlive()) return null;
        if (!player.getUUID().equals(target)) { target = player.getUUID(); defeatedBefore = player.getPersistentData().getBoolean("arkofcosmos_mahou_defeated"); }
        return player;
    }
    private void say(MahouTsukaiEntity boss, ServerPlayer player, String key) {
        if (player != null) player.sendSystemMessage(Component.empty().append(boss.getDisplayName()).append(" ").append(Component.translatable(key)).withStyle(ChatFormatting.AQUA));
    }
    void tick(MahouTsukaiEntity boss) {
        if (boss.level().isClientSide || boss.defeating()) return;
        ServerPlayer player = target(boss);
        if (player == null) return;
        int left = boss.phaseTransitionTicks();
        if (left == 300) {
            if (boss.phase() == 1 && !firstStart) { firstStart=true; say(boss,player, defeatedBefore?"dialogue.arkofcosmos.mahou.first_rain_repeat":"dialogue.arkofcosmos.mahou.first_rain"); }
            else if (boss.phase() == 2 && !secondStart) { secondStart=true; say(boss,player, defeatedBefore?"dialogue.arkofcosmos.mahou.second_rain_repeat":"dialogue.arkofcosmos.mahou.second_rain"); }
            else if (boss.phase() == 3 && !thirdRain) { thirdRain=true; say(boss,player, defeatedBefore?"dialogue.arkofcosmos.mahou.third_rain_repeat":"dialogue.arkofcosmos.mahou.third_rain"); }
        }
        if (left == 1) {
            if (boss.phase()==1 && !firstEnd) { firstEnd=true; if (defeatedBefore || player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS)>0) say(boss,player, defeatedBefore?"dialogue.arkofcosmos.mahou.first_end_repeat":"dialogue.arkofcosmos.mahou.first_end"); }
        }
        if (boss.phase()==2 && boss.getHealth()<boss.getMaxHealth()*.5f && !phaseTwoHalf) { phaseTwoHalf=true; if(!defeatedBefore)say(boss,player,"dialogue.arkofcosmos.mahou.phase_two_half"); }
        if (boss.phase()==3 && boss.getHealth()<boss.getMaxHealth()*.25f && !defeatedBefore && lowQuarterLine<4 && --lowQuarterDelay<=0) {
            String[] lines={"dialogue.arkofcosmos.mahou.low_quarter_1","dialogue.arkofcosmos.mahou.low_quarter_2","dialogue.arkofcosmos.mahou.low_quarter_3","dialogue.arkofcosmos.mahou.low_quarter_4"};
            say(boss,player,lines[lowQuarterLine++]);
            lowQuarterDelay=20;
        }
    }
    void teleport(MahouTsukaiEntity boss){ if(!boss.level().isClientSide&&!defeatedBefore) say(boss,target(boss),"dialogue.arkofcosmos.mahou.escape"); }
    void summoned(MahouTsukaiEntity boss){ if(!summon){summon=true;say(boss,target(boss),defeatedBefore?"dialogue.arkofcosmos.mahou.summon_repeat":"dialogue.arkofcosmos.mahou.summon");} }
    void defeatedSummon(MahouTsukaiEntity boss){ if(!summonDead){summonDead=true;say(boss,target(boss),defeatedBefore?"dialogue.arkofcosmos.mahou.summon_dead_repeat":"dialogue.arkofcosmos.mahou.summon_dead");} }
    void vortex(MahouTsukaiEntity boss){ if(!vortex){vortex=true;if(defeatedBefore)say(boss,target(boss),"dialogue.arkofcosmos.mahou.vortex_repeat");} }
    void beginDefeat(MahouTsukaiEntity boss){ ServerPlayer p=target(boss); if(p==null)return; defeatRepeat=defeatedBefore; p.getPersistentData().putBoolean("arkofcosmos_mahou_defeated",true); defeatedBefore=true; defeatLine=0; }
    void defeatTick(MahouTsukaiEntity boss){ if(defeatLine<4&&boss.defeatTicks()%20==0)say(boss,target(boss),"dialogue.arkofcosmos.mahou.defeat_"+(++defeatLine)+(defeatRepeat?"_repeat":"")); }
    void disappeared(MahouTsukaiEntity boss){ if(!boss.level().isClientSide)say(boss,target(boss),"dialogue.arkofcosmos.mahou.disappear"); }
}
