package net.everla.projectc.arkofcosmos.mixin.compact.goetyAndGoetyRevelation;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.everla.projectc.arkofcosmos.SatsukiContent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** arkofcosmos:summon_satsuki_rin reuses Goety's Apostle summoning lead-up, then spawns Satsuki Rin instead. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets="com.Polarice3.Goety.common.entities.util.SummonApostle")
public abstract class SatsukiSummonMixin {
    private boolean ark$satsuki(){return SatsukiContent.ENABLED&&((net.minecraft.world.entity.Entity)(Object)this).getType()==SatsukiContent.SUMMON.get();}
    @WrapOperation(method={"tick","m_8119_"},at=@At(value="NEW",remap=false,target="(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)Lcom/Polarice3/Goety/common/entities/boss/Apostle;"),remap=false,require=1)
    private Apostle ark$spawnSatsuki(EntityType<?> type,Level level,Operation<Apostle> original){
        return ark$satsuki()?SatsukiContent.BOSS.get().create(level):original.call(type,level);
    }
    @ModifyExpressionValue(method={"tick","m_8119_"},at=@At(value="CONSTANT",args="stringValue=info.goety.apostle.summon"),remap=false,require=0)
    private String ark$message(String key){return ark$satsuki()?"info.arkofcosmos.satsuki_rin.summon":key;}
}
