package net.everla.projectc.arkofcosmos;

import net.everla.projectc.arkofcosmos.entity.BejeweledBoltEntity;
import net.everla.projectc.arkofcosmos.item.BejeweledStaffItem;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

public final class BejeweledContent {
    // The Bejeweled Staff is Satsuki Rin content: Goety Revelation is required as well.
    public static final boolean ENABLED=net.minecraftforge.fml.ModList.get().isLoaded("goety")&&net.minecraftforge.fml.ModList.get().isLoaded("goety_revelation");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ArkOfCosmos.MOD_ID);
    public static final RegistryObject<Item> STAFF=ENABLED?ITEMS.register("bejeweled_staff",BejeweledStaffItem::new):null;
    public static final RegistryObject<EntityType<BejeweledBoltEntity>> BOLT=ENABLED?ENTITIES.register("bejeweled_bolt",()->EntityType.Builder.<BejeweledBoltEntity>of(BejeweledBoltEntity::new,MobCategory.MISC).sized(.625f,.625f).noSave().noSummon().clientTrackingRange(128).updateInterval(1).build("arkofcosmos:bejeweled_bolt")):null;
    public static final RegistryObject<SoundEvent> CAST=sound("bejeweled_cast"),FLIGHT=sound("bejeweled_flight"),HIT=sound("bejeweled_hit");
    private static RegistryObject<SoundEvent> sound(String name){return ENABLED?SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,name))):null;}
    private static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){
        if(event.getEntity().getUseItem().is(STAFF.get()))event.getEntity().stopUsingItem();
    }
    private static void looting(net.minecraftforge.event.entity.living.LootingLevelEvent event){
        if(event.getDamageSource()!=null&&event.getDamageSource().getDirectEntity() instanceof BejeweledBoltEntity bolt)
            event.setLootingLevel(bolt.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));
    }
    public static void register(IEventBus bus){if(!ENABLED)return;net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(BejeweledContent::looting);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(BejeweledContent::dimension);ITEMS.register(bus);ENTITIES.register(bus);SOUNDS.register(bus);bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.COMBAT)e.accept(STAFF);});}
}
