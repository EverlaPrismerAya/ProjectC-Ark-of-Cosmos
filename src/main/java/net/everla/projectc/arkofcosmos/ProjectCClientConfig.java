package net.everla.projectc.arkofcosmos;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ProjectCClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue APOLLYON_BOSS_BAR;
    static {
        var builder=new ForgeConfigSpec.Builder();
        APOLLYON_BOSS_BAR=builder.comment("Use the ProjectC multi-stage boss bar for Apollyon.")
            .define("apollyonBossBar",true);
        SPEC=builder.build();
    }
    private ProjectCClientConfig() {}
}
