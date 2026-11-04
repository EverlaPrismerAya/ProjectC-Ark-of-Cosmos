package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.BrokenBiomeCombat;
import net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class BrokenBiomeClient {
    private static boolean left,right,holding;private static int heartbeat;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.getConnection()==null){left=right=holding=false;return;}
        boolean held=BrokenBiomeCombat.holding(mc.player),active=held&&mc.screen==null&&mc.isWindowActive();boolean l=active&&mc.options.keyAttack.isDown(),r=active&&mc.options.keyUse.isDown();
        if(l!=left||r!=right||held!=holding||held&&++heartbeat>=10){ArkNetwork.brokenBiome(l,-1,-1);heartbeat=0;}left=l;right=r;holding=held;
        if(active&&r)mc.setScreen(new Selector());
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e){Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&BrokenBiomeCombat.holding(mc.player)&&(e.isAttack()||e.isUseItem())){e.setCanceled(true);e.setSwingHand(false);}}
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||!BrokenBiomeCombat.holding(mc.player))return;
        if(mc.options.keyUse.matchesMouse(e.getButton())&&e.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS)mc.setScreen(new Selector());
    }
    private static final class Selector extends Screen {
        private int selected=-1,slot=-1;
        Selector() {super(Component.translatable("broken_biome.select", Component.keybind("key.use")));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick() {
            if(minecraft.player==null || !BrokenBiomeCombat.holding(minecraft.player) || !minecraft.isWindowActive()) onClose();
        }
        private void choose() {
            if(selected>=0&&(BiomeClient.unlocks&(1<<selected))!=0) ArkNetwork.brokenBiome(false,slot,selected);
            onClose();
        }
        @Override public boolean mouseReleased(double x,double y,int button) {
            if(minecraft.options.keyUse.matchesMouse(button)) {choose();return true;}return super.mouseReleased(x,y,button);
        }
        @Override public boolean keyReleased(int key,int scan,int modifiers) {
            if(minecraft.options.keyUse.matches(key,scan)) {choose();return true;}return super.keyReleased(key,scan,modifiers);
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            int x=width/2,y=height/2,dx=mouseX-x,dy=mouseY-y;
            double outer=Math.max(24,Math.min(130,Math.min(width/2d-8,height/2d-36))),inner=outer*.22;
            double radiusCursor=Math.sqrt(dx*dx+dy*dy);slot=radiusCursor<inner||radiusCursor>outer?-1:0;
            selected=slot<0?-1:(int)Math.floor((Math.atan2(dy,dx)+Math.PI/2+Math.PI/5+Math.PI*2)%(Math.PI*2)/(Math.PI*2/5));
            g.flush();
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int i=0;i<5;i++) {
                double angle=-Math.PI/2+i*Math.PI*2/5;
                boolean unlocked=(BiomeClient.unlocks&(1<<i))!=0;
                int color=net.everla.projectc.arkofcosmos.calamity.combat.BrokenBiomeRules.COLORS[i],alpha=i==selected?230:110;
                if(!unlocked){color=0x555555;alpha=75;}
                double lo=inner,hi=outer;
                arc(out,g,x,y,lo,hi,angle,color,alpha);
                arc(out,g,x,y,lo,lo+1.5,angle,color,255);
                arc(out,g,x,y,hi-1.5,hi,angle,color,255);
            }
            BufferUploader.drawWithShader(out.end());
            RenderSystem.enableCull();RenderSystem.disableBlend();
            for(int i=0;i<5;i++) {
                double angle=-Math.PI/2+i*Math.PI*2/5,radius=(inner+outer)/2;
                int cx=x+(int)(Math.cos(angle)*radius),cy=y+(int)(Math.sin(angle)*radius);
                var label=font.split(Component.translatable("broken_biome.mode."+i).append((BiomeClient.unlocks&(1<<i))==0?" ×":""),Math.max(20,(int)(outer*.36)));
                for(int line=0;line<label.size();line++)
                    g.drawCenteredString(font,label.get(line),cx,cy-label.size()*font.lineHeight/2+line*font.lineHeight,
                            i==selected?0xFFFFFF:0xD0D0DD);
            }
            g.drawCenteredString(font,title,x,y-(int)outer-16,0xFFFFFF);
            if(selected>=0&&(BiomeClient.unlocks&(1<<selected))==0)g.drawCenteredString(font,Component.translatable("broken_biome.locked"),x,y+(int)outer+20,0xAAAAAA);
            if(minecraft.player==null)return;
            var held=minecraft.player.getMainHandItem();
            g.drawCenteredString(font,Component.translatable("broken_biome.selection_state",Component.translatable("broken_biome.mode."+net.everla.projectc.arkofcosmos.item.BrokenBiomeBladeItem.main(held))),x,y+(int)outer+8,0xFFFFFF);
        }
        private void arc(BufferBuilder out,GuiGraphics g,int x,int y,double inner,double outer,double angle,int color,int alpha) {
            double start=angle-Math.PI/5+.025,end=angle+Math.PI/5-.025;
            for(int step=0;step<40;step++) {
                double a=start+(end-start)*step/40,b=start+(end-start)*(step+1)/40;
                arcVertex(out,g,x,y,inner,a,color,alpha);arcVertex(out,g,x,y,outer,a,color,alpha);
                arcVertex(out,g,x,y,outer,b,color,alpha);arcVertex(out,g,x,y,inner,b,color,alpha);
            }
        }
        private void arcVertex(BufferBuilder out,GuiGraphics g,int x,int y,double radius,double angle,int color,int alpha) {
            out.vertex(g.pose().last().pose(),(float)(x+Math.cos(angle)*radius),(float)(y+Math.sin(angle)*radius),0)
                    .color((color>>16)&255,(color>>8)&255,color&255,alpha).endVertex();
        }
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class BrokenBiomeRegistration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ArkOfCosmos.BROKEN_BIOME_ATTACK.get(),BrokenBiomeRenderer::new);}
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.BROKEN_BIOME_BLADE.get(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),(stack,level,entity,seed)->entity!=null&&!entity.level().getEntitiesOfClass(BrokenBiomeEntity.class,entity.getBoundingBox().inflate(128),a->a.ownerId()==entity.getId()&&a.kind()==BrokenBiomeEntity.BLADE&&(a.view().getInt("Mode")>0||a.level().getGameTime()-a.view().getLong("StartTick")<10)).isEmpty()?1:0));}
    }
}
