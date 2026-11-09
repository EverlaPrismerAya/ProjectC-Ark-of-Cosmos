import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Match Forge IEventListenerFactory.getUniqueName, including nested-class simple names. */
class EventSubscriberAudit {
    public static void main(String[] args) throws Exception {
        for (String path : args) {
            Map<String,String> handlers = new TreeMap<>();
            try (JarFile jar = new JarFile(path)) {
                for (var entry : Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class")) continue;
                    ClassNode type = new ClassNode();
                    try (var input = jar.getInputStream(entry)) {
                        new ClassReader(input).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    }
                    String simple = simpleName(type.name);
                    for (var inner : type.innerClasses)
                        if (inner.name.equals(type.name) && inner.innerName != null) simple = inner.innerName;
                    for (var method : type.methods) {
                        if (method.visibleAnnotations == null || method.visibleAnnotations.stream().noneMatch(a -> a.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;"))) continue;
                        Type[] parameters = Type.getArgumentTypes(method.desc);
                        if (parameters.length != 1) throw new IllegalStateException("Invalid event handler: " + type.name + "." + method.name);
                        String key = type.name.substring(0,type.name.lastIndexOf('/')+1) + "__" + simple + "_" + method.name + "_" + simpleName(parameters[0].getInternalName());
                        String owner = type.name + "." + method.name + method.desc;
                        String previous = handlers.putIfAbsent(key,owner);
                        if (previous != null && !previous.equals(owner)) throw new IllegalStateException("Forge event wrapper collision: " + key + "\n" + previous + "\n" + owner);
                    }
                }
            }
            System.out.println("Verified " + handlers.size() + " unique Forge event handlers in " + path);
        }
    }
    private static String simpleName(String name) {
        return name.substring(Math.max(name.lastIndexOf('/'),name.lastIndexOf('$'))+1);
    }
}
