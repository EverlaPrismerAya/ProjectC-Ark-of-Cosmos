package net.everla.projectc.arkofcosmos.mixin;

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Recognize health ceilings and direct-damage adapters by vanilla types and bytecode. */
final class BossDamageTransformer implements Opcodes {
    private static final String LIVING="Lnet/minecraft/world/entity/LivingEntity;";
    private static final String SOURCE="Lnet/minecraft/world/damagesource/DamageSource;";
    private static final String DATA="Lnet/minecraft/network/syncher/EntityDataAccessor;";
    private static final String HOOK="net/everla/projectc/arkofcosmos/combat/BossDamageBridge";
    private static final String HEALING="net/everla/projectc/arkofcosmos/combat/BossHealingLock";

    static boolean apply(ClassNode node){
        if(node.name.startsWith("net/everla/projectc/arkofcosmos/"))return false;
        boolean changed=false;
        Set<String> readers=new HashSet<>();
        // A float health filter receives the original health and an arbitrary entity.
        for(var method:node.methods)if((method.access&ACC_STATIC)!=0&&method.desc.equals("(FLjava/lang/Object;)F")){
            boolean ceiling=false;
            for(var insn:method.instructions)if(insn instanceof MethodInsnNode call
                    &&call.owner.equals("net/minecraft/world/entity/LivingEntity")
                    &&(call.name.equals("getMaxHealth")||call.name.equals("m_21233_")))ceiling=true;
            if(ceiling){
                boolean installed=false;
                for(var insn:method.instructions){
                    if(insn instanceof MethodInsnNode call&&call.owner.equals(HEALING))installed=true;
                    if(insn instanceof MethodInsnNode call&&call.owner.equals(node.name)
                            &&call.desc.equals("("+LIVING+")F"))readers.add(call.name);
                }
                if(!installed)for(var insn:method.instructions.toArray())if(insn.getOpcode()==FRETURN){
                    InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,1));
                    guard.add(new MethodInsnNode(INVOKESTATIC,HEALING,"filterHealth","(FLjava/lang/Object;)F",false));
                    method.instructions.insertBefore(insn,guard);changed=true;
                }
            }
        }
        Set<String> offsets=new HashSet<>();
        for(var method:node.methods)if(readers.contains(method.name)&&method.desc.equals("("+LIVING+")F"))
            for(var insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==GETSTATIC
                    &&field.owner.equals(node.name)&&field.desc.equals(DATA))offsets.add(field.name);

        Set<String> adapters=new HashSet<>();
        boolean added;
        do {
            added=false;
            for(var method:node.methods){
                if((method.access&(ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE))!=0
                        ||!(method.desc.equals("("+SOURCE+"F)V")||method.desc.equals("("+SOURCE+"FZ)V")))continue;
                for(var insn:method.instructions)if(insn instanceof MethodInsnNode call
                        &&(call.owner.equals("net/minecraft/world/entity/LivingEntity")&&call.desc.equals("(F)V")
                            &&(call.name.equals("setHealth")||call.name.equals("m_21153_"))
                        ||call.owner.equals(node.name)&&adapters.contains(call.name+call.desc)))
                    added|=adapters.add(method.name+method.desc);
            }
        }while(added);
        for(var method:node.methods){
            boolean installed=false;
            for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(HOOK))installed=true;
            if(installed)continue;
            FieldInsnNode offset=null;
            for(var insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==GETSTATIC
                    &&field.owner.equals(node.name)&&offsets.contains(field.name))offset=field;
            boolean read=readers.contains(method.name)&&method.desc.equals("("+LIVING+")F");
            boolean write=method.desc.equals("("+LIVING+"F)V");
            if((method.access&ACC_STATIC)!=0&&offset!=null&&(read||write)){
                InsnList guard=new InsnList();LabelNode original=new LabelNode();
                guard.add(new VarInsnNode(ALOAD,0));
                guard.add(new FieldInsnNode(GETSTATIC,node.name,offset.name,DATA));
                if(write)guard.add(new VarInsnNode(FLOAD,1));
                guard.add(new MethodInsnNode(INVOKESTATIC,HOOK,read?"readOffset":"offset",
                        read?"(Ljava/lang/Object;Ljava/lang/Object;)Z":"(Ljava/lang/Object;Ljava/lang/Object;F)Z",false));
                guard.add(new JumpInsnNode(IFEQ,original));
                if(read)guard.add(new InsnNode(FCONST_0));
                guard.add(new InsnNode(read?FRETURN:RETURN));guard.add(original);
                method.instructions.insert(guard);changed=true;
            }

            // A utility holding exactly one target and directly setting its health is a damage adapter.
            if((method.access&(ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE))!=0
                    ||!(method.desc.equals("("+SOURCE+"F)V")||method.desc.equals("("+SOURCE+"FZ)V")))continue;
            FieldNode target=null;int count=0;
            for(var field:node.fields)if((field.access&ACC_STATIC)==0&&field.desc.equals(LIVING)){target=field;count++;}
            if(count!=1)continue;
            if(!adapters.contains(method.name+method.desc))continue;
            InsnList guard=new InsnList();LabelNode original=new LabelNode();
            guard.add(new VarInsnNode(ALOAD,0));guard.add(new FieldInsnNode(GETFIELD,node.name,target.name,LIVING));
            guard.add(new VarInsnNode(ALOAD,1));guard.add(new VarInsnNode(FLOAD,2));
            guard.add(new MethodInsnNode(INVOKESTATIC,HOOK,"damage","(Ljava/lang/Object;Ljava/lang/Object;F)Z",false));
            guard.add(new JumpInsnNode(IFEQ,original));guard.add(new InsnNode(RETURN));guard.add(original);
            method.instructions.insert(guard);changed=true;
        }
        return changed;
    }
}
