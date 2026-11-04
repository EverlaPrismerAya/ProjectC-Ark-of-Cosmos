package net.everla.projectc.arkofcosmos.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import java.util.*;
import static net.everla.projectc.arkofcosmos.entity.ElementsEntity.vector;

/** Source SoundStyle defaults, owner-only/relative positioning and replacement limits. */
public final class TrueBiomeAudio {
    private TrueBiomeAudio(){}
    private static final Map<String,SoundInstance> ACTIVE=new HashMap<>();
    public static void receive(CompoundTag s){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||s.getBoolean("OwnerOnly")&&mc.player.getId()!=s.getInt("Owner"))return;
        String name=s.getString("Sound");boolean hit=name.equals("galaxia_hit30")||name.equals("galaxia_hit43");
        double variance=hit?.2:name.contains("item")||name.equals("biome_swing")||name.equals("biome_evil")||name.equals("item71")||name.contains("beam_")||name.equals("true_biome_hook_end")?.12:0;
        float volume=Math.min(1,s.getFloat("Volume")*(hit?.5f:1));float pitch=(float)Math.pow(2,s.getFloat("Pitch")+(mc.level.random.nextFloat()-.5)*variance);
        boolean relative=s.getBoolean("Relative");var at=vector(s,"At");
        SoundInstance sound=new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath("arkofcosmos",name),SoundSource.PLAYERS,volume,pitch,SoundInstance.createUnseededRandom(),false,0,
            relative?SoundInstance.Attenuation.NONE:SoundInstance.Attenuation.LINEAR,relative?0:at.x,relative?0:at.y,relative?0:at.z,relative);
        if(!Set.of("fractured_swing","biome_cold_thrust","biome_evil","galaxia_impact","galaxia_eruption","true_biome_charge","true_biome_flail","true_biome_blast","true_biome_beam_end").contains(name)){
            SoundInstance old=ACTIVE.put(name,sound);if(old!=null)mc.getSoundManager().stop(old);
        }
        mc.getSoundManager().play(sound);
    }
}
