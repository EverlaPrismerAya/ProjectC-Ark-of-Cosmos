package net.everla.projectc.arkofcosmos.combat;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.ViolenceEntity;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class ViolenceCombat {
    private ViolenceCombat() {}
    private static final Map<UUID,Controls> CONTROLS=new HashMap<>();
    private static final class Controls { boolean left,right; long heartbeat,nextUse; ViolenceEntity attack; }
    public static boolean holding(Player p) { return p!=null && p.getMainHandItem().is(ArkOfCosmos.VIOLENCE.get()); }
    public static boolean channeling(Player p) {
        Controls c=CONTROLS.get(p.getUUID());
        return c!=null && c.left && holding(p) && p.level().getGameTime()-c.heartbeat<=40;
    }
    public static boolean active(Player p) {
        if(p.level().isClientSide)return !p.level().getEntitiesOfClass(ViolenceEntity.class,p.getBoundingBox().inflate(256),e->e.ownerId()==p.getId()).isEmpty();
        Controls c=CONTROLS.get(p.getUUID());return c!=null && c.attack!=null && !c.attack.isRemoved();
    }
    public static void input(ServerPlayer p,ArkNetwork.Input input) {
        if(!holding(p)){Controls c=CONTROLS.get(p.getUUID());if(c!=null)c.left=c.right=false;return;}
        Controls c=CONTROLS.computeIfAbsent(p.getUUID(),k->new Controls());
        c.left=input.left();c.right=input.right();c.heartbeat=p.level().getGameTime();
        use(p,c);
    }
    private static void use(ServerPlayer p,Controls c) {
        long now=p.level().getGameTime()*3;
        if(!holding(p)||!WeaponUser.active(p)||!p.level().hasChunkAt(p.blockPosition())||now<c.nextUse||active(p)||!c.left&&!c.right)return;
        c.attack=ViolenceEntity.launch(p,c.right);
        c.nextUse=now+(c.right?60:25);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        Controls c=CONTROLS.get(p.getUUID());if(c==null)return;
        if(!holding(p)||!WeaponUser.active(p)||p.level().getGameTime()-c.heartbeat>40){c.left=c.right=false;return;}
        use(p,c);
    }
    private static void clear(Player p) { Controls c=CONTROLS.remove(p.getUUID());if(c!=null&&c.attack!=null)c.attack.discard(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){clear(event.getOriginal());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock event){if(holding(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event){if(holding(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void rightEntity(PlayerInteractEvent.EntityInteract event){if(holding(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent event){if(holding(event.getPlayer()))event.setCanceled(true);}
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent event){
        if(event.getDamageSource()!=null&&event.getDamageSource().getDirectEntity() instanceof ViolenceEntity attack)
            event.setLootingLevel(attack.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
}
