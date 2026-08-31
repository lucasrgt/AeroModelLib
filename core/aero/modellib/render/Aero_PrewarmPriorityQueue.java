package aero.modellib.render;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

import aero.modellib.optimization.OptimizationRef;

/** Bounded identity queue that promotes visible prewarm work ahead of speculative work. */
@OptimizationRef({"aero.render.prewarm"})
public final class Aero_PrewarmPriorityQueue<T> {
    private final int capacity;
    private final ArrayDeque<T> urgent = new ArrayDeque<T>();
    private final ArrayDeque<T> speculative = new ArrayDeque<T>();
    private final IdentityHashMap<T, Boolean> queued = new IdentityHashMap<T, Boolean>();
    private int dropped, promoted;

    public Aero_PrewarmPriorityQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public boolean offer(T value, boolean isUrgent) {
        if (value == null) return false;
        Boolean existing = queued.get(value);
        if (existing != null) {
            if (isUrgent && !existing.booleanValue()) promote(value);
            return true;
        }
        if (queued.size() >= capacity && !makeRoom(isUrgent)) {
            dropped++;
            return false;
        }
        queued.put(value, Boolean.valueOf(isUrgent));
        (isUrgent ? urgent : speculative).addLast(value);
        return true;
    }

    public T poll() {
        T value = pollUrgent();
        return value != null ? value : pollSpeculative();
    }

    public T pollUrgent() { return finishPoll(urgent.pollFirst()); }
    public T pollSpeculative() { return finishPoll(speculative.pollFirst()); }

    /** Removes one identity when visible first use beats speculative drainage. */
    public boolean remove(T value) {
        Boolean lane = queued.remove(value);
        if (lane == null) return false;
        return (lane.booleanValue() ? urgent : speculative).remove(value);
    }

    public boolean contains(T value) { return queued.containsKey(value); }
    public int size() { return queued.size(); }
    public int urgentSize() { return urgent.size(); }
    public int speculativeSize() { return speculative.size(); }
    public int dropped() { return dropped; }
    public int promoted() { return promoted; }

    private T finishPoll(T value) {
        if (value != null) queued.remove(value);
        return value;
    }

    private boolean makeRoom(boolean isUrgent) {
        if (!isUrgent) return false;
        T displaced = speculative.pollLast();
        if (displaced == null) return false;
        queued.remove(displaced);
        dropped++;
        return true;
    }

    private void promote(T value) {
        if (!speculative.remove(value)) return;
        queued.put(value, Boolean.TRUE);
        urgent.addLast(value);
        promoted++;
    }
}
