package net.everla.projectc.arkofcosmos;

import java.lang.reflect.*;
import java.util.*;
import net.everla.projectc.arkofcosmos.combat.SatsukiBoss;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.ForgeConfigSpec;

/** Independent values; optional native reads are filtered using their actual owner. */
public final class SatsukiConfig {
    private SatsukiConfig() {}
    public static double value(String key){return ProjectCCommonConfig.value("boss.satsuki_rin."+key);}
    static void define(ForgeConfigSpec.Builder b){
        b.push("boss").push("satsuki_rin");
        number(b,"health",6666,1,1e9,"Maximum health; live changes preserve the current health ratio");
        number(b,"hurt_limit",0,0,1e9,"Maximum incoming damage per hit; zero disables this optional cap");
        number(b,"permanent_damage_reduction",0,0,100,"Additional permanent damage reduction in percent; zero preserves the default Calamitas policy");
        flag(b,"dynamic_damage_reduction",true,"Enable the existing Supreme Calamitas dynamic damage reduction");
        number(b,"invulnerable_time",10,0,32767,"Non-player damage cooldown in ticks; player-owned damage retains its normal rules");
        number(b,"hellfire_damage_amount",.0444,0,100,"Hellfire damage as a fraction of the victim's maximum health");
        number(b,"hellcloud_damage_amount",.001,0,100,"Hellcloud damage as a fraction of the victim's maximum health, before its native flat addition");
        number(b,"barrier_preparation",3,3,10,"Seconds for the native death barrier to open");
        number(b,"shooting_cooldown",1600,0,32767,"Death-arrow skill cooldown in ticks; the apocalypse uses half this value");
        number(b,"second_phase_damage_multiplier",2,0,100,"Native Nether phase-two outgoing damage multiplier");
        number(b,"fractal_sigil_damage",16,0,1000000,"Damage of each First Fractal slash from the phase-two fractal sigils (replacing fire blast traps)");
        number(b,"genesis_damage_multiplier",4,0,100,"Additional native Genesis outgoing damage multiplier");
        number(b,"armor_piercing",30,0,100,"Native armor-piercing attribute base value in percent");
        number(b,"enchantment_piercing",30,0,100,"Native enchantment-piercing attribute base value in percent");
        flag(b,"modpack_mode",false,"Use the configurable native healing rates and apocalypse health fraction");
        number(b,"self_healing",.002,0,1,"Natural healing fraction per native healing trigger when modpack_mode is enabled");
        number(b,"arrow_healing",.005,0,1,"First-phase death-arrow hit healing fraction when modpack_mode is enabled");
        number(b,"second_phase_arrow_healing",.01,0,1,"Second-phase death-arrow hit healing fraction when modpack_mode is enabled");
        number(b,"doom_percent",1d/14,0.000001,.999999,"Apocalypse maximum-health fraction when modpack_mode is enabled");
        flag(b,"barrier_killing_mobs_healing",true,"Allow the native death barrier's kill-triggered revival");
        flag(b,"powerful_servants",false,"Enable native servant enhancement when supported by the installed RevelationFix; existing enhancements are not reverted");
        for(boolean nether:new boolean[]{false,true}){
            b.push(nether?"servants_nether":"servants_non_nether");
            number(b,"hurt_limit_ratio",nether?1d/3:.75,Double.MIN_NORMAL,1,"Native servant per-hit limit as a fraction of maximum health");
            number(b,"protection_level",nether?4:2,0,255,"Protection enchantment level for newly issued servant armor");
            number(b,"extra_armor",nether?20:10,0,32768,"Additional armor for servants that cannot wear a full armor set");
            number(b,"extra_toughness",nether?10:5,0,32768,"Additional toughness for servants that cannot wear a full armor set");
            for(String key:new String[]{"attack_multiplier","health_multiplier","knockback_multiplier","speed_multiplier"})
                number(b,key,key.equals("speed_multiplier")?1.5:nether?5:2.5,Double.MIN_NORMAL,32768,"Native servant attribute multiplier; applied when the servant is enhanced");
            b.pop();
        }
        b.pop().pop();
    }
    private static void number(ForgeConfigSpec.Builder b,String key,double value,double min,double max,String comment){ProjectCCommonConfig.define(b,key,value,min,max,comment);}
    private static void flag(ForgeConfigSpec.Builder b,String key,boolean value,String comment){ProjectCCommonConfig.flag(b,key,value,comment);}

    private static final ClassValue<List<AccessibleObject>> OWNERS=new ClassValue<>(){
        @Override protected List<AccessibleObject> computeValue(Class<?> type){
            List<AccessibleObject> access=new ArrayList<>();
            for(String name:List.of("getMasterOwner","getOwner"))try{access.add(type.getMethod(name));}catch(NoSuchMethodException ignored){}
            for(Class<?> c=type;c!=null;c=c.getSuperclass())for(var field:c.getDeclaredFields())
                if(!Modifier.isStatic(field.getModifiers())&&Set.of("apostle","mob","caster","owner").contains(field.getName())&&field.trySetAccessible())access.add(field);
            return List.copyOf(access);
        }
    };
    public static boolean owned(Object context){return owned(context,0);}
    private static boolean owned(Object context,int depth){
        if(context instanceof SatsukiBoss)return true;
        if(context==null||depth>5)return false;
        if(context instanceof DamageSource source)return owned(source.getEntity(),depth+1);
        if(context instanceof net.minecraftforge.event.entity.EntityEvent event)return owned(event.getEntity(),depth+1);
        if(context instanceof net.minecraft.world.entity.projectile.Projectile projectile)return owned(projectile.getOwner(),depth+1);
        if(context instanceof net.minecraft.world.entity.OwnableEntity entity)return owned(entity.getOwner(),depth+1);
        for(var access:OWNERS.get(context.getClass()))try{
            Object owner=access instanceof Method method?method.invoke(context):((Field)access).get(context);
            if(owner!=context&&owned(owner,depth+1))return true;
        }catch(ReflectiveOperationException ignored){}
        return false;
    }
    public static double nativeDouble(double original,String key,Object context){
        if(!owned(context))return original;
        // RF uses its self-healing field for first-phase death-arrow hits too.
        if(key.equals("self_healing")&&context instanceof net.minecraft.world.entity.projectile.Projectile)return value("arrow_healing");
        if(key.equals("armor_piercing")||key.equals("enchantment_piercing"))return 1+value(key)/100;
        if(key.equals("barrier_ticks"))return (int)value("barrier_preparation")*20;
        return value(key);
    }
    public static float nativeFloat(float original,String key,Object context){return (float)nativeDouble(original,key,context);}
    public static int nativeInt(int original,String key,Object context){return (int)nativeDouble(original,key,context);}
    public static boolean nativeBoolean(boolean original,String key,Object context){return owned(context)?value(key)!=0:original;}
    public static Object nativeConfig(Object original,String key,Object context){
        if(!owned(context))return original;
        return value(key);
    }
}
