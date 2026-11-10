package net.everla.projectc.arkofcosmos.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dimensional Will dropped by Satsuki Rin (loot NBT {@code ArkSatsukiFantasy}) shows its own head line.
 * RF 4.0 and 4.9 both build that line in the constructor lambda {@code lambda$new$0(ItemStack)} via
 * {@code I18n.get("item.goety_revelation.dimensional_will.real_desc")}; other Dimensional Wills are untouched.
 */
@Pseudo
@Mixin(targets="com.mega.revelationfix.common.item.curios.DimensionalWillItem",remap=false)
public abstract class SatsukiDimensionalWillMixin {
    @WrapOperation(method="lambda$new$0",at={
            @At(value="INVOKE",target="Lnet/minecraft/client/resources/language/I18n;get(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"),
            @At(value="INVOKE",target="Lnet/minecraft/client/resources/language/I18n;m_118938_(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
    },remap=false,require=1)
    private static String ark$satsukiDescription(String key,Object[] args,Operation<String> original,@Local(argsOnly=true) ItemStack stack){
        if(key.equals("item.goety_revelation.dimensional_will.real_desc")&&stack!=null&&stack.getTag()!=null
                &&stack.getTag().getBoolean("ArkSatsukiFantasy"))
            key="item.arkofcosmos.satsuki_dimensional_will.real_desc";
        return original.call(key,args);
    }
}
