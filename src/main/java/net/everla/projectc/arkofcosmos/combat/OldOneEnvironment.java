package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

/** Approved Minecraft environment substitutions; order matches ArkoftheCosmos/Galaxia2.OnHitNPC. */
public final class OldOneEnvironment {
    public enum Zone { ASTRAL,JUNGLE,SNOW,BEACH,CORRUPT,CRIMSON,DUNGEON,DESERT,MUSHROOM,HELL,HOLY,NEBULA,STARDUST,SOLAR,VORTEX,DEFAULT }
    private OldOneEnvironment() {}
    public static boolean island(Player p){return p.level().dimension()==Level.END && p.position().horizontalDistanceSqr()<=1024d*1024;}
    public static boolean moons(Player p){return island(p)&&p.level() instanceof ServerLevel l&&l.getDragonFight()!=null&&l.getDragonFight().getDragonUUID()!=null
            &&l.getEntity(l.getDragonFight().getDragonUUID()) instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon d&&d.isAlive();}
    public static Zone zone(Player p){
        var b=p.level().getBiome(p.blockPosition());
        if(island(p))return Zone.ASTRAL;
        if(b.is(BiomeTags.IS_JUNGLE))return Zone.JUNGLE;
        if(b.is(Biomes.SNOWY_PLAINS)||b.is(Biomes.SNOWY_TAIGA)||b.is(Biomes.SNOWY_BEACH)||b.is(Biomes.SNOWY_SLOPES)||b.is(Biomes.GROVE)||b.is(Biomes.ICE_SPIKES)||b.is(Biomes.FROZEN_PEAKS)||b.is(Biomes.JAGGED_PEAKS))return Zone.SNOW;
        if(b.is(BiomeTags.IS_BEACH)||b.is(BiomeTags.IS_OCEAN))return Zone.BEACH;
        if(b.is(Biomes.WARPED_FOREST))return Zone.CORRUPT;
        if(b.is(Biomes.CRIMSON_FOREST))return Zone.CRIMSON;
        if(p.level() instanceof ServerLevel l){
            var reg=l.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for(var key:java.util.List.of(BuiltinStructures.FORTRESS,BuiltinStructures.STRONGHOLD)){
                var structure=reg.get(key);if(structure!=null&&l.structureManager().getStructureWithPieceAt(p.blockPosition(),structure).isValid())return Zone.DUNGEON;
            }
        }
        if(b.is(Biomes.DESERT))return Zone.DESERT;
        if(b.is(Biomes.MUSHROOM_FIELDS))return Zone.MUSHROOM;
        if(p.level().dimension()==Level.NETHER)return Zone.HELL;
        if(b.is(Biomes.CHERRY_GROVE))return Zone.HOLY;
        if(b.is(Biomes.END_HIGHLANDS))return Zone.NEBULA;
        if(b.is(Biomes.END_MIDLANDS))return Zone.STARDUST;
        if(b.is(Biomes.END_BARRENS))return Zone.SOLAR;
        if(b.is(Biomes.SMALL_END_ISLANDS))return Zone.VORTEX;
        return Zone.DEFAULT;
    }
}
