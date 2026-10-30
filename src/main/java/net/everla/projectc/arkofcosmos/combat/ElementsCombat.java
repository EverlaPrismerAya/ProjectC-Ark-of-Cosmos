package net.everla.projectc.arkofcosmos.combat;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.calamity.combat.ElementsRules;
import net.everla.projectc.arkofcosmos.entity.ElementsEntity;
import net.everla.projectc.arkofcosmos.item.ElementsItem;
import net.everla.projectc.arkofcosmos.network.ArkNetwork;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=ArkOfCosmos.MOD_ID)
public final class ElementsCombat {
    private ElementsCombat() {}
    private static final Map<UUID,Controls> CONTROLS=new HashMap<>();
    private static final class Controls {
        boolean left,right,parried; int cooldown,use,idle; long invulnerableUntil;
        ItemStack parryStack=ItemStack.EMPTY;
    }
    public static boolean holding(Player p){return p.getMainHandItem().is(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get());}
    public static boolean channeling(Player p){Controls c=CONTROLS.get(p.getUUID());return holding(p)&&p.isAlive()&&!p.isSpectator()&&c!=null&&c.left;}
    public static int cooldown(Player p){Controls c=CONTROLS.get(p.getUUID());return c==null?0:c.cooldown;}
    public static boolean active(Player p){return !p.level().getEntitiesOfClass(ElementsEntity.class,p.getBoundingBox().inflate(80),e->e.ownerId()==p.getId()&&(e.kind()==ElementsEntity.BLADE||e.kind()==ElementsEntity.BLAST)).isEmpty();}
    public static void input(ServerPlayer p,ArkNetwork.Input input){
        Controls c=CONTROLS.get(p.getUUID());
        if(c==null){if(!holding(p))return;c=new Controls();CONTROLS.put(p.getUUID(),c);}
        boolean left=input.left()&&!c.left,right=input.right()&&!c.right;
        c.left=holding(p)&&input.left();c.right=holding(p)&&input.right();c.idle=0;
        if(!holding(p)||!p.isAlive()||p.isSpectator())return;
        if(right&&!active(p)&&c.use==0){
            ItemStack stack=p.getMainHandItem();int charge=ElementsItem.charge(stack);
            if(input.alt()&&charge>0){
                ElementsEntity.create(p,ElementsEntity.BLAST,0,charge,ElementsRules.blastDamage(charge,0),p.getBoundingBox().getCenter().add(p.getLookAngle().scale(90/16d)),Vec3.ZERO,AttackFrame.capture(p));
                ElementsItem.charge(stack,0);c.use=20;
            }else if(c.cooldown==0){
                c.parried=false;c.parryStack=stack;c.invulnerableUntil=p.level().getGameTime()+5;
                c.cooldown=340;c.use=20;stack.getOrCreateTag().putInt("ElementsCooldown",340);
                ElementsEntity.create(p,ElementsEntity.PARRY,0,0,7,p.getBoundingBox().getCenter(),Vec3.ZERO,AttackFrame.capture(p));
            }
        }
        if(left)attack(p,c);
    }
    private static void attack(ServerPlayer p,Controls c){
        if(c.use>0||active(p))return;
        ItemStack stack=p.getMainHandItem();int combo=Math.floorMod(stack.getOrCreateTag().getInt("ElementsCombo"),5),mode=ElementsRules.mode(combo),charge=ElementsItem.charge(stack);
        float damage=ElementsRules.charged(7,charge);AttackFrame f=AttackFrame.capture(p);Vec3 at=p.getBoundingBox().getCenter();
        ElementsEntity.create(p,ElementsEntity.BLADE,mode,charge,damage,at,f.forward(),f);
        if(mode==1){
            ElementsEntity.create(p,ElementsEntity.NEEDLE,0,charge,damage*.8f,at.add(f.forward().scale(20/16d)),f.forward().scale(16*2.8/16d),f);
            for(int side:new int[]{-1,1})for(int i=0;i<2;i++)
                ElementsEntity.create(p,ElementsEntity.STAR,0,0,damage*.2f,at.add(f.right().scale(side*20/16d*(i==0?1:1.2))),f.direction(side*(i==0?.2356194704771042:.3141592741012573)).scale(i==0?1:.8),f);
        }
        stack.getOrCreateTag().putInt("ElementsCombo",(combo+1)%5);ElementsItem.charge(stack,ElementsRules.afterAttack(charge));c.use=20;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        Controls c=CONTROLS.get(p.getUUID());if(c==null)return;
        int old=c.cooldown;c.cooldown=Math.max(0,c.cooldown-3);c.use=Math.max(0,c.use-3);
        if(old>0&&c.cooldown==0)sound(p,p.getId(),"item35",1,true,true);
        if(++c.idle>40||!holding(p)||!p.isAlive()||p.isSpectator()){c.left=c.right=false;c.invulnerableUntil=0;return;}
        if(c.left)attack(p,c);
    }
    public static boolean parried(Player p){Controls c=CONTROLS.get(p.getUUID());return c!=null&&c.parried;}
    public static void parrySuccess(Player p){
        Controls c=CONTROLS.get(p.getUUID());if(c==null||c.parried)return;c.parried=true;
        if(c.parryStack.is(ArkOfCosmos.ARK_OF_THE_ELEMENTS.get())){ElementsItem.charge(c.parryStack,10);c.parryStack.getOrCreateTag().putInt("ElementsCombo",0);}
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.arkofcosmos.parry"),true);
        sound(p,p.getId(),"crystal",1,false,true);
        var parry=p.level().getEntitiesOfClass(ElementsEntity.class,p.getBoundingBox().inflate(8),a->a.ownerId()==p.getId()&&a.kind()==ElementsEntity.PARRY).stream().findFirst();
        sound(p,p.getId(),"snap",1,false,false,parry.map(ElementsEntity::position).orElse(p.getBoundingBox().getCenter()));
        CompoundTag fx=new CompoundTag();fx.putString("Event","elements_parry");fx.putInt("Owner",p.getId());ElementsEntity.vector(fx,"At",p.getBoundingBox().getCenter());ArkNetwork.effect(p,fx);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void incoming(LivingAttackEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getAmount()<=0||!holding(p))return;
        Controls c=CONTROLS.get(p.getUUID());if(c!=null&&p.level().getGameTime()<c.invulnerableUntil){e.setCanceled(true);parrySuccess(p);}
    }
    /** Elapsed time belongs to the target, so refreshes cannot postpone or duplicate pulses. */
    @SubscribeEvent public static void resonance(LivingEvent.LivingTickEvent e){
        LivingEntity t=e.getEntity();if(t.level().isClientSide)return;
        CompoundTag data=t.getPersistentData();
        if(!t.hasEffect(ArkOfCosmos.ELEMENTAL_MIX.get())){data.remove("ElementsDotElapsed");data.remove("ElementsDotOwner");return;}
        CompoundTag visual=new CompoundTag();visual.putString("Event","elements_resonance");visual.putInt("Target",t.getId());
        net.everla.projectc.arkofcosmos.network.ArkNetwork.effect(t,visual);
        int elapsed=data.getInt("ElementsDotElapsed")+1;data.putInt("ElementsDotElapsed",elapsed);
        if(ElementsRules.dotDue(elapsed)){
            if(t instanceof Player p&&(p.isCreative()||p.isSpectator()))return;
            var source=t.damageSources().magic();
            if(data.hasUUID("ElementsDotOwner")&&t.level() instanceof net.minecraft.server.level.ServerLevel level){var owner=level.getPlayerByUUID(data.getUUID("ElementsDotOwner"));if(owner!=null)source=t.damageSources().indirectMagic(owner,owner);}
            int cooldown=t.invulnerableTime;t.invulnerableTime=0;
            try{t.hurt(source,1);}finally{t.invulnerableTime=Math.max(cooldown,t.invulnerableTime);}
        }
    }
    public static void applyResonance(LivingEntity t,Player owner,int ticks){
        t.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArkOfCosmos.ELEMENTAL_MIX.get(),ticks,0,false,false,true),owner);
        t.getPersistentData().putUUID("ElementsDotOwner",owner.getUUID());
    }
    public static void sound(net.minecraft.world.entity.Entity at,int owner,String name,float volume,boolean ownerOnly,boolean relative){
        sound(at,owner,name,volume,ownerOnly,relative,at.position());
    }
    public static void sound(net.minecraft.world.entity.Entity at,int owner,String name,float volume,boolean ownerOnly,boolean relative,Vec3 position){
        CompoundTag data=new CompoundTag();data.putString("Event","elements_sound");data.putString("Sound",name);data.putInt("Owner",owner);data.putFloat("Volume",volume);data.putBoolean("OwnerOnly",ownerOnly);data.putBoolean("Relative",relative);ElementsEntity.vector(data,"At",position);
        var player=at.level().getEntity(owner);ArkNetwork.effect(player==null?at:player,data);
    }
    @SubscribeEvent public static void loot(net.minecraftforge.event.entity.living.LootingLevelEvent e){if(e.getDamageSource()!=null&&e.getDamageSource().getDirectEntity() instanceof ElementsEntity a)e.setLootingLevel(a.weapon().getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING));}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){CONTROLS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){CONTROLS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void rightEntity(PlayerInteractEvent.EntityInteract e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e){if(holding(e.getPlayer()))e.setCanceled(true);}
}
