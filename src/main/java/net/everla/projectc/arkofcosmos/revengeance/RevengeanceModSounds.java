package net.everla.projectc.arkofcosmos.revengeance;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RevengeanceModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ArkOfCosmos.MOD_ID);
	public static final RegistryObject<SoundEvent> RAGE_ACTIVITE = REGISTRY.register("rage_activite", 
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":rage_activite")));
	public static final RegistryObject<SoundEvent> RAGE_END = REGISTRY.register("rage_end", 
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":rage_end")));
	public static final RegistryObject<SoundEvent> RAGE_FULL = REGISTRY.register("rage_full",
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":rage_full")));
	public static final RegistryObject<SoundEvent> ADRENALINE_ACTIVITE = REGISTRY.register("adrenaline_activite",
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":adrenaline_activite")));
	public static final RegistryObject<SoundEvent> ADRENALINE_FULL = REGISTRY.register("adrenaline_full",
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":adrenaline_full")));
	public static final RegistryObject<SoundEvent> ADRENALINE_CHARGE_BREAK = REGISTRY.register("adrenaline_charge_break",
		() -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(ArkOfCosmos.MOD_ID + ":adrenaline_charge_break")));

	public static void register(IEventBus eventBus) {
		REGISTRY.register(eventBus);
	}
}