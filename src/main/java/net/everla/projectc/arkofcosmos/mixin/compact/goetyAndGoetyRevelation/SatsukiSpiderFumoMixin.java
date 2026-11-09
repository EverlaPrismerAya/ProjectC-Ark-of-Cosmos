package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.Polarice3.Goety.common.entities.ally.spider.AbstractSpiderServant;
import net.everla.projectc.arkofcosmos.combat.FumoAppearance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.Polarice3.Goety.common.entities.ally.spider.AbstractSpiderServant")
public abstract class SatsukiSpiderFumoMixin implements FumoAppearance {
    @Unique private static final EntityDataAccessor<Integer> ARK_FUMO=SynchedEntityData.defineId(AbstractSpiderServant.class,EntityDataSerializers.INT);
    @Inject(method={"defineSynchedData","m_8097_"},at=@At("TAIL"),remap=false)
    private void ark$define(CallbackInfo ci){((Entity)(Object)this).getEntityData().define(ARK_FUMO,0);}
    public int ark$fumo(){return ((Entity)(Object)this).getEntityData().get(ARK_FUMO);}
    public void ark$fumo(int variant){((Entity)(Object)this).getEntityData().set(ARK_FUMO,variant);}
    @Inject(method={"addAdditionalSaveData","m_7380_"},at=@At("TAIL"),remap=false)
    private void ark$save(CompoundTag tag,CallbackInfo ci){tag.putInt("ArkFumo",ark$fumo());}
    @Inject(method={"readAdditionalSaveData","m_7378_"},at=@At("TAIL"),remap=false)
    private void ark$load(CompoundTag tag,CallbackInfo ci){ark$fumo(Math.max(0,Math.min(7,tag.getInt("ArkFumo"))));}
}
