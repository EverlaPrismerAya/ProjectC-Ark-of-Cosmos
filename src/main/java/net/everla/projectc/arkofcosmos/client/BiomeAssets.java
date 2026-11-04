package net.everla.projectc.arkofcosmos.client;

/** Original resources; see licenses/RESOURCE-SOURCES.json. */
public final class BiomeAssets {
    private BiomeAssets() {}
    public static String texture(String name) { return switch(name) {
        case "item" -> "item/biome_blade";
        case "attune" -> "fx/biome_attune";
        case "pure" -> "fx/biome_pure";
        case "sigil" -> "fx/true_biome_sigil";
        case "hot" -> "fx/biome_hot";
        case "hot_extra" -> "fx/biome_hot_extra";
        case "cold" -> "fx/biome_cold";
        case "cold_thrust" -> "fx/biome_cold_thrust";
        case "tropical" -> "fx/biome_tropical";
        case "tropical_glow" -> "fx/biome_tropical_glow";
        case "vine" -> "fx/biome_vine";
        case "evil" -> "fx/biome_evil";
        case "dash" -> "fx/biome_dash";
        case "holy" -> "fx/biome_holy";
        case "holy_chain" -> "galaxia/phoenixchain";
        case "astral" -> "fx/biome_astral";
        case "monolith" -> "fx/biome_monolith";
        case "monolith_glow" -> "fx/true_biome_monolith_glow";
        case "orb" -> "fx/biome_orb";
        case "wave" -> "fx/biome_wave";
        case "aqua" -> "fx/biome_aqua";
        case "mist" -> "fx/biome_mist";
        case "smear" -> "fx/biome_smear";
        case "light" -> "fx/light";
        case "spark" -> "fx/biome_spark";
        case "sparkle" -> "fx/sparkle";
        case "bloom" -> "fx/bloomcircle";
        case "snow" -> "fx/true_biome_snowflake";
        case "smoke" -> "fx/true_biome_smoke";
        case "stone" -> "fx/true_biome_stone";
        case "water" -> "fx/biome_water";
        case "dust" -> "fx/biome_dust";
        case "bloom_strong" -> "fx/bloomcircle";
        case "thin_spark" -> "fx/thinsparkle";
        case "dust_big" -> "fx/biome_dust_big";
        default -> throw new IllegalArgumentException(name);
    }; }
}
