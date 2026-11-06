package net.everla.projectc.arkofcosmos.mixin.entity.calamitas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.everla.projectc.arkofcosmos.entity.SupremeCalamitasEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's section padding assumes small entities. Preserve the native query's
 * actual box, filter and limit while including a loaded Calamitas' larger body. */
@Mixin(Level.class)
abstract class CalamitasHitboxQueryMixin {
    @Unique private boolean ark$missingBody(SupremeCalamitasEntity boss, AABB box) {
        if (boss.isRemoved() || ((Level)(Object)this).getEntity(boss.getId()) != boss || !boss.getBoundingBox().intersects(box)) return false;
        int x=SectionPos.posToSectionCoord(boss.getX()), y=SectionPos.posToSectionCoord(boss.getY()), z=SectionPos.posToSectionCoord(boss.getZ());
        // Never evaluate the caller's predicate twice for an entity visited by vanilla.
        return x<SectionPos.posToSectionCoord(box.minX-2) || x>SectionPos.posToSectionCoord(box.maxX+2)
                || y<SectionPos.posToSectionCoord(box.minY-4) || y>SectionPos.posToSectionCoord(box.maxY)
                || z<SectionPos.posToSectionCoord(box.minZ-2) || z>SectionPos.posToSectionCoord(box.maxZ+2);
    }
    @Inject(method="getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at=@At("RETURN"), cancellable=true)
    private void ark$calamitasBody(Entity excluded, AABB box, Predicate<? super Entity> filter, CallbackInfoReturnable<List<Entity>> callback) {
        List<Entity> result = callback.getReturnValue();
        boolean copied = false;
        for (var boss : SupremeCalamitasEntity.loaded((Level)(Object)this))
            if (boss != excluded && ark$missingBody(boss, box) && !result.contains(boss) && filter.test(boss)) {
                if (!copied) { result = new ArrayList<>(result); copied = true; }
                result.add(boss);
            }
        if (copied) callback.setReturnValue(result);
    }
    @Inject(method="getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V", at=@At("RETURN"))
    private <T extends Entity> void ark$calamitasTypedBody(EntityTypeTest<Entity,T> type, AABB box, Predicate<? super T> filter, List<? super T> result, int limit, CallbackInfo callback) {
        for (var boss : SupremeCalamitasEntity.loaded((Level)(Object)this)) {
            if (result.size() >= limit) break;
            if (!ark$missingBody(boss, box) || result.contains(boss)) continue;
            T entity = type.tryCast(boss);
            if (entity != null && filter.test(entity)) result.add(entity);
        }
    }
}
