package net.everla.projectc.arkofcosmos.mixin;

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
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
