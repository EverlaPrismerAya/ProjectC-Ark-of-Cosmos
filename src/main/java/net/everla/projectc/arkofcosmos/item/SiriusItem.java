package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.combat.vanilla.SpearMaterial;
import net.everla.projectc.arkofcosmos.calamity.combat.GalaxiaRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class SiriusItem extends SpearItem {
    public SiriusItem() { super(SpearMaterial.VALUES[6],1.5); }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(s->s.withColor(GalaxiaRules.NAME_COLOR));
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("sirius.description",Component.keybind("key.arkofcosmos.dash")));
    }
}
