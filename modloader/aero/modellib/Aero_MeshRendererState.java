package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

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
 * AeroMesh Renderer by lucasrgt - aerocoding.dev
 * Renders OBJ models (Aero_MeshModel) using GL_TRIANGLES.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time
 *   - setColorOpaque_F called 4× per draw (vs N× in the naive approach)
 *   - Coordinate division by `sc` replaced with single multiplication
 *   - Smooth-light drawing lives in Aero_MeshSmoothLightRenderer
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc)
 *   - Bone/pivot resolution is memoized per (clip identity) on the model,
 *     so the per-group HashMap and linear-scan lookups happen only when
 *     the active clip changes
 *
 * Static geometry usage (TileEntitySpecialRenderer):
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, rotation, brightness);
 *
 * Animated part usage:
 *   // Render static geometry (everything except the named animated group)
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, 0, brightness);
 *   // Render animated group with per-tick angle + partial tick smoothing
 *   float angle = tile.fanAngle + (tile.isActive ? SPEED * partialTick : 0f);
 *   Aero_MeshRenderer.renderGroupRotated(MODEL, "fan",
 *       d + ox, d1 + oy, d2 + oz, brightness,
 *       pivotX, pivotY, pivotZ,   // pivot in model space (block units)
 *       angle, 0, 1, 0);          // angle + axis (Y-axis spin)
 *
 * Inventory usage:
 *   Aero_MeshRenderer.renderInventory(rb, MODEL);
 *
 * NOTE: uses Tessellator with GL_TRIANGLES — only call outside an active
 * startDrawingQuads() block. The TileEntitySpecialRenderer context is safe.
 */
class Aero_MeshRendererState {

    static final int MESH_ATTRIB_BITS =
        GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT
        | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TRANSFORM_BIT;

    static final boolean BONE_PAGES_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.bonepages"));
    static final boolean AT_REST_LISTS_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.atRestLists"));
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

    // Pose pool indexed by clip.boneNames[i]. Pre-resolved once per frame
    // so the hierarchical render walk + IK pre-pass can read poses by
    // ancestor index without re-resolving per child. Grows monotonically;
    // never shrinks (keeps the largest clip's working set hot).
    static Aero_BoneRenderPose[] POSE_POOL = Aero_MeshPoseRenderer.newPosePool(16);

    static final int ANIMATED_RENDER_CULLED = 0;
    static final int ANIMATED_RENDER_ACTIVE = 1;
    static final int ANIMATED_RENDER_STATIC_DONE = 2;

    

    // -----------------------------------------------------------------------
    // Full model render
    // -----------------------------------------------------------------------

    /**
     * Renders static geometry (triangles not in any named group) with flat lighting.
     *
     * @param brightness  base brightness (0.0–1.0), from getLightBrightness()
     */
    

    

    /**
     * Renders static geometry plus every named group at rest pose.
     * This is useful for distant animation LOD because it avoids clip
     * sampling, bone resolution and per-bone GL transforms.
     */
    

    

    

    

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
     * thread (single-threaded in Beta 1.7.3, so any tile-entity tick / TESR
     * call site is fine).
     *
     * <p><strong>When to actually call this.</strong> Beta has no stable
     * client-shutdown hook for ModLoader mods, and the GL driver releases
     * every list on context destruction anyway — calling this on game exit
     * is redundant. The intended call sites are:
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
     * Renders static geometry with smooth lighting (bilinear world sample above structure).
     *
     * @param world   current world
     * @param ox,oz   XZ world origin of the structure
     * @param topY    world Y above the structure top (e.g. originY + structureHeight)
     */
    

    

    // -----------------------------------------------------------------------
    // Named group render (for animated parts)
    // -----------------------------------------------------------------------

    /**
     * Draws a named group into the current GL matrix, with flat lighting.
     * Does NOT push/pop matrix — the caller is responsible for all GL transforms.
     * Use this inside a glPushMatrix / glPopMatrix block where you have already
     * applied translation and rotation for the animated part.
     *
     * @param groupName  OBJ object/group name (e.g. "fan", "piston", "gear")
     * @param brightness base brightness (0.0–1.0)
     */
    

    

    /**
     * Renders a named group with a rotation around a pivot point in model space.
     * Handles the full GL setup: push, translate to world position, apply rotation
     * around the pivot, draw, pop.
     */
    

    

    // -----------------------------------------------------------------------
    // Animated render (mini-GeckoLib)
    // -----------------------------------------------------------------------

    /**
     * Renders a complete model with keyframe animation.
     *
     * Renders static geometry and, for each named group in the model,
     * fetches keyframes from the active clip, interpolates position and rotation
     * at the current time, and applies the GL transform before drawing the group.
     *
     * Hot path: bone resolution (indexOfBone, childMap walk, prefix scan) is
     * memoized in model.boneRefsFor(clip), so per-frame work is bounded by
     * the GL transforms and the scratch-buffer keyframe samples.
     */
    

    

    /**
     * Renders a complete model with platform-neutral animation playback.
     * This overload is useful for entity helpers, tools and tests that do not
     * need the loader-specific NBT adapter.
     */
    

    

    /**
     * Bundle/def/state overload with procedural pose + IK chain hooks.
     * IK chains run between keyframe-pose resolution and the GL render
     * walk: the solver mutates intermediate-bone rotations to bring each
     * chain's end-effector close to its target, then the rendered pose
     * reflects the IK-corrected angles.
     */
    

    /**
     * Maximal overload with morph state on top of procedural + IK. The
     * morph state blends static-geometry vertex positions before emit;
     * named-group (per-bone animated) parts are not morphed in v0.2.0.
     */
    

    /**
     * Bundle/def/state overload with a procedural pose hook layered on top
     * of the keyframe pose — the canonical entry point for vehicles whose
     * turret/barrel/propeller follow runtime input.
     */
    

    

    

    

    /**
     * Renders with a playback object that already owns its definition/bundle.
     */
    

    

    

    

    

    /**
     * Renders the model with an {@link Aero_AnimationGraph} driving every
     * bone's pose. Bones are looked up by name (no hierarchy walk — graph
     * rendering is flat in v0.2.0 because Graph itself doesn't model
     * parent-child relationships). The bundle is used for pivot lookup.
     */
    

    

    /**
     * Stack overload with a procedural pose hook layered on top of the
     * blended pose from every layer.
     */
    

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    

    

    /**
     * Walks the resolved ancestor chain (root → leaf) of the given BoneRef
     * and applies each ancestor's pose to the current GL matrix, so a child
     * bone's geometry inherits every animated parent's transform. This is
     * what makes parent rotations propagate to children — Blockbench's
     * default animator behavior.
     *
     * <p>Returns the deepest ancestor's pose (the leaf) so the caller can
     * read its UV transform fields for the per-vertex emit.
     */
    

    

    

    

    

    

    /**
     * Resolves each {@link Aero_IkChain}'s named bones to indices + pivots
     * via the active clip, then dispatches to {@link Aero_CCDSolver}. Chain
     * names that are missing from the clip are silently skipped (caller
     * receives no exception so a transient missing bone in a partial state
     * doesn't crash the render).
     */
    

    /** Draws triangle groups at full brightness — used by Aero_InventoryRenderer. */
    

    /**
     * Static-geometry draw with morph-target blending. Per-vertex applies
     * {@code finalPos = base + Σ(weight × delta)} across every active
     * target. Skips back to the raw fast path when no targets have a
     * non-zero weight, so the caller doesn't need to gate this externally.
     */
    // Pooled scratch for drawGroupsMorph — see stationapi twin for rationale.
    static Aero_MorphTarget[] SCRATCH_MORPH_TARGETS = new Aero_MorphTarget[4];
    static float[] SCRATCH_MORPH_WEIGHTS = new float[4];

    

    /** Draws triangle groups with flat lighting (uniform brightness per group). */
    

    /**
     * UV-aware variant. Animated bones pass their pose's UV offset/scale so
     * the emit loop transforms each vertex's u/v before tessellation. When
     * the transform is identity (the default for any bone with no
     * uv_offset / uv_scale channels), the inner emit loop branches to the
     * raw path that allocates zero work per vertex.
     */
    

    /**
     * Draws triangle groups with smooth lighting using a precomputed light cache
     * over the structure footprint. Each unique (x,z) world column is sampled
     * once via getLightBrightness, then bilinearly interpolated at every
     * triangle centroid — replacing the previous 4 lookups per triangle.
     */
    

    

    

    

    

    

    

    

}
