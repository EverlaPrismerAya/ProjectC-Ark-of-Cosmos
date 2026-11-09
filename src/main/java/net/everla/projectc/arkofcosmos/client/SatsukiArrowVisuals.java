package net.everla.projectc.arkofcosmos.client;

import java.util.*;
import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only disguise for Satsuki Rin's native Apollyon arrows: the arrow geometry is skipped and
 * its motion is fed into the Bejeweled gem-dust visual. The server-side arrow is untouched.
 */
@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID,value=Dist.CLIENT)
public final class SatsukiArrowVisuals {
    private SatsukiArrowVisuals(){}
    private static final class Mark {int variant,owner,age=-1;long touched;boolean seen,burst;Vec3 last,velocity=Vec3.ZERO;}
    private static final Map<UUID,Mark> MARKS=new HashMap<>();
    private static Object world;

    public static void receive(CompoundTag data){
        var level=Minecraft.getInstance().level;if(level==null||!data.hasUUID("Arrow"))return;
        Mark mark=MARKS.computeIfAbsent(data.getUUID("Arrow"),k->new Mark());
        mark.variant=data.getInt("Variant");mark.owner=data.getInt("Owner");mark.touched=level.getGameTime();
    }
    public static boolean hides(Entity entity){return !MARKS.isEmpty()&&entity instanceof AbstractArrow&&MARKS.containsKey(entity.getUUID());}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level!=world){world=level;MARKS.clear();}
        if(level==null||MARKS.isEmpty()||mc.isPaused())return;
        Map<UUID,AbstractArrow> present=new HashMap<>();
        for(Entity entity:level.entitiesForRendering())
            if(entity instanceof AbstractArrow arrow&&MARKS.containsKey(arrow.getUUID()))present.put(arrow.getUUID(),arrow);
        long now=level.getGameTime();
        for(var it=MARKS.entrySet().iterator();it.hasNext();){
            var entry=it.next();Mark mark=entry.getValue();AbstractArrow arrow=present.get(entry.getKey());
            if(arrow==null){
                // Removed (hit/discarded): burst once at the last known position. Unseen marks expire.
                if(mark.seen){if(!mark.burst)burst(entry.getKey(),mark);it.remove();}
                else if(now-mark.touched>100)it.remove();
                continue;
            }
            mark.seen=true;mark.touched=now;
            Vec3 from=new Vec3(arrow.xo,arrow.yo,arrow.zo),to=arrow.position();
            if(to.distanceToSqr(from)>1e-6){
                // Bejeweled bolts run three sub-steps per tick; sample the arrow's segment the same way.
                Vec3 step=to.subtract(from).scale(1/3d);ListTag samples=new ListTag();
                for(int sub=1;sub<=3;sub++){
                    CompoundTag sample=point(from.add(step.scale(sub)),step);sample.putInt("Age",++mark.age);samples.add(sample);
                }
                CompoundTag data=packet(entry.getKey(),mark,to,step);data.put("Samples",samples);
                BejeweledEffects.receive(data);
                mark.velocity=step;
            }else if(mark.age>=0&&!mark.burst)burst(entry.getKey(),mark); // stuck in a block
            mark.last=to;
        }
    }
    private static void burst(UUID id,Mark mark){
        mark.burst=true;if(mark.last==null)return;
        CompoundTag data=packet(id,mark,mark.last,mark.velocity);data.putBoolean("Burst",true);data.put("Samples",new ListTag());
        BejeweledEffects.receive(data);
    }
    private static CompoundTag packet(UUID id,Mark mark,Vec3 at,Vec3 velocity){
        CompoundTag data=point(at,velocity);data.putUUID("Attack",id);data.putInt("Owner",mark.owner);
        data.putInt("Variant",mark.variant);data.putInt("Age",Math.max(0,mark.age));return data;
    }
    private static CompoundTag point(Vec3 at,Vec3 velocity){
        CompoundTag t=new CompoundTag();t.putDouble("X",at.x);t.putDouble("Y",at.y);t.putDouble("Z",at.z);
        t.putDouble("VX",velocity.x);t.putDouble("VY",velocity.y);t.putDouble("VZ",velocity.z);return t;
    }
}
