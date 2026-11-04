package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.mixin.client.gui.BossBarProgressAccessor;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class FourthCalamityBossBar {
    private static final int[] RAINBOW={0xFFFF5555,0xFFFF9900,0xFFFFFF55,0xFF55DD55,0xFF55DDDD,0xFF5577FF,0xFFBB55FF};
    private static final int TOTAL_BARS=44;
    private static final class State {
        int bars;
        double previous=Double.NaN, trailing;
        long lastFrame, delayUntil;
        State(int bars) { this.bars=bars; }
        void setBars(int bars) {
            if(this.bars!=bars) { previous=Double.NaN;delayUntil=0; }
            this.bars=bars;
        }
        double trail(double health,long now) {
            if(Double.isNaN(previous)||health>previous)trailing=health;
            else if(health<previous)delayUntil=now+150_000_000L;
            // Time-based speed: one full bar per second, independent of frame rate.
            long start=Math.max(lastFrame,delayUntil);
            if(now>start)trailing=Math.max(health,trailing-(now-start)/1_000_000_000.0);
            previous=health;lastFrame=now;
            return Math.max(0,Math.min(1,trailing));
        }
    }
    private static final Map<UUID,State> BARS=new HashMap<>();
    private FourthCalamityBossBar() {}
    public static void update(UUID id,int phase,int bars) {
        if(phase==0)BARS.remove(id);else BARS.computeIfAbsent(id,key->new State(bars)).setBars(bars);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BARS.clear(); }
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        State state=BARS.get(event.getBossEvent().getId());if(state==null)return;
        event.setCanceled(true);event.setIncrement(25);
        var graphics=event.getGuiGraphics();var font=Minecraft.getInstance().font;
        int x=graphics.guiWidth()/2-91,y=event.getY();
        int color=barColor(state.bars);
        // Read the authoritative target, not vanilla's 100 ms interpolation (which wraps on each bar).
        float progress=((BossBarProgressAccessor)event.getBossEvent()).arkofcosmos$targetProgress();
        double trailing=state.trail(progress,System.nanoTime());
        graphics.fill(x-1,y-1,x+183,y+6,0xFF000000);
        graphics.fill(x,y,x+182,y+5,state.bars>1?barColor(state.bars-1):0xFF442222);
        graphics.fill(x,y,x+(int)Math.round(182*trailing),y+5,0xFFFFF2B0);
        int filled=Math.round(182*event.getBossEvent().getProgress());
        graphics.fill(x,y,x+filled,y+5,color);
        Component name=event.getBossEvent().getName();
        graphics.drawString(font,name,graphics.guiWidth()/2-font.width(name)/2,y-10,0xFFFFFF);
        graphics.drawString(font,"×"+state.bars,x+187,y-2,color);
    }
    private static int barColor(int bars) { return RAINBOW[Math.floorMod(TOTAL_BARS-bars,RAINBOW.length)]; }
}
