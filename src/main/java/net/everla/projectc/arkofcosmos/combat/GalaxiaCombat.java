package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.GalaxiaEntity;
import net.everla.projectc.arkofcosmos.item.GalaxiaItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class GalaxiaCombat {
    private static final Map<UUID,Control> INPUT = new HashMap<>();
    private static final class Control { boolean left; int idle, timer; }
    public static boolean holding(Player player) { return player.getMainHandItem().is(ArkOfCosmos.GALAXIA.get()); }
    public static boolean channeling(Player player) {
        Control c=INPUT.get(player.getUUID());
        return c!=null && c.left && holding(player) && player.isAlive() && !player.isSpectator();
    }
    public static void input(ServerPlayer player, boolean left, int selection) {
        Control c=INPUT.computeIfAbsent(player.getUUID(), id->new Control());
        c.idle=0;
        c.left=left && holding(player) && !player.isSpectator();
        if(selection>=0 && selection<4 && holding(player) && !active(player)) {
            player.getMainHandItem().getOrCreateTag().putInt("GalaxiaMode",selection);
            sound(player,player.getId(),"lightning",.4f,false,false);
        }
        if(c.left && !active(player)) GalaxiaEntity.blade(player,GalaxiaItem.mode(player.getMainHandItem()));
    }
    private static boolean active(Player player) {
        return !player.level().getEntitiesOfClass(GalaxiaEntity.class,player.getBoundingBox().inflate(128),
                e->e.ownerId()==player.getId() && e.kind()==GalaxiaEntity.BLADE).isEmpty();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Control c=INPUT.get(p.getUUID()); if(c==null) return;
        if(++c.idle>40 || !holding(p) || !p.isAlive() || p.isSpectator()) c.left=false;
        if(!holding(p)) return;
        for(int substep=0;substep<3;substep++) {
            if(active(p)) c.timer++;
            if(c.timer%500==449) {
                sound(p,p.getId(),"item78",1,true,true);
                GalaxiaEntity.ring(p,GalaxiaItem.mode(p.getMainHandItem())<2);
                c.timer++;
            }
        }
        if(c.left && !active(p)) GalaxiaEntity.blade(p,GalaxiaItem.mode(p.getMainHandItem()));
    }
    public static void sound(net.minecraft.world.entity.Entity entity,int owner,String name,float volume,boolean ownerOnly,boolean relative) {
        var data=new net.minecraft.nbt.CompoundTag();data.putString("Event","galaxia_sound");
        data.putString("Sound",name);data.putInt("Owner",owner);data.putFloat("Volume",volume);
        data.putBoolean("OwnerOnly",ownerOnly);data.putBoolean("Relative",relative);
        data.putDouble("AtX",entity.getX());data.putDouble("AtY",entity.getY());data.putDouble("AtZ",entity.getZ());
        // Distribute through the established owner so first-update projectile sounds are not lost before tracking starts.
        var player=entity.level().getEntity(owner);
        net.everla.projectc.arkofcosmos.network.ArkNetwork.effect(player==null?entity:player,data);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { INPUT.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { INPUT.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e) { if(holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e) { if(holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e) { if(holding(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e) {
        if(e.getDamageSource()!=null && e.getDamageSource().getDirectEntity() instanceof GalaxiaEntity attack)
            e.setLootingLevel(attack.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
}
