package net.everla.projectc.arkofcosmos.calamity;

/** Infernum SupremeCalamitasBehaviorOverride attack ordering, independent of Minecraft. */
public final class MahouInfernumEncounter {
    public enum Attack { HorizontalDarkSoulRelease, CondemnationFanBurst, ExplosiveCharges, HellblastBarrage, BecomeBerserk, SummonSuicideBomberDemons, BrimstoneJewelBeam, DarkMagicBombWalls, FireLaserSpin, SummonSepulcher, SummonBrothers, SummonSeekers, PhaseTransition, DesperationPhase, SummonShadowDemon, ShadowDemon_ReleaseExplodingShadowBlasts, ShadowDemon_ShadowGigablastsAndCharges }
    public static final Attack[] CYCLE1={Attack.HorizontalDarkSoulRelease,Attack.CondemnationFanBurst,Attack.ExplosiveCharges,Attack.SummonSuicideBomberDemons,Attack.HorizontalDarkSoulRelease,Attack.CondemnationFanBurst,Attack.ExplosiveCharges,Attack.SummonSuicideBomberDemons};
    public static final Attack[] CYCLE2={Attack.CondemnationFanBurst,Attack.ShadowDemon_ReleaseExplodingShadowBlasts,Attack.HellblastBarrage,Attack.BrimstoneJewelBeam,Attack.CondemnationFanBurst,Attack.ExplosiveCharges,Attack.ShadowDemon_ReleaseExplodingShadowBlasts,Attack.HorizontalDarkSoulRelease,Attack.HellblastBarrage,Attack.SummonSuicideBomberDemons,Attack.ShadowDemon_ReleaseExplodingShadowBlasts,Attack.BrimstoneJewelBeam,Attack.ExplosiveCharges,Attack.HellblastBarrage,Attack.CondemnationFanBurst,Attack.ShadowDemon_ReleaseExplodingShadowBlasts,Attack.HorizontalDarkSoulRelease,Attack.HellblastBarrage,Attack.BrimstoneJewelBeam,Attack.SummonSuicideBomberDemons};
    public static final Attack[] CYCLE3={Attack.ShadowDemon_ShadowGigablastsAndCharges,Attack.FireLaserSpin,Attack.DarkMagicBombWalls,Attack.CondemnationFanBurst,Attack.BecomeBerserk,Attack.BrimstoneJewelBeam,Attack.ShadowDemon_ShadowGigablastsAndCharges,Attack.DarkMagicBombWalls,Attack.ExplosiveCharges,Attack.BecomeBerserk,Attack.CondemnationFanBurst,Attack.ShadowDemon_ShadowGigablastsAndCharges,Attack.BrimstoneJewelBeam,Attack.FireLaserSpin,Attack.ExplosiveCharges};
    public static int nextStage(int stage,float ratio) {
        // The source uses consecutive if statements: one hit can cross all three.
        if(stage==0&&ratio<.75f)stage=1;
        if(stage==1&&ratio<.45f)stage=2;
        if(stage==2&&ratio<.25f)stage=3;
        return stage;
    }
    public static Attack cycle(int stage,int index){
        var cycle=stage==0?CYCLE1:stage==1?CYCLE2:CYCLE3;
        return cycle[Math.floorMod(index,cycle.length)];
    }
    private MahouInfernumEncounter(){}
}
