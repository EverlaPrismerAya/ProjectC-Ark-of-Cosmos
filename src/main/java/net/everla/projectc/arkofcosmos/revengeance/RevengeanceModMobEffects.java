package net.everla.projectc.arkofcosmos.revengeance;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RevengeanceModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ArkOfCosmos.MOD_ID);

    public static final RegistryObject<MobEffect> RAGE_EFFECT = MOB_EFFECTS.register("rage",
            RageMobEffect::new);

    public static final RegistryObject<MobEffect> ADRENALINE_EFFECT = MOB_EFFECTS.register("adrenaline",
            AdrenalineMobEffect::new);

    public static void register(IEventBus eventBus){
        MOB_EFFECTS.register(eventBus);
    }
}
