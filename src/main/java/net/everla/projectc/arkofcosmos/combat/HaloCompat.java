package net.everla.projectc.arkofcosmos.combat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.everla.projectc.arkofcosmos.item.*;

/** Optional Curios equipment integration; no Goety or RevelationFix class linkage. */
public final class HaloCompat {
    private HaloCompat() {}

    public static boolean equipped(Player player, String path) {
        return ModList.get().isLoaded("curios") && CuriosEquipment.equipped(player, path);
    }

    private static final class CuriosEquipment {
        static boolean equipped(Player player, String path) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inventory -> inventory.findFirstCurio(stack -> {
                        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                        return id != null && id.getPath().equals(path)
                                && (id.getNamespace().equals("goety_revelation") || id.getNamespace().equals("revelation"));
                    })).isPresent();
        }
    }

    public static int charge(Player player, int value, int maximum) {
        return equipped(player, "halo_of_the_end") ? maximum : value;
    }

    public static int cooldown(Player player, int remaining) {
        return equipped(player, "halo_of_the_end") ? 0 : remaining;
    }

    public static int cooldownStep(Player player) {
        return equipped(player, "ascension_halo") ? 6 : 3;
    }

    public static void refill(Player player, ItemStack stack) {
        if (!equipped(player, "halo_of_the_end")) return;
        if (stack.getItem() instanceof ArkItem) ArkItem.charge(stack, 16);
        else if (stack.getItem() instanceof ElementsItem) ElementsItem.charge(stack, 10);
        else if (stack.getItem() instanceof FracturedArkItem) FracturedArkItem.charge(stack, 10);
        else if (stack.getItem() instanceof AncientsArkItem) AncientsArkItem.charge(stack, 10);
    }
}
