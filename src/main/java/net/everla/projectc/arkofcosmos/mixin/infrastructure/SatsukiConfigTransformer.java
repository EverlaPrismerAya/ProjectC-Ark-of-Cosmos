package net.everla.projectc.arkofcosmos.mixin.infrastructure;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep native algorithms and substitute only config reads with a Rin-owned context. */
final class SatsukiConfigTransformer implements Opcodes {
    private static final String HOOK="net/everla/projectc/arkofcosmos/SatsukiConfig";
    private static final Map<String,String> KEYS=new HashMap<>();
    static {
        String[][] fields={
            {"APOLLYON_HEALTH","health"},{"HELLFIRE_DAMAGE_AMOUNT","hellfire_damage_amount"},{"HELLCLOUD_DAMAGE_AMOUNT","hellcloud_damage_amount"},
            {"apollyonBarrierPreparation","barrier_preparation"},{"CHANGE_TIME","barrier_ticks"},{"apollyonShootingCooldown","shooting_cooldown"},
            {"apollyon_nether_phase2DamageMultiplier","second_phase_damage_multiplier"},{"apollyon_nether_phaseGenesisDamageMultiplier","genesis_damage_multiplier"},
            {"apollyon_armorPiercing","armor_piercing"},{"apollyon_enchantmentPiercing","enchantment_piercing"},
            {"apollyonModpackMode","modpack_mode"},{"apollyonSelfHealing","self_healing"},{"apollyonArrowHealing","arrow_healing"},
            {"apollyonSecondPhaseArrowHealing","second_phase_arrow_healing"},{"netherTheDoomPercent","doom_percent"},
            {"barrierKillingMobsHealingMode","barrier_killing_mobs_healing"},{"powerfulServants","powerful_servants"}
        };
        for(var entry:fields)KEYS.put(entry[0],entry[1]);
        for(String dimension:List.of("Nether","NonNether"))for(String attribute:List.of("HurtLimitRatio","ProtectionLevel","ExtraArmor","ExtraToughness","AttackMultiplier","HealthMultiplier","KnockbackMultiplier","SpeedMultiplier")){
            String key=attribute.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            KEYS.put("apollyonServant"+dimension+attribute,"servants_"+(dimension.equals("Nether")?"nether":"non_nether")+"."+key);
        }
    }
    static boolean apply(ClassNode node){
        if(!(node.name.startsWith("com/Polarice3/Goety/")||node.name.startsWith("z1gned/goetyrevelation/")||node.name.startsWith("com/mega/revelationfix/")))return false;
        boolean changed=false;
        for(var method:node.methods){
            if(method.name.equals("<clinit>"))continue;
            int context=context(method,node.name.endsWith("/GRExtraCapability"));
            if(context<0)continue;
            for(var insn:method.instructions.toArray())if(insn instanceof FieldInsnNode field&&field.getOpcode()==GETSTATIC){
                if(!(field.owner.equals("z1gned/goetyrevelation/config/ModConfig")||field.owner.equals("com/mega/revelationfix/common/config/CommonConfig")
                        ||field.owner.equals("com/mega/revelationfix/common/config/ModpackCommonConfig")||field.owner.equals("com/mega/revelationfix/common/apollyon/common/DeathPerformance")))continue;
                String key=KEYS.get(field.name);if(key==null)continue;
                String type=field.desc,hook=switch(type){case "D"->"nativeDouble";case "F"->"nativeFloat";case "I"->"nativeInt";case "Z"->"nativeBoolean";default->null;};
                AbstractInsnNode after=field;
                if(hook==null){
                    var next=field.getNext();
                    if(!(next instanceof MethodInsnNode call&&call.name.equals("get")&&call.desc.equals("()Ljava/lang/Object;")))continue;
                    after=next;type="Ljava/lang/Object;";hook="nativeConfig";
                }
                InsnList filter=new InsnList();filter.add(new LdcInsnNode(key));filter.add(new VarInsnNode(ALOAD,context));
                filter.add(new MethodInsnNode(INVOKESTATIC,HOOK,hook,"("+type+"Ljava/lang/String;Ljava/lang/Object;)"+type,false));
                method.instructions.insert(after,filter);changed=true;
            }
        }
        return changed;
    }
    private static int context(MethodNode method,boolean entityArgument){
        boolean instance=(method.access&ACC_STATIC)==0;
        if(instance&&!entityArgument)return method.name.equals("<init>")?-1:0;
        var args=Type.getArgumentTypes(method.desc);int slot=instance?1:0,entity=-1;
        for(var arg:args){
            String name=arg.getDescriptor();
            if(name.equals("Lnet/minecraft/world/damagesource/DamageSource;"))return slot;
            if(entity<0&&(name.contains("/entities/boss/Apostle;")||name.equals("Lnet/minecraft/world/entity/Mob;")||name.equals("Lnet/minecraft/world/entity/LivingEntity;")||name.equals("Lnet/minecraft/world/entity/Entity;")||name.startsWith("Lnet/minecraftforge/event/entity/")))entity=slot;
            slot+=arg.getSize();
        }
        return entity;
    }
}
