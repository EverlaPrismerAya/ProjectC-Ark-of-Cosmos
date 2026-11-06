package net.everla.projectc.arkofcosmos;

import java.util.EnumMap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.everla.projectc.arkofcosmos.entity.CalamitasAttackEntity;
import net.everla.projectc.arkofcosmos.entity.CalamitasMinionEntity;
import net.everla.projectc.arkofcosmos.entity.CalamitasSepulcherEntity;
import net.everla.projectc.arkofcosmos.entity.CalamitasBrotherEntity;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.everla.projectc.arkofcosmos.calamity.SupremeCalamitasBrothers;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Optional content has no link to Goety Java classes on either distribution. */
public final class CalamitasContent {
    private CalamitasContent() {}
    public static final boolean ENABLED = ModList.get().isLoaded("goety");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ArkOfCosmos.MOD_ID);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> VULNERABILITY_HEX = ENABLED ? EFFECTS.register("vulnerability_hex", net.everla.projectc.arkofcosmos.calamity.CalamitasHexEffect::new) : null;
    private static final java.util.Map<String, RegistryObject<SoundEvent>> AUDIO = new java.util.HashMap<>();
    public static final RegistryObject<Attribute> MAX_HEALTH = ENABLED ? ATTRIBUTES.register("calamitas_max_health",
            () -> new RangedAttribute("attribute.name.generic.max_health", 100, 1, 1000000000).setSyncable(true)) : null;
    public static final RegistryObject<EntityType<SupremeCalamitasEntity>> BOSS = ENABLED ? ENTITIES.register("supreme_calamitas",
            () -> EntityType.Builder.<SupremeCalamitasEntity>of(SupremeCalamitasEntity::new, MobCategory.MONSTER)
                    .sized(.6f, 1.8f).clientTrackingRange(160).updateInterval(1).build(ArkOfCosmos.MOD_ID + ":supreme_calamitas")) : null;
    public static final RegistryObject<net.minecraft.world.item.Item> SPAWN_EGG = ENABLED ? ITEMS.register("supreme_calamitas_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(BOSS, 0x36132E, 0xE83A37, new net.minecraft.world.item.Item.Properties())) : null;
    public static final EnumMap<CalamitasMinionEntity.Kind, RegistryObject<EntityType<CalamitasMinionEntity>>> MINIONS = new EnumMap<>(CalamitasMinionEntity.Kind.class);
    public static final EnumMap<CalamitasAttackEntity.Kind, RegistryObject<EntityType<CalamitasAttackEntity>>> ATTACKS = new EnumMap<>(CalamitasAttackEntity.Kind.class);
    public static final EnumMap<CalamitasSepulcherEntity.Kind, RegistryObject<EntityType<CalamitasSepulcherEntity>>> SEPULCHER = new EnumMap<>(CalamitasSepulcherEntity.Kind.class);
    public static final EnumMap<SupremeCalamitasBrothers.Kind, RegistryObject<EntityType<CalamitasBrotherEntity>>> BROTHERS = new EnumMap<>(SupremeCalamitasBrothers.Kind.class);
    static {
        if (ENABLED) for (String name : new String[]{"shot", "big_shot", "hellblast", "dash", "fireblast_impact",
                "gigablast_impact", "vortex_spawn", "sepulcher_spawn", "barrage_end", "vortex_drone",
                "brother_hurt", "brother_death", "shield_hurt", "transition_burst", "cataclysm_spawn", "catastrophe_spawn",
                "brother_roar", "brother_charge", "brother_echo", "brother_finisher", "brother_resonance", "brother_swing", "brother_dash", "brother_revive", "barrage_rumble", "sepulcher_burst", "sepulcher_death", "vortex_burn", "heart_hurt", "heart_death", "seeker_death", "flame", "heart_cast", "seeker_cast", "seeker_impact", "vortex_warning", "music_1", "music_2", "music_3", "music_4", "acceptance"}) {
            String id = "calamitas_" + name;
            AUDIO.put(name, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID, id))));
        }
        if (ENABLED) for (var kind : SupremeCalamitasBrothers.Kind.values()) {
            String id = "calamitas_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            BROTHERS.put(kind, ENTITIES.register(id, () -> EntityType.Builder
                    .<CalamitasBrotherEntity>of((type, level) -> new CalamitasBrotherEntity(type, level, kind), MobCategory.MONSTER)
                    .sized(.6f, 1.95f).clientTrackingRange(160).updateInterval(1).build(ArkOfCosmos.MOD_ID + ":" + id)));
        }
        if (ENABLED) for (var kind : CalamitasSepulcherEntity.Kind.values()) {
            String id = "calamitas_sepulcher_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            float width = switch (kind) { case HEAD -> 62f / 16; case BODY -> 3; case ARM -> 34f / 16; default -> 1.25f; };
            float height = switch (kind) { case HEAD -> 4; case BODY, ARM -> 3; default -> 1.25f; };
            SEPULCHER.put(kind, ENTITIES.register(id, () -> {
                var builder = EntityType.Builder.<CalamitasSepulcherEntity>of((type, level) -> new CalamitasSepulcherEntity(type, level, kind), MobCategory.MONSTER)
                        .sized(width, height).clientTrackingRange(160).updateInterval(1);
                if (kind == CalamitasSepulcherEntity.Kind.ARM) builder.noSummon(); // Old-save compatibility only.
                return builder.build(ArkOfCosmos.MOD_ID + ":" + id);
            }));
        }
        if (ENABLED) for (var kind : CalamitasMinionEntity.Kind.values()) {
            String id = "calamitas_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            MINIONS.put(kind, ENTITIES.register(id, () -> EntityType.Builder
                    .<CalamitasMinionEntity>of((type, level) -> new CalamitasMinionEntity(type, level, kind), MobCategory.MONSTER)
                    .sized(kind == CalamitasMinionEntity.Kind.HEART ? 1.5f : .6f, kind == CalamitasMinionEntity.Kind.HEART ? 1.5f : .8f)
                    .clientTrackingRange(160).updateInterval(1).build(ArkOfCosmos.MOD_ID + ":" + id)));
        }
        if (ENABLED) for (var kind : CalamitasAttackEntity.Kind.values()) {
            String id = "calamitas_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            ATTACKS.put(kind, ENTITIES.register(id, () -> EntityType.Builder
                    .<CalamitasAttackEntity>of((type, level) -> new CalamitasAttackEntity(type, level, kind), MobCategory.MISC)
                    .sized(.25f, .25f).clientTrackingRange(160).updateInterval(1)
                    .build(ArkOfCosmos.MOD_ID + ":" + id)));
        }
    }
    public static void register(IEventBus bus) {
        if (!ENABLED) return;
        ENTITIES.register(bus); ATTRIBUTES.register(bus); ITEMS.register(bus); SOUNDS.register(bus); EFFECTS.register(bus);
        bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event) -> {
            event.put(BOSS.get(), SupremeCalamitasEntity.createAttributes().build());
            MINIONS.values().forEach(type -> event.put(type.get(), CalamitasMinionEntity.createAttributes().build()));
            SEPULCHER.values().forEach(type -> event.put(type.get(), CalamitasSepulcherEntity.createAttributes().build()));
            BROTHERS.values().forEach(type -> event.put(type.get(), CalamitasBrotherEntity.createAttributes().build()));
        });
    }
    public static SoundEvent sound(String name) { return AUDIO.get(name).get(); }
    public static void sound(net.minecraft.world.entity.Entity entity, String name) {
        var at = entity instanceof CalamitasAttackEntity ? entity.position() : entity.getBoundingBox().getCenter();
        sound(entity, name, at, 1, 1);
    }
    public static void sound(net.minecraft.world.entity.Entity entity, String name, net.minecraft.world.phys.Vec3 at, float volume, float pitch) {
        if (!entity.level().isClientSide) entity.level().playSound(null, at.x, at.y, at.z, sound(name), SoundSource.HOSTILE, volume, pitch);
    }
}
