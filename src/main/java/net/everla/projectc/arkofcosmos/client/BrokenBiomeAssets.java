package net.everla.projectc.arkofcosmos.client;
/** Original Calamity PNGs, SHA-256 deduplicated; see resource manifest. */
public final class BrokenBiomeAssets {
 private BrokenBiomeAssets(){}
 public static String texture(String key){return switch(key){
 case "item" -> "item/broken_biome_blade";
 case "pure" -> "fx/biome_pure";
 case "hot" -> "fx/broken_biome_hot";
 case "hot_extra" -> "fx/broken_biome_hot_extra";
 case "cold_small" -> "fx/broken_biome_cold_small";
 case "cold_big" -> "fx/broken_biome_cold_big";
 case "cold_thrust" -> "fx/broken_biome_cold_thrust";
 case "tropical" -> "fx/broken_biome_tropical";
 case "tropical_glow" -> "fx/broken_biome_tropical_glow";
 case "vine" -> "fx/biome_vine";
 case "evil" -> "fx/broken_biome_evil";
 default -> BiomeAssets.texture(key); };}
}
