package net.everla.projectc.arkofcosmos.mixin.infrastructure;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Register after the existing launch plugins so guards see methods added by Mixins too. */
public final class ArkMixinPlugin implements IMixinConfigPlugin {
    private Boolean goetyBossBarCompatible;
    private Boolean legacyApollyon;
    @Override public void onLoad(String mixinPackage) {
        try {
            Field field = Launcher.class.getDeclaredField("launchPlugins");
            field.setAccessible(true);
            Object handler = field.get(Launcher.INSTANCE);
            field = handler.getClass().getDeclaredField("plugins");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>)field.get(handler);
            plugins.putIfAbsent("ark_health", new ArkHealthTransformer());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install universal Ark health guards", failure);
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if(mixin.endsWith(".SatsukiLegacyBossBarMixin")) {
            var provider=org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider();
            try {
                provider.getClassNode("com.mega.revelationfix.client.screen.bossbar.ApollyonBossbar");
                return false;
            } catch(java.io.IOException|ClassNotFoundException oldVersion) {
                try {
                    var node=provider.getClassNode("com.mega.revelationfix.mixin.goety.BossBarEventMixin");
                    return node.methods.stream().anyMatch(m->m.name.equals("revelationfix$getPercent"));
                } catch(java.io.IOException|ClassNotFoundException absent) { return false; }
            }
        }
        if(mixin.contains(".LegacyApollyon")) {
            if(legacyApollyon!=null)return legacyApollyon;
            try {
                var provider=org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider();
                ClassNode old=provider.getClassNode("com.mega.revelationfix.mixin.goety.ApollyonMixin");
                legacyApollyon=old.fields.stream().anyMatch(f->f.name.equals("HIT_COOLDOWN2"));
                if(legacyApollyon)try {
                    provider.getClassNode("com.mega.revelationfix.common.apollyon.common.ApollyonDamageWindow");
                    legacyApollyon=false;
                } catch(java.io.IOException|ClassNotFoundException absent) { /* Backport only when upstream's fix is absent. */ }
                return legacyApollyon;
            } catch(java.io.IOException|ClassNotFoundException absent) { return legacyApollyon=false; }
        }
        if(mixin.endsWith(".WitchModernTouchSoulMixin")||mixin.endsWith(".WitchLegacyTouchSoulMixin")) {
            String descriptor="(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;"
                    +(mixin.endsWith(".WitchModernTouchSoulMixin")?"Lnet/minecraft/world/entity/LivingEntity;":"")+")Z";
            try {
                ClassNode node=org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(target);
                return node.methods.stream().anyMatch(m->m.name.equals("canCastTouch")&&m.desc.equals(descriptor));
            } catch(java.io.IOException|ClassNotFoundException unavailable) { return false; }
        }
        if(!mixin.endsWith(".client.gui.GoetyBossBarMixin"))return true;
        if(goetyBossBarCompatible!=null)return goetyBossBarCompatible;
        try {
            ClassNode node=org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(target);
            if(bossBarContract(node))return goetyBossBarCompatible=true;
        } catch(java.io.IOException|ClassNotFoundException unavailable) {
            // Optional integration: inspect bytecode without initializing Goety classes.
        }
        org.slf4j.LoggerFactory.getLogger(ArkMixinPlugin.class).warn(
                "Skipping Ark Goety boss-bar integration: {} is absent or has an incompatible field/method contract",target);
        return goetyBossBarCompatible=false;
    }
    private static boolean bossBarContract(ClassNode node) {
        boolean field=node.fields.stream().anyMatch(f->f.name.equals("BOSS_BARS")
                &&f.desc.equals("Ljava/util/Map;")&&(f.access&org.objectweb.asm.Opcodes.ACC_STATIC)!=0);
        boolean method=node.methods.stream().anyMatch(m->m.name.equals("renderBossBar")
                &&m.desc.equals("(Lnet/minecraftforge/client/event/CustomizeGuiOverlayEvent$BossEventProgress;)V")
                &&(m.access&(org.objectweb.asm.Opcodes.ACC_STATIC|org.objectweb.asm.Opcodes.ACC_ABSTRACT|org.objectweb.asm.Opcodes.ACC_NATIVE))
                    ==org.objectweb.asm.Opcodes.ACC_STATIC);
        return field&&method;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if(mixin.endsWith(".SatsukiDamageMixin")) {
            // Bare GR resets the doom target's max-health base directly in its merged callback.
            // RF replaces that callback; preserve every other part of either version's tick.
            for(var method:node.methods)if(method.name.endsWith("$tickAbility"))
                for(var insn:method.instructions.toArray())if(insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                        &&call.owner.equals("net/minecraft/world/entity/ai/attributes/AttributeInstance")
                        &&call.desc.equals("(D)V")&&(call.name.equals("setBaseValue")||call.name.equals("m_22100_"))) {
                    method.instructions.insertBefore(call,new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD,0));
                    method.instructions.set(call,new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                            "net/everla/projectc/arkofcosmos/combat/SatsukiBoss","doomAttribute",
                            "(Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;DLjava/lang/Object;)V",true));
                }
        }
        if(!mixin.endsWith(".LegacyApollyonLivingMixin"))return;
        int replaced=0;
        // These two old GR/RF callbacks only affect lower Apollyon: one rejects every
        // hit while cooling down, the other starts cooldown before any health is lost.
        // Mixin renames merged callbacks, so identify their origin instead of a hash.
        for(var method:node.methods) {
            if(!(method.name.endsWith("$canHurt")||method.name.endsWith("$setCooldown")))continue;
            if(method.visibleAnnotations==null)continue;
            for(var annotation:method.visibleAnnotations) {
                if(!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;"))continue;
                boolean oldOrigin=false;
                for(int i=0;i<annotation.values.size();i+=2)
                    if(annotation.values.get(i).equals("mixin")) {
                        String origin=String.valueOf(annotation.values.get(i+1));
                        oldOrigin=origin.equals("com.mega.revelationfix.mixin.gr.LivingEntityMixin")
                                ||origin.equals("z1gned.goetyrevelation.mixin.LivingEntityMixin");
                    }
                if(!oldOrigin)continue;
                method.instructions.clear();
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
                method.tryCatchBlocks.clear();
                if(method.localVariables!=null)method.localVariables.clear();
                replaced++;
            }
        }
        if(replaced<2)throw new IllegalStateException("Legacy Apollyon cooldown callbacks changed: found "+replaced);
    }
}
