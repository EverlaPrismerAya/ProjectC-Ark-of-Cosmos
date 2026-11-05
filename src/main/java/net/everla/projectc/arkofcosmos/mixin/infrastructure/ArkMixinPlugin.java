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
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
