package aero.modellib;


import aero.modellib.optimization.OptimizationRef;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;
import aero.modellib.render.Aero_PrewarmAdmission;
import aero.modellib.render.Aero_PrewarmPriorityQueue;
import aero.modellib.util.Aero_PerfConfig;

/**
 * Opt-in render-thread prewarm queue for display-list caches. Consumers can
 * enqueue models after loading them; the queue drains gradually during render
 * frames so cache compilation does not all land on the first visible frame.
 */
@OptimizationRef({"aero.render.prewarm"})
public final class Aero_Prewarm {

    public static final boolean ENABLED =
        Aero_PerfConfig.booleanProperty("aero.prewarm", false, true);
    private static final int PER_FRAME =
        Aero_PerfConfig.intProperty("aero.prewarm.perFrame", 0, 4, 0, 1024);
    private static final long MAX_NANOS_PER_FRAME = (long)
        (Aero_PerfConfig.doubleProperty("aero.prewarm.maxMsPerFrame",
            0.0d, 1.0d, 0.0d, 1000.0d) * 1000000.0d);
    private static final int MAX_QUEUED =
        Aero_PerfConfig.intProperty("aero.prewarm.maxQueued", 256, 256, 1, 65536);
    private static final boolean ADAPTIVE =
        Aero_PerfConfig.booleanProperty("aero.prewarm.adaptive", false, false);
    private static final int HOTNESS_THRESHOLD =
        Aero_PerfConfig.intProperty("aero.prewarm.hotnessThreshold", 4, 4, 1, 1024);
    private static final int DECAY_FRAMES =
        Aero_PerfConfig.intProperty("aero.prewarm.decayFrames", 60, 60, 1, 100000);
    private static final int STALE_FRAMES =
        Aero_PerfConfig.intProperty("aero.prewarm.staleFrames", 180, 180, 1, 100000);
    private static final double IDLE_MAX_FRAME_MS =
        Aero_PerfConfig.doubleProperty("aero.prewarm.idleMaxFrameMs",
            20.0d, 20.0d, 0.0d, 1000.0d);
    private static final Aero_PrewarmPriorityQueue<Aero_MeshModel> MODELS =
        new Aero_PrewarmPriorityQueue<Aero_MeshModel>(MAX_QUEUED);
    private static final Aero_PrewarmAdmission<Aero_MeshModel> ADMISSION =
        new Aero_PrewarmAdmission<Aero_MeshModel>(
            HOTNESS_THRESHOLD, DECAY_FRAMES, Math.max(DECAY_FRAMES, STALE_FRAMES));

    private static int drainedThisFrame, urgentDrainedThisFrame;
    private static int queuedModels;
    private static int discoveredCacheRevision = -1;
    private static int pressureSkips;
    private static double lastFrameMs;

    private Aero_Prewarm() {}

    public static void enqueueModel(Aero_MeshModel model) {
        if (ADAPTIVE) ADMISSION.admit(model);
        enqueue(model, false);
    }

    static void observeModel(Aero_MeshModel model, boolean visible) {
        if (!needsCompile(model)) return;
        if (ADAPTIVE && !ADMISSION.observe(model, visible)) return;
        enqueue(model, visible);
    }

    static void discoverLoadedModels() {
        if (!active()) return;
        int revision = Aero_ObjLoader.cacheRevision();
        if (revision == discoveredCacheRevision) return;
        Aero_MeshModel[] loaded = Aero_ObjLoader.cachedModels();
        for (int index = 0; index < loaded.length; index++) {
            if (ADAPTIVE) ADMISSION.discover(loaded[index]);
            else enqueue(loaded[index], false);
        }
        discoveredCacheRevision = revision;
    }

    static boolean deferFirstUse(Aero_MeshModel model) {
        if (!needsCompile(model)) return false;
        enqueue(model, true);
        return MODELS.contains(model);
    }

    static void drainFrame() {
        drainedThisFrame = urgentDrainedThisFrame = 0;
        if (ADAPTIVE) ADMISSION.beginFrame();
        if (!active() || MODELS.size() == 0) return;
        boolean pressure = ADAPTIVE && !Aero_PrewarmAdmission.allowsSpeculation(
            lastFrameMs, IDLE_MAX_FRAME_MS);
        long start = System.nanoTime();
        int inspected = 0;
        while (drainedThisFrame < PER_FRAME && MODELS.size() > 0
            && inspected++ < MAX_QUEUED) {
            if (MAX_NANOS_PER_FRAME > 0
                && System.nanoTime() - start >= MAX_NANOS_PER_FRAME) {
                break;
            }
            boolean urgent = MODELS.urgentSize() > 0;
            if (!urgent && pressure) { pressureSkips++; break; }
            Aero_MeshModel model = urgent ? MODELS.pollUrgent() : MODELS.pollSpeculative();
            if (ADAPTIVE && !urgent && !ADMISSION.shouldDrain(model)) continue;
            Aero_MeshRenderer.prewarmModel(model);
            if (ADAPTIVE) ADMISSION.forget(model);
            drainedThisFrame++;
            if (urgent) urgentDrainedThisFrame++;
        }
    }

    static void recordFrameTime(double frameMs) { lastFrameMs = frameMs; }
    static boolean timingRequired() { return active() && ADAPTIVE; }

    public static int queuedModelCount() {
        return MODELS.size();
    }

    public static int queuedModelsTotal() {
        return queuedModels;
    }

    public static int drainedThisFrame() {
        return drainedThisFrame;
    }

    public static int urgentDrainedThisFrame() { return urgentDrainedThisFrame; }
    public static int speculativeQueued() { return MODELS.speculativeSize(); }
    public static int promotedModelsTotal() { return MODELS.promoted(); }
    public static int droppedModelsTotal() { return MODELS.dropped(); }
    public static int admissionTracked() { return ADMISSION.tracked(); }
    public static int admissionAcceptedTotal() { return ADMISSION.admitted(); }
    public static int admissionRejectedTotal() { return ADMISSION.rejected(); }
    public static int admissionExpiredTotal() { return ADMISSION.expired(); }
    public static int pressureSkipsTotal() { return pressureSkips; }

    private static void enqueue(Aero_MeshModel model, boolean urgent) {
        if (!active() || !needsCompile(model)) return;
        boolean existing = MODELS.contains(model);
        if (MODELS.offer(model, urgent) && !existing) queuedModels++;
    }

    private static boolean active() { return ENABLED && PER_FRAME > 0; }

    private static boolean needsCompile(Aero_MeshModel model) {
        return active() && model != null && model.getAtRestListIds() == null
            && !model.atRestListsCompileFailed();
    }
}
