package net.everla.projectc.arkofcosmos.mixin.client.gui;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;

@Mixin(BeaconScreen.class)
public abstract class BeaconSummonScreenMixin extends AbstractContainerScreen<BeaconMenu> {
    @Unique private AbstractWidget ark$confirm;
    @Unique private Button ark$summon;
    protected BeaconSummonScreenMixin(BeaconMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); }
    @Inject(method="init",at=@At("TAIL"))
    private void ark$init(CallbackInfo ci) {
        ark$confirm=null;
        for(var child:children())if(child instanceof AbstractWidget widget&&widget.getX()==leftPos+164&&widget.getY()==topPos+107)ark$confirm=widget;
        ark$summon=addRenderableWidget(new Button(leftPos+164,topPos+107,22,22,Component.empty(),button->{
            minecraft.getConnection().send(new ServerboundSetBeaconPacket(Optional.empty(),Optional.empty()));
            minecraft.player.closeContainer();
        },supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics graphics,int x,int y,float partial) {
                graphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","textures/gui/container/beacon.png"),getX(),getY(),isHoveredOrFocused()?66:0,219,22,22);
                graphics.renderItem(new ItemStack(Items.NETHERITE_SWORD),getX()+3,getY()+3);
            }
        });
        ark$summon.setTooltip(Tooltip.create(Component.translatable("gui.arkofcosmos.summon")));
        ark$summon.setMessage(Component.translatable("gui.arkofcosmos.summon"));
        ark$summon.visible=false;
    }
    @Inject(method="render",at=@At("HEAD"))
    private void ark$update(GuiGraphics graphics,int x,int y,float partial,CallbackInfo ci) {
        var stack=menu.getSlot(0).getItem();
        boolean summon=stack.is(ArkOfCosmos.ASHES_OF_CALAMITY.get())||stack.is(ArkOfCosmos.AURIC_BAR.get());
        if(ark$confirm!=null)ark$confirm.visible=!summon;
        if(ark$summon!=null){ark$summon.visible=summon;ark$summon.active=summon;}
    }
}

