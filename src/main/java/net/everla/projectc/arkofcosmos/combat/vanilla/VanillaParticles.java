package net.everla.projectc.arkofcosmos.combat.vanilla;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.*;
public final class VanillaParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> REGISTER=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,"arkofcosmos");
    public static final RegistryObject<SimpleParticleType> GUST=REGISTER.register("gust",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> LARGE_EMITTER=REGISTER.register("gust_emitter_large",()->new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> EMITTER=REGISTER.register("gust_emitter",()->new SimpleParticleType(true));
}
