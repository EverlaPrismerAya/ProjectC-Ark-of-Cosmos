import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check production annotation strings, including injectors supplied by MixinExtras. */
class MixinRefmapAudit {
    static void checkRfArgs(ClassNode node, boolean rfTarget) {
        if (!rfTarget) return;
        for (MethodNode method : node.methods)
            for (AnnotationNode a : annotations(method.visibleAnnotations, method.invisibleAnnotations))
                if (a.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;"))
                    throw new AssertionError(node.name + "." + method.name
                            + ": RF boss-bar transformation cannot load generated Args classes; use WrapOperation");
    }

    static Map<String, Object> values(AnnotationNode annotation) {
        Map<String, Object> result = new HashMap<>();
        if (annotation != null && annotation.values != null)
            for (int i = 0; i < annotation.values.size(); i += 2)
                result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return result;
    }

    static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    static void check(JsonObject refs, String selector, String context) {
        String member = (selector.startsWith("L") ? selector.substring(selector.indexOf(';') + 1) : selector)
                .split("[(:]", 2)[0];
        if (member.startsWith("<") || member.matches("[mf]_\\d+_")) return;
        if (!refs.has(selector)) throw new AssertionError(context + ": missing SRG refmap entry for " + selector);
        String mapped = refs.get(selector).getAsString();
        if (!mapped.matches(".*[mf]_\\d+_.*"))
            throw new AssertionError(context + ": unmapped SRG selector " + selector + " -> " + mapped);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        // Regression: the rejected injector previously built successfully but crashed when a boss bar first rendered.
        ClassNode regression = new ClassNode();
        regression.name = "RfArgsRegression";
        MethodNode callback = new MethodNode();
        callback.visibleAnnotations = List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;"));
        regression.methods.add(callback);
        boolean rejected = false;
        try { checkRfArgs(regression, true); } catch (AssertionError expected) { rejected = true; }
        if (!rejected) throw new AssertionError("RF ModifyArgs regression guard did not reject unsafe injector");
        checkRfArgs(regression, false);
        for (String path : args) try (ZipFile jar = new ZipFile(path)) {
            JsonObject config, refs;
            try (Reader r = new InputStreamReader(jar.getInputStream(jar.getEntry("arkofcosmos.mixins.json")))) {
                config = JsonParser.parseReader(r).getAsJsonObject();
            }
            try (Reader r = new InputStreamReader(jar.getInputStream(jar.getEntry("arkofcosmos.refmap.json")))) {
                refs = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("mappings");
            }
            int mixins = 0, extras = 0, selectors = 0;
            for (String side : List.of("mixins", "client")) for (JsonElement item : config.getAsJsonArray(side)) {
                String name = (config.get("package").getAsString() + "." + item.getAsString()).replace('.', '/');
                ClassNode node = new ClassNode();
                try (InputStream in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in).accept(node, 0); }
                boolean remap = true, vanilla = false, rfTarget = false;
                for (AnnotationNode a : annotations(node.visibleAnnotations, node.invisibleAnnotations))
                    if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        Map<String, Object> v = values(a);
                        remap = !Boolean.FALSE.equals(v.get("remap"));
                        for (Type t : (List<Type>) v.getOrDefault("value", List.of()))
                            vanilla |= t.getInternalName().startsWith("net/minecraft/");
                        for (String target : (List<String>) v.getOrDefault("targets", List.of()))
                            rfTarget |= target.equals("com.mega.revelationfix.client.screen.bossbar.ApollyonBossbar")
                                    || target.equals("com.Polarice3.Goety.client.events.BossBarEvent");
                    }
                checkRfArgs(node, rfTarget);
                JsonObject mappings = refs.has(name) ? refs.getAsJsonObject(name) : new JsonObject();
                for (MethodNode method : node.methods)
                    for (AnnotationNode a : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                        Map<String, Object> v = values(a);
                        if (!v.containsKey("method") || !v.containsKey("at")) continue;
                        if (a.desc.startsWith("Lcom/llamalad7/mixinextras/")) extras++;
                        boolean auto = (boolean) v.getOrDefault("remap", remap);
                        String context = name + "." + method.name;
                        if (auto && vanilla) for (String selector : (List<String>) v.get("method")) {
                            check(mappings, selector, context); selectors++;
                        }
                        Object at = v.get("at");
                        List<AnnotationNode> sites = at instanceof List<?> ? (List<AnnotationNode>) at : List.of((AnnotationNode) at);
                        for (AnnotationNode site : sites) {
                            Map<String, Object> av = values(site);
                            String target = (String) av.get("target");
                            if (auto && !Boolean.FALSE.equals(av.get("remap")) && target != null && target.startsWith("Lnet/minecraft/")) {
                                check(mappings, target, context); selectors++;
                            }
                        }
                    }
                mixins++;
            }
            System.out.printf("Verified %s: %d configured Mixins, %d MixinExtras injections, %d SRG selectors%n", path, mixins, extras, selectors);
        }
    }
}
