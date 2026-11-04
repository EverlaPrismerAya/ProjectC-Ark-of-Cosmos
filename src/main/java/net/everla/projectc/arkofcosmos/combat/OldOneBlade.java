package net.everla.projectc.arkofcosmos.combat;
import net.everla.projectc.arkofcosmos.util.AttackFrame;
import net.minecraft.world.phys.*;
/** Legacy API backed by the shared original-alpha continuous blade collision. */
public final class OldOneBlade {
    private static final SpriteBlade SHAPE=new SpriteBlade("item/ark_of_the_old_one");
    public static final int WIDTH=SHAPE.WIDTH,HEIGHT=SHAPE.HEIGHT;
    private OldOneBlade(){}
    public static boolean intersects(AABB box,Vec3 at,AttackFrame frame,double angle){return SHAPE.intersects(box,at,frame,angle);}
    public static boolean sweeps(AABB box,Vec3 at,AttackFrame frame,double from,double to){return SHAPE.sweeps(box,at,frame,from,to);}
}
