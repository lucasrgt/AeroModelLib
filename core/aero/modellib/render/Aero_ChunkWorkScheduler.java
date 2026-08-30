package aero.modellib.render;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import aero.modellib.optimization.OptimizationRef;

/** Bounded, starvation-safe scheduler for identity-based chunk work. */
@OptimizationRef({"aero.chunk.compile-budget"})
public final class Aero_ChunkWorkScheduler<T> {
    public interface Adapter<T> {
        boolean isDirty(T work);
        boolean isVisible(T work);
        double squaredDistance(T work);
        void rebuild(T work);
        void markClean(T work);
    }

    private final IdentityHashMap<T, State> states =
        new IdentityHashMap<T, State>();
    private Object activeQueue;
    private int invocation;
    private int built, visibleBuilt, urgentBuilt, oldestAge, maximumDebt;

    public int schedule(List<T> queue, Adapter<T> adapter, int budget,
                        int maximumAge, int debtLimit) {
        require(queue, adapter, budget, maximumAge, debtLimit);
        begin(queue);
        collect(queue, adapter);
        int slots = Math.min(budget, queue.size());
        for (int slot = 0; slot < slots; slot++) {
            int selected = best(queue, adapter, maximumAge, debtLimit);
            if (selected < 0) break;
            T work = queue.get(selected);
            State state = states.get(work);
            boolean visible = adapter.isVisible(work);
            boolean urgent = urgent(state, maximumAge, debtLimit);
            adapter.rebuild(work);
            adapter.markClean(work);
            queue.remove(selected);
            states.remove(work);
            built++;
            if (visible) visibleBuilt++;
            if (urgent) urgentBuilt++;
        }
        ageDebtAndSweep(adapter);
        return built;
    }

    public void reset() {
        states.clear();
        activeQueue = null;
        invocation = 0;
        clearMetrics();
    }

    public int built() { return built; }
    public int visibleBuilt() { return visibleBuilt; }
    public int urgentBuilt() { return urgentBuilt; }
    public int oldestAge() { return oldestAge; }
    public int maximumDebt() { return maximumDebt; }

    private void begin(List<T> queue) {
        if (activeQueue != queue) {
            states.clear();
            activeQueue = queue;
        }
        if (invocation == Integer.MAX_VALUE) {
            states.clear();
            invocation = 0;
        }
        invocation++;
        clearMetrics();
    }

    private void collect(List<T> queue, Adapter<T> adapter) {
        for (int index = queue.size() - 1; index >= 0; index--) {
            T work = queue.get(index);
            if (work == null || !adapter.isDirty(work)) {
                queue.remove(index);
                if (work != null) states.remove(work);
                continue;
            }
            State state = states.get(work);
            if (state != null && state.seen == invocation) {
                queue.remove(index);
                continue;
            }
            if (state == null) {
                state = new State();
                states.put(work, state);
            }
            state.seen = invocation;
            state.age = increment(state.age);
        }
    }

    private int best(List<T> queue, Adapter<T> adapter,
                     int maximumAge, int debtLimit) {
        int best = -1;
        for (int index = 0; index < queue.size(); index++) {
            T candidate = queue.get(index);
            if (candidate == null || !adapter.isDirty(candidate)) continue;
            if (best < 0 || before(candidate, queue.get(best), adapter,
                    maximumAge, debtLimit)) best = index;
        }
        return best;
    }

    private boolean before(T left, T right, Adapter<T> adapter,
                           int maximumAge, int debtLimit) {
        State a = states.get(left), b = states.get(right);
        boolean urgentA = urgent(a, maximumAge, debtLimit);
        boolean urgentB = urgent(b, maximumAge, debtLimit);
        if (urgentA != urgentB) return urgentA;
        boolean visibleA = adapter.isVisible(left);
        boolean visibleB = adapter.isVisible(right);
        if (visibleA != visibleB) return visibleA;
        if (a.debt != b.debt) return a.debt > b.debt;
        if (a.age != b.age) return a.age > b.age;
        return distance(adapter.squaredDistance(left))
            < distance(adapter.squaredDistance(right));
    }

    private void ageDebtAndSweep(Adapter<T> adapter) {
        Iterator<Map.Entry<T, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, State> entry = iterator.next();
            State state = entry.getValue();
            if (state.seen != invocation || !adapter.isDirty(entry.getKey())) {
                iterator.remove();
                continue;
            }
            state.debt = increment(state.debt);
            if (state.age > oldestAge) oldestAge = state.age;
            if (state.debt > maximumDebt) maximumDebt = state.debt;
        }
    }

    private void clearMetrics() {
        built = visibleBuilt = urgentBuilt = oldestAge = maximumDebt = 0;
    }

    private static boolean urgent(State state, int maximumAge, int debtLimit) {
        return state.age >= maximumAge || state.debt >= debtLimit;
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static double distance(double value) {
        return Double.isNaN(value) ? Double.POSITIVE_INFINITY : value;
    }

    private static void require(Object queue, Object adapter, int budget,
                                int maximumAge, int debtLimit) {
        if (queue == null || adapter == null) throw new NullPointerException();
        if (budget < 1 || maximumAge < 1 || debtLimit < 1)
            throw new IllegalArgumentException("scheduler limits must be positive");
    }

    private static final class State {
        int seen, age, debt;
    }
}
