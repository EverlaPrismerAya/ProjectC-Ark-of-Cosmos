package net.everla.projectc.arkofcosmos.mixin.compact.goetyRevelation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Pseudo
@Mixin(targets="com.Polarice3.Goety.utils.SEHelper",remap=false)
public abstract class WitchSoulDebitMixin {
    @WrapMethod(method="decreaseSoulsNoDiscount",require=1)
    private static void debit(Player p,int amount,Operation<Void> original){
        net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.begin(p);
        try{original.call(p,amount);}
        finally{net.everla.projectc.arkofcosmos.enchant.WitchSoulEvents.complete(p);}
    }
}
