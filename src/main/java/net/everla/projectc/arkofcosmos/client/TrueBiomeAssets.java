package net.everla.projectc.arkofcosmos.client;

/** Canonical resources, SHA-256 deduplicated against the existing runtime tree. */
public final class TrueBiomeAssets {
    private TrueBiomeAssets(){}
    public static String texture(String name){return switch(name){
        case "item" -> "item/true_biome_blade";
        case "pride" -> "fx/true_biome_pride";
        case "chained" -> "fx/true_biome_chained";
        case "fury" -> "fx/true_biome_fury";
        case "tides" -> "fx/true_biome_tides";
        case "beam" -> "fx/true_biome_beam";
        case "fury_extra" -> "fx/true_biome_fury_extra";
        case "flail" -> "fx/true_biome_flail";
        case "ghost_chain" -> "fx/true_biome_ghost_chain";
        case "hook" -> "fx/true_biome_hook";
        case "hook_body" -> "fx/true_biome_hook_body";
        case "shockwave" -> "fx/true_biome_shockwave";
        case "monolith" -> "fx/true_biome_monolith";
        case "monolith_glow" -> "fx/true_biome_monolith_glow";
        case "sigil" -> "fx/true_biome_sigil";
        case "pride_chain" -> "galaxia/phoenixchain";
        case "semi_smear" -> "fx/true_biome_semi_smear";
        case "circular_smear" -> "fx/circularsmearsmokey";
        case "thin_line" -> "fx/thinendedline";
        case "bloom_circle" -> "fx/bloomcircle";
        case "thin_sparkle" -> "fx/thinsparkle";
        case "sparkle" -> "fx/sparkle";
        case "bloom_ring" -> "galaxia/bloomring";
        case "smoke" -> "fx/true_biome_smoke";
        case "stone" -> "fx/true_biome_stone";
        case "snowflake" -> "fx/true_biome_snowflake";
        case "glacial_icon" -> "mob_effect/glacial_state";
        default -> throw new IllegalArgumentException(name);
    };}
}
