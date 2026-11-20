package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mahou uses the shared simplified bar and one continuous health pool. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class MahouBossBar {
    private static final Map<UUID,FourthCalamityBossBar.State> BARS=new HashMap<>();
    private static final Map<UUID,FourthCalamityBossBar.State> CHILDREN=new HashMap<>();
    private MahouBossBar() {}
    public static void update(UUID id,int phase) {
        if(phase<=0) BARS.remove(id);
        else BARS.computeIfAbsent(id,key->new FourthCalamityBossBar.State(1));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){BARS.clear();CHILDREN.clear();}
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event){
        var state=BARS.get(event.getBossEvent().getId());
        if(state==null)return;
        float progress=((net.everla.projectc.arkofcosmos.mixin.client.gui.BossBarProgressAccessor)event.getBossEvent()).arkofcosmos$targetProgress();
        FourthCalamityBossBar.draw(event,state,progress,event.getBossEvent().getProgress(),1,"Lv.2147483647",
            Component.translatable("boss.arkofcosmos.mahou.name"),Component.translatable("boss.arkofcosmos.mahou.title_1"),
            event.getBossEvent().getColor()==net.minecraft.world.BossEvent.BossBarColor.RED?0xFFE53935:0);
        drawBrothers(event);
    }
    private static void drawBrothers(CustomizeGuiOverlayEvent.BossEventProgress event){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;
        var boss=net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity.loaded(mc.level).stream().filter(b->{
            var view=b.infernumView();return view.hasUUID("BossBarId")&&view.getUUID("BossBarId").equals(event.getBossEvent().getId());
        }).findFirst().orElse(null);
        if(boss==null)return;
        var brothers=new net.everla.projectc.arkofcosmos.entity.MahouInfernumSummonEntity[2];
        var live=new HashSet<UUID>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.everla.projectc.arkofcosmos.entity.MahouInfernumSummonEntity m&&m.ownedBy(boss)&&m.isAlive()){
            int slot=switch(m.kind()){case CATACLYSM->0;case CATASTROPHE->1;default->-1;};
            if(slot>=0){brothers[slot]=m;live.add(m.getUUID());}
        }
        CHILDREN.keySet().removeIf(id->!live.contains(id));
        if(live.isEmpty())return;
        var g=event.getGuiGraphics();int gap=16,width=Math.max(40,Math.min(170,(g.guiWidth()-32-gap)/2));
        int left=(g.guiWidth()-width*2-gap)/2,y=event.getY()+58;
        for(int i=0;i<2;i++)if(brothers[i]!=null){
            var m=brothers[i];int x=left+i*(width+gap);
            double ratio=Math.max(0,Math.min(1,m.getHealth()/m.getMaxHealth()));
            double trail=CHILDREN.computeIfAbsent(m.getUUID(),id->new FourthCalamityBossBar.State(1)).trail(ratio,System.nanoTime());
            FourthCalamityBossBar.bevel(g,x-2,y-2,width+4,11,width+4,0xC0182630);
            FourthCalamityBossBar.bevel(g,x-1,y-1,width+2,9,width+2,0xFF83949B);
            FourthCalamityBossBar.bevel(g,x,y,width,7,width,0xFF342A32);
            FourthCalamityBossBar.bevel(g,x,y,width,7,(int)Math.round(width*trail),0xFFFFF2B0);
            FourthCalamityBossBar.bevel(g,x,y,width,7,(int)Math.round(width*ratio),0xFFFF5555);
            g.fill(x+3,y,x+width-3,y+1,0x40FFFFFF);
            childText(g,Component.translatable(i==0?"boss.arkofcosmos.mahou_saint.title":"boss.arkofcosmos.mahou_fox.title"),x+width/2,y-25,1);
            childText(g,Component.translatable("boss.arkofcosmos.mahou_brother.name"),x+width/2,y-14,.8f);
            g.drawString(mc.font,"Lv.32767",x,y+10,0xFFF3F4EF);
        }
        event.setIncrement(86);
    }
    private static void childText(net.minecraft.client.gui.GuiGraphics g,Component text,int x,int y,float scale){
        var font=net.minecraft.client.Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.drawString(font,text,-font.width(text)/2,0,0xFFF3F4EF);g.pose().popPose();
    }
}
