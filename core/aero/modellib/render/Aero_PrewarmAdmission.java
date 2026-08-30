package aero.modellib.render;

import java.util.IdentityHashMap;

import aero.modellib.optimization.OptimizationRef;

/** Identity hotness tracker for speculative display-list prewarm admission. */
@OptimizationRef({"aero.render.prewarm"})
public final class Aero_PrewarmAdmission<T> {
    private final IdentityHashMap<T, State> states = new IdentityHashMap<T, State>();
    private final int threshold, decayFrames, staleFrames;
    private int frame, admitted, rejected, expired;

    public Aero_PrewarmAdmission(int threshold, int decayFrames, int staleFrames) {
        if (threshold < 1 || decayFrames < 1 || staleFrames < decayFrames) {
            throw new IllegalArgumentException("invalid prewarm admission policy");
        }
        this.threshold = threshold;
        this.decayFrames = decayFrames;
        this.staleFrames = staleFrames;
    }

    public void beginFrame() { frame++; }

    public void discover(T value) {
        if (value != null && !states.containsKey(value)) states.put(value, new State(frame));
    }

    public boolean observe(T value, boolean visible) {
        if (value == null) return false;
        State state = state(value);
        decay(state);
        state.lastSeenFrame = frame;
        state.score = visible ? threshold : Math.min(threshold, state.score + 1);
        if (state.score >= threshold) {
            if (!state.admitted) admitted++;
            state.admitted = true;
            return true;
        }
        rejected++;
        return false;
    }

    public void admit(T value) {
        if (value == null) return;
        State state = state(value);
        state.lastSeenFrame = frame;
        state.score = threshold;
        if (!state.admitted) admitted++;
        state.admitted = true;
    }

    public boolean shouldDrain(T value) {
        State state = states.get(value);
        if (state == null) return false;
        decay(state);
        if (frame - state.lastSeenFrame > staleFrames || state.score < threshold) {
            states.remove(value);
            expired++;
            return false;
        }
        return true;
    }

    public void forget(T value) { states.remove(value); }
    public int tracked() { return states.size(); }
    public int admitted() { return admitted; }
    public int rejected() { return rejected; }
    public int expired() { return expired; }

    public static boolean allowsSpeculation(double lastFrameMs, double maximumFrameMs) {
        return maximumFrameMs <= 0.0d || lastFrameMs <= maximumFrameMs;
    }

    private State state(T value) {
        State state = states.get(value);
        if (state == null) {
            state = new State(frame);
            states.put(value, state);
        }
        return state;
    }

    private void decay(State state) {
        int elapsed = frame - state.lastDecayFrame;
        if (elapsed < decayFrames) return;
        state.score = Math.max(0, state.score - elapsed / decayFrames);
        state.lastDecayFrame = frame;
        if (state.score < threshold) state.admitted = false;
    }

    private static final class State {
        int score, lastSeenFrame, lastDecayFrame;
        boolean admitted;
        State(int frame) { lastSeenFrame = lastDecayFrame = frame; }
    }
}
