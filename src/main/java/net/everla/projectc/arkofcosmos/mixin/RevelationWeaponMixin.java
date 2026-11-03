package net.everla.projectc.arkofcosmos.mixin;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.ApollyonCompatTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.mega.revelationfix.common.event.handler.CommonEventsHandler$ModItemEvents", remap=false)
public abstract class RevelationWeaponMixin {
 @Unique private static ItemStack arkofcosmos$weapon(LivingEntity a, LivingEntity v){
  ItemStack h=a.getMainHandItem();
  if(a instanceof Player&&v instanceof ApollyonCompatTarget x&&x.arkofcosmos$isNetherApollyon()&&(h.is(ArkOfCosmos.ARK_OF_COSMOS.get())||h.is(ArkOfCosmos.ARK_OF_THE_OLD_ONE.get()))){
   var id=ResourceLocation.fromNamespaceAndPath("goety_revelation","valettein");
   if(ForgeRegistries.ITEMS.containsKey(id))return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
  } return h;
 }
 @Redirect(method="modWeaponHurtEvent1",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",remap=true))
 private static ItemStack arkofcosmos$event1(LivingEntity a, LivingHurtEvent event){ return arkofcosmos$weapon(a,event.getEntity()); }
 @Redirect(method="modWeaponHurtEvent2",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",remap=true))
 private static ItemStack arkofcosmos$event2(LivingEntity a, LivingAttackEvent event){ return arkofcosmos$weapon(a,event.getEntity()); }
 @Redirect(method="modWeaponHurtEvent3",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",remap=true))
 private static ItemStack arkofcosmos$event3(LivingEntity a, LivingDamageEvent event){ return arkofcosmos$weapon(a,event.getEntity()); }
 @Redirect(method="modWeaponHurtEvent5",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",remap=true))
 private static ItemStack arkofcosmos$event5(LivingEntity a, LivingDeathEvent event){ return arkofcosmos$weapon(a,event.getEntity()); }
 @Redirect(method="modWeaponHurtEvent",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;",remap=true))
 private static ItemStack arkofcosmos$eventLowest(LivingEntity a, LivingDeathEvent event){ return arkofcosmos$weapon(a,event.getEntity()); }
}
