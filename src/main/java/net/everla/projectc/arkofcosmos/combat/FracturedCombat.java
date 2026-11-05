package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.FracturedRules;
import net.everla.projectc.arkofcosmos.entity.FracturedEntity;
import net.everla.projectc.arkofcosmos.item.FracturedArkItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class FracturedCombat {
    public static void sound(net.minecraft.world.entity.Entity source, Player owner, String name, float volume, boolean relative, boolean ownerOnly) {
        var data=new net.minecraft.nbt.CompoundTag();data.putString("Event","fractured_sound");data.putString("Sound",name);
        data.putInt("Owner",owner.getId());data.putFloat("Volume",volume);data.putBoolean("Relative",relative);data.putBoolean("OwnerOnly",ownerOnly);
        net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector(data,"At",source.position());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(source,data);
    }
    private FracturedCombat() {}
    private static final Map<UUID, Controls> CONTROLS = new HashMap<>();
    private static final class Controls { boolean left, right; long heartbeat, nextUse; }
    public static boolean holding(Player p) { return p.getMainHandItem().is(ArkOfCosmos.FRACTURED_ARK.get()); }
    public static boolean active(Player p) {
        return !p.level().getEntitiesOfClass(FracturedEntity.class, p.getBoundingBox().inflate(16), e -> e.ownerId() == p.getId() && e.kind() != FracturedEntity.BEAM).isEmpty();
    }
    public static void input(ServerPlayer p, ArkNetwork.Input input) {
        Controls c = CONTROLS.get(p.getUUID());
        if (c == null) { if (!holding(p)) return; c = new Controls(); CONTROLS.put(p.getUUID(), c); }
        boolean left = input.left() && !c.left, right = input.right() && !c.right;
        c.left = holding(p) && input.left(); c.right = holding(p) && input.right(); c.heartbeat = p.level().getGameTime();
        if (!holding(p) || !WeaponUser.active(p)) return;
        long now = p.level().getGameTime() * 3;
        if (right && now >= c.nextUse && !active(p) && ArkCombat.beginParry(p, p.getMainHandItem(), FracturedRules.baseDamage())) c.nextUse = now + 15;
        if (left) attack(p, c, false);
    }
    private static void attack(ServerPlayer p, Controls c, boolean continuous) {
        long now = p.level().getGameTime() * 3;
        if (now < c.nextUse || active(p)) return;
        var stack = p.getMainHandItem();
        int sign = stack.getOrCreateTag().getInt("FracturedCombo") == -1 ? -1 : 1, charge = HaloCompat.charge(p, FracturedArkItem.charge(stack), 10);
        FracturedEntity.swing(p, sign, charge, AttackFrame.capture(p));
        stack.getOrCreateTag().putInt("FracturedCombo", -sign); FracturedArkItem.charge(stack, HaloCompat.charge(p, charge - 1, 10));
        // Carry the 22/3 fractional tick remainder during held input; clicks after an idle restart at now.
        c.nextUse = continuous && now - c.nextUse < 3 ? c.nextUse + FracturedRules.USE_TIME : now + FracturedRules.USE_TIME;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Controls c = CONTROLS.get(p.getUUID()); if (c == null) return;
        if (!holding(p) || !WeaponUser.active(p) || p.level().getGameTime() - c.heartbeat > 40) { c.left = c.right = false; return; }
        if (c.left) attack(p, c, true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { CONTROLS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { CONTROLS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void knockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) { if (FracturedEntity.hurting(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e) {
        if (e.getDamageSource() != null && e.getDamageSource().getDirectEntity() instanceof FracturedEntity attack)
            e.setLootingLevel(attack.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) { if (holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock e) { if (holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightEntity(PlayerInteractEvent.EntityInteract e) { if (holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) { if (holding(e.getPlayer())) e.setCanceled(true); }
}
