package net.everla.projectc.arkofcosmos.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.everla.projectc.arkofcosmos.*;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.everla.projectc.arkofcosmos.entity.BrimstoneWitchEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class WitchClient {
    private record Flame(java.util.UUID uuid,long until) {}
    private static final java.util.Map<Integer,Flame> FLAMES=new java.util.HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel flameLevel;
    private static void world(){var level=Minecraft.getInstance().level;if(flameLevel!=level){FLAMES.clear();flameLevel=level;}}
    public static void flames(int entity,java.util.UUID uuid,int ticks){world();if(flameLevel==null)return;if(ticks<=0)FLAMES.remove(entity);else FLAMES.put(entity,new Flame(uuid,flameLevel.getGameTime()+ticks));}
    @SubscribeEvent public static void flameWorld(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        world();if(flameLevel==null)return;
        var mc=Minecraft.getInstance();
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.matrix=event.getPoseStack().last().pose();
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.camera=event.getCamera().getPosition();
        net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.fadeEye=null;
        for(var entry:FLAMES.entrySet()){
            var entity=flameLevel.getEntity(entry.getKey());var flame=entry.getValue();
            if(entity==null||!entity.getUUID().equals(flame.uuid)||!entity.isAlive()||flame.until<=flameLevel.getGameTime())continue;
            if(entity==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson())continue;
            var at=entity.getPosition(event.getPartialTick()).add(0,entity.getBbHeight()*.5,0);
            net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.witchFlames(at,Math.max(.3,entity.getBbWidth()*.8),entity.getBbHeight()*.65,(entity.tickCount+event.getPartialTick())/20f);
        }
    }
    private static final KeyMapping COLLECT=new KeyMapping("key.arkofcosmos.collect_witch",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,-1,"key.categories.arkofcosmos");
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();world();
        if(flameLevel!=null){
            FLAMES.entrySet().removeIf(e->e.getValue().until<=flameLevel.getGameTime());
            if(net.everla.projectc.arkofcosmos.client.render.ArkFxDraw.witchFlamesShader==null)for(var entry:FLAMES.entrySet()){
                var target=flameLevel.getEntity(entry.getKey());if(target!=null&&target.isAlive()&&target.getUUID().equals(entry.getValue().uuid))flameLevel.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,target.getRandomX(.6),target.getRandomY(),target.getRandomZ(.6),0,.03,0);
            }
        }
        while(COLLECT.consumeClick())if(mc.player!=null && mc.screen==null){
            var eye=mc.player.getEyePosition();var end=eye.add(mc.player.getLookAngle().scale(6));
            var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mc.player,eye,end,mc.player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),e->e instanceof BrimstoneWitchEntity,36);
            if(hit!=null && hit.getEntity() instanceof BrimstoneWitchEntity witch)WitchEnchanting.collect(witch.getId());
        }
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class WitchRegistration {
        @SubscribeEvent public static void overlay(RegisterGuiOverlaysEvent event){
            event.registerAboveAll("witch_ephemeral",(gui,g,partial,width,height)->{
                var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
                var item=mc.player.getMainHandItem();if(Curse.of(item)!=Curse.EPHEMERAL)return;
                int x=8,y=height-8-70;int fill=Math.round(34*Math.max(0,Math.min(1,item.getOrCreateTag().getFloat("ArkWitchCharge")/1600f)));
                var border=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"textures/gui/witch/ephemeral_bar_border.png");
                var bar=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"textures/gui/witch/ephemeral_bar.png");
                g.blit(border,x,y,0,0,34,70,34,70);
                if(fill>0)g.blit(bar,x+14,y+18+34-fill,0,34-fill,6,fill,6,34);
            });
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){if(CalamitasContent.ENABLED)e.register(COLLECT);}
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{MenuScreens.register(WitchEnchanting.MENU.get(),WitchScreen::new);if(CalamitasContent.ENABLED)for(var item:new net.minecraft.world.item.Item[]{CalamitasContent.RANCOR.get(),CalamitasContent.GRUESOME_EMINENCE.get()})net.minecraft.client.renderer.item.ItemProperties.register(item,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"channeling"),(stack,level,holder,seed)->holder!=null&&holder.isUsingItem()&&holder.getUseItem()==stack?1:0);});}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){if(CalamitasContent.ENABLED){e.registerEntityRenderer(CalamitasContent.WITCH.get(),BrimstoneWitchRenderer::new);e.registerEntityRenderer(CalamitasContent.CURSE_ACTOR.get(),WitchCurseRenderer::new);e.registerEntityRenderer(CalamitasContent.NECROMANTIC_ATTACK.get(),NecromanticAttackRenderer::new);}}
    }
}
