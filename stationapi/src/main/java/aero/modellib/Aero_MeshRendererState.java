package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.animation.Aero_AnimationStack;
import aero.modellib.animation.graph.Aero_AnimationGraph;
import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_AnimationTickLOD;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BonePageLists;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;
import aero.modellib.skeletal.Aero_IkChain;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.skeletal.Aero_MorphTarget;
import aero.modellib.skeletal.Aero_ProceduralPose;
import aero.modellib.util.Aero_Profiler;

/**
 * AeroMesh Renderer (StationAPI/Yarn port). Same algorithm as the ModLoader
 * version, with Yarn-mapped Tessellator + World API.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time.
 *   - Tessellator color called 4× per draw (vs N× naive).
 *   - Coordinate division by `sc` replaced with single multiplication.
 *   - Smooth-light drawing lives in Aero_MeshSmoothLightRenderer.
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc).
 *   - Bone/pivot resolution memoized per (clip identity) on the model.
 */
class Aero_MeshRendererState {

    static final int MESH_ATTRIB_BITS =
        GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT
        | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TRANSFORM_BIT;

    static final boolean BONE_PAGES_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.bonepages"));
    static final int BONE_PAGES_MIN_TRIS =
        Math.max(0, Integer.getInteger("aero.bonepages.minTris", 24).intValue());
    static final boolean SKELETAL_LOD_ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.skeletalLod"));
    static final double SKELETAL_LOD_DISTANCE =
        Aero_MeshGlStateRenderer.doubleProperty("aero.skeletalLod.distance", 48.0d, 0.0d, 4096.0d);
    static final int SKELETAL_LOD_DEPTH =
        Math.max(0, Integer.getInteger("aero.skeletalLod.depth", 1).intValue());

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    static final float[] SCRATCH_ROT = new float[3];
    static final float[] SCRATCH_POS = new float[3];
    static final float[] SCRATCH_SCL = new float[3];
    static final float[] SCRATCH_PIVOT = new float[3];
    static final Aero_BoneRenderPose SCRATCH_POSE = new Aero_BoneRenderPose();
    static final IdentityHashMap<Aero_MeshModel, BatchPlanCache> BATCH_PLAN_CACHE =
        new IdentityHashMap<Aero_MeshModel, BatchPlanCache>();

    static int atRestRendersThisFrame;
    static int atRestListCallsThisFrame;
    static int atRestTessFallbacksThisFrame;

    // Pose pool indexed by clip.boneNames[i]. Pre-resolved once per frame
    // so the hierarchical render walk + any IK pre-pass can read poses by
    // ancestor index without re-resolving per child.
    static Aero_BoneRenderPose[] POSE_POOL = Aero_MeshPoseRenderer.newPosePool(16);

    static final int ANIMATED_RENDER_CULLED = 0;
    static final int ANIMATED_RENDER_ACTIVE = 1;
    static final int ANIMATED_RENDER_STATIC_DONE = 2;

    // -----------------------------------------------------------------------
    // Frustum cull glue
    // -----------------------------------------------------------------------
    //
    // BlockEntity / EntityRenderDispatcher iterate every loaded entity in
    // distance range and never check the view frustum. When animated LOD is
    // bumped past vanilla's 64 block cap, half of those renders happen for
    // entities behind the player — full Tessellator + GL dispatch cost,
    // zero pixels on screen. updateCameraForward() refreshes the cached
    // forward vector from the local player; the actual cull is a single
    // dot product per render call (Aero_FrustumCull.isLikelyVisible).

    /**
     * Refreshes Aero_FrustumCull's cached forward vector from the local
     * player. Cheap (4 trig + 3 muls); no allocation. Called at the entry
     * of every public render method so a renderer that wraps us doesn't
     * have to know about frustum state.
     */
    

    // -----------------------------------------------------------------------
    // Full model render
    // -----------------------------------------------------------------------

    

    

    

    

    

    

    // -----------------------------------------------------------------------
    // Display-list fast-path for the at-rest composition
    // -----------------------------------------------------------------------
    //
    // Replaces N Tessellator.draw cycles (FloatBuffer fill + 4× pointer setup
    // + getBufferAddress JNI hit + glDrawArrays JNI hit) with 4 glCallList
    // calls — one per brightness bucket. Geometry+UV is baked into the list
    // once at first render; brightness × tint × alpha is applied via glColor4f
    // before each glCallList so the same list serves every render of the
    // model regardless of world light or render-options state.
    //
    // Tradeoff: 4 glCallList vs 1 Tessellator.draw, but each glCallList is
    // a single JNI driver call replaying a pre-baked command stream. The
    // Tessellator path's per-call overhead (buffer.put N vertices, JNI for
    // getAddress, JNI for each pointer setter, JNI for glDrawArrays) is gone.
    //
    // Skips empty buckets (id 0) so models with concentrated geometry pay
    // for at most as many glCallList invocations as they have non-empty
    // buckets. Falls back to the Tessellator path if glGenLists returns 0
    // (out of list ids — extremely rare on Beta 1.7.3 / OpenGL 1.1).

    

    

    

    

    

    /**
     * Releases the GL display lists cached on a model. Must run on the GL
     * thread (single-threaded in Beta 1.7.3, so any block-entity tick / BER
     * call site is fine).
     *
     * <p><strong>When to actually call this.</strong> Beta has no stable
     * client-shutdown hook, and the GL driver releases every list on
     * context destruction anyway — calling this on game exit is redundant.
     * The intended call sites are:
     * <ul>
     *   <li>Resource-pack reload — bind a new texture, dispose the model,
     *       next render recompiles the lists with the new texture coords
     *       baked in.</li>
     *   <li>Model hot-swap during dev — replace the {@code Aero_MeshModel}
     *       reference with a freshly loaded one and dispose the old one
     *       before dropping it.</li>
     *   <li>Tooling / CI — disposing models between tests so the JVM can
     *       be reused without leaking list IDs.</li>
     * </ul>
     *
     * <p>Idempotent: a model that's never been rendered (or already disposed)
     * is a no-op. After dispose, the next render of the model recompiles
     * from scratch.
     */
    

    

    

    // -----------------------------------------------------------------------
    // Bone-page display lists for rigid animated groups
    // -----------------------------------------------------------------------

    

    

    /**
     * Render-thread cache warmup hook used by {@link Aero_Prewarm}. It is a
     * no-op on failed/empty models and uses the same lazy compilers as normal
     * rendering, so visual output remains unchanged.
     */
    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    // -----------------------------------------------------------------------
    // Named group render (for animated parts)
    // -----------------------------------------------------------------------

    

    

    

    

    // -----------------------------------------------------------------------
    // Animated render
    // -----------------------------------------------------------------------

    

    

    

    

    /**
     * Exact animated path for clips whose visual output depends on per-vertex
     * UVs. This intentionally bypasses animated display-list pages while still
     * keeping the normal culling/LOD gate. Use it for uv_offset/uv_scale clips
     * such as scrolling belts or runes.
     */
    

    

    /**
     * Bundle/def/state overload with procedural pose + IK chain hooks.
     */
    

    /** Maximal overload with morph state. */
    

    /**
     * Bundle/def/state overload with a procedural pose hook layered on top
     * of the keyframe pose — the canonical entry point for vehicles whose
     * turret/barrel/propeller follow runtime input.
     */
    

    

    

    

    

    

    

    /**
     * Render entry point for the multi-controller / additive layering API
     * ({@link Aero_AnimationStack}). Walks the model's named groups by
     * NAME and asks the stack for each bone's combined rotation /
     * position / scale, so a base layer + secondary layers (head look,
     * arm wave, hit reaction) all compose into a single GL transform per
     * bone.
     *
     * <p>The stack is responsible for ticking its own layers — this
     * renderer only samples them at {@code partialTick} for visual frames.
     *
     * <p>Pivots come from the FIRST layer's bundle that knows about the
     * bone (via {@code Aero_AnimationBundle.hasPivot} +
     * {@code getPivotInto}); secondary layers usually share the same
     * bundle so the pivot resolves on the first hit. Bones missing from
     * every layer's clip render at the origin, no GL transform applied.
     */
    

    

    /**
     * Renders the model with an {@link Aero_AnimationGraph} driving every
     * bone's pose. Bones are looked up by name (graph rendering is flat
     * in v0.2.0 — no hierarchy walk). The bundle provides pivot lookup.
     */
    

    

    /**
     * Stack overload with a procedural pose hook layered on top of the
     * blended pose from every layer.
     */
    

    // -----------------------------------------------------------------------
    // Batched animated render — v3.0 BE batching path
    // -----------------------------------------------------------------------
    //
    // Drains an Aero_AnimatedBatcher.Batch in a single Tessellator session
    // per bone (vs one per instance × bone in the non-batched path). Per-
    // vertex CPU matrix transforms replace the GL matrix-stack push/pop +
    // glRotate/glTranslate sequence each instance previously paid.
    //
    // Win is bounded by the GL state-setup cost saved per cycle; for the
    // stress test's 9-10 motor grid sharing one model with one named bone
    // ("rotor"), this collapses 9-10 tess cycles per frame into 1.
    //
    // Constraints (v3.0):
    //   - Flat-skeleton only: bones with ancestorBoneIdx.length > 1 fall
    //     back to per-instance rendering (composing nested ancestor poses
    //     in CPU is doable but adds complexity for marginal gain — most
    //     mass-produced static-machine BEs are flat).
    //   - No procedural pose / IK / morph batching — those route to the
    //     non-batched overload.

    

    

    /**
     * Per-call scratch grown on demand. Holds {@code [instanceCount][boneCount]}
     * resolved poses for the duration of one batched render. The poses
     * themselves are pulled from {@link #POSE_POOL} (per-bone reuse) and
     * snapshotted into the matching {@code BoneRef} index.
     */
    static Aero_BoneRenderPose[][] BATCH_POSES = new Aero_BoneRenderPose[16][];
    static boolean[][] BATCH_POSE_ACTIVE = new boolean[16][];

    

    

    /**
     * Resolves per-instance bone poses for the batch. Returns false if
     * any instance has a nested ancestor chain (multi-bone-deep), in
     * which case the caller must fall back to per-instance rendering.
     */
    

    

    

    

    static final class BatchPlanCache {
        static final int SIZE = 8;
        private final Aero_AnimationClip[] clips = new Aero_AnimationClip[SIZE];
        private final Aero_AnimationBundle[] bundles = new Aero_AnimationBundle[SIZE];
        private final BatchPlan[] plans = new BatchPlan[SIZE];
        private int nextSlot;

        BatchPlan get(Aero_MeshModel model, Aero_AnimationClip clip,
                      Aero_AnimationBundle bundle, Aero_MeshModel.NamedGroup[] entries) {
            for (int i = 0; i < SIZE; i++) {
                BatchPlan plan = plans[i];
                if (plan != null && clips[i] == clip && bundles[i] == bundle) return plan;
            }
            BatchPlan plan = Aero_MeshBatchRenderer2.buildBatchPlan(model, clip, bundle, entries);
            int slot = nextSlot;
            clips[slot] = clip;
            bundles[slot] = bundle;
            plans[slot] = plan;
            nextSlot = (slot + 1) & (SIZE - 1);
            return plan;
        }
    }

    

    

    static final class BatchPlan {
        final boolean batchableFlat;
        final int[] entryBoneIdx;
        final int[] drawableEntries;
        final boolean hasStaticGeometry;

        BatchPlan(boolean batchableFlat, int[] entryBoneIdx,
                  int[] drawableEntries, boolean hasStaticGeometry) {
            this.batchableFlat = batchableFlat;
            this.entryBoneIdx = entryBoneIdx;
            this.drawableEntries = drawableEntries;
            this.hasStaticGeometry = hasStaticGeometry;
        }
    }

    /**
     * Emits all batch instances of {@code tris} for a single brightness
     * bucket. Returns the last-set {@code bright} value so the caller can
     * carry it across buckets and avoid re-issuing identical
     * {@code tess.color(...)} calls (the dedup hits ~80% of the time when
     * many BEs in a batch share the same chunk lighting).
     */
    

    

    /**
     * Fast path for UV-only/translation-only bones. With identity
     * rotation+scale the pivot cancels out, so each vertex is just model
     * position plus instance translation plus pose offset.
     */
    

    /**
     * Fast path for scale/translation bones. Avoids all trig and rotation
     * math while keeping the same pivoted scale order as Aero_MeshPoseRenderer.applyPose().
     */
    

    /**
     * Like {@link #emitBoneInstanceBatched} but for the rest-pose case
     * (no animated bone — named group exists in the OBJ but has no
     * matching pivot/clip in the bundle). Mirrors the unbatched path's
     * behavior of still drawing the geometry at its model-space
     * position when {@code applyPoseChain} returns null.
     */
    

    /**
     * Drains a batch by rendering each instance through the unbatched
     * path. Used when the batch contains a model that can't be safely
     * batched (multi-bone skeleton, etc).
     */
    

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    

    /**
     * Walks the resolved ancestor chain (root → leaf) of the given BoneRef
     * and applies each ancestor's pose to the current GL matrix, so a child
     * bone's geometry inherits every animated parent's transform.
     */
    

    

    

    

    

    // Render-thread-only scratch reused by runIkChains. Avoids three
    // allocations per IK chain per frame on entities with rigging
    // (e.g. turret blocks). The solver consumes inputs synchronously and
    // does not retain references after returning, so these are safe to
    // recycle across chains within a single render call.
    //
    // boneIdx and pivots are sized to the current chain length: same-size
    // chains in the same scene reuse the buffer; a chain-length change
    // realloc once and steady state goes back to zero allocs. The solver
    // reads {@code chainBoneIdx.length} as the chain length, so the
    // buffer must be exactly chain-sized — grow-only would feed the
    // solver stale tail entries.
    static final float[] SCRATCH_IK_TARGET = new float[3];
    static int[] SCRATCH_IK_BONE_IDX;
    static float[][] SCRATCH_IK_PIVOTS;

    

    

    

    /**
     * Static-geometry draw with morph-target blending. Per-vertex applies
     * {@code finalPos = base + Σ(weight × delta)} across active targets.
     */
    // Pooled scratch for drawGroupsMorph — render thread is single-threaded
    // in Beta 1.7.3 so static reuse is safe. Pre-sized to 4 since 99% of
    // morph cases have ≤4 active targets; grows on demand.
    static Aero_MorphTarget[] SCRATCH_MORPH_TARGETS = new Aero_MorphTarget[4];
    static float[] SCRATCH_MORPH_WEIGHTS = new float[4];

    

    

    /**
     * UV-aware variant. Per-bone calls pass pose UV offset/scale so the
     * emit loop transforms each vertex's u/v before tessellation. Identity
     * UV branches to the raw path that allocates zero work per vertex —
     * any bone without uv_offset/uv_scale channels pays nothing.
     */
    

    

    

    

    

    

    

    

    

    

}
