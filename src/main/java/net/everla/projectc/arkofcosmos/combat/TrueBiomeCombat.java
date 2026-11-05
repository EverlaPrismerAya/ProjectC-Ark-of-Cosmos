package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity;
import net.everla.projectc.arkofcosmos.item.TrueBiomeBladeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class TrueBiomeCombat {
    private TrueBiomeCombat(){}
    private static final Map<UUID,Control> INPUT=new HashMap<>();
    private static final class Control {boolean left,proc;int idle,timer;ItemStack stack=ItemStack.EMPTY;}
    public static boolean holding(Player p){return p.getMainHandItem().is(ArkOfCosmos.TRUE_BIOME_BLADE.get());}
    public static boolean channeling(Player p){Control c=INPUT.get(p.getUUID());return c!=null&&c.left&&holding(p)&&p.isAlive()&&!p.isSpectator();}
    public static List<TrueBiomeEntity> attacks(Player p){return p.level().getEntitiesOfClass(TrueBiomeEntity.class,p.getBoundingBox().inflate(128),e->e.ownerId()==p.getId());}
    private static boolean active(Player p){return attacks(p).stream().anyMatch(e->e.kind()==TrueBiomeEntity.BLADE);}
    public static void cancel(Player p){
        if(p.level() instanceof net.minecraft.server.level.ServerLevel level){
            var list=new ArrayList<TrueBiomeEntity>();
            for(var e:level.getAllEntities())if(e instanceof TrueBiomeEntity a&&a.ownerId()==p.getId())list.add(a);
            list.forEach(TrueBiomeEntity::discardAttack);
        }
    }
    public static void input(ServerPlayer p,boolean left,int slot,int mode){
        if(!holding(p)||!p.isAlive()||p.isSpectator())return;
        Control c=INPUT.computeIfAbsent(p.getUUID(),id->new Control());
        if(c.stack!=p.getMainHandItem()){cancel(p);c.timer=0;c.proc=false;c.stack=p.getMainHandItem();}
        c.idle=0;c.left=left;
        if(slot>=0&&slot<2&&mode>=0&&mode<4){
            cancel(p);TrueBiomeBladeItem.select(c.stack,slot,mode);c.left=false;c.timer=0;c.proc=false;
            p.getInventory().setChanged();p.containerMenu.broadcastChanges();
            sound(p,p,"galaxia_lightning",.4f,0,false,false);
        }
        if(c.left&&!active(p))TrueBiomeEntity.blade(p,TrueBiomeBladeItem.main(c.stack));
    }
    public static void proc(Player p,double probability){
        Control c=INPUT.get(p.getUUID());if(c!=null&&holding(p)&&p.getRandom().nextFloat()<=probability)c.proc=true;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!holding(p)||!p.isAlive()||p.isSpectator()){
            if(INPUT.remove(p.getUUID())!=null)cancel(p);return;
        }
        Control c=INPUT.computeIfAbsent(p.getUUID(),id->new Control());
        if(c.stack!=p.getMainHandItem()){cancel(p);c.left=false;c.timer=0;c.proc=false;c.stack=p.getMainHandItem();}
        if(++c.idle>40)c.left=false;
        int secondary=TrueBiomeBladeItem.secondary(c.stack);
        for(int step=0;step<3;step++){
            if(active(p))c.timer++;
            if(secondary==0&&c.timer%30==29&&p.getRandom().nextBoolean()){
                TrueBiomeEntity.passive(p,0);sound(p,p,"galaxia_item78",1,0,true,true);c.timer++;
            }
            if(secondary==1)TrueBiomeEntity.ensureHook(p,c.proc);
            if(secondary==2&&c.proc)p.heal(net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules.originalDamage(7));
            if(secondary==3&&c.timer%120==119){TrueBiomeEntity.passive(p,3);c.timer++;}
            if(secondary==1||secondary==2)c.proc=false;
        }
        if(c.left&&!active(p))TrueBiomeEntity.blade(p,TrueBiomeBladeItem.main(c.stack));
    }
    public static void sound(net.minecraft.world.entity.Entity at,Player owner,String name,float volume,float pitch,boolean ownerOnly,boolean relative){
        var s=new net.minecraft.nbt.CompoundTag();s.putString("Event","true_biome_sound");s.putString("Sound",name);
        s.putInt("Owner",owner.getId());s.putFloat("Volume",volume);s.putFloat("Pitch",pitch);s.putBoolean("OwnerOnly",ownerOnly);s.putBoolean("Relative",relative);
        net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector(s,"At",at.position());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.nearbyEffect(at,s);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){INPUT.remove(e.getEntity().getUUID());cancel(e.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){INPUT.remove(e.getEntity().getUUID());cancel(e.getOriginal());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){INPUT.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e){
        if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof TrueBiomeEntity a)e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
}
