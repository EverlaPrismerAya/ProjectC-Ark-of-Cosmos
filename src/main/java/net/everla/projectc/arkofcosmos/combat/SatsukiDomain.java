package net.everla.projectc.arkofcosmos.combat;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import net.everla.projectc.arkofcosmos.SatsukiConfig;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import java.util.List;

/** Presentation only: never replaces the installed death routine or its damage. */
public final class SatsukiDomain {
    private static final boolean RF=ModList.get().isLoaded("revelationfix");
    private SatsukiDomain() {}
    public static AABB bounds(Apostle source){return new AABB(source.blockPosition()).inflate(34);}
    private static boolean introducing(Apostle source){return source instanceof net.everla.projectc.arkofcosmos.entity.SatsukiRinEntity rin&&rin.domainIntro()<rin.INTRO_TICKS;}
    public static boolean active(Apostle source){return source instanceof SatsukiBoss&&!introducing(source)&&RF&&Native.active(source);}
    public static float scale(Apostle source){
        if(!source.isDeadOrDying()||introducing(source))return 0;
        if(RF)return Native.scale(source);
        float t=source.deathTime;
        return ease(t<60?t/60:t>150?1-(t-150)/50:1);
    }
    private static float ease(float progress){return (float)((1-Math.cos(Math.PI*Math.max(0,Math.min(1,progress))))/2);}
    // This class is resolved only when RF is installed; the shared API exists in RF 4.0 and 4.9.
    private static final class Native {
        static boolean active(Apostle source){
            int left=com.mega.revelationfix.common.apollyon.common.DeathPerformance.getLeftTime(source);
            int change=(int)SatsukiConfig.value("barrier_preparation")*20;
            return source.isDeadOrDying()&&560-left>change&&left>change/2f;
        }
        static float scale(Apostle source){
            if(com.mega.revelationfix.common.apollyon.common.DeathPerformance.getLeftTime(source)<=0)return 0;
            int time=com.mega.revelationfix.common.apollyon.common.DeathPerformance.getDeathGrowingTime(source);
            int end=com.mega.revelationfix.common.apollyon.common.DeathPerformance.getFinalDeathTime(source);
            int change=(int)SatsukiConfig.value("barrier_preparation")*20;
            if(end!=-1)time+=end;
            return ease(time<change?(float)time/change:end!=-1?1-(float)(time-(560-change))/change:1);
        }
    }
    public record Victim(LivingEntity entity,float vitality) {}
    public static List<Victim> before(Apostle source){
        if(source.level().isClientSide||!active(source))return List.of();
        return source.level().getEntitiesOfClass(LivingEntity.class,bounds(source),e->e!=source)
                .stream().map(e->new Victim(e,e.getHealth()+e.getAbsorptionAmount())).toList();
    }
    public static void after(List<Victim> victims){
        for(var victim:victims){
            var entity=victim.entity();
            if(entity.getHealth()+entity.getAbsorptionAmount()>=victim.vitality())continue;
            var data=entity.getPersistentData();long now=entity.level().getGameTime();
            if(data.contains("ArkFumoSlashTime")&&!ready(data.getLong("ArkFumoSlashTime"),now))continue;
            data.putLong("ArkFumoSlashTime",now);
            var packet=new CompoundTag();packet.putString("Event","satsuki_slash");
            packet.putInt("Entity",entity.getId());
            packet.putLong("Seed",java.util.concurrent.ThreadLocalRandom.current().nextLong());
            var center=entity.getBoundingBox().getCenter();
            packet.putDouble("X",center.x);packet.putDouble("Y",center.y);packet.putDouble("Z",center.z);
            packet.putFloat("Size",Math.max(.6f,Math.max(entity.getBbWidth(),entity.getBbHeight())));
            ArkNetwork.nearbyEffect(entity,packet);
        }
    }
    public static boolean ready(long last,long now){return now<last||now-last>=5;}
}
