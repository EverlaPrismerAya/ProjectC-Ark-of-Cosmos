package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ProjectCClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/** Optional integration: no Goety or RevelationFix classes are linked on startup. */
public final class ApollyonBossBar {
    private static final Map<UUID,FourthCalamityBossBar.State> STATES=new HashMap<>();
    private static Object world;
    private ApollyonBossBar() {}
    static void clear() { STATES.clear();world=null; }

    public static boolean render(CustomizeGuiOverlayEvent.BossEventProgress event,Map<UUID,Mob> bosses) {
        if(!ProjectCClientConfig.APOLLYON_BOSS_BAR.get()) { STATES.clear();return false; }
        Mob boss=bosses.get(event.getBossEvent().getId());
        if(boss==null)return false;
        try {
            Class<?> helper=Class.forName("z1gned.goetyrevelation.util.ApollyonAbilityHelper");
            if(!helper.isInstance(boss)||!(boolean)helper.getMethod("allTitlesApostle_1_20_1$isApollyon").invoke(boss))return false;
            if(world!=boss.level()) { STATES.clear();world=boss.level(); }
            STATES.keySet().retainAll(bosses.keySet());
            float max=((Number)((ForgeConfigSpec.ConfigValue<?>)Class.forName("z1gned.goetyrevelation.config.ModConfig")
                .getField("APOLLYON_HEALTH").get(null)).get()).floatValue();
            float health=boss.getHealth();
            try {
                // This synchronized value is also used by RevelationFix's own bar.
                Object context=boss.getClass().getMethod("revelaionfix$apollyonEC").invoke(boss);
                health=context.getClass().getField("percent").getFloat(context)*max;
            } catch(NoSuchMethodException ignored) { /* Goety Revelation without RevelationFix. */ }
            boolean second=(boolean)boss.getClass().getMethod("isSecondPhase").invoke(boss)
                ||(boolean)boss.getClass().getMethod("isSettingUpSecond").invoke(boss);
            float doomFraction=1f/14;
            try {
                Class<?> config=Class.forName("com.mega.revelationfix.common.config.ModpackCommonConfig");
                if(config.getField("apollyonModpackMode").getBoolean(null)
                    &&(boolean)boss.getClass().getMethod("isInNether").invoke(boss))
                    doomFraction=(float)config.getField("netherTheDoomPercent").getDouble(null);
            } catch(ClassNotFoundException ignored) { /* RevelationFix is optional. */ }
            List<Segment> segments=segments(max,second,doomFraction);
            int index=segments.size()-1;
            for(int i=0;i<segments.size();i++)if(health>segments.get(i).low()) { index=i;break; }
            Segment segment=segments.get(index);
            float progress=Math.max(0,Math.min(1,(health-segment.low())/(segment.high()-segment.low())));
            int remaining=segments.size()-index;
            var state=STATES.computeIfAbsent(event.getBossEvent().getId(),key->new FourthCalamityBossBar.State(remaining));
            state.setBars(remaining);
            int title=(int)helper.getMethod("allTitleApostle$getTitleNumber").invoke(boss);
            FourthCalamityBossBar.draw(event,state,progress,progress,segments.size(),"Lv.666",
                Component.translatable("name.goety_revelation.apollyon"),Component.translatable("title.goety."+title));
            return true;
        } catch(ReflectiveOperationException|LinkageError failure) {
            // Unknown optional-mod versions retain their native bar.
            return false;
        }
    }

    record Segment(float low,float high,int title) {}
    static List<Segment> segments(float max,boolean second) {
        return segments(max,second,1f/14);
    }
    static List<Segment> segments(float max,boolean second,float doomFraction) {
        // Goety Revelation 2.3.6: fourteen health bands; phase two overrides the first five titles.
        int[] titles={7,4,2,9,3,6,10,5,1,8,11,0,12,13};
        var boundaries=new TreeSet<Float>(Comparator.reverseOrder());
        float step=max*(1-doomFraction)/13;
        for(int i=0;i<=13;i++)boundaries.add(Math.max(0,max-i*step));
        boundaries.add(0f);
        if(second&&max>240)boundaries.add(max-240);
        var points=new ArrayList<>(boundaries);
        var result=new ArrayList<Segment>();
        for(int i=0;i<points.size()-1;i++) {
            float high=points.get(i),low=points.get(i+1),mid=(high+low)/2;
            int band=Math.min(13,(int)((max-mid)/step));
            int title=second&&band<5?(mid>max-240?6:7):titles[band];
            if(!result.isEmpty()&&result.get(result.size()-1).title()==title) {
                Segment previous=result.remove(result.size()-1);
                result.add(new Segment(low,previous.high(),title));
            } else result.add(new Segment(low,high,title));
        }
        return result;
    }
}
