package net.everla.projectc.arkofcosmos.enchant;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.Comparator;
import java.util.List;

/** An irreversible, single-item recipe; emerald price is deliberately not recipe data. */
public record NecromanticInfusion(ResourceLocation id, Ingredient ingredient, ItemStack result) implements Recipe<Container> {
    public static final int COST=64;
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,ArkOfCosmos.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,ArkOfCosmos.MOD_ID);
    public static final RegistryObject<RecipeType<NecromanticInfusion>> TYPE=TYPES.register("necromantic_infusion",()->new RecipeType<>() {public String toString(){return "arkofcosmos:necromantic_infusion";}});
    public static final RegistryObject<RecipeSerializer<NecromanticInfusion>> SERIALIZER=SERIALIZERS.register("necromantic_infusion",Serializer::new);
    public static void register(IEventBus bus){if(!net.everla.projectc.arkofcosmos.CalamitasContent.ENABLED)return;TYPES.register(bus);SERIALIZERS.register(bus);}
    public static List<NecromanticInfusion> options(Level level,ItemStack input){
        if(input.isEmpty()||Curse.of(input)!=null||product(level,input))return List.of();
        return level.getRecipeManager().getAllRecipesFor(TYPE.get()).stream().filter(r->r.ingredient.test(input)).sorted(Comparator.comparing(r->r.id.toString())).toList();
    }
    public static boolean product(Level level,ItemStack stack){return stack.getItem() instanceof net.everla.projectc.arkofcosmos.item.NecromanticWeaponItem || level.getRecipeManager().getAllRecipesFor(TYPE.get()).stream().anyMatch(r->r.result.is(stack.getItem()));}
    @Override public boolean matches(Container input,Level level){return input.getContainerSize()>0&&input.getItem(0).getCount()==1&&ingredient.test(input.getItem(0))&&Curse.of(input.getItem(0))==null&&!product(level,input.getItem(0));}
    @Override public ItemStack assemble(Container input,RegistryAccess access){return result.copy();}
    @Override public ItemStack getResultItem(RegistryAccess access){return result.copy();}
    @Override public ResourceLocation getId(){return id;}
    @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return TYPE.get();}
    @Override public boolean canCraftInDimensions(int width,int height){return width*height>=1;}
    @Override public boolean isSpecial(){return true;}
    private static final class Serializer implements RecipeSerializer<NecromanticInfusion> {
        @Override public NecromanticInfusion fromJson(ResourceLocation id,JsonObject json){
            Ingredient input=Ingredient.fromJson(json.get("ingredient"));
            ItemStack output=ShapedRecipe.itemStackFromJson(json.getAsJsonObject("result"));
            if(input.isEmpty()||output.isEmpty()||output.getCount()!=1)throw new JsonSyntaxException("Necromantic infusion requires a nonempty ingredient and exactly one result item");
            return new NecromanticInfusion(id,input,output);
        }
        @Override public NecromanticInfusion fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){return new NecromanticInfusion(id,Ingredient.fromNetwork(buffer),buffer.readItem());}
        @Override public void toNetwork(FriendlyByteBuf buffer,NecromanticInfusion recipe){recipe.ingredient.toNetwork(buffer);buffer.writeItem(recipe.result);}
    }
}
