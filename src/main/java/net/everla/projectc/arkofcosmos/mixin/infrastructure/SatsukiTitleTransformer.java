package net.everla.projectc.arkofcosmos.mixin.infrastructure;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * In GR / RF classes, rewrite every "title == 12" comparison that is the second half of a
 * "title == k || title == 12" ability check (k not 0, 12 or 13) to compare SatsukiTitles.unify(title, apostle).
 * Works for direct allTitleApostle$getTitleNumber() calls and for locals stored from them (the receiver is kept in a
 * fresh local). Standalone 12 / 13 checks (their own abilities) are not matched.
 */
final class SatsukiTitleTransformer implements Opcodes {
    private static final String GET="allTitleApostle$getTitleNumber",HOOK="net/everla/projectc/arkofcosmos/combat/SatsukiTitles";
    private SatsukiTitleTransformer(){}

    static boolean apply(ClassNode node){
        // GR/RF mixin code is merged into Goety classes at runtime, so Goety targets are scanned too.
        if(!(node.name.startsWith("com/Polarice3/Goety/")||node.name.startsWith("z1gned/goetyrevelation/")||node.name.startsWith("com/mega/revelationfix/")))return false;
        boolean changed=false;
        for(MethodNode method:node.methods){
            if(method.name.equals(GET)||method.name.startsWith("<")||method.instructions.size()==0)continue;
            changed|=method(method);
        }
        return changed;
    }

    private static boolean method(MethodNode method){
        AbstractInsnNode[] insns=method.instructions.toArray();
        // Title locals: "getTitleNumber(); ISTORE n" -> receiver kept in a fresh slot.
        Map<Integer,Integer> receivers=new LinkedHashMap<>();
        boolean any=false;
        for(var insn:insns)if(isGet(insn)){
            any=true;
            if(real(insn.getNext()) instanceof VarInsnNode store&&store.getOpcode()==ISTORE)receivers.putIfAbsent(store.var,-1);
        }
        if(!any)return false;
        int next=method.maxLocals;
        for(var key:receivers.keySet())receivers.put(key,next++);
        List<AbstractInsnNode> sites=new ArrayList<>();
        for(var insn:insns){
            if(!titleValue(insn,receivers)||!Integer.valueOf(12).equals(constant(real(insn.getNext()))))continue;
            var jump=real(real(insn.getNext()).getNext());
            if(!(jump instanceof JumpInsnNode j&&(j.getOpcode()==IF_ICMPEQ||j.getOpcode()==IF_ICMPNE)))continue;
            if(orPartner(insn,receivers))sites.add(insn);
        }
        if(sites.isEmpty())return false;
        boolean locals=false;
        for(var site:sites){
            if(isGet(site)){
                method.instructions.insertBefore(site,new InsnNode(DUP));
                InsnList after=new InsnList();after.add(new InsnNode(SWAP));after.add(unify());
                method.instructions.insert(site,after);
            }else{
                InsnList after=new InsnList();after.add(new VarInsnNode(ALOAD,receivers.get(((VarInsnNode)site).var)));after.add(unify());
                method.instructions.insert(site,after);locals=true;
            }
        }
        if(locals){
            // Definitely assign the receiver slots, then record the receiver at every title store.
            InsnList init=new InsnList();
            for(int slot:receivers.values()){init.add(new InsnNode(ACONST_NULL));init.add(new VarInsnNode(ASTORE,slot));}
            method.instructions.insert(init);
            for(var insn:insns)if(isGet(insn)&&real(insn.getNext()) instanceof VarInsnNode store&&store.getOpcode()==ISTORE){
                InsnList keep=new InsnList();keep.add(new InsnNode(DUP));keep.add(new VarInsnNode(ASTORE,receivers.get(store.var)));
                method.instructions.insertBefore(insn,keep);
            }
            method.maxLocals=next;
        }
        return true;
    }

    /** The preceding comparison (within a few instructions) tests the same title against another ability title k. */
    private static boolean orPartner(AbstractInsnNode site,Map<Integer,Integer> receivers){
        AbstractInsnNode cursor=site.getPrevious();
        for(int steps=0;cursor!=null&&steps<10;cursor=cursor.getPrevious()){
            if(cursor.getOpcode()<0)continue;steps++;
            if(cursor instanceof JumpInsnNode j&&(j.getOpcode()==IF_ICMPEQ||j.getOpcode()==IF_ICMPNE)){
                var push=realBack(j.getPrevious());Integer k=push==null?null:constant(push);
                var value=push==null?null:realBack(push.getPrevious());
                return k!=null&&k!=0&&k!=12&&k!=13&&value!=null&&titleValue(value,receivers);
            }
        }
        return false;
    }
    private static boolean titleValue(AbstractInsnNode insn,Map<Integer,Integer> receivers){
        return isGet(insn)||insn instanceof VarInsnNode load&&load.getOpcode()==ILOAD&&receivers.containsKey(load.var);
    }
    private static boolean isGet(AbstractInsnNode insn){
        return insn instanceof MethodInsnNode call&&call.getOpcode()!=INVOKESTATIC&&call.name.equals(GET)&&call.desc.equals("()I");
    }
    private static MethodInsnNode unify(){return new MethodInsnNode(INVOKESTATIC,HOOK,"unify","(ILjava/lang/Object;)I",false);}
    private static Integer constant(AbstractInsnNode insn){
        if(insn==null)return null;
        int op=insn.getOpcode();
        if(op>=ICONST_M1&&op<=ICONST_5)return op-ICONST_0;
        if((op==BIPUSH||op==SIPUSH)&&insn instanceof IntInsnNode i)return i.operand;
        return null;
    }
    private static AbstractInsnNode real(AbstractInsnNode insn){while(insn!=null&&insn.getOpcode()<0)insn=insn.getNext();return insn;}
    private static AbstractInsnNode realBack(AbstractInsnNode insn){while(insn!=null&&insn.getOpcode()<0)insn=insn.getPrevious();return insn;}
}
