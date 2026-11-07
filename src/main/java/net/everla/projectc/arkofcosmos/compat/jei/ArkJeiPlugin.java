package net.everla.projectc.arkofcosmos.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/** JEI discovers this plugin only when installed; common gameplay never references JEI. */
@JeiPlugin
public final class ArkJeiPlugin implements IModPlugin {
    private static final RecipeType<Summoning> SUMMONING=RecipeType.create(ArkOfCosmos.MOD_ID,"beacon_summoning",Summoning.class);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(ArkOfCosmos.MOD_ID,"jei"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SummoningCategory(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(new ItemStack(ArkOfCosmos.ASHES_OF_CALAMITY.get()),VanillaTypes.ITEM_STACK,
            Component.translatable("jei.arkofcosmos.ashes_of_calamity.source"));
        if (net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED)
            registration.addIngredientInfo(new ItemStack(ArkOfCosmos.ANNIHILATION_EMBER.get()),VanillaTypes.ITEM_STACK,
                Component.translatable("jei.arkofcosmos.annihilation_ember.source"));
        registration.addRecipes(SUMMONING,List.of(
            new Summoning(new ItemStack(ArkOfCosmos.ASHES_OF_CALAMITY.get()),Component.translatable("jei.arkofcosmos.boss.mahou")),
            new Summoning(new ItemStack(ArkOfCosmos.AURIC_BAR.get()),Component.translatable("entity.arkofcosmos.the_player"))));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.BEACON),SUMMONING);
    }
    public record Summoning(ItemStack material,Component boss) {}
    private static final class SummoningCategory implements IRecipeCategory<Summoning> {
        private final IDrawable background,icon,slot,arrow;
        SummoningCategory(IGuiHelper gui) {
            background=gui.createBlankDrawable(180,86);
            icon=gui.createDrawableItemStack(new ItemStack(Items.BEACON));
            slot=gui.getSlotDrawable();arrow=gui.getRecipeArrow();
        }
        @Override public RecipeType<Summoning> getRecipeType() { return SUMMONING; }
        @Override public Component getTitle() { return Component.translatable("jei.arkofcosmos.beacon_summoning"); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder,Summoning recipe,IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT,5,8).setBackground(slot,-1,-1).addItemStack(recipe.material());
            builder.addSlot(RecipeIngredientRole.CATALYST,29,8).addItemStack(new ItemStack(Items.BEACON));
        }
        @Override public void draw(Summoning recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY) {
            var font=Minecraft.getInstance().font;
            arrow.draw(graphics,53,8);
            graphics.drawString(font,recipe.boss(),82,12,0x404040,false);
            graphics.drawWordWrap(font,Component.translatable("jei.arkofcosmos.beacon_summoning.instructions"),4,34,172,0x404040);
        }
    }
}
