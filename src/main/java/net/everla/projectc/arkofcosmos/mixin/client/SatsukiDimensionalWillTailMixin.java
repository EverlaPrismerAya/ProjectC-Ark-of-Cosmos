package net.everla.projectc.arkofcosmos.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;

/**
 * Grey quote lines of a Dimensional Will dropped by Satsuki Rin (loot NBT {@code ArkSatsukiFantasy}).
 * RF builds them once in the constructor ({@code withTail}, no stack), so they are swapped where EndingLib bakes the
 * tail per stack: {@code SimpleDescriptiveCurio.getTailDescriptionLines(ItemStack)} (EndingLib 2.2.1 and 2.2.3, used by
 * RF 4.0 and 4.9 alike). Only the translation keys change; RF's styling stays.
 */
@Pseudo
@Mixin(targets="com.mega.endinglib.api.item.curios.SimpleDescriptiveCurio",remap=false)
public abstract class SatsukiDimensionalWillTailMixin {
    @Unique private static final Map<String,String> ark$keys=Map.of(
            "item.goety_revelation.dimensional_will.desc0","item.arkofcosmos.satsuki_dimensional_will.desc0",
            "item.goety_revelation.dimensional_will.desc1","item.arkofcosmos.satsuki_dimensional_will.desc1");

    @Inject(method="getTailDescriptionLines",at=@At("RETURN"),cancellable=true,remap=false,require=1)
    private void ark$satsukiTail(ItemStack stack,CallbackInfoReturnable<List<MutableComponent>> cir){
        if(!getClass().getName().equals("com.mega.revelationfix.common.item.curios.DimensionalWillItem"))return;
        if(stack==null||stack.getTag()==null||!stack.getTag().getBoolean("ArkSatsukiFantasy"))return;
        var lines=cir.getReturnValue();if(lines==null)return;
        cir.setReturnValue(lines.stream().map(SatsukiDimensionalWillTailMixin::ark$rekey).toList());
    }

    /** Rebuilds the component tree, swapping only the two quote keys and keeping every style. */
    @Unique private static MutableComponent ark$rekey(Component line){
        MutableComponent copy;
        if(line.getContents() instanceof TranslatableContents translatable&&ark$keys.containsKey(translatable.getKey()))
            copy=Component.translatable(ark$keys.get(translatable.getKey()),translatable.getArgs());
        else copy=MutableComponent.create(line.getContents());
        copy.setStyle(line.getStyle());
        for(var sibling:line.getSiblings())copy.append(ark$rekey(sibling));
        return copy;
    }
}
