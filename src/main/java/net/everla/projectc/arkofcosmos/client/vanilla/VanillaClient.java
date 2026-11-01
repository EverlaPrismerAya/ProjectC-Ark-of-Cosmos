package net.everla.projectc.arkofcosmos.client.vanilla;
import net.everla.projectc.arkofcosmos.item.SpearItem;
import net.everla.projectc.arkofcosmos.combat.vanilla.*;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class VanillaClient {
    private static final java.util.Map<Integer,Integer> FEEDBACK=new java.util.HashMap<>();
    public static void feedback(int id) { var level=Minecraft.getInstance().level;if(level!=null){var e=level.getEntity(id);if(e!=null)FEEDBACK.put(id,e.tickCount);} }
    public static float feedbackAge(net.minecraft.world.entity.Entity e,float partial) { return e.tickCount+partial-FEEDBACK.getOrDefault(e.getId(),-100); }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        var mc=Minecraft.getInstance();if(mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof SpearItem)||!e.isAttack())return;
        e.setCanceled(true);e.setSwingHand(false);ArkNetwork.spearJab();
    }
    @Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void render(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(VanillaCombat.WIND_CHARGE.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new); }
        @SubscribeEvent public static void particles(RegisterParticleProvidersEvent e) {
            e.registerSpriteSet(VanillaParticles.GUST.get(),sprites->(type,level,x,y,z,dx,dy,dz)->new GustParticle(level,x,y,z,sprites));
            e.registerSpecial(VanillaParticles.LARGE_EMITTER.get(),(type,level,x,y,z,dx,dy,dz)->new GustParticle.Seed(level,x,y,z,true));
            e.registerSpecial(VanillaParticles.EMITTER.get(),(type,level,x,y,z,dx,dy,dz)->new GustParticle.Seed(level,x,y,z,false));
        }
    }
}
