package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/** GR easter egg: OdamaneHalo wearers occasionally fall flat with hands behind the back in sweet berry bushes. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class BerryTrip {
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> HALO_BERRY_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"halo_sweet_berry_bush"));
    private BerryTrip(){}
    public static final int DURATION=100, INTERVAL=10;
    // Keyed by entity id and stamped with the side's own game time; the client map is filled from the sync packet.
    private static final Map<Integer,Long> SERVER=new HashMap<>(), CLIENT=new HashMap<>(), NEXT_CHECK=new HashMap<>();

    private static Map<Integer,Long> starts(Entity e){return e.level().isClientSide?CLIENT:SERVER;}
    public static float ticks(Entity e,float partial){
        Long start=starts(e).get(e.getId());
        if(start==null)return -1;
        long age=e.level().getGameTime()-start;
        if(age<0||age>=DURATION){starts(e).remove(e.getId());return -1;}
        return age+partial;
    }
    public static boolean tripped(Entity e){return e instanceof Player&&ticks(e,0)>=0;}
    public static void clientStart(int entity,long gameTime){CLIENT.put(entity,gameTime);}
    public static void clearClient(){CLIENT.clear();}

    public static void trip(Player p) {
        if (p.level().isClientSide) return;
        long now = p.level().getGameTime();
        SERVER.put(p.getId(), now);
        p.setSprinting(false);
        p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
        p.hurtMarked = true;
        CompoundTag data = new CompoundTag();
        data.putString("Event", "berry_trip");
        data.putInt("Entity", p.getId());
        ArkNetwork.effect(p, data);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||p.level().isClientSide||e.getAmount()<=0||!e.getSource().is(DamageTypes.SWEET_BERRY_BUSH))return;
        checkTrip(p);
    }
    public static boolean berryDamage(Entity entity,net.minecraft.world.damagesource.DamageSource source,float amount){
        return berryDamage(entity,source,amount,()->entity.hurt(source,amount));
    }
    public static boolean berryDamage(Entity entity,net.minecraft.world.damagesource.DamageSource source,float amount,java.util.function.BooleanSupplier original){
        if(!(entity instanceof Player p)||!HaloCompat.equipped(p,"halo_of_the_end"))return original.getAsBoolean();
        var berrySource=new net.minecraft.world.damagesource.DamageSource(p.level().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(HALO_BERRY_DAMAGE));
        if(!ArkDamage.hurtAtInterval(p,10,()->ArkDamage.hurtUnmitigated(p,berrySource,1)))return false;
        BossHealingLock.apply(p,10);
        checkTrip(p);
        return true;
    }
    private static void checkTrip(Player p){
        long now=p.level().getGameTime();
        if(now<NEXT_CHECK.getOrDefault(p.getId(),Long.MIN_VALUE)||tripped(p)||!HaloCompat.equipped(p,"halo_of_the_end"))return;
        NEXT_CHECK.put(p.getId(),now+INTERVAL);
        if(p.getRandom().nextFloat()>=net.everla.projectc.arkofcosmos.ProjectCCommonConfig.value("integration.end_halo_trip_chance"))return;
        trip(p);
        ArkOfCosmos.sound(p,"wilhelm_scream",1);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SERVER.remove(e.getEntity().getId());NEXT_CHECK.remove(e.getEntity().getId());}
    @SubscribeEvent public static void melee(AttackEntityEvent e){if(tripped(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem e){if(tripped(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock e){if(tripped(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void useEntity(PlayerInteractEvent.EntityInteract e){if(tripped(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void breakBlock(PlayerInteractEvent.LeftClickBlock e){if(tripped(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void item(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick e){if(tripped(e.getEntity()))e.getEntity().stopUsingItem();}
}
