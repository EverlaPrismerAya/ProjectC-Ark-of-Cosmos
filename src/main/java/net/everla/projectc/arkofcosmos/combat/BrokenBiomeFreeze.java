package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class BrokenBiomeFreeze {
 private BrokenBiomeFreeze(){}
 public static boolean frozen(LivingEntity target){return target.hasEffect(ArkOfCosmos.BROKEN_BIOME_FREEZE.get());}
 public static void apply(LivingEntity target,int sourceFrames){
     if(OldOneEffects.movementImmune(target,true))return;
     target.addEffect(new MobEffectInstance(ArkOfCosmos.BROKEN_BIOME_FREEZE.get(),(sourceFrames+2)/3));
 }
 @SubscribeEvent public static void attack(LivingAttackEvent e){if(e.getSource().getEntity() instanceof LivingEntity attacker&&frozen(attacker))e.setCanceled(true);}
 @SubscribeEvent public static void melee(AttackEntityEvent e){if(frozen(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem e){if(frozen(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e){if(frozen(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e){if(frozen(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent public static void item(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick e){if(frozen(e.getEntity()))e.getEntity().stopUsingItem();}
}
