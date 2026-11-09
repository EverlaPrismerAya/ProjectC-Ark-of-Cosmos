package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.SatsukiConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid="arkofcosmos")
public final class SatsukiFumo {
    public static final String[] TEXTURES={"", "marisa", "cirno", "yuyuko", "remilia", "patchouli", "youmu", "reimu"};
    private SatsukiFumo() {}
    public static int variant(Entity entity) {
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if(id==null||!(id.getNamespace().equals("goety")||id.getNamespace().equals("goety_revelation")))return 0;
        return switch(id.getPath()) {
            case "inferno" -> 1;
            case "stray_servant" -> 2;
            // Nether-monolith summons (zombified piglins are undead like the Yuyuko group).
            case "wraith_servant", "malghast", "husk_servant", "damned", "zpiglin_servant", "zpiglin_brute_servant" -> 3;
            case "phantom_servant" -> 4;
            case "cave_spider_servant", "spider_servant", "heretic_servant" -> 5;
            case "skeleton_servant", "wither_skeleton_servant" -> 6;
            case "obsidian_monolith" -> 7;
            default -> 0;
        };
    }
    public static void update(Entity entity) {
        if(entity.level().isClientSide||!(entity instanceof FumoAppearance appearance))return;
        int variant=variant(entity);
        if(variant==0)return;
        // Preserve the saved marker while the owner is unloaded; clear it on an actual ownership change.
        if(entity instanceof OwnableEntity owned){
            if(owned.getOwnerUUID()==null)appearance.ark$fumo(0);
            else if(owned.getOwner()!=null)appearance.ark$fumo(SatsukiConfig.owned(entity)?variant:0);
        }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){update(event.getEntity());}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){update(event.getEntity());}
}
