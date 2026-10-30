package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.client.*;
import net.everla.projectc.arkofcosmos.item.ElementsItem;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.client.render.NativeSprites;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class ElementsClient {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END)ElementsEffects.tick();}
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=net.minecraft.client.Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
        ElementsEffects.render(e.getPoseStack(),buffers,e.getCamera().getPosition(),e.getPartialTick());buffers.endBatch();
    }
    @SubscribeEvent public static void playerPost(RenderPlayerEvent.Post e){
        var player=e.getEntity();if(!net.everla.projectc.arkofcosmos.combat.ElementsCombat.holding(player))return;
        if(!player.level().getEntitiesOfClass(ElementsEntity.class,player.getBoundingBox().inflate(80),a->a.ownerId()==player.getId()&&(a.kind()==ElementsEntity.BLADE||a.kind()==ElementsEntity.PARRY||a.kind()==ElementsEntity.BLAST)).isEmpty())return;
        double yaw=Math.toRadians(player.getYRot());var right=new net.minecraft.world.phys.Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
        var at=new net.minecraft.world.phys.Vec3(-right.z,0,right.x).scale(.45).add(0,1.05,0);
        var face=new net.everla.projectc.arkofcosmos.util.AttackFrame(new net.minecraft.world.phys.Vec3(0,1,0),right);
        var d=new NativeSprites.Draw(e.getPoseStack(),e.getMultiBufferSource(),net.minecraft.world.phys.Vec3.ZERO,-1);
        for(String texture:new String[]{"item/ark_of_the_elements_back","item/ark_of_the_elements"})
            d.sprite(texture,at,face,Math.PI/2,.22,.22,NativeSprites.width(texture)/2d,NativeSprites.height(texture)/2d,ArkEffects.WHITE,false,true,15728880,texture.endsWith("_back")?0:.002);
    }
    @Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ElementsRegistration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ArkOfCosmos.ELEMENTS_ATTACK.get(),ElementsRenderer::new);}
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"),(stack,level,entity,seed)->entity!=null&&!entity.level().getEntitiesOfClass(ElementsEntity.class,entity.getBoundingBox().inflate(80),a->a.ownerId()==entity.getId()&&(a.kind()==ElementsEntity.BLADE||a.kind()==ElementsEntity.PARRY||a.kind()==ElementsEntity.BLAST)).isEmpty()?1:0));}
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->NativeSprites.clear());}
        @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent e){e.register(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get(),(g,font,stack,x,y)->{
            int charge=ElementsItem.charge(stack),cooldown=ElementsItem.cooldown(stack);
            if(charge>0){g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+11,x+15,y+13,0xFF000000);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+11,x+2+Math.round(13*charge/10f),y+12,0xFFFF9F36);}
            if(cooldown>0){g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+14,x+15,y+16,0xFF000000);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+14,x+2+Math.round(13*cooldown/340f),y+15,0xFFFF6324);}
            return charge>0||cooldown>0;
        });}
    }
}
