package net.everla.projectc.arkofcosmos.client;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.item.ProcedureRarity;
import net.everla.projectc.arkofcosmos.item.ProcedureSwordItem;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="arkofcosmos",value=Dist.CLIENT)
public final class ProcedureSwordTooltip {
    public static Component authorizedName() {
        return ProcedureSwordItem.unrestricted(net.minecraft.client.Minecraft.getInstance().player)
                ? Component.translatable("item.arkofcosmos.procedure_sword.authorized") : null;
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ArkOfCosmos.PROCEDURE_SWORD.get())) return;
        var lines=event.getToolTip();
        for (int i=0;i<lines.size();i++) lines.set(i,replace(lines.get(i),ProcedureSwordItem.unrestricted(event.getEntity())));
    }
    private static Component replace(Component line,boolean authorized) {
        if (line.getContents() instanceof TranslatableContents text && text.getKey().startsWith("attribute.modifier.")
                && text.getArgs().length==2 && text.getArgs()[1] instanceof Component attribute
                && attribute.getContents() instanceof TranslatableContents name && name.getKey().equals("attribute.name.generic.attack_damage")) {
            return Component.translatable(text.getKey(),authorized?ProcedureRarity.number():Component.literal("12"),attribute).withStyle(line.getStyle());
        }
        var copy=MutableComponent.create(line.getContents()).withStyle(line.getStyle());
        for(var sibling:line.getSiblings())copy.append(replace(sibling,authorized));
        return copy;
    }
}
