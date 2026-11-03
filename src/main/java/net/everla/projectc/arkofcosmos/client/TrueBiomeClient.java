package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.TrueBiomeCombat;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.client.TrueBiomeRenderer;
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
import net.everla.projectc.arkofcosmos.calamity.combat.TrueBiomeRules;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class TrueBiomeClient {
    private static boolean last,wasHolding;
    private static int heartbeat;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.getConnection()==null) {last=wasHolding=false;return;}
        boolean holding=mc.player!=null && TrueBiomeCombat.holding(mc.player);
        boolean active=holding && mc.screen==null && mc.isWindowActive();
        boolean left=active && mc.options.keyAttack.isDown();
        if(left!=last || holding!=wasHolding || holding && ++heartbeat>=10) {ArkNetwork.trueBiome(left,-1,-1);heartbeat=0;last=left;wasHolding=holding;}
        if(active && mc.options.keyUse.isDown()) mc.setScreen(new Selector());
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null && TrueBiomeCombat.holding(mc.player) && (e.isAttack() || e.isUseItem())) {e.setCanceled(true);e.setSwingHand(false);}
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || !TrueBiomeCombat.holding(mc.player)) return;
        if(mc.options.keyAttack.matchesMouse(e.getButton())) {
            last=e.getAction()!=GLFW.GLFW_RELEASE;ArkNetwork.trueBiome(last,-1,-1);
        }
        if(mc.options.keyUse.matchesMouse(e.getButton()) && e.getAction()==GLFW.GLFW_PRESS) mc.setScreen(new Selector());
    }
    private static final class Selector extends Screen {
        private int selected=-1,slot=-1;
        Selector() {super(Component.translatable("true_biome.select", Component.keybind("key.use")));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void tick() {
            if(minecraft.player==null || !TrueBiomeCombat.holding(minecraft.player) || !minecraft.isWindowActive()) onClose();
        }
        private void choose() {
            if(selected>=0) ArkNetwork.trueBiome(false,slot,selected);
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
            double outer=Math.max(24,Math.min(130,Math.min(width/2d-8,height/2d-25))),inner=outer*.22,mid=outer*.58;
            double radiusCursor=Math.sqrt(dx*dx+dy*dy);slot=radiusCursor<inner||radiusCursor>outer?-1:radiusCursor<mid?0:1;
            selected=slot<0?-1:Math.abs(dx)>Math.abs(dy)?(dx>0?1:3):(dy>0?2:0);
            g.flush();
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            for(int ring=0;ring<2;ring++)for(int i=0;i<4;i++) {
                double angle=-Math.PI/2+i*Math.PI/2;
                int color=TrueBiomeRules.COLOR[i],alpha=i==selected&&ring==slot?230:110;
                double lo=ring==0?inner:mid+3,hi=ring==0?mid-3:outer;
                arc(out,g,x,y,lo,hi,angle,color,alpha);
                arc(out,g,x,y,lo,lo+1.5,angle,color,255);
                arc(out,g,x,y,hi-1.5,hi,angle,color,255);
            }
            BufferUploader.drawWithShader(out.end());
            RenderSystem.enableCull();RenderSystem.disableBlend();
            for(int ring=0;ring<2;ring++)for(int i=0;i<4;i++) {
                double angle=-Math.PI/2+i*Math.PI/2,radius=ring==0?(inner+mid)/2:(mid+outer)/2;
                int cx=x+(int)(Math.cos(angle)*radius),cy=y+(int)(Math.sin(angle)*radius);
                var label=font.split(Component.translatable("true_biome.mode."+i),Math.max(20,(int)(outer*.72)));
                for(int line=0;line<label.size();line++)
                    g.drawCenteredString(font,label.get(line),cx,cy-label.size()*font.lineHeight/2+line*font.lineHeight,
                            i==selected&&ring==slot?0xFFFFFF:0xD0D0DD);
            }
            g.drawCenteredString(font,title,x,y-(int)outer-16,0xFFFFFF);
            var held=minecraft.player.getMainHandItem();
            g.drawCenteredString(font,Component.translatable("true_biome.selection_state",Component.translatable("true_biome.mode."+net.everla.projectc.arkofcosmos.item.TrueBiomeBladeItem.main(held)),Component.translatable("true_biome.mode."+net.everla.projectc.arkofcosmos.item.TrueBiomeBladeItem.secondary(held))),x,y+(int)outer+8,0xFFFFFF);
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
    public static final class TrueBiomeRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.TRUE_BIOME_BLADE.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),
                    (stack,level,entity,seed) -> entity!=null && !entity.level().getEntitiesOfClass(net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity.class,
                            entity.getBoundingBox().inflate(128),attack -> attack.ownerId()==entity.getId() && attack.kind()==net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity.BLADE).isEmpty()?1:0));
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ArkOfCosmos.TRUE_BIOME_ATTACK.get(),TrueBiomeRenderer::new);
        }
    }
}
