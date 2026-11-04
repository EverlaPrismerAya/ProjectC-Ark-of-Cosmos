package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.BiomeEntity;
import net.everla.projectc.arkofcosmos.item.BiomeBladeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class BiomeCombat {
    private static final List<TagKey<Biome>> ENVIRONMENTS=Arrays.stream(new String[]{"hot","cold","tropical","evil","holy","astral","marine"}).map(name->TagKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"biome_blade/"+name))).toList();
    private static final String UNLOCKS="BiomeBladeUnlocks";
    public static int unlocked(Player p){return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(UNLOCKS)&255;}
    public static void unlock(ServerPlayer p,int mode){
        if(mode<0||mode>7)return;int old=unlocked(p),mask=old|(1<<mode);
        if(mask!=old){var data=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);data.putInt(UNLOCKS,mask);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,data);sync(p);}
    }
    public static void sync(ServerPlayer p){net.everla.projectc.arkofcosmos.network.ArkNetwork.biomeUnlocks(p,unlocked(p));}
    public static void select(ServerPlayer p,int slot,int mode){
        if(!holding(p)||!p.isAlive()||p.isSpectator()||slot!=0||mode<0||mode>7||(unlocked(p)&(1<<mode))==0)return;
        cancel(p);var stack=p.getMainHandItem();int a=mode;
        BiomeBladeItem.select(stack,a);var c=control(p);c.left=c.right=false;c.combo=0;c.comboReset=0;c.cooldown=Math.max(c.cooldown,a<=0?15:21);p.getInventory().setChanged();p.containerMenu.broadcastChanges();
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){restoreVisits(p);unlock(p,environment(p));sync(p);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}

    private BiomeCombat(){}
    public static final class Control {
        public boolean left,right;public int idle,cooldown,combo,lunge=2,power,comboReset;
        public ItemStack stack=ItemStack.EMPTY;
    }
    private static final Map<UUID,Control> INPUT=new HashMap<>();
    public static Control control(Player p){return INPUT.computeIfAbsent(p.getUUID(),id->new Control());}
    public static boolean holding(Player p){return p!=null&&p.getMainHandItem().is(ArkOfCosmos.BIOME_BLADE.get());}
    public static boolean channeling(Player p){return holding(p)&&control(p).left&&!control(p).right;}
    public static List<BiomeEntity> attacks(Player p){return p.level().getEntitiesOfClass(BiomeEntity.class,p.getBoundingBox().inflate(256),a->a.ownerId()==p.getId());}
    public static boolean active(Player p){return attacks(p).stream().anyMatch(BiomeEntity::activeBlade);}
    public static void input(ServerPlayer p,boolean left,boolean right){
        if(!holding(p)||!p.isAlive()||p.isSpectator())return;
        Control c=control(p);if(c.stack!=p.getMainHandItem()){cancel(p);INPUT.remove(p.getUUID());c=control(p);c.stack=p.getMainHandItem();}
        c.idle=0;c.left=left;c.right=false;
    }
    public static void cancel(Player p){
        if(p.level() instanceof net.minecraft.server.level.ServerLevel l){var list=new ArrayList<BiomeEntity>();for(var e:l.getAllEntities())if(e instanceof BiomeEntity b&&b.ownerId()==p.getId())list.add(b);list.forEach(BiomeEntity::discardAttack);}
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        unlock(p,environment(p));
        if(!holding(p)||!p.isAlive()||p.isSpectator()){if(INPUT.containsKey(p.getUUID())){cancel(p);INPUT.remove(p.getUUID());}return;}
        Control c=control(p);if(c.stack!=p.getMainHandItem()){cancel(p);INPUT.remove(p.getUUID());c=control(p);c.stack=p.getMainHandItem();}
        if(++c.idle>40)c.left=c.right=false;
        if(p.onGround()){c.lunge=2;if(c.power!=3)c.power=0;}
        for(int i=0;i<3;i++){
            if(c.cooldown>0)c.cooldown--;
            if(BiomeBladeItem.main(c.stack)==2&&!active(p)&&c.comboReset>0&&--c.comboReset==0)c.combo=0;
            if(c.cooldown<=0&&!active(p)){
                if(c.left){BiomeEntity.attack(p);c.cooldown=BiomeBladeItem.main(c.stack)<=0?15:21;c.comboReset=50;}
            }
        }
    }
    public static int environment(Player p){return environment(p.level().getBiome(p.blockPosition()),p.level().dimension());}
    private static int environment(net.minecraft.core.Holder<Biome> biome,net.minecraft.resources.ResourceKey<Level> dimension){
        int mode=0;
        for(int i=0;i<ENVIRONMENTS.size();i++)if(biome.is(ENVIRONMENTS.get(i))||i==3&&dimension.equals(Level.NETHER)||i==5&&dimension.equals(Level.END))mode=i+1;
        return mode;
    }
    /** Recover pre-install exploration where vanilla retained biome visit criteria. */
    public static void restoreVisits(ServerPlayer p){
        var advancement=p.server.getAdvancements().getAdvancement(ResourceLocation.fromNamespaceAndPath("minecraft","adventure/adventuring_time"));
        if(advancement==null)return;
        var registry=p.level().registryAccess().registryOrThrow(Registries.BIOME);
        for(String criterion:p.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()){
            var id=ResourceLocation.tryParse(criterion);if(id==null)continue;
            registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.BIOME,id)).ifPresent(biome->unlock(p,environment(biome,Level.OVERWORLD)));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){cancel(e.getEntity());INPUT.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){var data=e.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);data.putInt(UNLOCKS,unlocked(e.getOriginal()));e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,data);cancel(e.getOriginal());INPUT.remove(e.getOriginal().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){INPUT.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent e){if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof BiomeEntity a)e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));}
}
