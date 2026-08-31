package aero.modellib.render;

/** Conservative camera-cone culling with a stricter block-entity view path. */
@aero.modellib.optimization.OptimizationRef({"aero.render.cone-frustum-cull"})
public final class Aero_FrustumCull {
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.frustumcull"));
    public static final boolean BE_VIEW_CULL_ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.beViewCull"));
    public static final double DEFAULT_BEHIND_TOLERANCE = 8.0d;

    private static final double CLOSE_RANGE_SQ = 256.0d;
    private static final double DEFAULT_CONE_COS_HALF_ANGLE_SQ = 0.030d;
    private static double coneCosHalfAngleSq = DEFAULT_CONE_COS_HALF_ANGLE_SQ;
    private static double forwardX;
    private static double forwardY;
    private static double forwardZ = 1.0d;
    private static boolean cameraValid;
    private static int lastYawBits;
    private static int lastPitchBits;

    private Aero_FrustumCull() {}

    public static void updateCameraForward(float yawDegrees, float pitchDegrees) {
        int yawBits = Float.floatToIntBits(yawDegrees);
        int pitchBits = Float.floatToIntBits(pitchDegrees);
        if (cameraValid && yawBits == lastYawBits && pitchBits == lastPitchBits) return;
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        forwardX = -Math.sin(yaw) * cosPitch;
        forwardY = -Math.sin(pitch);
        forwardZ = Math.cos(yaw) * cosPitch;
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double upX = forwardY * rightZ;
        double upY = forwardZ * rightX - forwardX * rightZ;
        double upZ = -forwardY * rightX;
        Aero_BlockEntityViewCull.update(yawDegrees, pitchDegrees,
            forwardX, forwardY, forwardZ, rightX, 0.0d, rightZ, upX, upY, upZ);
        cameraValid = true;
        lastYawBits = yawBits;
        lastPitchBits = pitchBits;
    }

    public static void clearCamera() {
        cameraValid = false;
        Aero_BlockEntityViewCull.clear();
    }

    public static void setConeHalfAngleDegrees(double halfAngleDegrees) {
        if (halfAngleDegrees <= 0.0d || halfAngleDegrees >= 90.0d) {
            resetCone();
            return;
        }
        double cosine = Math.cos(Math.toRadians(halfAngleDegrees));
        coneCosHalfAngleSq = cosine * cosine;
    }

    public static void setViewportHalfAnglesDegrees(double horizontal, double vertical) {
        Aero_BlockEntityViewCull.setViewportHalfAnglesDegrees(horizontal, vertical);
    }

    public static void resetCone() { coneCosHalfAngleSq = DEFAULT_CONE_COS_HALF_ANGLE_SQ; }

    public static void beginFrameCounters() { Aero_BlockEntityViewCull.beginFrame(); }
    public static int beViewCulledThisFrame() { return Aero_BlockEntityViewCull.culledThisFrame(); }
    public static int beViewFastTurnHoldFrames() { return Aero_BlockEntityViewCull.fastTurnHoldFrames(); }
    public static int beViewHistoryAcceptedThisFrame() {
        return Aero_BlockEntityViewCull.historyAcceptedThisFrame();
    }

    public static boolean isLikelyVisible(double dx, double dy, double dz, double behindTolerance) {
        if (!ENABLED || !cameraValid) return true;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < CLOSE_RANGE_SQ + behindTolerance * behindTolerance) return true;
        double dot = dx * forwardX + dy * forwardY + dz * forwardZ;
        return dot > 0.0d && dot * dot >= distanceSquared * coneCosHalfAngleSq;
    }

    public static boolean isLikelyVisible(double dx, double dy, double dz) {
        return isLikelyVisible(dx, dy, dz, DEFAULT_BEHIND_TOLERANCE);
    }

    public static boolean isLikelyVisible(double dx, double dy, double dz,
            double visualRadiusBlocks, double extraBehindTolerance) {
        double radius = Math.max(0.0d, visualRadiusBlocks);
        double tolerance = Math.max(0.0d, extraBehindTolerance);
        return isLikelyVisible(dx, dy, dz, radius + tolerance);
    }

    public static boolean isLikelyVisibleWithRadius(double dx, double dy, double dz,
            double visualRadiusBlocks) {
        return isLikelyVisible(dx, dy, dz, visualRadiusBlocks, DEFAULT_BEHIND_TOLERANCE);
    }

    public static boolean isBlockEntityViewVisible(double dx, double dy, double dz,
            double visualRadiusBlocks) {
        if (!ENABLED || !BE_VIEW_CULL_ENABLED || !cameraValid) return true;
        return Aero_BlockEntityViewCull.visible(dx, dy, dz, visualRadiusBlocks);
    }
}
