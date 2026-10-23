package dev.stonespawn.plugin.intro;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntroPathTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void worlds() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    private static IntroPoint point(World world, double x, double y, double z, float yaw, Vector lookAt, double orbit,
                                    int fly, int stay, Long time) {
        return new IntroPoint("p", new Location(world, x, y, z, yaw, 0f), lookAt, orbit, "", "", "", List.of(), "", 1f, 1f,
                List.of(), time, null, fly, stay);
    }

    private IntroPoint stop(double x, double z, int fly, int stay) {
        return point(world, x, 100, z, 0f, null, 0, fly, stay, null);
    }

    private static Vector at(IntroPath path, int tick) {
        return path.frame(tick).camera().toVector();
    }

    private static double step(IntroPath path, int tick) {
        return at(path, tick + 1).distance(at(path, tick));
    }

    @Test
    void passesExactlyThroughEveryPointAndReportsArrivals() {
        for (boolean curve : new boolean[]{true, false}) {
            IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10), stop(20, 0, 20, 10), stop(20, 20, 20, 10)),
                    curve, false, null);

            assertEquals(70, path.totalTicks());
            assertTrue(path.frame(0).cut(), "the first frame jumps to the first point");
            assertFalse(path.frame(1).cut());
            assertEquals(0, path.frame(28).pointIndex());
            assertEquals(1, path.frame(29).pointIndex(), "texts show the moment the camera arrives");
            assertTrue(at(path, 29).distance(new Vector(20, 100, 0)) < 1.0E-9);
            assertTrue(at(path, 59).distance(new Vector(20, 100, 20)) < 1.0E-9);
            assertEquals(2, path.frame(69).pointIndex());
        }
    }

    @Test
    void stopsAreApproachedAndLeftGently() {
        IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10), stop(40, 0, 20, 10)), true, false, null);
        double leaving = step(path, 9);
        double cruising = step(path, 19);
        double arriving = step(path, 28);
        assertTrue(leaving < cruising / 5, leaving + " vs " + cruising);
        assertTrue(arriving < cruising / 5, arriving + " vs " + cruising);
    }

    @Test
    void pointsWithoutStayAreFlownThroughAtAnEvenSpeed() {
        // B is flown through; the second flight is twice as long and takes twice as much time.
        IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10), stop(20, 0, 20, 0), stop(60, 0, 40, 10)),
                true, false, null);
        int arrivalAtB = 10 + 20 - 1;
        double before = step(path, arrivalAtB - 1);
        double after = step(path, arrivalAtB);
        assertTrue(before > 0.5, "still moving at full speed: " + before);
        assertEquals(1.0, after / before, 0.1, "no jerk when passing the point");
        assertEquals(1, path.frame(arrivalAtB).pointIndex());
    }

    @Test
    void orbitCirclesAroundTheTargetAndKeepsLookingAtIt() {
        Vector target = new Vector(10, 100, 0);
        IntroPath path = new IntroPath(List.of(point(world, 0, 100, 0, 0f, target, 180, 20, 40, null)), true, false, null);

        for (int tick = 0; tick < 40; tick++) {
            Location camera = path.frame(tick).camera();
            assertEquals(10.0, camera.toVector().distance(target), 1.0E-6, "stays on the circle");
            Vector towardsTarget = target.clone().subtract(camera.toVector()).normalize();
            assertEquals(1.0, camera.getDirection().dot(towardsTarget), 1.0E-4, "faces the target");
        }
        assertTrue(at(path, 39).distance(new Vector(20, 100, 0)) < 1.0E-6, "half a circle around the target");
    }

    @Test
    void lookAtTargetIsFollowedWhileFlyingTowardsIt() {
        Vector tower = new Vector(30, 120, 30);
        IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10),
                point(world, 20, 100, 0, 0f, tower, 0, 20, 10, null)), true, false, null);
        Location arrived = path.frame(35).camera();
        Vector towardsTower = tower.clone().subtract(arrived.toVector()).normalize();
        assertEquals(1.0, arrived.getDirection().dot(towardsTower), 1.0E-4);
    }

    @Test
    void anotherWorldIsReachedWithACut() {
        World sky = server.addSimpleWorld("sky");
        IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10), point(sky, 5, 150, 5, 0f, null, 0, 20, 10, null)),
                true, false, null);
        assertEquals(20, path.totalTicks(), "no flight between worlds");
        assertTrue(path.frame(10).cut());
        assertEquals(sky, path.frame(10).camera().getWorld());
    }

    @Test
    void bedrockViewersJumpAndStayForTheFlightTimeInstead() {
        IntroPath path = new IntroPath(List.of(stop(0, 0, 20, 10), stop(20, 0, 30, 10), stop(40, 0, 20, 10)),
                true, true, null);
        assertEquals((10 + 30) + (10 + 20) + 10, path.totalTicks());
        assertTrue(path.frame(0).cut() && path.frame(40).cut() && path.frame(70).cut());
        assertTrue(at(path, 39).distance(new Vector(0, 100, 0)) < 1.0E-9, "no movement between the cuts");
        assertEquals(1, path.frame(40).pointIndex());
    }

    @Test
    void timeOfDayBlendsTheShortWayRound() {
        assertEquals(0L, IntroPath.timeBetween(23000L, 1000L, 0.5), "night to morning passes midnight");
        assertEquals(9000L, IntroPath.timeBetween(6000L, 12000L, 0.5));
        assertEquals(6000L, IntroPath.timeBetween(null, 6000L, 0.3));

        IntroPath path = new IntroPath(List.of(point(world, 0, 100, 0, 0f, null, 0, 20, 10, 12000L),
                point(world, 20, 100, 0, 0f, null, 0, 20, 10, 18000L)), true, false, 6000L);
        assertEquals(12000L, path.frame(5).time());
        long midFlight = path.frame(19).time();
        assertTrue(midFlight > 12000 && midFlight < 18000, "the sky changes during the flight: " + midFlight);
        assertEquals(18000L, path.frame(35).time());
    }

    @Test
    void hermiteEasingHitsBothEnds() {
        for (double[] speeds : new double[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}, {0.5, 1.5}}) {
            assertEquals(0.0, IntroPath.hermite(0, speeds[0], speeds[1]), 1.0E-12);
            assertEquals(1.0, IntroPath.hermite(1, speeds[0], speeds[1]), 1.0E-12);
        }
        assertEquals(0.5, IntroPath.hermite(0.5, 1, 1), 1.0E-12, "1/1 is an even speed");
    }
}
