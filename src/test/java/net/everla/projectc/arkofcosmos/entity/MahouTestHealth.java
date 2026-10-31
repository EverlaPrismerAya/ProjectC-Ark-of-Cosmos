package net.everla.projectc.arkofcosmos.entity;

import net.minecraft.nbt.CompoundTag;

/** Load a combat fixture without treating test setup as an unattributed attack. */
public final class MahouTestHealth {
    public static void set(MahouTsukaiEntity boss,float health){
        CompoundTag tag=new CompoundTag();boss.saveWithoutId(tag);
        tag.putFloat("Health",health);tag.putInt("NonPlayerInvulnerabilityTicks",0);
        boss.load(tag);
    }
}
