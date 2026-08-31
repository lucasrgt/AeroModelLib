package aero.modellib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import aero.modellib.render.Aero_ChunkWorkScheduler;

import static org.junit.Assert.*;

public class ChunkWorkSchedulerTest {
    private final FakeAdapter adapter = new FakeAdapter();

    @Test
    public void visibleWorkWinsBeforeNonUrgentHiddenWork() {
        Fake hidden = new Fake("hidden", false, 1.0d);
        Fake visible = new Fake("visible", true, 100.0d);
        List<Fake> queue = list(hidden, visible);
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();

        assertEquals(1, scheduler.schedule(queue, adapter, 1, 10, 10));

        assertEquals(Arrays.asList("visible"), adapter.rebuilt);
        assertSame(hidden, queue.get(0));
        assertEquals(1, scheduler.visibleBuilt());
    }

    @Test
    public void debtEventuallyOverridesContinuousVisibleArrivals() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        Fake hidden = new Fake("hidden", false, 1.0d);
        List<Fake> queue = list(hidden);

        for (int frame = 0; frame < 3; frame++) {
            queue.add(new Fake("visible-" + frame, true, 1.0d));
            scheduler.schedule(queue, adapter, 1, 100, 3);
        }
        queue.add(new Fake("visible-3", true, 1.0d));
        scheduler.schedule(queue, adapter, 1, 100, 3);

        assertTrue(adapter.rebuilt.contains("hidden"));
        assertEquals(1, scheduler.urgentBuilt());
        assertFalse(hidden.dirty);
    }

    @Test
    public void urgentDebtWinsWhenVisibleArrivalsAlsoBecomeUrgent() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        List<Fake> hidden = new ArrayList<Fake>();
        List<Fake> queue = new ArrayList<Fake>();
        for (int index = 0; index < 64; index++) {
            Fake work = new Fake("hidden-" + index, false, index);
            hidden.add(work);
            queue.add(work);
        }

        for (int frame = 0; frame < 94; frame++) {
            queue.add(new Fake("visible-" + frame, true, frame));
            scheduler.schedule(queue, adapter, 1, 120, 30);
        }

        for (Fake work : hidden) assertFalse(work.name + " starved", work.dirty);
    }

    @Test
    public void ageCanMakeHiddenWorkUrgentIndependentlyOfDebtLimit() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        Fake hidden = new Fake("hidden", false, 1.0d);
        List<Fake> queue = list(hidden, new Fake("visible-0", true, 1.0d));
        scheduler.schedule(queue, adapter, 1, 3, 100);
        queue.add(new Fake("visible-1", true, 1.0d));
        scheduler.schedule(queue, adapter, 1, 3, 100);
        queue.add(new Fake("visible-2", true, 1.0d));

        scheduler.schedule(queue, adapter, 1, 3, 100);

        assertEquals("hidden", adapter.rebuilt.get(2));
        assertEquals(1, scheduler.urgentBuilt());
    }

    @Test
    public void nearerWorkBreaksOtherwiseEqualPriority() {
        Fake far = new Fake("far", true, 100.0d);
        Fake near = new Fake("near", true, 4.0d);
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();

        scheduler.schedule(list(far, near), adapter, 1, 10, 10);

        assertEquals(Arrays.asList("near"), adapter.rebuilt);
    }

    @Test
    public void prebakeTierWinsBeforeNearBackgroundWork() {
        Fake background = new Fake("background", false, 1.0d, 3, false);
        Fake adjacent = new Fake("adjacent", false, 100.0d, 1, true);
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();

        scheduler.schedule(list(background, adjacent), adapter, 1, 10, 10);

        assertEquals(Arrays.asList("adjacent"), adapter.rebuilt);
        assertEquals(1, scheduler.prebakeBuilt());
    }

    @Test
    public void queueIdentityChangeDropsOldWorldDebt() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        Fake old = new Fake("old", false, 1.0d);
        List<Fake> oldQueue = list(old, new Fake("visible", true, 1.0d));
        scheduler.schedule(oldQueue, adapter, 1, 100, 100);
        assertEquals(1, scheduler.maximumDebt());

        Fake fresh = new Fake("fresh", false, 1.0d);
        scheduler.schedule(list(fresh), adapter, 1, 100, 100);

        assertTrue(old.dirty);
        assertFalse(fresh.dirty);
        assertEquals(0, scheduler.maximumDebt());
    }

    @Test
    public void explicitResetDropsDebtEvenWhenQueueIdentityIsReused() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        Fake hidden = new Fake("hidden", false, 1.0d);
        List<Fake> queue = list(hidden, new Fake("visible-0", true, 1.0d));
        scheduler.schedule(queue, adapter, 1, 100, 100);
        assertEquals(1, scheduler.maximumDebt());

        scheduler.reset();
        queue.add(new Fake("visible-1", true, 1.0d));
        scheduler.schedule(queue, adapter, 1, 100, 100);

        assertTrue(hidden.dirty);
        assertEquals(1, scheduler.maximumDebt());
    }

    @Test
    public void duplicateAndAlreadyCleanEntriesAreCompacted() {
        Aero_ChunkWorkScheduler<Fake> scheduler = scheduler();
        Fake work = new Fake("one", true, 1.0d);
        Fake clean = new Fake("clean", true, 1.0d);
        clean.dirty = false;
        List<Fake> queue = list(work, work, clean);

        scheduler.schedule(queue, adapter, 1, 10, 10);

        assertTrue(queue.isEmpty());
        assertEquals(Arrays.asList("one"), adapter.rebuilt);
    }

    private static Aero_ChunkWorkScheduler<Fake> scheduler() {
        return new Aero_ChunkWorkScheduler<Fake>();
    }

    private static List<Fake> list(Fake... values) {
        return new ArrayList<Fake>(Arrays.asList(values));
    }

    private static final class Fake {
        final String name;
        final boolean visible;
        final double distance;
        final int priority;
        final boolean prebake;
        boolean dirty = true;

        Fake(String name, boolean visible, double distance) {
            this(name, visible, distance, visible ? 0 : 1, false);
        }

        Fake(String name, boolean visible, double distance,
             int priority, boolean prebake) {
            this.name = name;
            this.visible = visible;
            this.distance = distance;
            this.priority = priority;
            this.prebake = prebake;
        }
    }

    private static final class FakeAdapter
            implements Aero_ChunkWorkScheduler.Adapter<Fake> {
        final List<String> rebuilt = new ArrayList<String>();

        public boolean isDirty(Fake work) { return work.dirty; }
        public boolean isVisible(Fake work) { return work.visible; }
        public int priority(Fake work) { return work.priority; }
        public boolean isPrebake(Fake work, int priority) { return work.prebake; }
        public double squaredDistance(Fake work) { return work.distance; }
        public void rebuild(Fake work) { rebuilt.add(work.name); }
        public void markClean(Fake work) { work.dirty = false; }
    }
}
