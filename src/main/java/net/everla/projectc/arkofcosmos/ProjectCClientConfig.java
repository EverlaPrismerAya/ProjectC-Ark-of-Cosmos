package net.everla.projectc.arkofcosmos;

import net.minecraftforge.common.ForgeConfigSpec;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.UncheckedIOException;

public final class ProjectCClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue APOLLYON_BOSS_BAR;
    public static final ForgeConfigSpec.BooleanValue SATSUKI_BOSS_BAR;
    public static final ForgeConfigSpec.BooleanValue APOSTLE_BOSS_BAR;
    public static final ForgeConfigSpec.BooleanValue MAHOU_SIMPLE_BOSS_BAR;
    static {
        var builder=new ForgeConfigSpec.Builder();
        APOLLYON_BOSS_BAR=builder.comment("Use the ProjectC multi-stage boss bar for Apollyon.")
            .define("apollyonBossBar",false);
        APOSTLE_BOSS_BAR=builder.comment("Use the ProjectC boss bar for ordinary Apostles.")
            .define("apostleBossBar",false);
        SATSUKI_BOSS_BAR=builder.comment("Use the ProjectC multi-stage boss bar for Satsuki Rin.")
            .define("satsukiRinBossBar",true);
        MAHOU_SIMPLE_BOSS_BAR=builder.comment("Use the simplified ProjectC boss bar for Mahou Tsukai instead of the cosmic bar.")
            .define("mahouSimpleBossBar",false);
        SPEC=builder.build();
    }
    private ProjectCClientConfig() {}

    /** Run before Forge loads/corrects the file, so the backup retains the old bytes. */
    public static void backupBeforeUpgrade(Path path) {
        if(!Files.isRegularFile(path))return;
        boolean needsUpgrade;
        try(var config=CommentedFileConfig.builder(path).sync().build()) {
            try {
                config.load();
                needsUpgrade=!SPEC.isCorrect(config);
            } catch(com.electronwill.nightconfig.core.io.ParsingException invalid) {
                needsUpgrade=true; // Forge will handle recovery after the original is backed up.
            }
        }
        if(needsUpgrade) {
            Path backup=path.resolveSibling(path.getFileName()+"."+java.time.Instant.now().toEpochMilli()+"-"+java.util.UUID.randomUUID()+".bak");
            try { Files.copy(path,backup); }
            catch(IOException failure) {
                throw new UncheckedIOException("Cannot back up client config before upgrading: "+path,failure);
            }
        }
    }
}
