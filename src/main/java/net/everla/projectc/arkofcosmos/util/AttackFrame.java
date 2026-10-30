package net.everla.projectc.arkofcosmos.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Attack plane: forward follows pitch; right follows yaw even when looking vertically. */
public record AttackFrame(Vec3 forward, Vec3 right) {
    public static AttackFrame capture(Player player) {
        double yaw = Math.toRadians(player.getYRot());
        return new AttackFrame(player.getLookAngle().normalize(), new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)));
    }

    /** Interpolate player-view frames without collapsing at a half turn or vertical pitch. */
    public static AttackFrame interpolateView(AttackFrame from, AttackFrame to, double t) {
        double yaw = Math.atan2(-from.right.z, -from.right.x);
        double delta = Math.atan2(-to.right.z, -to.right.x) - yaw;
        yaw += Math.atan2(Math.sin(delta), Math.cos(delta)) * t;
        double pitch = Math.asin(Math.max(-1, Math.min(1, -from.forward.y)));
        pitch += (Math.asin(Math.max(-1, Math.min(1, -to.forward.y))) - pitch) * t;
        return new AttackFrame(new Vec3(-Math.sin(yaw)*Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw)*Math.cos(pitch)),
                new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)));
    }

    public Vec3 direction(double angle) {
        return forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
    }

    public double angle(Vec3 direction) {
        return Math.atan2(direction.dot(right), direction.dot(forward));
    }

    public Vec3 normal() { return forward.cross(right).normalize(); }

}
