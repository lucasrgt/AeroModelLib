package aero.modellib.model;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.skeletal.Aero_BonePageLists;
import aero.modellib.skeletal.Aero_MorphTarget;

/**
 * AeroMesh Model — container for triangulated OBJ models.
 *
 * Triangles are pre-classified into 4 brightness groups at parse time
 * (same directional system as Aero_JsonModelRenderer):
 *
 *   GROUP_TOP    (dominant ny, positive) → factor 1.0
 *   GROUP_BOTTOM (dominant ny, negative) → factor 0.5
 *   GROUP_NS     (dominant nz)           → factor 0.8
 *   GROUP_EW     (dominant nx)           → factor 0.6
 *
 * Classification happens during parsing (Aero_ObjLoader), not per frame.
 * This reduces setColorOpaque_F calls from O(N triangles) to 4.
 *
 * Each triangle is float[15]:
 *   [0-4]   vertex 0: x, y, z, u, v
 *   [5-9]   vertex 1: x, y, z, u, v
 *   [10-14] vertex 2: x, y, z, u, v
 *
 * Named groups (OBJ "o" / "g" directives):
 *   Triangles belonging to a named OBJ object/group are stored separately
 *   in namedGroups and excluded from the main groups array. This allows
 *   animated parts (fan, piston, gear) to be rendered independently with
 *   their own GL transforms, while the static geometry renders normally.
 *
 * Render-time caches:
 *   - getNamedGroupArray() returns a precomputed (name, tris) array so the
 *     animated render path can iterate without allocating a HashMap iterator.
 *   - boneRefsFor(clip, bundle) memoizes the resolved bone index and pivot
 *     for each named group against a given clip. The active clip rarely
 *     changes (state transitions only), so the per-frame cost collapses to
 *     a single identity check.
 */
public class Aero_MeshModel {

    public static final int GROUP_TOP    = 0;
    public static final int GROUP_BOTTOM = 1;
    public static final int GROUP_NS     = 2;
    public static final int GROUP_EW     = 3;

    public static final float[] BRIGHTNESS_FACTORS = {1.0f, 0.5f, 0.8f, 0.6f};

    public final String name;
    public final float scale;
    public final float invScale;

    /**
     * Static triangles per brightness group (excludes named groups).
     * {@code groups[GROUP_TOP][i] = float[15]} for the i-th top-facing
     * triangle. Treat as read-only — the renderers iterate this array
     * every frame and rely on it being stable.
     */
    public final float[][][] groups;

    /**
     * Named group triangles: {@code Map<String, float[][][]>}. Each entry
     * has the same 4-brightness-group structure as {@link #groups}. Empty
     * map if the OBJ has no named objects/groups.
     *
     * <p>The map is wrapped with {@link Collections#unmodifiableMap(Map)};
     * iteration is fine, mutation throws. The float arrays inside are raw
     * — treat them as read-only.
     */
    public final Map namedGroups;

    // Render-time caches (lazy, never invalidated — model topology is immutable).
    private NamedGroup[] cachedNamedGroups;
    private Aero_AnimationClip cachedClip;
    private Aero_AnimationBundle cachedBundle;
    private BoneRef[] cachedBoneRefs;
    private static final int BONE_REF_CACHE_SIZE = 4;
    private final Aero_AnimationClip[] cachedBoneRefClips = new Aero_AnimationClip[BONE_REF_CACHE_SIZE];
    private final Aero_AnimationBundle[] cachedBoneRefBundles = new Aero_AnimationBundle[BONE_REF_CACHE_SIZE];
    private final BoneRef[][] cachedBoneRefArrays = new BoneRef[BONE_REF_CACHE_SIZE][];
    private int cachedBoneRefNextSlot;
    private float[] cachedBounds;
    private SmoothLightData cachedStaticSmoothLightData;

    // Display-list cache for the at-rest render path (groups + namedGroups
    // composed at rest pose). Renderer-managed: Aero_MeshRenderer compiles
    // on first static draw and stores the 4 GL list IDs (one per brightness
    // bucket). Empty buckets get id 0 — caller skips them. Compile failure
    // flips the failed flag so we don't hammer glGenLists every frame.
    // Pure ints, no GL imports — keeps this class shared across runtimes.
    private final Aero_MeshDisplayListState displayLists = new Aero_MeshDisplayListState();

    // Display-list cache for rigid animated groups. The platform renderer
    // stores one optional page set for static geometry plus one optional page
    // set per named group. Kept here so all render overloads share the same
    // compiled GL ids for a model instance.
    /**
     * Optional morph targets keyed by name. Mutable holder — load-time
     * code can attach targets after construction via
     * {@link #attachMorphTarget(Aero_MorphTarget)}. Renderer fast-paths
     * skip blending when this is empty or all weights are zero.
     */
    private final Aero_MorphTargetRegistry morphTargets = new Aero_MorphTargetRegistry();

    public Aero_MeshModel(String name, float[][][] groups, float scale, Map namedGroups) {
        if (scale == 0f) throw new IllegalArgumentException("scale must be non-zero");
        this.name = name;
        this.groups = groups;
        this.scale = scale;
        this.invScale = 1f / scale;
        this.namedGroups = Collections.unmodifiableMap(namedGroups);
    }

    /**
     * Attaches a morph variant. Targets are validated topology-side at
     * construction of {@link Aero_MorphTarget#fromTargetMesh}, so this
     * method only registers the named entry.
     */
    public void attachMorphTarget(Aero_MorphTarget target) {
        morphTargets.attach(target);
    }

    /** Returns a morph target by name, or null if absent. */
    public Aero_MorphTarget getMorphTarget(String name) {
        return morphTargets.get(name);
    }

    /** True if at least one morph target is registered. Render fast-path probe. */
    public boolean hasMorphTargets() {
        return morphTargets.hasTargets();
    }

    /**
     * Returns the cached display-list IDs for the at-rest composition, or
     * null if not yet compiled. {@code int[4]} indexed by brightness bucket;
     * a zero entry means the bucket has no geometry and the caller should
     * skip it. Renderer-only state — model code never reads it.
     */
    public int[] getAtRestListIds() {
        return displayLists.atRestIds();
    }

    /** Stores compiled list IDs (renderer-only). */
    public void setAtRestListIds(int[] ids) {
        displayLists.atRestIds(ids);
    }

    /**
     * True if at-rest list compilation already failed once. Renderer uses
     * this to avoid retrying glGenLists every frame.
     */
    public boolean atRestListsCompileFailed() {
        return displayLists.atRestFailed();
    }

    /** Marks at-rest list compilation as permanently failed (renderer-only). */
    public void markAtRestListsCompileFailed() {
        displayLists.markAtRestFailed();
    }

    /**
     * Atomically returns the cached at-rest list IDs and clears the model's
     * cache state (failed flag included so a recompile is allowed afterwards).
     * The renderer's {@code disposeModel} hook calls this and then issues
     * {@code glDeleteLists} on every non-zero id — keeps the API in core
     * (no GL dependency) while still letting the platform-specific renderer
     * release driver-side handles.
     */
    public int[] extractAndClearAtRestListIds() {
        return displayLists.clearAtRest();
    }

    /** Returns cached rigid animated display-list pages, or null if not compiled. */
    public Aero_BonePageLists getBonePageLists() {
        return displayLists.bonePages();
    }

    /** Stores rigid animated display-list pages (renderer-only). */
    public void setBonePageLists(Aero_BonePageLists lists) {
        displayLists.bonePages(lists);
    }

    /** True if animated page-list compilation failed once this model lifetime. */
    public boolean bonePageListsCompileFailed() {
        return displayLists.bonePagesFailed();
    }

    /** Marks animated page-list compilation as failed (renderer-only). */
    public void markBonePageListsCompileFailed() {
        displayLists.markBonePagesFailed();
    }

    /**
     * Atomically returns cached animated page lists and clears the cache
     * state so the renderer can delete GL ids and allow a future recompile.
     */
    public Aero_BonePageLists extractAndClearBonePageLists() {
        return displayLists.clearBonePages();
    }

    /** Convenience constructor: scale=1, empty named groups. */
    public Aero_MeshModel(String name, float[][][] groups) {
        this(name, groups, 1.0f, new java.util.HashMap());
    }

    /** Total triangle count in static geometry (excludes named groups). */
    public int triangleCount() {
        return Aero_MeshGeometryMetadata.triangleCount(groups);
    }

    /** Total triangle count in a named group, or 0 if not found. */
    public int triangleCountForGroup(String groupName) {
        return Aero_MeshGeometryMetadata.triangleCount(getNamedGroup(groupName));
    }

    /** Returns a named group's 4 brightness buckets, or null if absent. */
    public float[][][] getNamedGroup(String groupName) {
        return (float[][][]) namedGroups.get(groupName);
    }

    /**
     * Returns the model's axis-aligned bounding box in block units, computed
     * once and cached. Used by Aero_InventoryRenderer to center and scale the
     * model into a slot — without this cache, every inventory icon paints
     * O(triangles) of work just measuring the model.
     *
     * @return float[6] = {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public float[] getBounds() {
        float[] cached = cachedBounds;
        if (cached != null) return cached;
        cached = Aero_MeshGeometryMetadata.bounds(groups, getNamedGroupArray(), invScale);
        cachedBounds = cached;
        return cached;
    }

    /**
     * Returns cached smooth-light metadata for static geometry.
     *
     * The renderer uses this to avoid rescanning every triangle each frame just
     * to derive the XZ light footprint and triangle centroid sample positions.
     */
    public SmoothLightData getStaticSmoothLightData() {
        SmoothLightData cached = cachedStaticSmoothLightData;
        if (cached != null) return cached;
        cached = Aero_MeshGeometryMetadata.smoothLight(groups, invScale);
        cachedStaticSmoothLightData = cached;
        return cached;
    }

    /**
     * Returns the named groups as an array, computed once and cached.
     * Used by the animated render path to avoid allocating a HashMap
     * iterator + Map.Entry views every frame.
     */
    public NamedGroup[] getNamedGroupArray() {
        NamedGroup[] arr = cachedNamedGroups;
        if (arr != null) return arr;
        arr = new NamedGroup[namedGroups.size()];
        int i = 0;
        Iterator it = namedGroups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            arr[i++] = new NamedGroup((String) e.getKey(), (float[][][]) e.getValue());
        }
        cachedNamedGroups = arr;
        return arr;
    }

    /**
     * Returns the resolved (bone index, pivot) for each named group against
     * the given clip. Single-slot cache keyed by clip+bundle identity — the
     * active clip rarely changes between frames, so this collapses the
     * per-group HashMap and prefix-scan lookups into a single ref-equality check.
     *
     * @param clip   the active clip; may be null (returns refs with boneIdx=-1
     *               and pivot from the bundle)
     * @param bundle the animation bundle (used to look up pivots and childMap)
     */
    public BoneRef[] boneRefsFor(Aero_AnimationClip clip, Aero_AnimationBundle bundle) {
        if (clip == cachedClip && bundle == cachedBundle && cachedBoneRefs != null) {
            return cachedBoneRefs;
        }
        for (int c = 0; c < BONE_REF_CACHE_SIZE; c++) {
            BoneRef[] refs = cachedBoneRefArrays[c];
            if (refs != null && clip == cachedBoneRefClips[c] && bundle == cachedBoneRefBundles[c]) {
                cachedClip = clip;
                cachedBundle = bundle;
                cachedBoneRefs = refs;
                return refs;
            }
        }

        BoneRef[] refs = Aero_MeshBoneResolver.resolve(getNamedGroupArray(), clip, bundle);

        cachedClip     = clip;
        cachedBundle   = bundle;
        cachedBoneRefs = refs;
        int slot = cachedBoneRefNextSlot;
        cachedBoneRefClips[slot] = clip;
        cachedBoneRefBundles[slot] = bundle;
        cachedBoneRefArrays[slot] = refs;
        cachedBoneRefNextSlot = (slot + 1) & (BONE_REF_CACHE_SIZE - 1);
        return refs;
    }

    private static final int[] EMPTY_INT = new int[0];
    private static final String[] EMPTY_STRING = new String[0];
    private static final float[][] EMPTY_PIVOTS = new float[0][];
    /** Pair of (group name, triangles) — used by the animated render path. */
    public static final class NamedGroup {
        public final String name;
        public final float[][][] tris;
        NamedGroup(String name, float[][][] tris) {
            this.name = name;
            this.tris = tris;
        }
    }

    /**
     * Resolved bone hierarchy for a named group against a clip.
     *
     * <p>{@link #boneIdx} / {@link #boneName} / {@link #pivot} describe the
     * <em>deepest</em> animated ancestor — the bone whose pose is applied
     * last when rendering. The {@link #ancestorBoneIdx} /
     * {@link #ancestorBoneNames} / {@link #ancestorPivots} arrays hold the
     * full chain of animated ancestors from root to deepest, inclusive,
     * so the renderer can compose parent transforms hierarchically (parent
     * rotation moves child along, exactly like Blockbench's animator).
     *
     * <p>For groups without animation in the clip, the chain length is 0
     * and {@link #boneIdx} is -1 — the renderer skips them entirely (the
     * group's vertices stay at rest in absolute coordinates).
     */
    public static final class BoneRef {
        public final int boneIdx;             // -1 if no bone resolved
        public final String boneName;         // resolved animation bone name, or null
        public final float[] pivot;           // never null (falls back to bundle's zero-pivot)
        public final int[] ancestorBoneIdx;   // chain root → ... → boneIdx (inclusive). length 0 if boneIdx == -1
        public final String[] ancestorBoneNames; // matching names for procedural pose dispatch
        public final float[][] ancestorPivots;   // each ancestor's pivot (for applyPose); never null entries

        BoneRef(int boneIdx, String boneName, float[] pivot,
                int[] ancestorBoneIdx, String[] ancestorBoneNames, float[][] ancestorPivots) {
            this.boneIdx = boneIdx;
            this.boneName = boneName;
            this.pivot   = pivot;
            this.ancestorBoneIdx = ancestorBoneIdx;
            this.ancestorBoneNames = ancestorBoneNames;
            this.ancestorPivots = ancestorPivots;
        }

        /**
         * Convenience constructor for tests + manual procedural-pose flows
         * where the bone has no animated parent — builds a single-element
         * ancestor chain containing just this bone.
         */
        public BoneRef(int boneIdx, String boneName, float[] pivot) {
            this(boneIdx, boneName, pivot,
                boneIdx >= 0 ? new int[]{boneIdx} : EMPTY_INT,
                boneIdx >= 0 ? new String[]{boneName} : EMPTY_STRING,
                boneIdx >= 0 ? new float[][]{pivot} : EMPTY_PIVOTS);
        }
    }

    /** Precomputed static geometry data for smooth-light rendering. */
    public static final class SmoothLightData {
        public final boolean hasTriangles;
        public final float minX;
        public final float maxX;
        public final float minZ;
        public final float maxZ;
        public final float[][] centroidX;
        public final float[][] centroidZ;

        SmoothLightData(boolean hasTriangles, float minX, float maxX, float minZ, float maxZ,
                        float[][] centroidX, float[][] centroidZ) {
            this.hasTriangles = hasTriangles;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.centroidX = centroidX;
            this.centroidZ = centroidZ;
        }
    }
}
