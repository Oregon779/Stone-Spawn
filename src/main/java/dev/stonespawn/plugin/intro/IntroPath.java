package dev.stonespawn.plugin.intro;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The camera's route through the intro as a timeline: for every tick, where the camera is, where it looks, which point
 * it has reached and whether the picture cuts to another place. Pure math, so it can be checked without a server.
 *
 * <p>Within one world the camera glides from point to point - along a smooth curve through all points, or in straight
 * lines - and holds still (or circles) at points with a stay time. Points without a stay time are flown through at
 * an even speed. A point in another world is reached with a cut; viewers who can't follow a moving camera (Bedrock)
 * get a cut to every point, staying there for the time the flight would take.
 */
final class IntroPath {

    // An orbit without a look-at target circles around the spot this far in front of the camera.
    private static final double ORBIT_DISTANCE = 10.0;
    // Hermite speeds above 3 would make the camera overshoot and come back.
    private static final double MAX_SPEED = 3.0;

    /**
     * @param pointIndex the last point the camera reached (texts show when this changes)
     * @param cut        the camera jumps here instead of gliding (the first frame of every world, or every point)
     * @param time       time of day the viewer should see, or null to leave it alone
     */
    record Frame(Location camera, int pointIndex, boolean cut, Long time) {
    }

    private record Phase(boolean fly, int from, int to, int start, int ticks, double startSpeed, double endSpeed) {
    }

    private final List<IntroPoint> points;
    private final boolean curve;
    private final List<Phase> phases = new ArrayList<>();
    private final Set<Integer> cutTicks = new HashSet<>();
    private final boolean[] shotStart;
    private final Long[] times;
    private final int totalTicks;
    private int cursor;

    IntroPath(List<IntroPoint> points, boolean curve, boolean jumpOnly, Long startTime) {
        this.points = List.copyOf(points);
        this.curve = curve;
        int count = this.points.size();
        shotStart = new boolean[count];
        times = new Long[count];

        Long time = startTime;
        for (int i = 0; i < count; i++) {
            shotStart[i] = i == 0 || jumpOnly
                    || this.points.get(i).location().getWorld() != this.points.get(i - 1).location().getWorld();
            time = this.points.get(i).time() != null ? this.points.get(i).time() : time;
            times[i] = time;
        }

        int tick = 0;
        for (int i = 0; i < count; i++) {
            IntroPoint point = this.points.get(i);
            if (shotStart[i]) {
                cutTicks.add(tick);
            } else {
                int ticks = point.flyTicks();
                double startSpeed = isPassThrough(i - 1) ? evenSpeed(ticks, this.points.get(i - 1).flyTicks()) : 0;
                double endSpeed = isPassThrough(i) ? evenSpeed(ticks, this.points.get(i + 1).flyTicks()) : 0;
                phases.add(new Phase(true, i - 1, i, tick, ticks, startSpeed, endSpeed));
                tick += ticks;
            }
            int stay = point.stayTicks();
            if (jumpOnly && i + 1 < count) {
                stay += this.points.get(i + 1).flyTicks();
            }
            if (stay == 0 && (shotStart[i] || isShotEnd(i))) {
                stay = 1;
            }
            if (stay > 0) {
                phases.add(new Phase(false, i, i, tick, stay, 0, 0));
                tick += stay;
            }
        }
        totalTicks = tick;
    }

    int totalTicks() {
        return totalTicks;
    }

    List<IntroPoint> points() {
        return points;
    }

    /** The frame at {@code tick} (0 to {@link #totalTicks()} - 1). Fastest when asked for ticks in ascending order. */
    Frame frame(int tick) {
        if (cursor >= phases.size() || phases.get(cursor).start() > tick) {
            cursor = 0;
        }
        while (cursor < phases.size() - 1 && phases.get(cursor).start() + phases.get(cursor).ticks() <= tick) {
            cursor++;
        }
        Phase phase = phases.get(cursor);
        double progress = Math.min(1.0, (double) (tick - phase.start() + 1) / phase.ticks());
        boolean cut = cutTicks.contains(tick);

        if (!phase.fly()) {
            int index = phase.from();
            IntroPoint point = points.get(index);
            if (point.orbits()) {
                Vector center = orbitCenter(index);
                Vector position = orbitPosition(index, smoothstep(progress));
                return new Frame(facing(index, position, center), index, cut, times[index]);
            }
            Vector position = point.location().toVector();
            return new Frame(at(index, position, viewAt(index, position)), index, cut, times[index]);
        }

        int from = phase.from();
        int to = phase.to();
        double eased = hermite(progress, phase.startSpeed(), phase.endSpeed());
        Vector position = curve ? curvePosition(from, to, eased) : lerp(departure(from), arrival(to), eased);
        float[] view = blend(departureView(from, position), viewAt(to, position), eased);
        // The texts of the next point show as soon as the camera has arrived there.
        int reached = progress >= 1.0 ? to : from;
        return new Frame(at(from, position, view), reached, cut, timeBetween(times[from], times[to], eased));
    }

    // ---------------------------------------------------------------- shape of the route

    private boolean isShotEnd(int index) {
        return index == points.size() - 1 || shotStart[index + 1];
    }

    private boolean isPassThrough(int index) {
        return !shotStart[index] && !isShotEnd(index) && points.get(index).stayTicks() == 0;
    }

    /**
     * Speed at a point flown through, for a flight of {@code ticks} next to one of {@code otherTicks}, in units of the
     * flight's own length. Both flights then pass the point at the same blocks per tick, even if they take different
     * times (the curve has the same direction there on both sides).
     */
    private static double evenSpeed(int ticks, int otherTicks) {
        return Math.min(MAX_SPEED, 2.0 * ticks / (ticks + otherTicks));
    }

    private Vector arrival(int index) {
        return points.get(index).location().toVector();
    }

    /** Where the camera leaves a point: after an orbit it is somewhere else on the circle. */
    private Vector departure(int index) {
        return points.get(index).orbits() ? orbitPosition(index, 1.0) : arrival(index);
    }

    private Vector curvePosition(int from, int to, double t) {
        Vector p1 = departure(from);
        Vector p2 = arrival(to);
        Vector p0 = shotStart[from] ? p1.clone().multiply(2).subtract(p2) : departure(from - 1);
        Vector p3 = isShotEnd(to) ? p2.clone().multiply(2).subtract(p1) : arrival(to + 1);
        // Uniform Catmull-Rom: passes exactly through p1 and p2, with tangents shaped by the neighbours.
        double t2 = t * t;
        double t3 = t2 * t;
        return p1.clone().multiply(2)
                .add(p2.clone().subtract(p0).multiply(t))
                .add(p0.clone().multiply(2).subtract(p1.clone().multiply(5)).add(p2.clone().multiply(4)).subtract(p3).multiply(t2))
                .add(p3.clone().subtract(p0).add(p1.clone().multiply(3)).subtract(p2.clone().multiply(3)).multiply(t3))
                .multiply(0.5);
    }

    private Vector orbitCenter(int index) {
        IntroPoint point = points.get(index);
        if (point.lookAt() != null) {
            return point.lookAt().clone();
        }
        return point.location().toVector().add(point.location().getDirection().multiply(ORBIT_DISTANCE));
    }

    /** Position after {@code progress} (0 to 1) of the orbit: the point turned around its center, height kept. */
    private Vector orbitPosition(int index, double progress) {
        Vector center = orbitCenter(index);
        Vector offset = arrival(index).subtract(center);
        double angle = Math.toRadians(points.get(index).orbitDegrees() * progress);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(center.getX() + offset.getX() * cos - offset.getZ() * sin,
                arrival(index).getY(),
                center.getZ() + offset.getX() * sin + offset.getZ() * cos);
    }

    // ---------------------------------------------------------------- where the camera looks

    /** The view a point asks for at a camera position: towards its look-at target, or the saved yaw and pitch. */
    private float[] viewAt(int index, Vector position) {
        IntroPoint point = points.get(index);
        if (point.lookAt() != null) {
            return lookTowards(position, point.lookAt(), point.location().getYaw(), point.location().getPitch());
        }
        return new float[]{point.location().getYaw(), point.location().getPitch()};
    }

    private float[] departureView(int index, Vector position) {
        IntroPoint point = points.get(index);
        if (point.orbits()) {
            return lookTowards(position, orbitCenter(index), point.location().getYaw(), point.location().getPitch());
        }
        return viewAt(index, position);
    }

    private static float[] lookTowards(Vector from, Vector target, float fallbackYaw, float fallbackPitch) {
        Vector direction = target.clone().subtract(from);
        if (direction.lengthSquared() < 1.0E-4) {
            return new float[]{fallbackYaw, fallbackPitch};
        }
        double horizontal = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(direction.getY(), horizontal));
        return new float[]{yaw, pitch};
    }

    /** Turns from one view to the other the short way round. */
    private static float[] blend(float[] from, float[] to, double t) {
        double yawDelta = ((to[0] - from[0]) % 360 + 540) % 360 - 180;
        return new float[]{(float) (from[0] + yawDelta * t), (float) (from[1] + (to[1] - from[1]) * t)};
    }

    private Location facing(int index, Vector position, Vector target) {
        IntroPoint point = points.get(index);
        return at(index, position, lookTowards(position, target, point.location().getYaw(), point.location().getPitch()));
    }

    private Location at(int index, Vector position, float[] view) {
        return new Location(points.get(index).location().getWorld(), position.getX(), position.getY(), position.getZ(),
                view[0], view[1]);
    }

    // ---------------------------------------------------------------- timing

    /**
     * Cubic Hermite easing from 0 to 1 with the given speeds at both ends: 0/0 starts and stops gently (smoothstep),
     * 1/1 is an even speed.
     */
    static double hermite(double t, double startSpeed, double endSpeed) {
        double t2 = t * t;
        double t3 = t2 * t;
        return (startSpeed + endSpeed - 2) * t3 + (3 - 2 * startSpeed - endSpeed) * t2 + startSpeed * t;
    }

    private static double smoothstep(double t) {
        return hermite(t, 0, 0);
    }

    private static Vector lerp(Vector from, Vector to, double t) {
        return from.clone().add(to.clone().subtract(from).multiply(t));
    }

    /** Time of day between two points, the short way round the 24000-tick day (night to morning passes midnight). */
    static Long timeBetween(Long from, Long to, double t) {
        if (from == null || to == null) {
            return to != null ? to : from;
        }
        long delta = Math.floorMod(to - from + 12000, 24000L) - 12000;
        return Math.floorMod(from + Math.round(delta * t), 24000L);
    }
}
