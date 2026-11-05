package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.ArkDamage;
import net.everla.projectc.arkofcosmos.combat.BerryTrip;
import net.everla.projectc.arkofcosmos.combat.BossHealingLock;
import net.everla.projectc.arkofcosmos.combat.HaloCompat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Wooden-sword stat mistletoe that punishes Odamane halo wearers. */
public final class MistilteinnItem extends SwordItem {
    public MistilteinnItem() { super(Tiers.WOOD, 3, -2.4f, new Properties()); }

    @Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
    public static final class AttackHandler {
        private AttackHandler() {}
        @SubscribeEvent
        public static void attack(AttackEntityEvent event) {
            Player attacker = event.getEntity();
            if (attacker.level().isClientSide
                    || !(event.getTarget() instanceof Player target)
                    || !(attacker.getMainHandItem().getItem() instanceof MistilteinnItem)
                    || !HaloCompat.equipped(target, "halo_of_the_end")) return;
            event.setCanceled(true);
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            if (ArkDamage.hurt(target, source, 4)) {
                BossHealingLock.apply(target, attacker, 20);
                BerryTrip.trip(target);
            }
        }
    }
}
