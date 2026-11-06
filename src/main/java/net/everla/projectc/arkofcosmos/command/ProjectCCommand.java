package net.everla.projectc.arkofcosmos.command;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.combat.BiomeCombat;
import net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity;
import net.everla.projectc.arkofcosmos.entity.MahouTsukaiEntity;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.everla.projectc.arkofcosmos.CalamitasContent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = ArkOfCosmos.MOD_ID)
public final class ProjectCCommand {
    private ProjectCCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var unlock = Commands.literal("unlock");
        String[] environments = {"pure", "hot", "cold", "tropical", "evil", "holy", "astral", "marine"};
        for (int i = 0; i < environments.length; i++) {
            int mode = i;
            String environment = environments[i];
            unlock.then(Commands.literal(environment).executes(context -> {
                BiomeCombat.unlock(context.getSource().getPlayerOrException(), mode);
                context.getSource().sendSuccess(() -> Component.literal("已解锁环境之刃调谐：" + environment), false);
                return 1;
            }));
        }
        var root = Commands.literal("projectc")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("biomeblade")
                .then(Commands.literal("unlockall").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    for (int mode = 0; mode < 8; mode++) BiomeCombat.unlock(player, mode);
                    context.getSource().sendSuccess(() -> Component.literal("已解锁环境之刃全部调谐"), false);
                    return 1;
                }))
                .then(unlock))
            .then(Commands.literal("removeBoss").executes(context -> {
                var bosses = new ArrayList<Entity>();
                for (var level : context.getSource().getServer().getAllLevels())
                    for (var entity : level.getAllEntities())
                        if (!entity.isRemoved() && (entity instanceof MahouTsukaiEntity || entity instanceof FourthCalamityEntity || entity instanceof SupremeCalamitasEntity || net.everla.projectc.arkofcosmos.entity.CalamitasSummons.commandRoot(entity)))
                            bosses.add(entity);
                for (var boss : bosses) {
                    if (boss instanceof MahouTsukaiEntity mahou) mahou.removeByPlayerCommand();
                    else if (boss instanceof FourthCalamityEntity calamity) calamity.removeByPlayerCommand();
                    else if (boss instanceof SupremeCalamitasEntity calamitas) calamitas.endBattle(false);
                    else net.everla.projectc.arkofcosmos.entity.CalamitasSummons.removeCommandRoot(boss);
                }
                context.getSource().sendSuccess(() -> Component.literal("已移除 " + bosses.size() + " 个 Boss"), true);
                return bosses.size();
            }));
        if (CalamitasContent.ENABLED) root.then(Commands.literal("calamitas")
            .then(Commands.literal("summon").executes(context -> {
                var source = context.getSource();
                var target = source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null;
                var boss = SupremeCalamitasEntity.spawn(source.getLevel(), source.getPosition().add(0, 3, 0), target);
                if (boss == null) {
                    source.sendFailure(Component.translatable("commands.projectc.calamitas.summon_failed"));
                    return 0;
                }
                source.sendSuccess(() -> Component.translatable("commands.projectc.calamitas.summoned", boss.getDisplayName()), true);
                return 1;
            })));
        event.getDispatcher().register(root);
    }
}
