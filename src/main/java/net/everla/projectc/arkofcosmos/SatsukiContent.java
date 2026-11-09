package net.everla.projectc.arkofcosmos;

import net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.*;

/** The optional Apostle subclass is resolved only with both dependencies loaded. */
public final class SatsukiContent {
    private SatsukiContent() {}
    public static final boolean ENABLED=ModList.get().isLoaded("goety")&&ModList.get().isLoaded("goety_revelation");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<Attribute> ATTRIBUTES=DeferredRegister.create(ForgeRegistries.ATTRIBUTES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ArkOfCosmos.MOD_ID);
    public static final RegistryObject<Attribute> MAX_HEALTH=ENABLED?ATTRIBUTES.register("satsuki_max_health",()->new RangedAttribute("attribute.name.generic.max_health",6666,1,Double.MAX_VALUE).setSyncable(true)):null;
    public static final RegistryObject<EntityType<SatsukiRinEntity>> BOSS=ENABLED?ENTITIES.register("satsuki_rin",()->EntityType.Builder.<SatsukiRinEntity>of(SatsukiRinEntity::new,MobCategory.MONSTER).sized(.6f,1.8f).fireImmune().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:satsuki_rin")):null;
    public static final RegistryObject<Item> SPAWN_EGG=ENABLED?ITEMS.register("satsuki_rin_spawn_egg",()->new net.minecraftforge.common.ForgeSpawnEggItem(BOSS,0x77B957,0xE8D9A5,new Item.Properties())):null;
    public static void register(IEventBus bus) {
        if(!ENABLED)return;
        ATTRIBUTES.register(bus);ENTITIES.register(bus);ITEMS.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(BOSS.get(),SatsukiRinEntity.createAttributes().build()));
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.SPAWN_EGGS)e.accept(SPAWN_EGG);});
    }
}
