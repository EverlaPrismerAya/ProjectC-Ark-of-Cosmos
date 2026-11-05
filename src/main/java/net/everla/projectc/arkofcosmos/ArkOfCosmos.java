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
    private static final DeferredRegister<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTES=DeferredRegister.create(ForgeRegistries.ATTRIBUTES,MOD_ID);
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> FOURTH_MAX_HEALTH=ATTRIBUTES.register("fourth_max_health",
        ()->new net.minecraft.world.entity.ai.attributes.RangedAttribute("attribute.name.generic.max_health",880,1,Double.MAX_VALUE).setSyncable(true));
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity>> THE_PLAYER=ENTITIES.register("the_player",
        ()->EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity>of(net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity::new,MobCategory.MONSTER)
            .sized(.6f,1.8f).clientTrackingRange(128).updateInterval(1).build("arkofcosmos:the_player"));
    public static final RegistryObject<Item> THE_PLAYER_SPAWN_EGG=ITEMS.register("the_player_spawn_egg",
        ()->new net.minecraftforge.common.ForgeSpawnEggItem(THE_PLAYER,0x191923,0xCE4DCC,new Item.Properties()));
    private static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ARMOR_CRUNCH = EFFECTS.register("armor_crunch",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x7140BC) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "71675e1d-46bd-42da-9ab2-ad341910674e", -15, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> CRUMBLING = EFFECTS.register("crumbling",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xABB449) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "589846b5-795f-440b-a5dd-0b008c5afc47", -10, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ELEMENTAL_MIX = EFFECTS.register("elemental_mix",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xFF6900) {});
    public static final RegistryObject<Item> ARK_OF_THE_ELEMENTS = ITEMS.register("ark_of_the_elements", net.everla.projectc.arkofcosmos.item.ElementsItem::new);
    public static final RegistryObject<Item> ARK_OF_THE_ANCIENTS = ITEMS.register("ark_of_the_ancients", net.everla.projectc.arkofcosmos.item.AncientsArkItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.AncientsEntity>> ANCIENTS_ATTACK = ENTITIES.register("ancients_attack",
        () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.AncientsEntity>of(net.everla.projectc.arkofcosmos.entity.AncientsEntity::new, MobCategory.MISC)
            .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:ancients_attack"));
    public static final RegistryObject<Item> FRACTURED_ARK = ITEMS.register("fractured_ark", net.everla.projectc.arkofcosmos.item.FracturedArkItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.FracturedEntity>> FRACTURED_ATTACK = ENTITIES.register("fractured_attack",
        () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.FracturedEntity>of(net.everla.projectc.arkofcosmos.entity.FracturedEntity::new, MobCategory.MISC)
            .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:fractured_attack"));
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.ElementsEntity>> ELEMENTS_ATTACK = ENTITIES.register("elements_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.ElementsEntity>of(net.everla.projectc.arkofcosmos.entity.ElementsEntity::new, MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:elements_attack"));
    public static final RegistryObject<Item> GALAXIA = ITEMS.register("galaxia", net.everla.projectc.arkofcosmos.item.GalaxiaItem::new);
    public static final RegistryObject<Item> BROKEN_BIOME_BLADE = ITEMS.register("broken_biome_blade", net.everla.projectc.arkofcosmos.item.BrokenBiomeBladeItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity>> BROKEN_BIOME_ATTACK = ENTITIES.register("broken_biome_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity>of(net.everla.projectc.arkofcosmos.entity.BrokenBiomeEntity::new,MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:broken_biome_attack"));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BROKEN_BIOME_FREEZE = EFFECTS.register("broken_biome_freeze",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL,0xA9CFFF) {});
    public static final RegistryObject<Item> BIOME_BLADE = ITEMS.register("biome_blade", net.everla.projectc.arkofcosmos.item.BiomeBladeItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.BiomeEntity>> BIOME_ATTACK = ENTITIES.register("biome_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.BiomeEntity>of(net.everla.projectc.arkofcosmos.entity.BiomeEntity::new,MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:biome_attack"));
    public static final RegistryObject<Item> TRUE_BIOME_BLADE = ITEMS.register("true_biome_blade", net.everla.projectc.arkofcosmos.item.TrueBiomeBladeItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity>> TRUE_BIOME_ATTACK = ENTITIES.register("true_biome_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity>of(net.everla.projectc.arkofcosmos.entity.TrueBiomeEntity::new, MobCategory.MISC)
                    .sized(.5f,.5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:true_biome_attack"));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIAL_STATE = EFFECTS.register("glacial_state",
            net.everla.projectc.arkofcosmos.combat.GlacialStateEffect::new);
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
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity>> MAHORAGA=ENTITIES.register("mahoraga_warden",
            ()->EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity>of(net.everla.projectc.arkofcosmos.entity.MahoragaWardenEntity::new,MobCategory.MONSTER)
                    .sized(.9f,2.9f).clientTrackingRange(128).build("arkofcosmos:mahoraga_warden"));
    public static final RegistryObject<SoundEvent> SNAP = SOUNDS.register("snap",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "snap")));
    private static final java.util.Map<String, RegistryObject<SoundEvent>> AUDIO = new java.util.HashMap<>();
    static {
        AUDIO.put("snap", SNAP);
        for (String name : new String[]{"fractured_parry_start", "item67", "fractured_swing", "fractured_beam_death", "elements_item9", "phoenix", "meaty", "item4", "item35", "item63", "item64", "item65", "item71", "item84", "crystal", "needle_death", "mahou_defeat", "fourth_calamity_death",
                "galaxia_item60","galaxia_item71","galaxia_item76","galaxia_item77","galaxia_item78","galaxia_item79","galaxia_item80","galaxia_item90",
                "galaxia_item101","galaxia_item109","galaxia_item120","galaxia_hit30","galaxia_hit43","galaxia_book","galaxia_impact","galaxia_eruption",
                "galaxia_beacon","galaxia_lightning","galaxia_meaty",
                "biome_swap","biome_zap","biome_cold_thrust","biome_whip_snap","biome_vines","biome_evil","biome_splash","biome_eruption","biome_swing",
                "true_biome_charge","true_biome_flail","true_biome_blast","true_biome_beam_end","true_biome_beam_start","true_biome_hook_end","true_biome_slice","true_biome_pulse"})
            AUDIO.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, name))));
    }
    public static void sound(net.minecraft.world.entity.Entity entity, String name, float volume) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), AUDIO.get(name).get(), net.minecraft.sounds.SoundSource.PLAYERS, name.equals("item35") ? volume * .75f : volume, 1);
    }
    public static final RegistryObject<Item> ARK_OF_COSMOS = ITEMS.register("ark_of_cosmos",
            ArkItem::new);
    public static final RegistryObject<Item> ARK_OF_THE_OLD_ONE = ITEMS.register("ark_of_the_old_one",
            net.everla.projectc.arkofcosmos.item.OldOneItem::new);
    public static final RegistryObject<EntityType<net.everla.projectc.arkofcosmos.entity.OldOneEntity>> OLD_ONE_ATTACK = ENTITIES.register("old_one_attack",
            () -> EntityType.Builder.<net.everla.projectc.arkofcosmos.entity.OldOneEntity>of(net.everla.projectc.arkofcosmos.entity.OldOneEntity::new, MobCategory.MISC)
                    .sized(.25f,.25f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:old_one_attack"));
    public static final RegistryObject<Item> AURIC_BAR = ITEMS.register("auric_bar",
            net.everla.projectc.arkofcosmos.item.AuricBarItem::new);
    public static final RegistryObject<Item> ANNIHILATION_EMBER = ITEMS.register("annihilation_ember",
            net.everla.projectc.arkofcosmos.item.AnnihilationEmberItem::new);

    public static final RegistryObject<Item> ASHES_OF_CALAMITY = ITEMS.register("ashes_of_calamity",
            net.everla.projectc.arkofcosmos.item.AshesOfCalamityItem::new);

    public ArkOfCosmos(FMLJavaModLoadingContext context) {
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,ProjectCClientConfig.SPEC,"projectc-client.toml");
        IEventBus bus = context.getModEventBus();
        net.everla.projectc.arkofcosmos.combat.vanilla.VanillaCombat.register(bus);
        ITEMS.register(bus);
        ATTRIBUTES.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        net.everla.projectc.arkofcosmos.combat.OldOneEffects.register(bus);
        EFFECTS.register(bus);
        ArkNetwork.register();
        bus.addListener(this::addCreativeContents);
        bus.addListener(this::attributes);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(THE_PLAYER.get(),net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity.createAttributes().build());
        event.put(MAHORAGA.get(),net.minecraft.world.entity.monster.warden.Warden.createAttributes().build());
        event.put(MAHOU_TSUKAI.get(), net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity.createAttributes().build());
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ARK_OF_COSMOS);
            event.accept(ARK_OF_THE_OLD_ONE);
            event.accept(GALAXIA);
            event.accept(TRUE_BIOME_BLADE);
            event.accept(BIOME_BLADE);
            event.accept(BROKEN_BIOME_BLADE);
            event.accept(ARK_OF_THE_ELEMENTS);
            event.accept(FRACTURED_ARK);
            event.accept(ARK_OF_THE_ANCIENTS);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(AURIC_BAR);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(ANNIHILATION_EMBER);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(ASHES_OF_CALAMITY);
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(THE_PLAYER_SPAWN_EGG);
    }
}
