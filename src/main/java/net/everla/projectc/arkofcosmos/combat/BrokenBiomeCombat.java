package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity;
import net.everla.projectc.arkofcosmos.item.BrokenBiomeBladeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class BrokenBiomeCombat {
    public static int unlocked(Player p){return BiomeCombat.unlocked(p)&31;}
    public static void select(ServerPlayer p,int slot,int mode){
        if(!holding(p)||!p.isAlive()||p.isSpectator()||slot!=0||mode<0||mode>4||(unlocked(p)&(1<<mode))==0)return;
        cancel(p);var stack=p.getMainHandItem();int a=mode;
        BrokenBiomeBladeItem.select(stack,a);var c=control(p);c.left=c.right=false;c.combo=0;c.comboReset=0;c.cooldown=Math.max(c.cooldown,30);p.getInventory().setChanged();p.containerMenu.broadcastChanges();
    }
    private BrokenBiomeCombat(){}
    public static final class Control {
        public boolean left,right;public int idle,cooldown,combo,lunge=1,comboReset;
        public ItemStack stack=ItemStack.EMPTY;
    }
    private static final Map<UUID,Control> INPUT=new HashMap<>();
    public static Control control(Player p){return INPUT.computeIfAbsent(p.getUUID(),id->new Control());}
    public static boolean holding(Player p){return p!=null&&p.getMainHandItem().is(ArkOfCosmos.BROKEN_BIOME_BLADE.get());}
    public static boolean channeling(Player p){return holding(p)&&control(p).left&&!control(p).right;}
    public static List<BrokenBiomeEntity> attacks(Player p){return p.level().getEntitiesOfClass(BrokenBiomeEntity.class,p.getBoundingBox().inflate(256),a->a.ownerId()==p.getId());}
    public static boolean active(Player p){return attacks(p).stream().anyMatch(BrokenBiomeEntity::activeBlade);}
    public static void input(ServerPlayer p,boolean left,boolean right){
        if(!holding(p)||!p.isAlive()||p.isSpectator()||BrokenBiomeFreeze.frozen(p))return;
        Control c=control(p);if(c.stack!=p.getMainHandItem()){cancel(p);INPUT.remove(p.getUUID());c=control(p);c.stack=p.getMainHandItem();}
        c.idle=0;c.left=left;c.right=false;
    }
    public static void cancel(Player p){
        if(p.level() instanceof net.minecraft.server.level.ServerLevel l){var list=new ArrayList<BrokenBiomeEntity>();for(var e:l.getAllEntities())if(e instanceof BrokenBiomeEntity b&&b.ownerId()==p.getId())list.add(b);list.forEach(BrokenBiomeEntity::discardAttack);}
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        if(!holding(p)||!p.isAlive()||p.isSpectator()||BrokenBiomeFreeze.frozen(p)){if(INPUT.containsKey(p.getUUID())){cancel(p);INPUT.remove(p.getUUID());}return;}
        Control c=control(p);if(c.stack!=p.getMainHandItem()){cancel(p);INPUT.remove(p.getUUID());c=control(p);c.stack=p.getMainHandItem();}
        if(++c.idle>40)c.left=c.right=false;
        if(p.onGround())c.lunge=1;
        for(int i=0;i<3;i++){
            if(c.cooldown>0)c.cooldown--;
            if(BrokenBiomeBladeItem.main(c.stack)==2&&!active(p)&&c.comboReset>0&&--c.comboReset==0)c.combo=0;
            if(c.cooldown<=0&&!active(p)){
                if(c.left){BrokenBiomeEntity.attack(p);c.cooldown=30;c.comboReset=50;}
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){cancel(e.getEntity());INPUT.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){cancel(e.getOriginal());INPUT.remove(e.getOriginal().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){INPUT.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e){if (!WeaponUser.blockUsePriority(e.getLevel(), e.getPos()) && holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e){if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof BrokenBiomeEntity a)e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));}
}
