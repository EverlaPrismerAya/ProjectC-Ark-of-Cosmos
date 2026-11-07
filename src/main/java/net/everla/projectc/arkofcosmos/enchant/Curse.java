package net.everla.projectc.arkofcosmos.enchant;

import net.minecraft.world.item.*;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** One independent curse per stack; never edits vanilla enchantments or base item attributes. */
public enum Curse {
    INDIGNANT("Indignant"), AFLAME("Aflame"), OBLATORY("Oblatory"), RESENTFUL("Resentful"), BLOODTHIRSTY("Bloodthirsty"),
    EPHEMERAL("Ephemeral"), HELLBOUND("Hellbound"), TAINTED("Tainted"), TRAITOROUS("Traitorous"), WITHERING("Withered"), PERSECUTED("Persecuted"), LECHEROUS("Lecherous");
    public final String icon;
    Curse(String icon){this.icon=icon;}
    public String key(){return "curse.arkofcosmos."+name().toLowerCase(java.util.Locale.ROOT);}
    public static TagKey<Item> tag(String name){return TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("arkofcosmos","witch_enchanting/"+name));}
    public static boolean tagged(ItemStack stack,String type){return stack.is(tag(type));}
    public static boolean magic(ItemStack stack){return tagged(stack,"magic") || net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED && WitchSouls.magic(stack);}
    public static boolean summon(ItemStack stack){return tagged(stack,"summon") || net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED && WitchSouls.summon(stack);}
    public static boolean melee(ItemStack stack){return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem || tagged(stack,"melee");}
    public static boolean projectile(ItemStack stack){return stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem || tagged(stack,"projectile") || magic(stack) || summon(stack);}
    public static boolean channel(ItemStack stack){return net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED && WitchSouls.channel(stack) || tagged(stack,"channeling") || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;}
    public static boolean recognized(ItemStack stack){return !stack.isEmpty() && !(stack.getItem() instanceof net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem) && !tagged(stack,"excluded") && (melee(stack)||projectile(stack));}
    public boolean accepts(ItemStack stack){
        if(!recognized(stack))return false;
        return switch(this){
            case INDIGNANT,HELLBOUND -> summon(stack);
            case OBLATORY,TRAITOROUS -> magic(stack);
            case AFLAME,WITHERING -> !summon(stack);
            case RESENTFUL,BLOODTHIRSTY -> projectile(stack);
            case LECHEROUS -> projectile(stack)&&!summon(stack);
            case EPHEMERAL -> !summon(stack)&&!channel(stack);
            case TAINTED -> melee(stack)&&projectile(stack);
            case PERSECUTED -> projectile(stack);
        };
    }
    public static Curse of(ItemStack stack){
        if(!net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED || stack.isEmpty() || stack.getTag()==null)return null;
        String id=stack.getTag().getString("ArkWitchCurse");
        try{return valueOf(id);}catch(IllegalArgumentException absent){return null;}
    }
    public static void apply(ItemStack stack,Curse curse){
        stack.removeTagKey("ArkWitchCharge");stack.removeTagKey("ArkWitchUse");
        if(curse==null)stack.removeTagKey("ArkWitchCurse");else {stack.getOrCreateTag().putString("ArkWitchCurse",curse.name());if(curse==EPHEMERAL)stack.getOrCreateTag().putFloat("ArkWitchCharge",1600);}
    }
}
