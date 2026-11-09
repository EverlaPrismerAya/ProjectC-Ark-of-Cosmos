package net.everla.projectc.arkofcosmos;

import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;

/** The optional Apostle subclass is resolved only with both dependencies loaded. */
public final class SatsukiContent {
    private SatsukiContent() {}
    public static final boolean ENABLED=ModList.get().isLoaded("goety")&&ModList.get().isLoaded("goety_revelation");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<Attribute> ATTRIBUTES=DeferredRegister.create(ForgeRegistries.ATTRIBUTES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ArkOfCosmos.MOD_ID);
    public static final RegistryObject<Attribute> MAX_HEALTH=ENABLED?ATTRIBUTES.register("satsuki_max_health",()->new RangedAttribute("attribute.name.generic.max_health",6666,1,Double.MAX_VALUE).setSyncable(true)):null;
    public static final RegistryObject<EntityType<SatsukiRinEntity>> BOSS=ENABLED?ENTITIES.register("satsuki_rin",()->EntityType.Builder.<SatsukiRinEntity>of(SatsukiRinEntity::new,MobCategory.MONSTER).sized(.6f,1.8f).fireImmune().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:satsuki_rin")):null;
    // Goety's Apostle summoning entity (lead-up: darkness, sounds, flames, lightning); SatsukiSummonMixin swaps the spawned boss.
    public static final RegistryObject<EntityType<com.Polarice3.Goety.common.entities.util.SummonApostle>> SUMMON=ENABLED?ENTITIES.register("summon_satsuki_rin",()->EntityType.Builder.<com.Polarice3.Goety.common.entities.util.SummonApostle>of(com.Polarice3.Goety.common.entities.util.SummonApostle::new,MobCategory.MISC).fireImmune().sized(2,1.95f).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).build("arkofcosmos:summon_satsuki_rin")):null;
    public static final RegistryObject<Item> SPAWN_EGG=ENABLED?ITEMS.register("satsuki_rin_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(BOSS,0x77B957,0xE8D9A5,new Item.Properties())):null;
    public static final RegistryObject<SoundEvent> BATTLE_THEME=ENABLED?SOUNDS.register("satsuki_rin_battle_theme",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"satsuki_rin_battle_theme"))):null;
    public static final RegistryObject<SoundEvent> HURT=ENABLED?SOUNDS.register("satsuki_rin_hurt",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"satsuki_rin_hurt"))):null;
    public static final RegistryObject<SoundEvent> DEATH=ENABLED?SOUNDS.register("satsuki_rin_death",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"satsuki_rin_death"))):null;
    /** Satsuki herself, or the Satsuki at the top of a Goety servant's owner chain. */
    private static SatsukiRinEntity owner(net.minecraft.world.entity.LivingEntity entity){
        var seen=new java.util.HashSet<net.minecraft.world.entity.Entity>();
        while(entity!=null&&seen.add(entity)){
            if(entity instanceof SatsukiRinEntity rin)return rin;
            entity=entity instanceof com.Polarice3.Goety.api.entities.IOwned owned?owned.getTrueOwner():null;
        }
        return null;
    }
    private static java.util.Set<Item> lootItems;
    /** Items that can come out of arkofcosmos:entities/satsuki_rin (resolved once from the loaded table). */
    private static java.util.Set<Item> lootItems(net.minecraft.server.level.ServerLevel level){
        if(lootItems==null){
            var set=new java.util.HashSet<Item>();
            var table=level.getServer().getLootData().getLootTable(SatsukiRinEntity.LOOT);
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
            for(int i=0;i<4;i++)table.getRandomItems(params,stack->set.add(stack.getItem()));
            lootItems=set;
        }
        return lootItems;
    }
    public static void register(IEventBus bus) {
        if(!ENABLED)return;
        ATTRIBUTES.register(bus);ENTITIES.register(bus);ITEMS.register(bus);SOUNDS.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(BOSS.get(),SatsukiRinEntity.createAttributes().build()));
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(SPAWN_EGG);});
        var forge=net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        // Only the fixed Satsuki loot table may drop; strips Apollyon drops injected by GR/RF hooks.
        forge.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.entity.living.LivingDropsEvent.class,e->{
            if(e.getEntity() instanceof SatsukiRinEntity rin&&rin.level() instanceof net.minecraft.server.level.ServerLevel level)e.getDrops().removeIf(drop->!lootItems(level).contains(drop.getItem().getItem()));
        });
        // Native Apollyon arrows keep their type and logic; only Satsuki-owned ones get a client visual marker.
        forge.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e)->{
            if(!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                    &&arrow.getOwner() instanceof SatsukiRinEntity rin)rin.trackArrow(arrow);
        });
        // RevelationFix cancels any mob targeting an OdamaneHalo wearer; Satsuki and her summons may still target ordinary survival players.
        forge.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,true,net.minecraftforge.event.entity.living.LivingChangeTargetEvent.class,e->{
            if(!e.isCanceled()||!(e.getNewTarget() instanceof net.minecraft.world.entity.player.Player player))return;
            SatsukiRinEntity rin=owner(e.getEntity());
            if(rin!=null&&rin.validTarget(player))e.setCanceled(false);
        });
    }
}
