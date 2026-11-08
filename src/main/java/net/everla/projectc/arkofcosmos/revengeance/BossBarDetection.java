package net.everla.projectc.arkofcosmos.revengeance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** Read field types rather than names so obfuscation and custom ServerBossEvent subclasses work. */
public final class BossBarDetection {
    private BossBarDetection() {}

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            var fields = new ArrayList<Field>();
            for (Class<?> owner = type; owner != null && LivingEntity.class.isAssignableFrom(owner); owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !BossEvent.class.isAssignableFrom(field.getType())) continue;
                    try {
                        if (field.trySetAccessible()) fields.add(field);
                    } catch (SecurityException ignored) {
                        // Respect access restrictions imposed by optional mods or the runtime.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    public static boolean hasBossBar(LivingEntity entity) {
        if (!entity.isAlive() || entity.level().isClientSide()) return false;
        // The dragon's event belongs to EndDragonFight, not the entity itself.
        if (entity instanceof EnderDragon dragon && dragon.getDragonFight() != null) return true;
        for (Field field : FIELDS.get(entity.getClass())) {
            try {
                if (field.get(entity) instanceof ServerBossEvent) return true;
            } catch (IllegalAccessException ignored) {
                // An inaccessible optional mod field must not interrupt player ticking.
            }
        }
        return false;
    }
}
