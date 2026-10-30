package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.GalaxiaCombat;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.calamity.client.GalaxiaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class GalaxiaClient {
    private static boolean last,wasHolding;
    private static int heartbeat;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.getConnection()==null) {last=wasHolding=false;return;}
        boolean holding=mc.player!=null && GalaxiaCombat.holding(mc.player);
        boolean active=holding && mc.screen==null && mc.isWindowActive();
        boolean left=active && mc.options.keyAttack.isDown();
        if(left!=last || holding!=wasHolding || holding && ++heartbeat>=10) {ArkNetwork.galaxia(left,-1);heartbeat=0;last=left;wasHolding=holding;}
        if(active && mc.options.keyUse.isDown()) mc.setScreen(new Selector());
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null && GalaxiaCombat.holding(mc.player) && (e.isAttack() || e.isUseItem())) {e.setCanceled(true);e.setSwingHand(false);}
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || !GalaxiaCombat.holding(mc.player)) return;
        if(mc.options.keyAttack.matchesMouse(e.getButton())) {
            last=e.getAction()!=GLFW.GLFW_RELEASE;ArkNetwork.galaxia(last,-1);
        }
        if(mc.options.keyUse.matchesMouse(e.getButton()) && e.getAction()==GLFW.GLFW_PRESS) mc.setScreen(new Selector());
    }
    private static final class Selector extends Screen {
        private int selected=-1;
        Selector() {super(Component.translatable("galaxia.select", Component.keybind("key.use")));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick() {
            if(minecraft.player==null || !GalaxiaCombat.holding(minecraft.player) || !minecraft.isWindowActive()) onClose();
        }
        private void choose() {
            if(selected>=0) ArkNetwork.galaxia(false,selected);
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
            double outer=Math.max(24,Math.min(104,Math.min(width/2d-8,height/2d-25))),inner=outer*.34;
            selected=dx*dx+dy*dy<inner*inner?-1:Math.abs(dx)>Math.abs(dy)?(dx>0?1:3):(dy>0?2:0);
            g.flush();
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int i=0;i<4;i++) {
                double angle=-Math.PI/2+i*Math.PI/2;
                int color=GalaxiaRules.tooltipColor(i),alpha=i==selected?230:150;
                arc(out,g,x,y,inner,outer,angle,color,alpha);
                arc(out,g,x,y,inner,inner+1.5,angle,color,255);
                arc(out,g,x,y,outer-1.5,outer,angle,color,255);
            }
            BufferUploader.drawWithShader(out.end());
            RenderSystem.enableCull();RenderSystem.disableBlend();
            for(int i=0;i<4;i++) {
                double angle=-Math.PI/2+i*Math.PI/2,radius=(inner+outer)/2;
                int cx=x+(int)(Math.cos(angle)*radius),cy=y+(int)(Math.sin(angle)*radius);
                var label=font.split(Component.translatable("galaxia.mode."+i),Math.max(20,(int)(outer*.72)));
                for(int line=0;line<label.size();line++)
                    g.drawCenteredString(font,label.get(line),cx,cy-label.size()*font.lineHeight/2+line*font.lineHeight,
                            i==selected?0xFFFFFF:0xD0D0DD);
            }
            g.drawCenteredString(font,title,x,y-(int)outer-16,0xFFFFFF);
        }
        private void arc(BufferBuilder out,GuiGraphics g,int x,int y,double inner,double outer,double angle,int color,int alpha) {
            double start=angle-Math.PI/4+.025,end=angle+Math.PI/4-.025;
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
    // Forge wrapper names include the simple class name, not the enclosing class.
    public static final class GalaxiaRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.GALAXIA.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"dusk"),
                    (stack,level,entity,seed) -> net.everla.projectc.arkofcosmos.item.GalaxiaItem.mode(stack)>=2?1:0));
            e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.GALAXIA.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),
                    (stack,level,entity,seed) -> entity!=null && !entity.level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.class,
                            entity.getBoundingBox().inflate(128),attack -> attack.ownerId()==entity.getId() && attack.kind()==net.everla.projectc.arkofcosmos.entity.GalaxiaEntity.BLADE).isEmpty()?1:0));
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ArkOfCosmos.GALAXIA_ATTACK.get(),GalaxiaRenderer::new);
        }
    }
}
