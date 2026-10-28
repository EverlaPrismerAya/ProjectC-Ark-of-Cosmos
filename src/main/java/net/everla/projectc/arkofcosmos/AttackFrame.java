package net.everla.projectc.arkofcosmos;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A frozen attack plane: forward follows pitch; right follows yaw even when looking vertically. */
public record AttackFrame(Vec3 forward, Vec3 right) {
    public static AttackFrame capture(Player player) {
        double yaw = Math.toRadians(player.getYRot());
        return new AttackFrame(player.getLookAngle().normalize(), new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)));
    }

    public Vec3 direction(double angle) {
        return forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
    }

    public double angle(Vec3 direction) {
        return Math.atan2(direction.dot(right), direction.dot(forward));
    }

    public Vec3 normal() { return forward.cross(right).normalize(); }

}
