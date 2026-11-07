package net.everla.projectc.arkofcosmos.enchant;

import java.util.Map;
import java.util.WeakHashMap;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.entity.NecromanticAttackEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Source ashesOnDeath lasts ten source frames, independently of the killing damage. */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class NecromanticAsh {
    private static final Map<LivingEntity,Long> UNTIL=new WeakHashMap<>();
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> NON_ORGANIC=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"non_organic"));
    public static void hit(LivingEntity target,long sourceFrame){if(target.isAlive())UNTIL.put(target,sourceFrame+10);}
    public static boolean organic(LivingEntity target){return !(target instanceof net.minecraft.world.entity.player.Player)&&!(target instanceof net.minecraft.world.entity.animal.AbstractGolem)&&!(target instanceof net.minecraft.world.entity.decoration.ArmorStand)&&!target.getType().is(NON_ORGANIC);}
    public static boolean marked(LivingEntity target){return UNTIL.getOrDefault(target,Long.MIN_VALUE)>target.level().getGameTime()*3;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        var target=event.getEntity();if(target.level().isClientSide)return;
        boolean laser=event.getSource().getDirectEntity() instanceof NecromanticAttackEntity attack&&attack.kind()==NecromanticAttackEntity.Kind.RANCOR;
        if(organic(target)&&(laser||marked(target)))WitchEnchanting.necromanticVisual(target,2);
        UNTIL.remove(target);
    }
}
