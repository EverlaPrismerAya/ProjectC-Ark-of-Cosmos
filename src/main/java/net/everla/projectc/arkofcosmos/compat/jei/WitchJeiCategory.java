package net.everla.projectc.arkofcosmos.compat.jei;

import java.util.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.everla.projectc.arkofcosmos.enchant.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses the same synced recipes and eligibility rules as the witch menu. */
final class WitchJeiCategory implements IRecipeCategory<WitchJeiCategory.Entry> {
    private static final RecipeType<Entry> NECROMANTIC=RecipeType.create("arkofcosmos","necromantic_infusion",Entry.class);
    private static final RecipeType<Entry> CURSES=RecipeType.create("arkofcosmos","curse_infusion",Entry.class);
    record Entry(List<ItemStack> inputs,List<ItemStack> outputs,Curse curse) {}
    private final boolean curses;
    private final IDrawable background,icon,slot,arrow;
    WitchJeiCategory(IGuiHelper gui,boolean curses){
        this.curses=curses;background=gui.createBlankDrawable(180,148);
        icon=gui.createDrawableItemStack(new ItemStack(curses&&CalamitasContent.ENABLED?CalamitasContent.RANCOR.get():Items.EMERALD));
        slot=gui.getSlotDrawable();arrow=gui.getRecipeArrow();
    }
    static void register(IRecipeRegistration registration){
        var level=Minecraft.getInstance().level;
        if(!CalamitasContent.ENABLED||level==null)return;
        registration.addRecipes(NECROMANTIC,level.getRecipeManager().getAllRecipesFor(NecromanticInfusion.TYPE.get()).stream()
            .map(r->new Entry(Arrays.stream(r.ingredient().getItems()).filter(s->Curse.of(s)==null&&!NecromanticInfusion.product(level,s)).toList(),List.of(r.result().copy()),null))
            .filter(r->!r.inputs().isEmpty()).toList());
        List<Entry> recipes=new ArrayList<>();
        for(Curse curse:Curse.values()){
            List<ItemStack> inputs=new ArrayList<>(),outputs=new ArrayList<>();
            for(Item item:ForgeRegistries.ITEMS){
                ItemStack stack=new ItemStack(item);
                if(curse.accepts(stack)&&!NecromanticInfusion.product(level,stack)){
                    inputs.add(stack);ItemStack result=stack.copy();Curse.apply(result,curse);outputs.add(result);
                }
            }
            if(!inputs.isEmpty())recipes.add(new Entry(inputs,outputs,curse));
        }
        registration.addRecipes(CURSES,recipes);
    }
    @Override public RecipeType<Entry> getRecipeType(){return curses?CURSES:NECROMANTIC;}
    @Override public Component getTitle(){return Component.translatable(curses?"jei.arkofcosmos.curse_infusion":"menu.arkofcosmos.necromantic_infusion");}
    @Override public IDrawable getBackground(){return background;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,Entry recipe,IFocusGroup focuses){
        var input=builder.addSlot(RecipeIngredientRole.INPUT,5,5).setBackground(slot,-1,-1).addItemStacks(recipe.inputs());
        builder.addSlot(RecipeIngredientRole.INPUT,36,5).setBackground(slot,-1,-1).addItemStack(new ItemStack(Items.EMERALD,recipe.curse()==null?NecromanticInfusion.COST:(int)net.everla.projectc.arkofcosmos.ProjectCCommonConfig.calamitasValue("enchant_cost")));
        var output=builder.addSlot(RecipeIngredientRole.OUTPUT,112,5).setBackground(slot,-1,-1).addItemStacks(recipe.outputs());
        if(recipe.curse()!=null)builder.createFocusLink(input,output);
    }
    @Override public void draw(Entry recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY){
        var font=Minecraft.getInstance().font;arrow.draw(graphics,70,5);
        graphics.drawWordWrap(font,Component.translatable("jei.arkofcosmos.witch.instructions"),4,29,172,0x404040);
        if(recipe.curse()!=null){
            graphics.drawString(font,Component.translatable(recipe.curse().key()),4,56,0x6C2DC7,false);
            graphics.drawWordWrap(font,Component.translatable(recipe.curse().key()+".description"),4,71,172,0x404040);
        }else graphics.drawWordWrap(font,Component.translatable("menu.arkofcosmos.necromantic_infusion.description"),4,56,172,0x404040);
    }
}
