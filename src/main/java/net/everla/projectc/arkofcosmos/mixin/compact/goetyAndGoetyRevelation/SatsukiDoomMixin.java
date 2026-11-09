package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Rin does not enable RF's equipment confiscation and base-attribute reset. */
@Pseudo
@Mixin(targets="com.mega.revelationfix.common.apollyon.common.PlayerTickrateExecutor",remap=false)
public abstract class SatsukiDoomMixin {
    @Shadow public static Predicate<Entity> doomApollyon;
    @Inject(method="<clinit>",at=@At("RETURN"),remap=false)
    private static void ark$satsukiKeepsEquipment(CallbackInfo ci){
        doomApollyon=doomApollyon.and(e->!(e instanceof net.everla.projectc.arkofcosmos.combat.SatsukiBoss));
    }
}
