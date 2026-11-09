package net.everla.projectc.arkofcosmos.combat;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.FirstFractalEntity;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class FirstFractalCombat {
    private FirstFractalCombat() {}
    private static final Map<UUID,Controls> CONTROLS=new HashMap<>();
    private static final class Controls {
        boolean left,tap; long heartbeat; int frame; ItemStack stack=ItemStack.EMPTY;
        final List<FirstFractalEntity> attacks=new ArrayList<>();
    }
    public static boolean holding(Player p) { return p!=null&&ArkOfCosmos.FIRST_FRACTAL!=null&&p.getMainHandItem().is(ArkOfCosmos.FIRST_FRACTAL.get()); }
    public static boolean active(Player p) {
        if(p.level().isClientSide)return !p.level().getEntitiesOfClass(FirstFractalEntity.class,p.getBoundingBox().inflate(128),a->a.ownerId()==p.getId()).isEmpty();
        Controls c=CONTROLS.get(p.getUUID());return c!=null&&(c.left||c.attacks.stream().anyMatch(a->!a.isRemoved()));
    }
    public static void input(ServerPlayer p,ArkNetwork.Input input) {
        if(!holding(p))return;
        Controls c=CONTROLS.computeIfAbsent(p.getUUID(),id->new Controls());
        if(!c.left&&input.left()){c.frame=0;c.tap=true;}
        c.left=input.left();c.heartbeat=p.level().getGameTime();
        if(c.stack.isEmpty())c.stack=p.getMainHandItem();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        Controls c=CONTROLS.get(p.getUUID());if(c==null)return;
        if(!holding(p)||!WeaponUser.active(p)||p.getMainHandItem()!=c.stack){clear(p);return;}
        c.attacks.removeIf(a->a.isRemoved());
        if(p.level().getGameTime()-c.heartbeat>40)c.left=false;
        if(!c.left){
            if(c.tap)c.attacks.add(FirstFractalEntity.launch(p,0,0));
            c.tap=false;c.frame=0;return;
        }
        c.tap=false;
        for(int sub=0;sub<3;sub++) {
            if(c.frame%7==0)c.attacks.add(FirstFractalEntity.launch(p,c.frame/35d%1,sub*2));
            c.frame=(c.frame+1)%35;
        }
    }
    private static void clear(Player p) { Controls c=CONTROLS.remove(p.getUUID());if(c!=null)c.attacks.forEach(FirstFractalEntity::discardAttack); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){clear(e.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){clear(e.getOriginal());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){clear(e.getEntity());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){CONTROLS.clear();}
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(holding(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e){
        if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof FirstFractalEntity a)
            e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
}
