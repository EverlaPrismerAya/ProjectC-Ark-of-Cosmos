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
    public static int drain(Player p){int before=current(p);if(before>0)SEHelper.decreaseSoulsNoDiscount(p,before);return Math.max(0,before-current(p));}
    public static boolean pay(Player p,int amount){if(amount<=0||!active(p)||current(p)<amount)return false;SEHelper.decreaseSoulsNoDiscount(p,amount);return true;}
}
