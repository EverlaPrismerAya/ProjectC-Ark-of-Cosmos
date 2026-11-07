package net.everla.projectc.arkofcosmos.enchant;

import com.Polarice3.Goety.common.events.spell.ChangeSoulEnergyEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;


public final class WitchSoulEvents {
    private WitchSoulEvents() {}
    private record Payment(net.minecraft.world.entity.player.Player player,net.minecraft.world.item.ItemStack item) {}
    private static final ThreadLocal<Payment> PAYMENT=new ThreadLocal<>();
    private record Debit(net.minecraft.world.entity.player.Player player,int before,boolean cursed) {}
    private static final ThreadLocal<java.util.Deque<Debit>> DEBITS=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    public static void begin(net.minecraft.world.entity.player.Player p){var payment=PAYMENT.get();DEBITS.get().push(new Debit(p,WitchSouls.current(p),payment!=null&&payment.player()==p&&Curse.of(payment.item())==Curse.OBLATORY&&WitchSouls.active(p)));}
    public static void complete(net.minecraft.world.entity.player.Player p){var debits=DEBITS.get();if(debits.isEmpty())return;var debit=debits.pop();if(debits.isEmpty())DEBITS.remove();if(debit.player()==p&&debit.cursed()&&WitchSouls.current(p)<debit.before()&&p.getRandom().nextBoolean())CurseCombat.selfDamage(p,5);}
    public static <T> T casting(net.minecraft.world.entity.player.Player p,net.minecraft.world.item.ItemStack item,java.util.function.Supplier<T> operation){
        Payment previous=PAYMENT.get();PAYMENT.set(new Payment(p,item));
        try{return operation.get();}finally{if(previous==null)PAYMENT.remove();else PAYMENT.set(previous);}
    }
    public static void spell(net.minecraft.world.entity.player.Player p,net.minecraft.world.item.ItemStack item,Runnable operation){casting(p,item,()->{operation.run();return null;});}
    @SubscribeEvent public static void casting(com.Polarice3.Goety.common.events.spell.CastingMagicEvent event){if(event.getEntity() instanceof net.minecraft.world.entity.player.Player p)CurseCombat.source(p,event.getUseItem());}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void loss(ChangeSoulEnergyEvent.Loss event){
        var p=event.getEntity();var context=PAYMENT.get();
        if(context==null || context.player()!=p || p.level().isClientSide || Curse.of(context.item())!=Curse.OBLATORY || !WitchSouls.active(p) || event.getSoulChange()<=0)return;
        event.setSoulChange(CurseMath.soulCost(event.getSoulChange()));
    }
}
