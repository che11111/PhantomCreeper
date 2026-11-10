package dev.che1s.phantomcreeper.mob;

import dev.che1s.phantomcreeper.config.PluginSettings;
import dev.che1s.phantomcreeper.model.ModelAttachment;
import dev.che1s.phantomcreeper.scheduler.PlatformScheduler;
import dev.che1s.phantomcreeper.util.MovementMath;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

/**
 * 处理追击、盘旋、避让和模型位置同步。
 */
final class FlightController {
    private final PhantomCreeperController owner;
    private final PhantomCreeperManager manager;
    private final Creeper entity;
    private final Supplier<ModelAttachment> attachment;
    private final Location home;

    private double orbitAngle = Math.random() * Math.PI * 2.0;
    private Vector flightVelocity = new Vector();
    private volatile double trackedSpeed;

    FlightController(
            PhantomCreeperController owner,
            PhantomCreeperManager manager,
            Creeper entity,
            Supplier<ModelAttachment> attachment,
            Location home) {
        this.owner = owner;
        this.manager = manager;
        this.entity = entity;
        this.attachment = attachment;
        this.home = home;
    }

    double trackedSpeed() {
        return trackedSpeed;
    }

    void approach(Player target, PluginSettings current, int ageTicks) {
        Location location = entity.getLocation();
        Location targetLocation = target.getLocation();
        double horizontalDistance = Math.hypot(
                location.getX() - targetLocation.getX(),
                location.getZ() - targetLocation.getZ()
        );
        boolean close = horizontalDistance <= current.movement().closeRange();
        double desiredY = targetLocation.getY()
                + (close ? current.movement().strikeHeight() : current.movement().cruiseHeight());
        Vector toTarget = new Vector(
                targetLocation.getX() - location.getX(),
                desiredY - location.getY(),
                targetLocation.getZ() - location.getZ()
        );
        if (!close) {
            toTarget.add(new Vector(
                    -(targetLocation.getZ() - location.getZ()),
                    0.0,
                    targetLocation.getX() - location.getX()
            ).multiply(0.18));
        }
        applySeparation(toTarget, current.movement().minimumSeparation());
        toTarget.setY(toTarget.getY() + Math.sin(ageTicks * 0.09) * 0.035);
        double configuredSpeed = close
                ? current.movement().chaseSpeed()
                : current.movement().cruiseSpeed();
        double speed = Math.min(configuredSpeed, current.movement().maxSpeed());
        fly(toTarget, speed, current.movement().acceleration());
        faceTarget(target);
    }

    void patrol(PluginSettings current, int ageTicks) {
        Location location = entity.getLocation();
        if (location.distanceSquared(home) > current.movement().maxDistanceFromHome()
                * current.movement().maxDistanceFromHome()) {
            Vector backHome = home.toVector().subtract(location.toVector());
            fly(backHome, current.movement().chaseSpeed(), current.movement().acceleration());
            keepPatrolHeadNatural();
            return;
        }
        orbitAngle += 0.035;
        double radius = Math.max(4.0, current.movement().waypointRadius() * 1.8);
        Location waypoint = home.clone().add(
                Math.cos(orbitAngle) * radius,
                current.movement().cruiseHeight() * 0.35 + Math.sin(ageTicks * 0.05) * 1.2,
                Math.sin(orbitAngle) * radius
        );
        Vector toWaypoint = waypoint.toVector().subtract(location.toVector());
        applySeparation(toWaypoint, current.movement().minimumSeparation());
        fly(
                toWaypoint,
                Math.min(current.movement().cruiseSpeed(), current.movement().maxSpeed()),
                current.movement().acceleration()
        );
        keepPatrolHeadNatural();
    }

    void faceTarget(Player target) {
        Location location = entity.getLocation();
        Vector direction = target.getEyeLocation().toVector()
                .subtract(entity.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-5) {
            return;
        }
        location.setDirection(direction);
        location.setPitch(Math.max(-75.0F, Math.min(75.0F, location.getPitch())));
        entity.setRotation(location.getYaw(), location.getPitch());
        ModelAttachment current = attachment.get();
        if (current != null && current.isValid()) {
            current.setPatrolHeadLocked(false);
            current.setHeadRotation(location.getYaw(), location.getPitch());
        }
    }

    void stop() {
        entity.setVelocity(new Vector());
        flightVelocity = new Vector();
    }

    private void keepPatrolHeadNatural() {
        ModelAttachment current = attachment.get();
        if (current != null && current.isValid()) {
            current.setPatrolHeadLocked(true);
            current.setHeadRotation(entity.getYaw(), 0.0F);
        }
    }

    private void applySeparation(Vector steeringTarget, double radius) {
        Vector separation = manager.separationVector(owner, radius);
        double length = separation.length();
        if (length < 1.0E-6) {
            return;
        }
        if (length > 1.0) {
            separation.multiply(1.0 / length);
        }
        steeringTarget.add(separation.multiply(1.4));
    }

    private void fly(Vector toTarget, double maxSpeed, double acceleration) {
        Vector velocity = MovementMath.steer(
                flightVelocity, toTarget, maxSpeed, acceleration);
        flightVelocity = velocity.clone();
        trackedSpeed = velocity.length();
        // 位移由传送唯一负责，避免原版速度在下一 tick 再叠加一次。
        entity.setVelocity(new Vector());
        if (velocity.lengthSquared() <= 1.0E-5) {
            return;
        }
        Location next = entity.getLocation().add(velocity);
        Vector direction = velocity.clone().normalize();
        next.setDirection(direction);
        next.setPitch(Math.max(-55.0F, Math.min(55.0F, next.getPitch())));
        if (PlatformScheduler.isFolia()) {
            entity.teleportAsync(next);
        } else {
            entity.teleport(next);
        }
        ModelAttachment current = attachment.get();
        if (current != null && current.isValid()) {
            current.updateLocation(next);
        }
    }
}
