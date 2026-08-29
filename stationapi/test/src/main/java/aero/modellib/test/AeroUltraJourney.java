package aero.modellib.test;

import net.minecraft.client.Minecraft;

/** Deterministic camera journey that exercises visibility transitions in one client run. */
public final class AeroUltraJourney {
    private static final String[] NAMES = {
        "front-static", "yaw-sweep", "pitch-sweep", "lateral-strafe", "tower-dolly",
        "tower-orbit", "vertical-scan", "floor-occlusion", "chunk-teleports",
        "post-teleport-recovery"
    };
    private static long beginNs;
    private static int announcedPhase = Integer.MIN_VALUE;
    private static int cameraRescues;

    private AeroUltraJourney() {}

    static void prepare(Minecraft game) {
        protectCamera(game);
        pose(game, -8.5d, middleY(), 8.5d, -90.0f, basePitch());
        System.out.println("[AeroUltraJourney] phases=" + NAMES.length
            + " order=" + String.join(",", NAMES));
    }

    static void beginAt(long now) { beginNs = now; }

    static void drive(Minecraft game) {
        if (game.player == null) return;
        protectCamera(game);
        int phase = phaseIndex();
        double progress = phaseProgress();
        if (!AeroUltraStressConfig.JOURNEY || phase < 0) {
            if (phase < 0 && AeroUltraStressConfig.steadyWorld()) rehearse(game);
            else pose(game, -8.5d, middleY(), 8.5d, -90.0f, basePitch());
            return;
        }
        if (phase != announcedPhase) {
            announcedPhase = phase;
            System.out.println("[AeroUltraJourney] phase=" + phase + " name=" + name(phase));
        }
        applyPhase(game, phase, progress);
    }

    public static int phaseIndex() {
        if (!AeroUltraStressConfig.JOURNEY || beginNs == 0L) return -1;
        long elapsed = System.nanoTime() - beginNs
            - AeroUltraStressConfig.WARMUP_SECONDS * 1_000_000_000L;
        if (elapsed < 0L) return -1;
        if (AeroUltraStressConfig.JOURNEY_CHECKPOINT >= 0)
            return AeroUltraStressConfig.JOURNEY_CHECKPOINT;
        long duration = AeroUltraStressConfig.DURATION_SECONDS * 1_000_000_000L;
        return (int) Math.min(NAMES.length - 1, elapsed * NAMES.length / duration);
    }

    public static int phaseCount() { return NAMES.length; }
    public static String name(int phase) { return NAMES[Math.max(0, Math.min(NAMES.length - 1, phase))]; }
    static int cameraRescues() { return cameraRescues; }

    private static double phaseProgress() {
        long elapsed = System.nanoTime() - beginNs
            - AeroUltraStressConfig.WARMUP_SECONDS * 1_000_000_000L;
        if (elapsed <= 0L) return 0.0d;
        if (AeroUltraStressConfig.JOURNEY_CHECKPOINT >= 0) return 0.5d;
        double phaseNs = AeroUltraStressConfig.DURATION_SECONDS * 1_000_000_000.0d / NAMES.length;
        return Math.max(0.0d, Math.min(1.0d, (elapsed % (long) phaseNs) / phaseNs));
    }

    private static void applyPhase(Minecraft game, int phase, double t) {
        double y = middleY();
        if (phase == 0) pose(game, -8.5d, y, 8.5d, -90f, basePitch());
        else if (phase == 1) pose(game, -8.5d, y, 8.5d,
            (float) (-90d + 360d * t), basePitch());
        else if (phase == 2) pose(game, -8.5d, y, 8.5d, -90f, (float) (-70d + 140d * t));
        else if (phase == 3) pose(game, -8.5d, y, -12d + 41d * t, -90f, basePitch());
        else if (phase == 4) pose(game, -24d + 21d * triangle(t), y, 8.5d, -90f, basePitch());
        else if (phase == 5) orbit(game, y, t);
        else if (phase == 6) vertical(game, t);
        else if (phase == 7) floorOcclusion(game, t);
        else if (phase == 8) teleport(game, t);
        else pose(game, -8.5d, y, 8.5d + Math.sin(t * Math.PI * 4d) * 3d,
            -90f + (float) Math.sin(t * Math.PI * 2d) * 25f, basePitch());
    }

    /** Replays the route before measurement so steady mode visits every view set. */
    private static void rehearse(Minecraft game) {
        if (beginNs == 0L || AeroUltraStressConfig.WARMUP_SECONDS <= 0L) {
            pose(game, -8.5d, middleY(), 8.5d, -90.0f, basePitch());
            return;
        }
        double elapsed = (System.nanoTime() - beginNs) / 1_000_000_000.0d;
        double warmup = AeroUltraStressConfig.WARMUP_SECONDS;
        double scanFraction = 0.8d;
        if (elapsed >= warmup * scanFraction) {
            pose(game, -8.5d, middleY(), 8.5d, -90.0f, basePitch());
            return;
        }
        double route = Math.max(0.0d, elapsed / (warmup * scanFraction)) * NAMES.length;
        int phase = Math.min(NAMES.length - 1, (int) route);
        applyPhase(game, phase, route - phase);
    }

    private static void orbit(Minecraft game, double y, double t) {
        double angle = t * Math.PI * 2d;
        double x = 7.5d + Math.cos(angle) * 24d;
        double z = 7.5d + Math.sin(angle) * 24d;
        pose(game, x, y, z, lookYaw(x, z), basePitch());
    }

    private static void vertical(Minecraft game, double t) {
        double low = field() ? towerTopY() + 2.5d : AeroUltraStressScene.centralBaseY() + 0.6d;
        double high = field() ? low + 24d
            : low + Math.max(2, AeroUltraStressConfig.LAYERS * AeroUltraStressConfig.verticalStride());
        double y = low + (high - low) * triangle(t);
        pose(game, -8.5d, y, 8.5d, -90f,
            field() ? 55f : (float) ((middleY() - y) * 2d));
    }

    private static void floorOcclusion(Minecraft game, double t) {
        if (field()) {
            pose(game, 7.5d, towerTopY() + 2.5d, -8d + 31d * t, 0f, 89f);
            return;
        }
        int layer = Math.min(AeroUltraStressConfig.LAYERS - 1,
            (int) (t * AeroUltraStressConfig.LAYERS));
        double y = AeroUltraStressScene.centralBaseY()
            + layer * AeroUltraStressConfig.verticalStride() + 0.55d;
        pose(game, -3.5d, y, 8.5d, -90f, 0f);
    }

    private static void teleport(Minecraft game, double t) {
        double[] xs = {-8.5d, -40.5d, -72.5d, 72.5d, -8.5d};
        double x = xs[Math.min(xs.length - 1, (int) (t * xs.length))];
        pose(game, x, middleY(), 8.5d, lookYaw(x, 8.5d), basePitch());
    }

    private static void pose(Minecraft game, double x, double y, double z, float yaw, float pitch) {
        game.player.velocityX = game.player.velocityY = game.player.velocityZ = 0.0d;
        game.player.setPositionAndAngles(x, y, z, yaw, Math.max(-89f, Math.min(89f, pitch)));
    }

    private static double middleY() {
        int base = AeroUltraStressScene.centralBaseY();
        if (field() && base > 0) return towerTopY() + 6d;
        return base > 0 ? base + Math.min(12, AeroUltraStressConfig.LAYERS) : 70.0d;
    }

    private static double towerTopY() {
        return AeroUltraStressScene.centralBaseY()
            + (AeroUltraStressConfig.LAYERS - 1) * AeroUltraStressConfig.verticalStride()
            + (AeroUltraStressConfig.SOLID_FLOORS ? 1 : 0);
    }

    private static void protectCamera(Minecraft game) {
        if (game.player.health <= 0) cameraRescues++;
        game.player.health = game.player.maxHealth;
        game.player.fireTicks = 0;
        game.player.noClip = true;
        game.player.onGround = false;
    }

    private static float basePitch() { return field() ? 35f : 0f; }
    private static boolean field() { return AeroUltraStressConfig.SPACING_CHUNKS < 32; }

    private static float lookYaw(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-(7.5d - x), 7.5d - z));
    }

    private static double triangle(double t) { return t < 0.5d ? t * 2d : (1d - t) * 2d; }
}
