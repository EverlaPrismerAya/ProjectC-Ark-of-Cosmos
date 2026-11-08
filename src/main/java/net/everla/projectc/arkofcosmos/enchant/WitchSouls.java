package net.everla.projectc.arkofcosmos.enchant;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import com.Polarice3.Goety.utils.TotemFinder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Loaded only with Goety; reads the real active storage, never a synthetic mana pool. */
public final class WitchSouls {
    private WitchSouls() {}
    public static boolean magic(ItemStack stack){return stack.getItem() instanceof IWand;}
    public static boolean summon(ItemStack stack){return stack.getItem() instanceof IWand wand && wand.getSpell(stack) instanceof com.Polarice3.Goety.api.magic.ISummonSpell;}
    public static boolean channel(ItemStack stack){return stack.getItem() instanceof IWand wand && wand.getSpell(stack) instanceof com.Polarice3.Goety.api.magic.IBreathingSpell;}
    public static int current(Player p){return SEHelper.getSoulAmountInt(p);}
    public static int capacity(Player p){return SEHelper.getSEActive(p)?MainConfig.MaxArcaSouls.get():ITotem.maximumSouls(TotemFinder.FindTotem(p));}
    public static boolean active(Player p){return SEHelper.getSoulsContainer(p)&&current(p)>0&&capacity(p)>0;}
    public static double ratio(Player p){return active(p)?Math.min(1,(double)current(p)/capacity(p)):0;}
    private static final boolean LEGACY = java.util.Arrays.stream(SEHelper.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("decreaseSoulsNoDiscount"));
    private static void debit(Player p,int amount){
        if(!LEGACY){SEHelper.decreaseSoulsNoDiscount(p,amount);return;}
        WitchSoulEvents.begin(p);
        try{
            amount=com.Polarice3.Goety.common.events.spell.GoetyEventFactory.onSoulEnergyLoss(p,amount);
            if(SEHelper.getSEActive(p)){SEHelper.decreaseSESouls(p,amount);SEHelper.sendSEUpdatePacket(p);}
            else {var totem=TotemFinder.FindTotem(p);if(totem!=null)ITotem.decreaseSouls(totem,amount);}
        }finally{WitchSoulEvents.complete(p);}
    }
    public static int drain(Player p){int before=current(p);if(before>0)debit(p,before);return Math.max(0,before-current(p));}
    /** Same general attribute, armor/halo discount and integer rounding as Goety casting. */
    public static int cost(Player p,int amount){
        var discount=p.getAttribute(com.Polarice3.Goety.init.ModAttributes.SOUL_DISCOUNT.get());
        int cost=(int)(amount*Math.max(0,1-(discount==null?0:discount.getValue())));
        cost=Math.max(0,(int)(cost*SEHelper.soulDiscount(p)));
        return net.everla.projectc.arkofcosmos.combat.HaloCompat.equipped(p,"halo_of_the_end")?Math.min(1,cost):cost;
    }
    /** Shared magic-weapon debit: creative casting needs no soul container or balance. */
    public static boolean pay(Player p,int amount){if(p.getAbilities().instabuild)return true;amount=cost(p,amount);if(amount==0)return true;if(!active(p)||current(p)<amount)return false;debit(p,amount);return true;}
}
