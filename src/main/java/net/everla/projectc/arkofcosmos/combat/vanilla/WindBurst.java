package net.everla.projectc.arkofcosmos.combat.vanilla;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Non-destructive TRIGGER explosion. No damage, fire, drops, or block destruction calls. */
public final class WindBurst {
    public static void explode(Level level,Entity source,Vec3 center,float radius,float multiplier,boolean playerCharge) {
        if(!(level instanceof ServerLevel server))return;
        double diameter=2*radius;
        for(Entity entity:level.getEntities(source,new AABB(center,center).inflate(diameter+1))) {
            if(entity.isSpectator()||entity.ignoreExplosion()||entity instanceof Player p&&p.getAbilities().flying)continue;
            boolean ownBossCharge=entity instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity
                &&source instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner()==entity;
            if(entity instanceof net.everla.projectc.arkofcosmos.entity.FourthCalamityEntity&&!ownBossCharge)continue;
            double distance=entity.position().distanceTo(center)/diameter;if(distance>1)continue;
            Vec3 origin=entity instanceof net.minecraft.world.entity.item.PrimedTnt?entity.position():entity.getEyePosition();
            Vec3 direction=origin.subtract(center).normalize();
            double power=(1-distance)*Explosion.getSeenPercent(center,entity)*multiplier;
            if(entity instanceof LivingEntity living) {
                int protection=0;
                for(var armor:living.getArmorSlots())protection+=net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLAST_PROTECTION,armor);
                power*=1-Math.min(1,.15f*protection);
            }
            if(ownBossCharge)entity.setDeltaMovement(entity.getDeltaMovement().add(direction.scale(power)));
            else entity.push(direction.x*power,direction.y*power,direction.z*power);
            VanillaCombat.syncMotion(entity);
            if(entity instanceof LivingEntity living&&(entity instanceof Player
                ||source instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getOwner()==entity)) {
                if(playerCharge&&(entity instanceof Player||power>0))ImpulseFall.mark(living,false);else ImpulseFall.endGrace(living);
            }
        }
        for(BlockPos pos:blocks(server,center,radius))trigger(server,pos);
        server.sendParticles(radius>=2?VanillaParticles.LARGE_EMITTER.get():VanillaParticles.EMITTER.get(),center.x,center.y,center.z,1,0,0,0,0);
        server.playSound(null,center.x,center.y,center.z,VanillaCombat.AUDIO.get("entity.wind_charge.wind_burst").get(),SoundSource.BLOCKS,1,1);
        server.gameEvent(source,net.minecraft.world.level.gameevent.GameEvent.EXPLODE,center);
    }
    private static Set<BlockPos> blocks(ServerLevel level,Vec3 center,float radius) {
        Set<BlockPos> blocks=new HashSet<>();
        // Vanilla explosion boundary rays. Wind charges treat everything except bedrock/barrier as zero resistance.
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
            if(x!=0&&x!=15&&y!=0&&y!=15&&z!=0&&z!=15)continue;
            Vec3 step=new Vec3(x/15.0*2-1,y/15.0*2-1,z/15.0*2-1).normalize().scale(.3);
            float power=radius*(.7f+level.random.nextFloat()*.6f);Vec3 at=center;
            while(power>0) {
                BlockPos pos=BlockPos.containing(at);if(!level.isInWorldBounds(pos))break;
                BlockState state=level.getBlockState(pos);
                if(state.is(Blocks.BEDROCK)||state.is(Blocks.BARRIER))power-=(3600000f+.3f)*.3f;
                if(power>0)blocks.add(pos);
                at=at.add(step);power-=.22500001f;
            }
        }
        return blocks;
    }
    private static void trigger(ServerLevel level,BlockPos pos) {
        BlockState s=level.getBlockState(pos);Block b=s.getBlock();
        if(b instanceof DoorBlock door&&s.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&!s.getValue(DoorBlock.POWERED)&&!s.is(Blocks.IRON_DOOR))
            door.setOpen(null,level,s,pos,!s.getValue(DoorBlock.OPEN));
        else if(b instanceof TrapDoorBlock&&!s.getValue(TrapDoorBlock.POWERED)&&!s.is(Blocks.IRON_TRAPDOOR))
            s.use(level,null,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        else if(b instanceof ButtonBlock button&&!s.getValue(ButtonBlock.POWERED))button.press(s,level,pos);
        else if(b instanceof LeverBlock lever)lever.pull(s,level,pos);
        else if(b instanceof BellBlock bell)bell.attemptToRing(null,level,pos,null);
        else if(b instanceof CandleBlock&&s.getValue(CandleBlock.LIT))CandleBlock.extinguish(null,s,level,pos);
        else if(b instanceof CandleCakeBlock&&s.getValue(CandleCakeBlock.LIT))CandleBlock.extinguish(null,s,level,pos);
    }
}
