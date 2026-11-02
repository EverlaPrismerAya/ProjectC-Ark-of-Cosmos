package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class FourthCalamityBossBar {
    private record State(int phase,int bars) {}
    private static final Map<UUID,State> BARS=new HashMap<>();
    private FourthCalamityBossBar() {}
    public static void update(UUID id,int phase,int bars) {
        if(phase==0)BARS.remove(id);else BARS.put(id,new State(phase,bars));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BARS.clear(); }
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        State state=BARS.get(event.getBossEvent().getId());if(state==null)return;
        event.setCanceled(true);event.setIncrement(25);
        var graphics=event.getGuiGraphics();var font=Minecraft.getInstance().font;
        int x=graphics.guiWidth()/2-91,y=event.getY();
        int color=state.phase==1?0xFFFF5555:state.phase==2?0xFFCC55FF:0xFFFFFFFF;
        graphics.fill(x-1,y-1,x+183,y+6,0xFF000000);graphics.fill(x,y,x+182,y+5,0xFF442222);
        graphics.fill(x,y,x+Math.round(182*event.getBossEvent().getProgress()),y+5,color);
        Component name=event.getBossEvent().getName();
        graphics.drawString(font,name,graphics.guiWidth()/2-font.width(name)/2,y-10,0xFFFFFF);
        graphics.drawString(font,"×"+state.bars,x+187,y-2,color);
    }
}
