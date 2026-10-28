package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.item.ArkItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.everla.projectc.arkofcosmos.calamity.combat.ArkWeaponRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class ArkCombat {
    private ArkCombat() {}
    // Terraria damage remains proportional; this conversion changes no attack sequence or hit timing.
    public static final float BASE_DAMAGE = ArkWeaponRules.BASE_DAMAGE;
    public static float scaled(float damage, float multiplier) { return ArkWeaponRules.scaled(damage, multiplier); }
    public static final double PIXEL = 1d / 16, MAX_REACH = 760 * PIXEL;
    private static final Map<UUID, Controls> CONTROLS = new HashMap<>();
    public static final class Controls {
        boolean left;
        public boolean right, alt, parried;
        long invulnerableUntil;
        public long dashInvulnerableUntil;
        int parryCooldown, useCooldown, idleTicks;
        ItemStack parryStack = ItemStack.EMPTY;
    }
    public static Controls controls(Player player) { return CONTROLS.computeIfAbsent(player.getUUID(), k -> new Controls()); }
    public static int parryCooldown(Player player) { return controls(player).parryCooldown; }
    public static boolean holding(Player player) { return player.getMainHandItem().is(ArkOfCosmos.ARK_OF_COSMOS.get()); }
    public static boolean channeling(Player player) { return holding(player) && controls(player).left && player.isAlive() && !player.isSpectator(); }

    public static void input(ServerPlayer player, ArkNetwork.Input input) {
        Controls c = controls(player);
        boolean leftPress = input.left() && !c.left;
        c.left = holding(player) && input.left();
        c.alt = holding(player) && input.alt();
        boolean press = input.right() && !c.right;
        c.right = holding(player) && input.right();
        c.idleTicks = 0;
        if (press && holding(player) && player.isAlive() && !player.isSpectator()) right(player, c);
        if (leftPress && holding(player) && player.isAlive() && !player.isSpectator()) attack(player, c);
    }

    private static void right(ServerPlayer player, Controls c) {
        if (activeBlade(player) || c.useCooldown > 0) return;
        ItemStack stack = player.getMainHandItem();
        int charge = ArkItem.charge(stack);
        if (c.alt && charge > 0) {
            ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLAST, 0, charge,
                    scaled(BASE_DAMAGE, charge * 1.35f * .5f), player.getBoundingBox().getCenter().add(player.getLookAngle().scale(90 * PIXEL)), Vec3.ZERO,
                    AttackFrame.capture(player));
            ArkItem.charge(stack, 0);
            c.useCooldown = 15;
        } else if (c.parryCooldown == 0) {
            c.parried = false;
            c.parryStack = stack;
            c.invulnerableUntil = player.level().getGameTime() + 7; // Exactly 0.35 s at 20 TPS.
            c.parryCooldown = 340;
            stack.getOrCreateTag().putInt("ArkParryCooldown", 340);
            c.useCooldown = 15;
            ArkAttackEntity.create(player, ArkAttackEntity.Kind.PARRY, 0, 0, BASE_DAMAGE,
                    player.getBoundingBox().getCenter(), Vec3.ZERO, AttackFrame.capture(player));
        }
    }

    private static boolean activeBlade(Player player) {
        return !player.level().getEntitiesOfClass(ArkAttackEntity.class, player.getBoundingBox().inflate(64),
                attack -> attack.ownerId() == player.getId() && attack.kind() == ArkAttackEntity.Kind.BLADE).isEmpty();
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Controls c = CONTROLS.get(player.getUUID());
        if (c == null) return;
        int oldCooldown = c.parryCooldown;
        c.parryCooldown = Math.max(0, c.parryCooldown - 3);
        if (oldCooldown > 0 && c.parryCooldown == 0) ArkOfCosmos.sound(player, "item35", 2);
        c.useCooldown = Math.max(0, c.useCooldown - 3);
        // Releases are sent on GUI/focus/slot changes; heartbeat expiry also handles interrupted clients.
        if (++c.idleTicks > 40 || !holding(player) || !player.isAlive() || player.isSpectator()) {
            c.left = c.right = c.alt = false;
            return;
        }
        if (!c.left || c.useCooldown > 0 || activeBlade(player)) return;
        attack(player, c);
    }

    private static void attack(ServerPlayer player, Controls c) {
        if (c.useCooldown > 0 || activeBlade(player)) return;
        ItemStack stack = player.getMainHandItem();
        int combo = Math.floorMod(stack.getOrCreateTag().getInt("ArkCombo"), 5);
        int mode = ArkWeaponRules.comboMode(combo);
        int charge = ArkItem.charge(stack);
        float damage = charge > 0 ? scaled(BASE_DAMAGE, 1.35f) : BASE_DAMAGE;
        AttackFrame frame = AttackFrame.capture(player);
        Vec3 center = player.getBoundingBox().getCenter();
        ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, mode, charge, damage, center, frame.forward(), frame);
        if (mode != 2) ArkAttackEntity.create(player, ArkAttackEntity.Kind.NEEDLE, 0, 0,
                scaled(damage, .7f), center.add(frame.forward().scale(20 * PIXEL)), frame.forward().scale(28 * 1.4 * PIXEL), frame);
        stack.getOrCreateTag().putInt("ArkCombo", (combo + 1) % 5);
        ArkItem.charge(stack, charge - 1);
        c.useCooldown = 15;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void incoming(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        Controls c = CONTROLS.get(player.getUUID());
        if (c == null) return;
        long now = player.level().getGameTime();
        if (now < c.invulnerableUntil) {
            event.setCanceled(true);
            if (!c.parried) {
                parrySuccess(player);
            }
        } else if (now < c.dashInvulnerableUntil) event.setCanceled(true);
    }

    public static void parrySuccess(Player player) {
        Controls c = controls(player);
        if (c.parried) return;
        c.parried = true;
        AttackFrame effectFrame=AttackFrame.capture(player);
        for(ArkAttackEntity a:player.level().getEntitiesOfClass(ArkAttackEntity.class,player.getBoundingBox().inflate(20)))
            if(a.ownerId()==player.getId()&&a.kind()==ArkAttackEntity.Kind.PARRY){effectFrame=a.attackFrame();break;}
        ArkNetwork.parryEffect(player,effectFrame);
        ItemStack stack = c.parryStack.isEmpty() ? player.getMainHandItem() : c.parryStack;
        ArkItem.charge(stack, 16);
        stack.getOrCreateTag().putInt("ArkCombo", 0);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.arkofcosmos.parry"), true);
        ArkOfCosmos.sound(player, "crystal", 1);
        ArkOfCosmos.sound(player, "snap", 1.3f);
    }

    public static boolean canHit(Player owner, LivingEntity entity) {
        return entity.isAlive() && entity != owner && !(entity instanceof Player) && !entity.isAlliedTo(owner)
                && !entity.isInvulnerable() && !entity.isSpectator();
    }
    public static boolean enemy(Player owner, LivingEntity entity) {
        if (!canHit(owner, entity)) return false;
        if (entity instanceof NeutralMob) return entity instanceof Mob mob && mob.getTarget() == owner;
        return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == owner;
    }

    public static LivingEntity nearest(Player player, Vec3 from, double radius) {
        LivingEntity best = null;
        double distance = radius * radius;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(radius), e -> enemy(player, e))) {
            double d = entity.getBoundingBox().getCenter().distanceToSqr(from);
            if (d < distance) { distance = d; best = entity; }
        }
        return best;
    }

    public static Vec3 aim(Player player) {
        Vec3 origin = player.getEyePosition(), look = player.getLookAngle();
        LivingEntity best = null;
        double bestCos = Math.cos(Math.toRadians(12)), bestDistance = Double.MAX_VALUE;
        for (Entity candidate : ((ServerLevel)player.level()).getAllEntities()) {
            if (!(candidate instanceof LivingEntity entity) || !enemy(player, entity)) continue;
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(origin);
            double distance = offset.length();
            if (distance < .001) continue;
            double cos = offset.dot(look) / distance;
            if (cos > bestCos || (Math.abs(cos - bestCos) < 1e-6 && distance < bestDistance)) {
                best = entity; bestCos = cos; bestDistance = distance;
            }
        }
        Vec3 center = player.getBoundingBox().getCenter();
        Vec3 offset = best == null ? look.scale(20) : best.getBoundingBox().getCenter().subtract(center);
        return center.add(offset.length() > MAX_REACH ? offset.normalize().scale(MAX_REACH) : offset);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent event) {
        if (event.getDamageSource() != null && event.getDamageSource().getDirectEntity() instanceof ArkAttackEntity attack)
            event.setLootingLevel(attack.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { CONTROLS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { CONTROLS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock event) { if (holding(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) { if (holding(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void rightEntity(PlayerInteractEvent.EntityInteract event) { if (holding(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) { if (holding(event.getPlayer())) event.setCanceled(true); }
}
