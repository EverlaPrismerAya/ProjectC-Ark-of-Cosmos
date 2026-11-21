package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.antiMobBattle.OrphanObliterator;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;

/** Port of EverlaArtifacts' Sword of Procedure; destructive finish uses OrphanObliterator. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class ProcedureSwordItem extends SwordItem {
    public ProcedureSwordItem() {
        super(new Tier() {
            public int getUses() { return 0; }
            public float getSpeed() { return 12; }
            public float getAttackDamageBonus() { return 8; }
            public int getLevel() { return 0; }
            public int getEnchantmentValue() { return 15; }
            public Ingredient getRepairIngredient() { return Ingredient.of(ArkOfCosmos.AURIC_BAR.get()); }
        }, 3, -2.4f, new Item.Properties().stacksTo(1).fireResistant().rarity(net.everla.projectc.arkofcosmos.item.ProcedureRarity.RAINBOW));
    }

    @Override public boolean isEnchantable(ItemStack stack) { return stack.getCount() == 1; }

    public static boolean unrestricted(Player player) { return player != null && player.isCreative() && player.hasPermissions(2); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, net.minecraft.world.InteractionHand hand) {
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override public void releaseUsing(ItemStack stack, net.minecraft.world.level.Level world, LivingEntity user, int remaining) {
        if (!(world instanceof ServerLevel level) || !(user instanceof Player player) || !player.isAlive() || !unrestricted(player)) return;
        // Snapshot before removal and effect spawning modify the level's entity collection.
        var targets = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(128),
                target -> target != player && target.isAlive() && target.distanceToSqr(player) <= 128 * 128);
        for (LivingEntity target : targets) {
            threadripperHit(player, target, true);
            ArkOfCosmos.sound(target, "item84", .3f);
            OrphanObliterator.obliterate(target, player, true);
        }
    }

    private static void threadripperHit(Player player, Entity anchor, boolean purge) {
        var bounds = anchor.getBoundingBox();
        Vec3 at = bounds.getCenter();
        AttackFrame view = AttackFrame.capture(player);
        Vec3 direction = at.subtract(player.getEyePosition()).normalize();
        if (direction.lengthSqr() < 1e-8) direction = view.forward();
        Vec3 right = view.right().subtract(direction.scale(view.right().dot(direction))).normalize();
        if (right.lengthSqr() < 1e-8) right = view.normal().cross(direction).normalize();
        var hit = new CompoundTag();
        hit.putString("Event", "threadripper_hit");
        hit.putInt("Owner", player.getId());
        hit.putString("Dimension", player.level().dimension().location().toString());
        vector(hit, "At", at);
        vector(hit, "F", direction);
        vector(hit, "R", right);
        hit.putDouble("Width", Math.max(bounds.getXsize(), bounds.getZsize()));
        hit.putDouble("Height", bounds.getYsize());
        hit.putInt("Seed", 31 * anchor.getUUID().hashCode() + Long.hashCode(player.level().getGameTime()));
        hit.putBoolean("Purge", purge);
        ArkNetwork.nearbyEffect(anchor, hit);
    }

    private static void vector(CompoundTag hit, String key, Vec3 v) {
        hit.putDouble(key + "X", v.x); hit.putDouble(key + "Y", v.y); hit.putDouble(key + "Z", v.z);
    }

    public static boolean guarding(LivingEntity user, net.minecraft.world.phys.Vec3 source) {
        if (!user.isUsingItem() || !(user.getUseItem().getItem() instanceof ProcedureSwordItem) || source == null) return false;
        var offset = source.subtract(user.position()).multiply(1, 0, 1);
        return offset.lengthSqr() > 1e-12 && user.getLookAngle().multiply(1, 0, 1).dot(offset) >= 0;
    }

    public static float guardDamage(LivingEntity user, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return guarding(user, source.getSourcePosition()) ? amount * .5f : amount;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        event.setAmount(guardDamage(event.getEntity(), event.getSource(), event.getAmount()));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void arrow(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide || !(event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)
                || !(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity user) || !guarding(user, arrow.position())
                || arrow.getDeltaMovement().dot(user.getLookAngle()) >= 0) return;
        event.setCanceled(true);
        var motion = arrow.getDeltaMovement().reverse();
        arrow.setOwner(user);
        arrow.shoot(motion.x, motion.y, motion.z, (float)Math.max(.5, motion.length()), 0);
        arrow.hasImpulse = true;
        arrow.hurtMarked = true;
    }

    @Override public net.minecraft.network.chat.Component getName(ItemStack stack) {
        var name = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> net.everla.projectc.arkofcosmos.client.ProcedureSwordTooltip.authorizedName());
        return (name == null ? super.getName(stack) : name).copy().withStyle(s -> s.withFont(ProcedureRarity.FONT));
    }

    @Override public Rarity getRarity(ItemStack stack) { return ProcedureRarity.RAINBOW; }

    public static boolean canObliterate(Player player, LivingEntity target) {
        return unrestricted(player)
                || target.hasCustomName() && "Procedure".equals(target.getCustomName().getString());
    }

    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        LivingEntity target = entity instanceof EnderDragonPart part ? part.parentMob
                : entity instanceof LivingEntity living ? living : null;
        if (target == null || target.isRemoved() || !canObliterate(player, target))
            return super.onLeftClickEntity(stack, player, entity);
        if (!(player.level() instanceof ServerLevel)) return true;
        if (unrestricted(player)) {
            threadripperHit(player, entity, false);
            ArkOfCosmos.sound(entity, "item84", .52f);
        }
        OrphanObliterator.obliterate(target, player, true);
        return true;
    }

    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                    HumanoidArm arm, ItemStack stack, float partialTick, float equip, float swing) {
                return net.everla.projectc.arkofcosmos.client.ProcedureSwordRenderer.blockTransform(pose,player,arm,stack,swing);
            }
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return net.everla.projectc.arkofcosmos.client.ProcedureSwordRenderer.INSTANCE;
            }
        });
    }
}
