package net.everla.projectc.arkofcosmos.item;

import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class BrimstoneLocusItem extends Item {
    public BrimstoneLocusItem() { super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        var npc=CalamitasContent.WITCH.get().create(level); if(npc==null) return InteractionResult.FAIL;
        var pos=context.getClickedPos().relative(context.getClickedFace());
        if(context.getPlayer()!=null && !context.getPlayer().mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        npc.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        if(!level.noCollision(npc) || !level.addFreshEntity(npc)) return InteractionResult.FAIL;
        if(context.getPlayer()==null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
