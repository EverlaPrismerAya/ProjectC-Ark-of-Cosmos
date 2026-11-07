package net.everla.projectc.arkofcosmos.enchant;

import net.everla.projectc.arkofcosmos.ProjectCCommonConfig;
import net.everla.projectc.arkofcosmos.entity.BrimstoneWitchEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class WitchMenu extends AbstractContainerMenu {
    private final SimpleContainer input=new SimpleContainer(1);
    private final ContainerData costs=new SimpleContainerData(3);
    private final Player viewer;
    public int revision(){return costs.get(2);}
    public java.util.List<NecromanticInfusion> infusions(){return NecromanticInfusion.options(viewer.level(),item());}
    public boolean product(){return !item().isEmpty()&&NecromanticInfusion.product(viewer.level(),item());}
    private boolean accepts(ItemStack stack){return Curse.recognized(stack)||!NecromanticInfusion.options(viewer.level(),stack).isEmpty()||NecromanticInfusion.product(viewer.level(),stack);}
    public int cost(boolean remove){return costs.get(remove?1:0);}
    public final BrimstoneWitchEntity witch;
    public WitchMenu(int id,Inventory inventory,BrimstoneWitchEntity witch) {
        super(WitchEnchanting.MENU.get(),id);this.witch=witch;this.viewer=inventory.player;
        if(!inventory.player.level().isClientSide){costs.set(0,(int)ProjectCCommonConfig.calamitasValue("enchant_cost"));costs.set(1,(int)ProjectCCommonConfig.calamitasValue("disenchant_cost"));}addDataSlots(costs);
        addSlot(new Slot(input,0,31,44){@Override public boolean mayPlace(ItemStack stack){return accepts(stack);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,59+col*18,134+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,59+col*18,192));
    }
    public ItemStack item(){return input.getItem(0);}
    public boolean infuse(Player player,net.minecraft.resources.ResourceLocation id,ItemStack expected,ItemStack output,int revision){
        if(player!=viewer||player.containerMenu!=this||player.level().isClientSide||!stillValid(player)||revision!=revision()||!ItemStack.matches(item(),expected))return false;
        var candidate=player.level().getRecipeManager().byKey(id).orElse(null);
        if(!(candidate instanceof NecromanticInfusion recipe)||!recipe.matches(input,player.level())||!ItemStack.matches(recipe.result(),output)||!pay(player,NecromanticInfusion.COST))return false;
        costs.set(2,(revision()+1)&0x7FFF);input.setItem(0,recipe.assemble(input,player.level().registryAccess()));
        changed(player);return true;
    }
    private boolean pay(Player player,int cost){
        int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack stack=player.getInventory().getItem(i);if(stack.is(Items.EMERALD))count+=stack.getCount();}
        if(count<cost)return false;
        int left=cost;for(int i=0;i<player.getInventory().getContainerSize()&&left>0;i++){var stack=player.getInventory().getItem(i);if(stack.is(Items.EMERALD)){int used=Math.min(left,stack.getCount());stack.shrink(used);left-=used;}}
        player.getInventory().setChanged();return true;
    }
    private void changed(Player player){input.setChanged();broadcastChanges();var sound=net.everla.projectc.arkofcosmos.CalamitasContent.WITCH_ENCHANT.get();player.level().playSound(null,witch.blockPosition(),sound,net.minecraft.sounds.SoundSource.NEUTRAL,1,1);}
    @Override public void broadcastChanges(){if(witch!=null&&!witch.level().isClientSide){costs.set(0,(int)ProjectCCommonConfig.calamitasValue("enchant_cost"));costs.set(1,(int)ProjectCCommonConfig.calamitasValue("disenchant_cost"));}super.broadcastChanges();}
    @Override public boolean stillValid(Player player){return witch!=null && witch.isAlive() && (player.level().isClientSide || witch.customer(player)) && !witch.isRemoved() && !witch.fighting() && player.distanceToSqr(witch)<=64 && player.hasLineOfSight(witch);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(player.level().isClientSide || !stillValid(player))return false;
        ItemStack item=item();Curse current=Curse.of(item);
        if(item.isEmpty() || item.getCount()!=1 || !Curse.recognized(item)||product())return false;
        Curse wanted=button>0 && button<=Curse.values().length?Curse.values()[button-1]:null;
        if(button==0?current==null:current!=null || wanted==null || !wanted.accepts(item))return false;
        int cost=player.getAbilities().instabuild?0:(int)ProjectCCommonConfig.calamitasValue(button==0?"disenchant_cost":"enchant_cost");
        int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack stack=player.getInventory().getItem(i);if(stack.is(Items.EMERALD))count+=stack.getCount();}
        if(count<cost)return false;
        int left=cost;
        for(int i=0;i<player.getInventory().getContainerSize() && left>0;i++){
            ItemStack stack=player.getInventory().getItem(i);if(!stack.is(Items.EMERALD))continue;
            int used=Math.min(left,stack.getCount());stack.shrink(used);left-=used;
        }
        Curse.apply(item,wanted);input.setChanged();broadcastChanges();
        var sound=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arkofcosmos","witch_enchant"));
        if(sound!=null)player.level().playSound(null,witch.blockPosition(),sound,net.minecraft.sounds.SoundSource.NEUTRAL,1,1);
        return true;
    }
    @Override public void removed(Player player){super.removed(player);if(!player.level().isClientSide){clearContainer(player,input);if(witch!=null)witch.release(player);}}
    @Override public ItemStack quickMoveStack(Player player,int index){
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(!accepts(stack) || !moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
}
