package net.everla.projectc.arkofcosmos;

import net.everla.projectc.arkofcosmos.item.ArkItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.entity.ArkAttackEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

@Mod(ArkOfCosmos.MOD_ID)
public final class ArkOfCosmos {
    public static final String MOD_ID = "arkofcosmos";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    private static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ARMOR_CRUNCH = EFFECTS.register("armor_crunch",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x7140BC) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "71675e1d-46bd-42da-9ab2-ad341910674e", -15, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ELEMENTAL_MIX = EFFECTS.register("elemental_mix",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xFF6900) {});
    public static final RegistryObject<Item> ARK_OF_THE_ELEMENTS = ITEMS.register("ark_of_the_elements", net.everla.projectc.arkofcosmos.item.ElementsItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.ElementsEntity>> ELEMENTS_ATTACK = ENTITIES.register("elements_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.ElementsEntity>of(net.everla.projectc.arkofcosmos.entity.ElementsEntity::new, MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:elements_attack"));
    public static final RegistryObject<Item> GALAXIA = ITEMS.register("galaxia", net.everla.projectc.arkofcosmos.item.GalaxiaItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.GalaxiaEntity>> GALAXIA_ATTACK = ENTITIES.register("galaxia_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.GalaxiaEntity>of(net.everla.projectc.arkofcosmos.entity.GalaxiaEntity::new, MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:galaxia_attack"));
    public static final RegistryObject<EntityType<ArkAttackEntity>> ATTACK = ENTITIES.register("attack",
            () -> EntityType.Builder.<ArkAttackEntity>of(ArkAttackEntity::new, MobCategory.MISC)
                    .sized(.5f, .5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:attack"));
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity>> MAHOU_TSUKAI = ENTITIES.register("mahou_tsukai",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity>of(net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity::new, MobCategory.MONSTER)
                    .sized(.6f, 1.95f).clientTrackingRange(128).updateInterval(1).build("arkofcosmos:mahou_tsukai"));
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.MahouAttackEntity>> MAHOU_ATTACK = ENTITIES.register("mahou_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.MahouAttackEntity>of(net.everla.projectc.arkofcosmos.entity.MahouAttackEntity::new, MobCategory.MISC)
                    .sized(.2f,.2f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:mahou_attack"));
    public static final RegistryObject<SoundEvent> SNAP = SOUNDS.register("snap",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "snap")));
    private static final java.util.Map<String, RegistryObject<SoundEvent>> AUDIO = new java.util.HashMap<>();
    static {
        AUDIO.put("snap", SNAP);
        for (String name : new String[]{"elements_item9", "phoenix", "meaty", "item4", "item35", "item63", "item64", "item65", "item71", "item84", "crystal", "needle_death",
                "galaxia_item60","galaxia_item71","galaxia_item76","galaxia_item77","galaxia_item78","galaxia_item79","galaxia_item80","galaxia_item90",
                "galaxia_item101","galaxia_item109","galaxia_item120","galaxia_hit30","galaxia_hit43","galaxia_book","galaxia_impact","galaxia_eruption",
                "galaxia_beacon","galaxia_lightning","galaxia_meaty"})
            AUDIO.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, name))));
    }
    public static void sound(net.minecraft.world.entity.Entity entity, String name, float volume) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), AUDIO.get(name).get(), net.minecraft.sounds.SoundSource.PLAYERS, name.equals("item35") ? volume * .75f : volume, 1);
    }
    public static final RegistryObject<Item> ARK_OF_COSMOS = ITEMS.register("ark_of_cosmos",
            ArkItem::new);

    public ArkOfCosmos(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        EFFECTS.register(bus);
        ArkNetwork.register();
        bus.addListener(this::addCreativeContents);
        bus.addListener(this::attributes);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(MAHOU_TSUKAI.get(), net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity.createAttributes().build());
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ARK_OF_COSMOS);
            event.accept(GALAXIA);
            event.accept(ARK_OF_THE_ELEMENTS);
        }
    }
}
