package net.everla.projectc.arkofcosmos.combat.vanilla;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.*;
import net.minecraftforge.registries.*;

/** Two independent additions; existing table rolls are never replaced. */
public final class AncientCityLoot extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> REGISTER=DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,"arkofcosmos");
    public static final RegistryObject<Codec<AncientCityLoot>> TYPE=REGISTER.register("ancient_city_combat",()->RecordCodecBuilder.create(i->codecStart(i).apply(i,AncientCityLoot::new)));
    public AncientCityLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,LootContext context) {
        if(context.getRandom().nextFloat()<.025f)loot.add(new ItemStack(VanillaCombat.HEAVY_CORE.get()));
        if(context.getRandom().nextFloat()<.025f)loot.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(VanillaCombat.WIND_BURST.get(),1)));
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return TYPE.get(); }
}
