package net.everla.projectc.arkofcosmos.mixin.infrastructure;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.EnumSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Match the vanilla method contracts in every class, without mod IDs or entity names. */
public final class ArkHealthTransformer implements ILaunchPluginService, Opcodes {
    private static final String HOOK = "net/everla/projectc/arkofcosmos/combat/ArkDamage";
    private static final String HEALING = "net/everla/projectc/arkofcosmos/combat/BossHealingLock";

    @Override public String name() { return "ark_health"; }
    @Override public EnumSet<Phase> handlesClass(Type type, boolean empty) {
        return empty || type.getInternalName().startsWith("net/everla/projectc/arkofcosmos/mixin/")
                ? EnumSet.noneOf(Phase.class) : EnumSet.of(Phase.AFTER);
    }

    @Override public boolean processClass(Phase phase, ClassNode node, Type type) {
        boolean changed = BossDamageTransformer.apply(node);
        changed |= SatsukiConfigTransformer.apply(node);
        for (MethodNode method : node.methods) {
            // Observe only a completed vanilla health read. Float filters may also process offsets.
            // Skip internal super-getter calls: those can return an intermediate backing value.
            if (!(method.desc.equals("()F") && named(method,"getHealth","m_21223_"))) {
                for (var instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode call) || call.getOpcode()==INVOKESTATIC
                            || !call.desc.equals("()F")
                            || !(call.name.equals("getHealth") || call.name.equals("m_21223_"))) continue;
                    var next=call.getNext();
                    if(next!=null && next.getOpcode()==SWAP && next.getNext() instanceof MethodInsnNode observer
                            && observer.owner.equals(HEALING) && observer.name.equals("filterHealth")) continue;
                    method.instructions.insertBefore(call,new InsnNode(DUP));
                    InsnList observe=new InsnList();observe.add(new InsnNode(SWAP));
                    observe.add(new MethodInsnNode(INVOKESTATIC,HEALING,"filterHealth","(FLjava/lang/Object;)F",false));
                    method.instructions.insert(call,observe);changed=true;
                }
            }
            if ((method.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
            String hook = null;
            if (method.desc.equals("()F") && named(method, "getHealth", "m_21223_")) hook = "health";
            if (method.desc.equals("()Z") && named(method, "isAlive", "m_6084_")) hook = "alive";
            if (method.desc.equals("()Z") && named(method, "isDeadOrDying", "m_21224_")) hook = "dead";
            boolean setter = method.desc.equals("(F)V") && named(method, "setHealth", "m_21153_");
            if (hook == null && !setter) continue;
            boolean installed = false;
            for (AbstractInsnNode instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(HOOK)) installed = true;
            if (installed) continue;
            InsnList guard = new InsnList();
            LabelNode original = new LabelNode();
            guard.add(new VarInsnNode(ALOAD, 0));
            if (setter) {
                guard.add(new VarInsnNode(FLOAD, 1));
                guard.add(new MethodInsnNode(INVOKESTATIC, HOOK, "setHealth", "(Ljava/lang/Object;F)Z", false));
                guard.add(new JumpInsnNode(IFEQ, original));
                guard.add(new InsnNode(RETURN));
                guard.add(original);
            } else {
                boolean floating = hook.equals("health");
                guard.add(new MethodInsnNode(INVOKESTATIC, HOOK, hook,
                        floating ? "(Ljava/lang/Object;)F" : "(Ljava/lang/Object;)I", false));
                guard.add(new InsnNode(DUP));
                if (floating) {
                    guard.add(new InsnNode(FCONST_0));
                    guard.add(new InsnNode(FCMPL));
                }
                guard.add(new JumpInsnNode(IFLT, original));
                guard.add(new InsnNode(floating ? FRETURN : IRETURN));
                guard.add(original);
                guard.add(new InsnNode(POP));
            }
            method.instructions.insert(guard);
            changed = true;
        }
        return changed;
    }

    private static boolean named(MethodNode method, String dev, String srg) {
        return method.name.equals(dev) || method.name.equals(srg);
    }
}
