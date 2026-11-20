package net.everla.projectc.arkofcosmos.entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
/** Legacy registry tombstone: loading an old attack safely retires it. */
public final class MahouAttackEntity extends Entity {
    public MahouAttackEntity(EntityType<? extends MahouAttackEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public void tick(){super.tick();if(!level().isClientSide)discard();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
