package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.AncientsRules;
import net.everla.projectc.arkofcosmos.entity.AncientsEntity;
import net.everla.projectc.arkofcosmos.item.AncientsArkItem;
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
public final class AncientsCombat {
    private AncientsCombat() {}
    private static final Map<UUID, Controls> CONTROLS = new HashMap<>();
    private static final class Controls { boolean left, right, alt; long heartbeat, nextUse; }
    public static boolean holding(Player p) { return p.getMainHandItem().is(ArkOfCosmos.ARK_OF_THE_ANCIENTS.get()); }
    public static boolean active(Player p) {
        return !p.level().getEntitiesOfClass(AncientsEntity.class, p.getBoundingBox().inflate(16), e -> e.ownerId() == p.getId() && (e.kind() == AncientsEntity.BLADE || e.kind() == AncientsEntity.PARRY || e.kind() == AncientsEntity.BLAST)).isEmpty();
    }
    public static void input(ServerPlayer p, ArkNetwork.Input input) {
        Controls c = CONTROLS.get(p.getUUID());
        if (c == null) { if (!holding(p)) return; c = new Controls(); CONTROLS.put(p.getUUID(), c); }
        boolean left = input.left() && !c.left, right = input.right() && !c.right, burst = input.right() && input.alt() && !(c.right && c.alt);
        c.alt = holding(p) && input.alt();
        c.left = holding(p) && input.left(); c.right = holding(p) && input.right(); c.heartbeat = p.level().getGameTime();
        if (!holding(p) || !WeaponUser.active(p)) return;
        long now = p.level().getGameTime() * 3;
        if ((right || burst) && now >= c.nextUse && !active(p)) {
            int charge=HaloCompat.charge(p, AncientsArkItem.charge(p.getMainHandItem()), 10);
            if(c.alt && charge>0){AncientsEntity.blast(p,charge,AttackFrame.capture(p));AncientsArkItem.charge(p.getMainHandItem(), HaloCompat.charge(p, 0, 10));c.nextUse=now+25;}
            else if(right && ArkCombat.beginParry(p,p.getMainHandItem(),AncientsRules.baseDamage()))c.nextUse=now+15;
        }
        if (left) attack(p, c, false);
    }
    private static void attack(ServerPlayer p, Controls c, boolean continuous) {
        long now = p.level().getGameTime() * 3;
        if (now < c.nextUse || active(p)) return;
        var stack = p.getMainHandItem();
        int sign = stack.getOrCreateTag().getInt("AncientsCombo") == -1 ? -1 : 1, charge = HaloCompat.charge(p, AncientsArkItem.charge(stack), 10);
        AttackFrame frame=AttackFrame.capture(p);
        AncientsEntity.swing(p, sign, charge, frame); AncientsEntity.stars(p,sign,charge,frame);
        stack.getOrCreateTag().putInt("AncientsCombo", -sign); AncientsArkItem.charge(stack, HaloCompat.charge(p, charge - 1, 10));
        // Carry the 25/3 fractional tick remainder during held input; clicks after an idle restart at now.
        c.nextUse = continuous && now - c.nextUse < 3 ? c.nextUse + AncientsRules.USE_TIME : now + AncientsRules.USE_TIME;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Controls c = CONTROLS.get(p.getUUID()); if (c == null) return;
        if (!holding(p) || !WeaponUser.active(p) || p.level().getGameTime() - c.heartbeat > 40) { c.left = c.right = false; return; }
        if (c.left) attack(p, c, true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { CONTROLS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { CONTROLS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e) {
        if (e.getDamageSource() != null && e.getDamageSource().getDirectEntity() instanceof AncientsEntity attack)
            e.setLootingLevel(attack.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) { if (holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock e) { if (!WeaponUser.blockUsePriority(e.getLevel(), e.getPos()) && holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rightEntity(PlayerInteractEvent.EntityInteract e) { if (holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) { if (holding(e.getPlayer())) e.setCanceled(true); }
}
