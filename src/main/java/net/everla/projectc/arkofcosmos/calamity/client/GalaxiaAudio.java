package net.everla.projectc.arkofcosmos.calamity.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.Minecraft;

/** Source SoundStyle pitch and concurrency; receives one event per server logical trigger. */
public final class GalaxiaAudio {
    private static final java.util.Map<String, SoundInstance> ACTIVE = new java.util.HashMap<>();
    public static float volume(String name,float multiplier) {
        // SoundStyle.Volume clamps after every assignment, including LightningSound's initial 1.5.
        return Math.max(0,Math.min(1,multiplier*(name.startsWith("hit")?.5f:1f)));
    }
    public static float pitch(String name,float random) {
        return (float)Math.pow(2,(random-.5)*(name.startsWith("item")?.12:name.startsWith("hit")?.2:0));
    }
    public static void receive(CompoundTag data) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || data.getBoolean("OwnerOnly") && mc.player.getId()!=data.getInt("Owner"))return;
        String name=data.getString("Sound");
        float volume=volume(name,data.getFloat("Volume"));
        // XNA pitch is an octave offset; Minecraft expects the playback-rate multiplier.
        float pitch=pitch(name,mc.level.random.nextFloat());
        var id=ResourceLocation.fromNamespaceAndPath("arkofcosmos","galaxia_"+name);
        boolean relative=data.getBoolean("Relative");
        SoundInstance sound=new SimpleSoundInstance(id,SoundSource.PLAYERS,volume,pitch,SoundInstance.createUnseededRandom(),
                false,0,relative?SoundInstance.Attenuation.NONE:SoundInstance.Attenuation.LINEAR,
                relative?0:data.getDouble("AtX"),relative?0:data.getDouble("AtY"),relative?0:data.getDouble("AtZ"),relative);
        // Legacy CreateTrackable DD2 sounds have MaxInstances=0; other source styles replace the oldest (limit 1).
        if(!name.equals("book") && !name.equals("impact") && !name.equals("eruption")) {
            SoundInstance old=ACTIVE.put(name,sound);
            if(old!=null)mc.getSoundManager().stop(old);
        }
        mc.getSoundManager().play(sound);
    }
}
