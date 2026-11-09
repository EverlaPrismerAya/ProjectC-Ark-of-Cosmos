package net.everla.projectc.arkofcosmos.combat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
/** Optional-mod-free contract for common removal and doom hooks. */
public interface SatsukiBoss extends ProtectedBoss {
    boolean protectRemoval(Entity.RemovalReason reason);
    void removeByPlayerCommand();
    void applyConfiguredHealth();
    static void doomAttribute(AttributeInstance attribute,double value,Object owner){
        if(!(owner instanceof SatsukiBoss))attribute.setBaseValue(value);
    }
}
