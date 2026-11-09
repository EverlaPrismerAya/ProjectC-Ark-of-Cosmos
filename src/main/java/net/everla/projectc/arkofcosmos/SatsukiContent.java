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
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.FractalSigilEntity>> FRACTAL_SIGIL=ENABLED?ENTITIES.register("fractal_sigil",()->EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.FractalSigilEntity>of(net.everla.projectc.arkofcosmos.entity.FractalSigilEntity::new,MobCategory.MISC).sized(.5f,.25f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(20).build("arkofcosmos:fractal_sigil")):null;
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
    /**
     * GR/RF redirect the Apollyon death drop to their own loot table, so getDefaultLootTable() alone is not honoured.
     * Discard whatever was produced and roll arkofcosmos:entities/satsuki_rin with the real kill context.
     */
    private static void satsukiDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e,SatsukiRinEntity rin,net.minecraft.server.level.ServerLevel level){
        var source=e.getSource();
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY,rin)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,rin.position())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,source)
                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.KILLER_ENTITY,source.getEntity())
                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_KILLER_ENTITY,source.getDirectEntity())
                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER,source.getEntity() instanceof net.minecraft.world.entity.player.Player p?p:null)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
        e.getDrops().clear();
        level.getServer().getLootData().getLootTable(SatsukiRinEntity.LOOT).getRandomItems(params,stack->{
            var item=new net.minecraft.world.entity.item.ItemEntity(level,rin.getX(),rin.getY(),rin.getZ(),stack);
            // The death performance outlasts the normal pickup window comfortably; keep the loot around longer.
            item.setDefaultPickUpDelay();item.setExtendedLifetime();e.getDrops().add(item);
        });
    }
    public static void register(IEventBus bus) {
        if(!ENABLED)return;
        ATTRIBUTES.register(bus);ENTITIES.register(bus);ITEMS.register(bus);SOUNDS.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(BOSS.get(),SatsukiRinEntity.createAttributes().build()));
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(SPAWN_EGG);});
        var forge=net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        forge.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.entity.living.LivingDropsEvent.class,e->{
            if(e.getEntity() instanceof SatsukiRinEntity rin&&rin.level() instanceof net.minecraft.server.level.ServerLevel level)satsukiDrops(e,rin,level);
        });
        // Phase-two fire blast traps (GR/RF netherUpgrade, Apostle teleportHits) owned by Satsuki become fractal sigils.
        forge.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e)->{
            if(e.getLevel().isClientSide()||!(e.getEntity() instanceof com.Polarice3.Goety.common.entities.util.FireBlastTrap trap)||!(trap.getOwner() instanceof SatsukiRinEntity rin))return;
            e.setCanceled(true);
            e.getLevel().addFreshEntity(net.everla.projectc.arkofcosmos.entity.FractalSigilEntity.create(FRACTAL_SIGIL.get(),rin,trap.position(),trap.getImmediate(),trap.getAreaOfEffect()));
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
