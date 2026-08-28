package aero.modellib.animation;

import aero.modellib.util.Aero_Profiler;

/**
 * Platform-neutral animation playback state.
 *
 * ModLoader and StationAPI wrappers extend this class only to add their NBT
 * read/write adapters. Tick timing, clip caching and interpolation live here
 * so both loaders share exactly the same behavior.
 */
public class Aero_AnimationPlayback {

    private int currentState;

    protected final Aero_AnimationDefinition def;
    protected final Aero_AnimationBundle bundle;

    private float playbackTime;
    private float prevPlaybackTime;

    private Aero_AnimationClip cachedClip;
    private int cachedClipState = -1;
    private final Aero_AnimationSampleCursors cursors = new Aero_AnimationSampleCursors();
    private final Aero_AnimationTransition transition = new Aero_AnimationTransition();
    private final float[] pivotScratch = new float[3];

    // Optional keyframe-event sink — set by the consumer to receive
    // sound/particle/custom events from the playing clip.
    private Aero_AnimationEventListener eventListener;

    public Aero_AnimationPlayback(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this.def = def;
        this.bundle = bundle;
        this.currentState = 0;
        this.playbackTime = 0f;
        this.prevPlaybackTime = 0f;
    }

    /**
     * Registers a listener that receives non-pose keyframe events fired by
     * the active clip during {@link #tick()}. Pass {@code null} to clear.
     */
    public void setEventListener(Aero_AnimationEventListener listener) {
        this.eventListener = listener;
    }

    /** Advances playback by one game tick. Call before setState(). */
    public void tick() {
        Aero_Profiler.start("aero.playback.tick");
        try {
            tickBody();
        } finally {
            Aero_Profiler.end("aero.playback.tick");
        }
    }

    private void tickBody() {
        transition.tick();
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        boolean wrapped = false;
        if (clip.loop == Aero_AnimationLoop.LOOP) {
            if (playbackTime >= clip.length) {
                playbackTime = playbackTime % clip.length;
                if (prevPlaybackTime >= clip.length) prevPlaybackTime = prevPlaybackTime % clip.length;
                wrapped = true;
            }
        } else if (playbackTime >= clip.length) {
            // PLAY_ONCE and HOLD both clamp at the final keyframe — the
            // visual difference is captured by isFinished(), which only
            // PLAY_ONCE flips to true so callers can chain into the next
            // clip while HOLD keeps holding the last pose.
            playbackTime = clip.length;
            prevPlaybackTime = clip.length;
        }

        // Fire any non-pose keyframes whose timestamp lies in the just-
        // advanced window. For looped wraps the window splits in two
        // (prev..length], then [0..now) so events at the very end and the
        // very start of the loop both fire each cycle without being double-
        // counted. Non-wrap windows use the standard half-open interval.
        if (eventListener != null && clip.hasEvents()) {
            if (wrapped) {
                Aero_AnimationEventDispatcher.fire(eventListener, clip, prevPlaybackTime, clip.length, false);
                Aero_AnimationEventDispatcher.fire(eventListener, clip, 0f, playbackTime, true);
            } else {
                Aero_AnimationEventDispatcher.fire(
                    eventListener, clip, prevPlaybackTime, playbackTime, false);
            }
        }
    }

    /**
     * True when the active clip has reached its final keyframe AND its
     * loop type is {@link Aero_AnimationLoop#PLAY_ONCE}. HOLD
     * clips never finish (their pose just stays). LOOP clips never finish
     * (they wrap forever). Useful as a signal to advance the state machine
     * to the next clip in a chain.
     */
    public boolean isFinished() {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null) return false;
        return clip.loop == Aero_AnimationLoop.PLAY_ONCE
            && playbackTime >= clip.length;
    }

    /**
     * Seeks the active clip without firing keyframe events. Both current and
     * previous playback times are updated so the next render frame does not
     * interpolate across the old position.
     *
     * <p>This is useful when many identical machines are placed together and
     * should start at deterministic but different phases. Call after
     * {@link #setState(int)} selects the looping state.
     */
    public void setPlaybackTime(float timeSeconds) {
        Aero_AnimationClip clip = getCurrentClip();
        float time = Aero_AnimationTime.normalize(clip, timeSeconds);
        playbackTime = time;
        prevPlaybackTime = time;
        cursors.reset();
    }

    /**
     * Seeks the active clip to a normalized phase in [0, 1). Values outside
     * that range wrap, so callers can feed a hash-derived float directly.
     */
    public void setLoopPhase(float phase01) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            setPlaybackTime(0f);
            return;
        }
        float phase = phase01 - (float) Math.floor(phase01);
        setPlaybackTime(phase * clip.length);
    }

    /**
     * Changes current state. Playback resets only when the target clip changes.
     */
    public void setState(int stateId) {
        if (stateId == currentState) return;

        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);

        currentState = stateId;
        cachedClipState = -1;

        boolean clipChanged = (newClip == null) ? (oldClip != null) : !newClip.equals(oldClip);
        if (clipChanged) {
            playbackTime = 0f;
            prevPlaybackTime = 0f;
            cursors.reset();
        }
    }

    /**
     * Same as {@link #setState(int)} but smoothly fades the previous pose
     * into the new clip's start over the next {@code ticks} game ticks.
     * Call before {@link #tick()} on the same frame so the transition
     * counts down starting from the next tick.
     *
     * <p>If {@code ticks <= 0} or the target clip is identical, behaves
     * like the bare setState (snap, no fade).
     *
     * <p>Snapshot covers every bone in the OLD clip — bones that only exist
     * in the NEW clip start from the new value with no blend (no per-bone
     * "wrong identity" pop), and bones that only exist in the OLD clip
     * fade out to zero over the transition.
     */
    public void setStateWithTransition(int stateId, int ticks) {
        if (ticks <= 0) {
            setState(stateId);
            transition.cancel();
            return;
        }
        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);
        boolean clipChanged = (newClip == null) ? (oldClip != null) : !newClip.equals(oldClip);
        if (!clipChanged) {
            // Same clip — nothing to blend, just adopt the state.
            currentState = stateId;
            return;
        }
        transition.start(getCurrentClip(), playbackTime, ticks);
        setState(stateId);
    }

    /** True while a transition is still ramping the snapshot toward the new clip. */
    public boolean inTransition() {
        return transition.active();
    }

    /**
     * Returns the blend ratio for the current frame: 0 = full snapshot
     * pose, 1 = full new clip. Linear over the configured transition
     * duration; clamps at 1 once the transition ends.
     */
    public float getTransitionAlpha(float partialTick) {
        return transition.alpha(partialTick);
    }

    /**
     * Samples rotation for {@code boneIdx} in {@code clip} at {@code time},
     * blending against the snapshot pose for {@code boneName} when in
     * transition. Returns true if {@code out} now contains a usable value
     * (either a fresh sample, a blended sample, or a fading-out snapshot).
     *
     * <p>Renderers should call this in place of {@link Aero_AnimationClip#sampleRotInto}
     * to get free fade-in behaviour after {@link #setStateWithTransition}.
     */
    public boolean sampleRotBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleRotInto(boneIdx, time, out, cursors.rotation(clip));
        return transition.blend(Aero_AnimationTransition.ROTATION, boneName, partialTick, got, out);
    }

    public boolean samplePosBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.samplePosInto(boneIdx, time, out, cursors.position(clip));
        return transition.blend(Aero_AnimationTransition.POSITION, boneName, partialTick, got, out);
    }

    public boolean sampleSclBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleSclInto(boneIdx, time, out, cursors.scale(clip));
        // Scale rests at 1 (identity), not 0. A bone present in the OLD clip
        // but absent from the NEW one must fade its scale toward 1, not 0 —
        // otherwise the part collapses into the pivot during crossfade.
        return transition.blend(Aero_AnimationTransition.SCALE, boneName, partialTick, got, out);
    }

    /**
     * Samples the per-bone UV offset (rest = 0) — matches scroll/atlas-frame
     * style animations. Output components: x=u, y=v (z reserved).
     */
    public boolean sampleUvOffsetBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                         float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleUvOffsetInto(boneIdx, time, out, cursors.uvOffset(clip));
        // UV offset rests at 0 (identity, no scroll).
        return transition.blend(Aero_AnimationTransition.UV_OFFSET, boneName, partialTick, got, out);
    }

    /**
     * Samples the per-bone UV scale (rest = 1). Use to pulse texture zoom
     * or pick atlas frames in combination with sampleUvOffset.
     */
    public boolean sampleUvScaleBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                        float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleUvScaleInto(boneIdx, time, out, cursors.uvScale(clip));
        // UV scale rests at 1 (identity).
        return transition.blend(Aero_AnimationTransition.UV_SCALE, boneName, partialTick, got, out);
    }

    /**
     * Returns playback time interpolated for a render frame.
     *
     * Handles loop wrap without jumping backward across the clip boundary.
     */
    public float getInterpolatedTime(float partialTick) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) return 0f;

        float cur = playbackTime;
        float prev = prevPlaybackTime;

        if (clip.loop == Aero_AnimationLoop.LOOP && cur < prev) {
            cur += clip.length;
            float t = prev + (cur - prev) * partialTick;
            return t % clip.length;
        }

        return prev + (cur - prev) * partialTick;
    }

    /** Returns the currently active clip, or null if the state has no clip. */
    public Aero_AnimationClip getCurrentClip() {
        if (cachedClipState == currentState) return cachedClip;
        String clipName = def.getClipName(currentState);
        cachedClip = clipName != null ? bundle.getClip(clipName) : null;
        cachedClipState = currentState;
        return cachedClip;
    }

    public Aero_AnimationBundle getBundle() { return bundle; }
    public Aero_AnimationDefinition getDef() { return def; }
    public int getCurrentState() { return currentState; }

    /**
     * Resolves a locator (bone name) to its current animated pivot in
     * block units, relative to the BE / entity origin. Returns the
     * keyframed-position-offset added to the bundle's rest pivot for that
     * bone — so a locator declared on "muzzle" gives you "where the muzzle
     * IS right now in the animation", not where it started.
     *
     * <p>Rotation and scale are deliberately NOT applied here: the pivot
     * is a single point and rotation/scale don't move it (they rotate or
     * scale the surrounding mesh AROUND the pivot). For "tip of a swinging
     * blade" the OBJ should declare a separate bone at the tip with its
     * own pivot — then this method returns the tip's animated position
     * directly.
     *
     * <p>Returns {@code true} when the bone is known to the active clip
     * AND the bundle (so {@code out} now holds a meaningful position);
     * {@code false} otherwise, with {@code out} left untouched. Listeners
     * that need a fallback should check the return and use the BE coords.
     */
    public boolean getAnimatedPivot(String boneName, float partialTick, float[] out) {
        if (boneName == null || out == null) return false;
        Aero_AnimationClip clip = getCurrentClip();
        if (!bundle.getPivotInto(boneName, out)) return false;

        if (clip != null) {
            int bi = clip.indexOfBone(boneName);
            if (bi >= 0) {
                if (clip.samplePosInto(bi, getInterpolatedTime(partialTick), pivotScratch)) {
                    // Position offsets are stored in pixels (Blockbench
                    // convention); divide by 16 to bring them into the
                    // block-unit space of the pivot.
                    out[0] += pivotScratch[0] * (1f / 16f);
                    out[1] += pivotScratch[1] * (1f / 16f);
                    out[2] += pivotScratch[2] * (1f / 16f);
                }
            }
        }
        return true;
    }

    protected float getPlaybackTime() { return playbackTime; }

    protected void restorePlayback(int stateId, float time) {
        currentState = stateId;
        cachedClipState = -1;
        playbackTime = time;
        prevPlaybackTime = time;
    }
}
