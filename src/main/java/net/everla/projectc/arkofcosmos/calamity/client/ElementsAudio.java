package net.everla.projectc.arkofcosmos.calamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import java.util.*;

/** Original SoundStyle pitch/limits and ActiveSound's 2500-pixel linear attenuation. */
public final class ElementsAudio {
    private static final Map<String,SoundInstance> ACTIVE=new HashMap<>();
    public static float attenuation(double blocks){return (float)Math.max(0,Math.min(1,1-blocks*16/2500));}
    public static float pitch(String name,float random){return (float)Math.pow(2,(random-.5)*(name.equals("item35")?0:name.startsWith("item")||name.equals("elements_item9")?.12:0));}
    public static boolean unlimited(String name){return name.equals("needle_death")||name.equals("crystal")||name.equals("elements_item9");}
    public static void clear(){var manager=Minecraft.getInstance().getSoundManager();ACTIVE.values().forEach(manager::stop);ACTIVE.clear();}
    public static void receive(CompoundTag data){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||data.getBoolean("OwnerOnly")&&mc.player.getId()!=data.getInt("Owner"))return;
        String name=data.getString("Sound");SoundInstance sound=new Positional(data,pitch(name,mc.level.random.nextFloat()));
        if(!unlimited(name)){SoundInstance old=ACTIVE.put(name,sound);if(old!=null)mc.getSoundManager().stop(old);}
        mc.getSoundManager().play(sound);
    }
    private static final class Positional extends SimpleSoundInstance implements TickableSoundInstance {
        private final Object world=Minecraft.getInstance().level;
        private final float sourceVolume;
        Positional(CompoundTag data,float pitch){
            super(ResourceLocation.fromNamespaceAndPath("arkofcosmos",data.getString("Sound")),SoundSource.PLAYERS,1,pitch,SoundInstance.createUnseededRandom(),false,0,Attenuation.NONE,
                data.getBoolean("Relative")?0:data.getDouble("AtX"),data.getBoolean("Relative")?0:data.getDouble("AtY"),data.getBoolean("Relative")?0:data.getDouble("AtZ"),data.getBoolean("Relative"));
            sourceVolume=Math.max(0,Math.min(1,data.getFloat("Volume")));
        }
        @Override public float getVolume(){return sourceVolume*(isRelative()?1:attenuation(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(new net.minecraft.world.phys.Vec3(getX(),getY(),getZ()))));}
        @Override public boolean isStopped(){return Minecraft.getInstance().level!=world;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){}
    }
}
