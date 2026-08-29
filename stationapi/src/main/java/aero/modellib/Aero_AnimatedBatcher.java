package aero.modellib;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;

/** Per-frame collector that coalesces compatible animated mesh instances. */
@aero.modellib.optimization.OptimizationRef({"aero.render.animated-batcher"})
public final class Aero_AnimatedBatcher {
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.animatedbatch"));
    public static final boolean SORT_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.batcher.sort"));
    public static final boolean UV_BATCH_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.animatedbatch.uv"));
    private static final int BONE_PAGE_DRAIN_MIN = integer(
        "aero.animatedbatch.bonePageDrainMin", -1, -1, 100000);
    private static final HashMap<Aero_AnimatedBatchKey, Batch> BATCHES =
        new HashMap<Aero_AnimatedBatchKey, Batch>();
    private static final ArrayList<Batch> ACTIVE = new ArrayList<Batch>();
    private static final Aero_AnimatedBatchLookupKey LOOKUP = new Aero_AnimatedBatchLookupKey();
    private static final Comparator<Batch> BY_STATE = new Comparator<Batch>() {
        public int compare(Batch left, Batch right) { return left.key.compareTo(right.key); }
    };
    private static String lastBoundPath;
    private static int queued, flushedInstances, flushedBatches, bonePageDrained, immediate;

    private Aero_AnimatedBatcher() {}

    public static void queueAnimated(Aero_MeshModel model, String texture,
            Aero_AnimationPlayback state, double x, double y, double z,
            float brightness, float partialTick, Aero_RenderOptions options) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        queueAnimated(model, texture, state.getBundle(), state.getDef(), state,
            x, y, z, brightness, partialTick, options);
    }

    public static void queueAnimated(Aero_MeshModel model, String texture,
            Aero_AnimationBundle bundle, Aero_AnimationDefinition definition,
            Aero_AnimationPlayback state, double x, double y, double z,
            float brightness, float partialTick, Aero_RenderOptions options) {
        Aero_AnimationClip clip = state != null ? state.getCurrentClip() : null;
        if (clip != null && clip.hasUvAnimation() && !UV_BATCH_ENABLED) {
            immediate++;
            bindTexturePath(texture);
            Aero_MeshRenderer.renderAnimatedPrecise(model, bundle, definition, state,
                x, y, z, brightness, partialTick, options);
            return;
        }
        if (!ENABLED) {
            immediate++;
            bindTexturePath(texture);
            Aero_MeshRenderer.renderAnimated(model, bundle, definition, state,
                x, y, z, brightness, partialTick, options);
            return;
        }
        Aero_RenderOptions resolved = options != null ? options : Aero_RenderOptions.DEFAULT;
        Batch batch = BATCHES.get(LOOKUP.set(model, texture, resolved));
        if (batch == null) {
            Aero_AnimatedBatchKey key = new Aero_AnimatedBatchKey(model, texture, resolved);
            batch = new Batch(key);
            BATCHES.put(key, batch);
        }
        if (batch.count == 0) ACTIVE.add(batch);
        batch.add(bundle, definition, state, x, y, z, brightness, partialTick, resolved);
        queued++;
    }

    static void bindTexturePath(String path) { Aero_TextureBinder.bind(path); }

    static void bindBatchTexture(Batch batch) {
        String path = batch.texturePath;
        if (path == null || path == lastBoundPath || path.equals(lastBoundPath)) return;
        bindTexturePath(path);
        lastBoundPath = path;
    }

    public static void flush() {
        if (ACTIVE.isEmpty()) return;
        lastBoundPath = null;
        if (SORT_ENABLED && ACTIVE.size() > 1) ACTIVE.sort(BY_STATE);
        for (int index = 0; index < ACTIVE.size(); index++) {
            Batch batch = ACTIVE.get(index);
            flushedInstances += batch.count;
            flushedBatches++;
            if (BONE_PAGE_DRAIN_MIN >= 0 && batch.count >= BONE_PAGE_DRAIN_MIN) {
                bonePageDrained += batch.count;
                Aero_MeshRenderer.renderAnimatedBatchUnbatched(batch);
            } else Aero_MeshRenderer.renderAnimatedBatch(batch);
            batch.clear();
        }
        ACTIVE.clear();
    }

    static void beginFrameCounters() {
        queued = flushedInstances = flushedBatches = bonePageDrained = immediate = 0;
        Aero_ClientArrayBuffer.beginFrameCounters();
        Aero_BatchPoseReuse.beginFrameCounters();
        Aero_BatchVertexReuse.beginFrameCounters();
        Aero_TessellatorBulkWriter.beginFrameCounters();
    }
    public static int queuedThisFrame() { return queued; }
    public static int flushedInstancesThisFrame() { return flushedInstances; }
    public static int flushedBatchesThisFrame() { return flushedBatches; }
    public static int bonePageDrainedInstancesThisFrame() { return bonePageDrained; }
    public static int immediateRendersThisFrame() { return immediate; }
    public static int clientArrayDrawsThisFrame() {
        return Aero_ClientArrayBuffer.drawsThisFrame();
    }
    public static int clientArrayVerticesThisFrame() {
        return Aero_ClientArrayBuffer.verticesThisFrame();
    }
    public static int batchPosesReusedThisFrame() { return Aero_BatchPoseReuse.reusedThisFrame(); }
    public static int batchPosesResolvedThisFrame() { return Aero_BatchPoseReuse.resolvedThisFrame(); }
    public static int batchVerticesTransformedThisFrame() {
        return Aero_BatchVertexReuse.transformedThisFrame();
    }
    public static int batchVertexTransformsReusedThisFrame() {
        return Aero_BatchVertexReuse.reusedThisFrame();
    }
    public static int tessellatorBulkVerticesThisFrame() {
        return Aero_TessellatorBulkWriter.verticesThisFrame();
    }

    private static int integer(String name, int fallback, int min, int max) {
        try {
            String raw = System.getProperty(name);
            if (raw == null) return fallback;
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException error) { return fallback; }
    }

    static final class Batch extends Aero_AnimatedBatch {
        Batch(Aero_AnimatedBatchKey key) { super(key); }
    }
}
