package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.mixin.client.gui.BossBarProgressAccessor;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class FourthCalamityBossBar {
    private static final int[] RAINBOW={0xFFFF5555,0xFFFF9900,0xFFFFFF55,0xFF55DD55,0xFF55DDDD,0xFF5577FF,0xFFBB55FF};
    static final class State {
        int bars;
        double previous=Double.NaN, trailing;
        long lastFrame, delayUntil;
        State(int bars) { this.bars=bars; }
        void setBars(int bars) {
            if(this.bars!=bars) {
                // Only the surviving bar's overflow damage should leave a trail.
                if(bars<this.bars&&!Double.isNaN(previous))previous=trailing=1;
                else previous=Double.NaN;
                delayUntil=0;
            }
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
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BARS.clear();ApollyonBossBar.clear(); }
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        State state=BARS.get(event.getBossEvent().getId());if(state==null)return;
        float progress=((BossBarProgressAccessor)event.getBossEvent()).arkofcosmos$targetProgress();
        draw(event,state,progress,event.getBossEvent().getProgress(),44,"Lv.19",
            Component.translatable("boss.arkofcosmos.the_player.name"),Component.translatable("boss.arkofcosmos.the_player.title"));
    }
    static void draw(CustomizeGuiOverlayEvent.BossEventProgress event,State state,float progress,float visible,int total,String level,Component name,Component title) {
        event.setCanceled(true);event.setIncrement(44);
        var graphics=event.getGuiGraphics();var font=Minecraft.getInstance().font;
        int width=Math.max(40,Math.min(210,graphics.guiWidth()-100));
        int x=(graphics.guiWidth()-width)/2,y=event.getY()+16;
        int color=total>1?barColor(state.bars,total):RAINBOW[0];
        // Read the authoritative target, not vanilla's 100 ms interpolation (which wraps on each bar).
        double trailing=state.trail(progress,System.nanoTime());
        bevel(graphics,x-2,y-2,width+4,11,width+4,0xC0182630);
        bevel(graphics,x-1,y-1,width+2,9,width+2,0xFF83949B);
        bevel(graphics,x,y,width,7,width,state.bars>1?barColor(state.bars-1,total):0xFF342A32);
        bevel(graphics,x,y,width,7,(int)Math.round(width*trailing),0xFFFFF2B0);
        int filled=Math.round(width*visible);
        bevel(graphics,x,y,width,7,filled,color);
        // Thin highlights leave the original phase colors readable.
        graphics.fill(x+3,y,x+width-3,y+1,0x40FFFFFF);
        graphics.fill(x+3,y+6,x+width-3,y+7,0x50000000);
        graphics.drawString(font,level,x-font.width(level)-8,y,0xFFF3F4EF);
        centeredText(graphics,name,y-26,1.35f,0xFFF3F4EF);
        centeredText(graphics,title,y-11,.85f,0xFFB8C4CA);
        if(total>1) {
            String count="× "+state.bars;
            int countWidth=Math.max(34,font.width(count)+12),countX=x+width+8;
            bevel(graphics,countX,y-2,countWidth,11,countWidth,0xB025343E);
            graphics.drawString(font,count,countX+(countWidth-font.width(count))/2,y,0xFFF3F4EF);
        }
    }
    private static void centeredText(GuiGraphics graphics,Component text,int y,float scale,int color) {
        var font=Minecraft.getInstance().font;
        text=Component.literal(text.getString().strip());
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth()/2f,y,0);
        graphics.pose().scale(scale,scale,1);
        graphics.pose().translate(-font.width(text)/2f,0,0);
        graphics.drawString(font,text,0,0,color);
        graphics.pose().popPose();
    }
    /** Clip each layer to the same pointed silhouette, including an empty or full bar. */
    private static void bevel(GuiGraphics graphics,int x,int y,int width,int height,int filled,int color) {
        filled=Math.max(0,Math.min(width,filled));
        for(int row=0;row<height;row++) {
            int inset=Math.abs(row-height/2),right=Math.min(width-inset,filled);
            if(right>inset)graphics.fill(x+inset,y+row,x+right,y+row+1,color);
        }
    }
    private static int barColor(int bars,int total) { return RAINBOW[Math.floorMod(total-bars,RAINBOW.length)]; }
}
