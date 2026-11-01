package net.everla.projectc.arkofcosmos.client.vanilla;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.everla.projectc.arkofcosmos.combat.vanilla.VanillaParticles;
public final class GustParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    public GustParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites) {
        super(level,x,y,z,0,0,0);this.sprites=sprites;setSpriteFromAge(sprites);lifetime=12+random.nextInt(4);quadSize=1;setSize(1,1);
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }
    @Override public int getLightColor(float partial) { return 15728880; }
    @Override public void tick() { if(age++>=lifetime)remove();else setSpriteFromAge(sprites); }
    public static final class Seed extends NoRenderParticle {
        private final double scale;private final int delay;
        public Seed(ClientLevel level,double x,double y,double z,boolean large) { super(level,x,y,z,0,0,0);scale=large?3:1;lifetime=large?7:3;delay=large?0:2; }
        @Override public void tick() {
            if(age%(delay+1)==0)for(int i=0;i<3;i++)level.addParticle(VanillaParticles.GUST.get(),x+(random.nextDouble()-random.nextDouble())*scale,y+(random.nextDouble()-random.nextDouble())*scale,z+(random.nextDouble()-random.nextDouble())*scale,(float)age/lifetime,0,0);
            if(age++==lifetime)remove();
        }
    }
}
