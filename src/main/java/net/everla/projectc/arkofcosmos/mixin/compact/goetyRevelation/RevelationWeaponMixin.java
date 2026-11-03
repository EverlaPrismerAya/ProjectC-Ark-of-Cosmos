package net.everla.projectc.arkofcosmos.mixin.compact.goetyRevelation;

import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatch to the registered Valettein instance without replacing the held weapon. */
@Pseudo
@Mixin(targets="com.mega.revelationfix.common.event.handler.CommonEventsHandler$ModItemEvents", remap=false)
public abstract class RevelationWeaponMixin {
    @Unique
    private static boolean arkofcosmos$callback(LivingEvent event, String callback, Class<?> eventType) {
        var source = event instanceof LivingAttackEvent e ? e.getSource()
                : event instanceof LivingHurtEvent e ? e.getSource() : ((LivingDamageEvent) event).getSource();
        if (!ArkDamage.apollyonProjectile(event.getEntity(), source)) return false;
        var id = ResourceLocation.fromNamespaceAndPath("goety_revelation", "valettein");
        if (!ForgeRegistries.ITEMS.containsKey(id)) return false;
        var weapon = ForgeRegistries.ITEMS.getValue(id);
        try {
            // Native tag 2 skips Valettein's random effects, retaining its unconditional bypass flags.
            if (event instanceof LivingAttackEvent)
                source.getClass().getMethod("giveSpecialTag", byte.class).invoke(source, (byte) 2);
            if (event instanceof LivingHurtEvent e) e.setAmount(ArkDamage.apollyonPanel(source));
            if (event instanceof LivingDamageEvent e) e.setAmount(ArkDamage.apollyonPanel(source));
            weapon.getClass().getMethod(callback, ItemStack.class, eventType)
                    .invoke(weapon, new ItemStack(weapon), event);
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not invoke Valettein's native callback", failure);
        }
    }

    @Inject(method="modWeaponHurtEvent2", at=@At("HEAD"), cancellable=true)
    private static void arkofcosmos$attack(LivingAttackEvent event, CallbackInfo ci) {
        if (arkofcosmos$callback(event, "onAttack", LivingAttackEvent.class)) ci.cancel();
    }

    @Inject(method="modWeaponHurtEvent1", at=@At("HEAD"), cancellable=true)
    private static void arkofcosmos$hurt(LivingHurtEvent event, CallbackInfo ci) {
        if (arkofcosmos$callback(event, "onHurt", LivingHurtEvent.class)) ci.cancel();
    }

    @Inject(method="modWeaponHurtEvent3", at=@At("HEAD"), cancellable=true)
    private static void arkofcosmos$damage(LivingDamageEvent event, CallbackInfo ci) {
        if (arkofcosmos$callback(event, "onDamage", LivingDamageEvent.class)) ci.cancel();
    }
}
