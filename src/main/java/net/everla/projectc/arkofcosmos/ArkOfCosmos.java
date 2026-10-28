package net.everla.projectc.arkofcosmos;

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

@Mod(ArkOfCosmos.MOD_ID)
public final class ArkOfCosmos {
    public static final String MOD_ID = "arkofcosmos";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    public static final RegistryObject<EntityType<ArkAttackEntity>> ATTACK = ENTITIES.register("attack",
            () -> EntityType.Builder.<ArkAttackEntity>of(ArkAttackEntity::new, MobCategory.MISC)
                    .sized(.5f, .5f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:attack"));
    public static final RegistryObject<SoundEvent> SNAP = SOUNDS.register("snap",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "snap")));
    private static final java.util.Map<String, RegistryObject<SoundEvent>> AUDIO = new java.util.HashMap<>();
    static {
        AUDIO.put("snap", SNAP);
        for (String name : new String[]{"phoenix", "meaty", "item4", "item35", "item63", "item64", "item65", "item71", "item84", "crystal", "needle_death"})
            AUDIO.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, name))));
    }
    static void sound(net.minecraft.world.entity.Entity entity, String name, float volume) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), AUDIO.get(name).get(), net.minecraft.sounds.SoundSource.PLAYERS, name.equals("item35") ? volume * .75f : volume, 1);
    }
    public static final RegistryObject<Item> ARK_OF_COSMOS = ITEMS.register("ark_of_cosmos",
            ArkItem::new);

    public ArkOfCosmos(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        ArkNetwork.register();
        bus.addListener(this::addCreativeContents);
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ARK_OF_COSMOS);
        }
    }
}
