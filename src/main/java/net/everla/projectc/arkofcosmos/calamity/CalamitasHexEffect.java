package net.everla.projectc.arkofcosmos.calamity;

import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.entity.CalamitasAttackEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Display-only Hex potion; WitchFlames owns Java timers, damage and penalties. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = net.everla.projectc.arkofcosmos.ArkOfCosmos.MOD_ID)
public final class CalamitasHexEffect extends MobEffect {
    public static final java.util.UUID ARMOR_MODIFIER = java.util.UUID.fromString("0c44e525-4704-44dd-ad96-bddcdff536bc");
    public CalamitasHexEffect() {
        super(MobEffectCategory.HARMFUL, 0x79154D);
    }

    private static boolean active(LivingEntity entity) {
        return net.everla.projectc.arkofcosmos.enchant.WitchFlames.weakened(entity);
    }

    public static float outgoing(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return source.getEntity() instanceof LivingEntity attacker && active(attacker) ? amount * .85f : amount;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void weaken(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        event.setAmount(outgoing(event.getSource(), event.getAmount()));
    }

    /** Source buffNoSave: filter the serialized snapshot without changing the live player. */
    public static void filterPlayerSave(LivingEntity entity, net.minecraft.nbt.CompoundTag tag) {
        if (!(entity instanceof Player) || !CalamitasContent.ENABLED
                || !CalamitasContent.VULNERABILITY_HEX.isPresent()) return;
        var effects = tag.getList("ActiveEffects", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = effects.size() - 1; i >= 0; i--) {
            var effect = MobEffectInstance.load(effects.getCompound(i));
            if (effect != null && effect.getEffect() == CalamitasContent.VULNERABILITY_HEX.get()) effects.remove(i);
        }
        // Also remove permanent modifiers from saves made by the previous potion implementation.
        var attributes = tag.getList("Attributes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            var modifiers = attributes.getCompound(i).getList("Modifiers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int j = modifiers.size() - 1; j >= 0; j--) {
                var modifier = modifiers.getCompound(j);
                if (modifier.hasUUID("UUID") && ARMOR_MODIFIER.equals(modifier.getUUID("UUID"))) modifiers.remove(j);
            }
        }
    }

    public static int duration(CalamitasAttackEntity.Kind kind) {
        // Default Calamity NerfExpertDebuffs=true: no expert/master duration multiplier.
        return switch (kind) {
            case HELLBLAST, HELLBLAST_BARRAGE, DART -> 60;
            case WAVE -> 40;
            case FIREBLAST -> 80;
            case GIGABLAST -> 100;
            case MAELSTROM -> 120;
            default -> 0;
        };
    }

    /** Call only after a successful native projectile hit. Source OnHitPlayer does not affect mobs. */
    public static void onHit(LivingEntity victim, CalamitasAttackEntity.Kind kind) {
        int ticks = duration(kind);
        if (ticks > 0 && victim instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            player.addEffect(new MobEffectInstance(CalamitasContent.VULNERABILITY_HEX.get(), ticks, 0, false, false, true));
            net.everla.projectc.arkofcosmos.combat.BossHealingLock.apply(player, ticks);
        }
    }
}
