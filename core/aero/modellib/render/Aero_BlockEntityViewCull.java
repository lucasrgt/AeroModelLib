package aero.modellib.render;

/** Camera history and sphere projection for conservative block-entity culling. */
final class Aero_BlockEntityViewCull {
    private static final double MARGIN = decimal("aero.beViewCull.marginDeg", 32.0d, 0.0d, 60.0d);
    private static final double RADIUS_PADDING = decimal("aero.beViewCull.radiusPad", 3.0d, 0.0d, 32.0d);
    private static final double NEAR_PADDING = decimal("aero.beViewCull.nearPad", 3.0d, 0.0d, 32.0d);
    private static final double FAST_TURN_DEGREES = decimal("aero.beViewCull.fastTurnDeg", 8.0d, 0.0d, 180.0d);
    private static final int FAST_TURN_HOLD = integer("aero.beViewCull.fastTurnHoldFrames", 6, 0, 60);
    private static final int HISTORY_FRAMES = integer("aero.beViewCull.historyFrames", 6, 0, 16);
    private static final Camera[] HISTORY = cameras(16);
    private static Camera current;
    private static Camera previous;
    private static int historyCursor;
    private static int historyCount;
    private static int holdFrames;
    private static int culled;
    private static int historyAccepted;
    private static float lastYaw;
    private static float lastPitch;
    private static boolean anglesValid;
    private static double tanHorizontal = Math.tan(Math.toRadians(60.0d));
    private static double tanVertical = Math.tan(Math.toRadians(43.0d));

    private Aero_BlockEntityViewCull() {}

    static void update(float yaw, float pitch, double fx, double fy, double fz,
            double rx, double ry, double rz, double ux, double uy, double uz) {
        if (anglesValid && FAST_TURN_HOLD > 0 && angularDelta(yaw, pitch) >= FAST_TURN_DEGREES)
            holdFrames = FAST_TURN_HOLD;
        if (current != null) {
            remember(current);
            previous = current;
        }
        current = new Camera(fx, fy, fz, rx, ry, rz, ux, uy, uz);
        lastYaw = yaw;
        lastPitch = pitch;
        anglesValid = true;
    }

    static void clear() {
        current = previous = null;
        historyCursor = historyCount = holdFrames = 0;
        anglesValid = false;
    }

    static void setViewportHalfAnglesDegrees(double horizontal, double vertical) {
        tanHorizontal = Math.tan(Math.toRadians(clampAngle(horizontal + MARGIN)));
        tanVertical = Math.tan(Math.toRadians(clampAngle(vertical + MARGIN)));
    }

    static void beginFrame() {
        culled = historyAccepted = 0;
        if (holdFrames > 0) holdFrames--;
    }

    static int culledThisFrame() { return culled; }
    static int fastTurnHoldFrames() { return holdFrames; }
    static int historyAcceptedThisFrame() { return historyAccepted; }

    static boolean visible(double dx, double dy, double dz, double visualRadius) {
        if (current == null || holdFrames > 0) return true;
        double radius = (visualRadius > 0.0d ? visualRadius : 0.5d) + RADIUS_PADDING;
        if (inside(current, dx, dy, dz, radius)) return true;
        if (previous != null && inside(previous, dx, dy, dz, radius)) {
            historyAccepted++;
            return true;
        }
        for (int index = 0; index < Math.min(historyCount, HISTORY_FRAMES); index++) {
            if (inside(HISTORY[index], dx, dy, dz, radius)) {
                historyAccepted++;
                return true;
            }
        }
        culled++;
        return false;
    }

    private static boolean inside(Camera camera, double dx, double dy, double dz, double radius) {
        double viewZ = dx * camera.fx + dy * camera.fy + dz * camera.fz;
        double viewX = dx * camera.rx + dy * camera.ry + dz * camera.rz;
        double viewY = dx * camera.ux + dy * camera.uy + dz * camera.uz;
        if (viewZ < -radius - NEAR_PADDING) return false;
        if (viewZ <= 0.0d) {
            double allowance = radius + NEAR_PADDING;
            return Math.abs(viewX) <= allowance && Math.abs(viewY) <= allowance;
        }
        if (Math.abs(viewX) > viewZ * tanHorizontal + radius + NEAR_PADDING) return false;
        return Math.abs(viewY) <= viewZ * tanVertical + radius + NEAR_PADDING;
    }

    private static void remember(Camera camera) {
        if (HISTORY_FRAMES <= 0) return;
        HISTORY[historyCursor] = camera;
        historyCursor = (historyCursor + 1) % HISTORY_FRAMES;
        if (historyCount < HISTORY_FRAMES) historyCount++;
    }

    private static double angularDelta(float yaw, float pitch) {
        double yawDelta = yaw - lastYaw;
        while (yawDelta >= 180.0d) yawDelta -= 360.0d;
        while (yawDelta < -180.0d) yawDelta += 360.0d;
        double pitchDelta = pitch - lastPitch;
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }

    private static double clampAngle(double value) { return Math.max(1.0d, Math.min(89.0d, value)); }

    private static double decimal(String name, double fallback, double min, double max) {
        try {
            String raw = System.getProperty(name);
            if (raw == null) return fallback;
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException error) { return fallback; }
    }

    private static int integer(String name, int fallback, int min, int max) {
        try {
            String raw = System.getProperty(name);
            if (raw == null) return fallback;
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException error) { return fallback; }
    }

    private static Camera[] cameras(int size) { return new Camera[size]; }

    private static final class Camera {
        final double fx, fy, fz, rx, ry, rz, ux, uy, uz;
        Camera(double fx, double fy, double fz, double rx, double ry, double rz,
                double ux, double uy, double uz) {
            this.fx = fx; this.fy = fy; this.fz = fz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.ux = ux; this.uy = uy; this.uz = uz;
        }
    }
}
