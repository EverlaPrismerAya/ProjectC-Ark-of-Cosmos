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
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> BONK_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, "mistilteinn"));
    public MistilteinnItem() { super(Tiers.WOOD, 3, -2.4f, new Properties()); }

    @Override
    public net.minecraft.world.item.Rarity getRarity(ItemStack stack) {
        var halo = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("goety_revelation", "halo_of_the_end"));
        return halo == null ? super.getRarity(stack) : halo.getRarity(halo.getDefaultInstance());
    }

    @Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
    public static final class AttackHandler {
        private AttackHandler() {}
        @SubscribeEvent
        public static void attack(AttackEntityEvent event) {
            Player attacker = event.getEntity();
            if (!attacker.level().isClientSide
                    && attacker.getMainHandItem().getItem() instanceof MistilteinnItem
                    && event.getTarget() instanceof net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity boss
                    && boss.enrageWithMistilteinn()) {
                event.setCanceled(true);
                attacker.resetAttackStrengthTicker();
                ArkOfCosmos.sound(boss, "bonk", 1);
                return;
            }
            if (attacker.level().isClientSide
                    || !(event.getTarget() instanceof Player target)
                    || !(attacker.getMainHandItem().getItem() instanceof MistilteinnItem)
                    || !HaloCompat.equipped(target, "halo_of_the_end")
                    || attacker.getAttackStrengthScale(.5f) < 1) return;
            event.setCanceled(true);
            attacker.resetAttackStrengthTicker();
            DamageSource source = new DamageSource(attacker.level().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(BONK_DAMAGE), attacker);
            if (ArkDamage.hurtAtInterval(target, 10, () -> ArkDamage.hurtWithVanillaCooldown(target, source, 4))) {
                BossHealingLock.apply(target, 20);
                BerryTrip.trip(target);
                ArkOfCosmos.sound(target, "bonk", 1);
            }
        }
    }
}
