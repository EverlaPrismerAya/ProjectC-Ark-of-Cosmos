package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.FracturedCombat;
import net.everla.projectc.arkofcosmos.entity.FracturedEntity;
import net.everla.projectc.arkofcosmos.item.FracturedArkItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FracturedClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) { e.enqueueWork(() -> ItemProperties.register(ArkOfCosmos.FRACTURED_ARK.get(),
        ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"active"), (stack, level, entity, seed) -> entity instanceof net.minecraft.world.entity.player.Player p && FracturedCombat.active(p) ? 1 : 0)); }
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(ArkOfCosmos.FRACTURED_ATTACK.get(), FracturedRenderer::new); }
    @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent e) { e.register(ArkOfCosmos.FRACTURED_ARK.get(), (g,font,stack,x,y) -> {
        int charge=FracturedArkItem.charge(stack), cooldown=FracturedArkItem.cooldown(stack);
        if(charge>0) { g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+11,x+15,y+13,0xFF000000); g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+11,x+2+Math.round(13*charge/10f),y+12,0xFF64EDCE); }
        if(cooldown>0) { g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+14,x+15,y+16,0xFF000000); g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x+2,y+14,x+2+Math.round(13*cooldown/340f),y+15,0xFF6495ED); }
        return charge>0||cooldown>0;
    }); }
}
