package net.everla.projectc.arkofcosmos.combat.vanilla;

/** Parameters from 26.1.2 Items, Item.Properties.spear and ToolMaterial. Seconds become ticks by truncation. */
public record SpearMaterial(String name, int durability, int enchantability, int damage, float duration,
        float multiplier, float delay, float dismountTime, float dismountSpeed, float knockbackTime,
        float knockbackSpeed, float damageTime, float relativeSpeed) {
    public int delayTicks() { return (int)(delay * 20); }
    public int dismountTicks() { return (int)(dismountTime * 20); }
    public int knockbackTicks() { return (int)(knockbackTime * 20); }
    public int damageTicks() { return (int)(damageTime * 20); }
    public static final SpearMaterial[] VALUES = {
        new SpearMaterial("wooden",59,15,0,.65f,.7f,.75f,5,14,10,5.1f,15,4.6f),
        new SpearMaterial("stone",131,5,1,.75f,.82f,.7f,4.5f,13,9,5.1f,13.75f,4.6f),
        new SpearMaterial("copper",190,13,1,.85f,.82f,.65f,4,12,8.25f,5.1f,12.5f,4.6f),
        new SpearMaterial("iron",250,14,2,.95f,.95f,.6f,2.5f,11,6.75f,5.1f,11.25f,4.6f),
        new SpearMaterial("golden",32,22,0,.95f,.7f,.7f,3.5f,13,8.5f,5.1f,13.75f,4.6f),
        new SpearMaterial("diamond",1561,10,3,1.05f,1.075f,.5f,3,10,6.5f,5.1f,10,4.6f),
        new SpearMaterial("netherite",2031,15,4,1.15f,1.2f,.4f,2.5f,9,5.5f,5.1f,8.75f,4.6f)
    };
}
