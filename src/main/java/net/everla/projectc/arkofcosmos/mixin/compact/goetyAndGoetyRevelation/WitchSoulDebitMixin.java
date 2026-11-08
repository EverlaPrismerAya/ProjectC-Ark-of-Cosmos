package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Pseudo
@Mixin(targets="com.Polarice3.Goety.utils.SEHelper",remap=false)
public abstract class WitchSoulDebitMixin {
    // Old Goety applies its discount directly in decreaseSouls instead of delegating.
    @WrapMethod(method="decreaseSouls",require=1)
    private static void legacyDebit(Player p,int amount,Operation<Void> original){
        if(!LEGACY){original.call(p,amount);return;}
        net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.begin(p);
        try{original.call(p,amount);}
        finally{net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.complete(p);}
    }
    @Unique private static final boolean LEGACY=java.util.Arrays.stream(com.Polarice3.Goety.utils.SEHelper.class.getDeclaredMethods())
            .noneMatch(method->method.getName().equals("decreaseSoulsNoDiscount"));
    @WrapMethod(method="decreaseSoulsNoDiscount",require=0)
    private static void debit(Player p,int amount,Operation<Void> original){
        net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.begin(p);
        try{original.call(p,amount);}
        finally{net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.complete(p);}
    }
}
