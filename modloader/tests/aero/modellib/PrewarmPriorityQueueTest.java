package aero.modellib;

import org.junit.Test;

import aero.modellib.render.Aero_PrewarmPriorityQueue;

import static org.junit.Assert.*;

public class PrewarmPriorityQueueTest {
    @Test
    public void visibleWorkRunsBeforeSpeculativeWork() {
        Aero_PrewarmPriorityQueue<Object> queue = new Aero_PrewarmPriorityQueue<Object>(4);
        Object distant = new Object(), visible = new Object();
        assertTrue(queue.offer(distant, false));
        assertTrue(queue.offer(visible, true));
        assertSame(visible, queue.poll());
        assertSame(distant, queue.poll());
    }

    @Test
    public void visibilityPromotesAnExistingIdentity() {
        Aero_PrewarmPriorityQueue<Object> queue = new Aero_PrewarmPriorityQueue<Object>(4);
        Object first = new Object(), promoted = new Object();
        queue.offer(first, false);
        queue.offer(promoted, false);
        assertTrue(queue.offer(promoted, true));
        assertEquals(1, queue.promoted());
        assertSame(promoted, queue.poll());
        assertEquals(1, queue.size());
    }

    @Test
    public void urgentWorkCanDisplaceSpeculationAtCapacity() {
        Aero_PrewarmPriorityQueue<Object> queue = new Aero_PrewarmPriorityQueue<Object>(2);
        Object old = new Object(), newer = new Object(), visible = new Object();
        queue.offer(old, false);
        queue.offer(newer, false);
        assertTrue(queue.offer(visible, true));
        assertEquals(1, queue.dropped());
        assertSame(visible, queue.poll());
        assertSame(old, queue.poll());
        assertFalse(queue.contains(newer));
    }

    @Test
    public void speculativeAndAllUrgentOverflowFailClosed() {
        Aero_PrewarmPriorityQueue<Object> queue = new Aero_PrewarmPriorityQueue<Object>(1);
        Object urgent = new Object();
        assertTrue(queue.offer(urgent, true));
        assertFalse(queue.offer(new Object(), false));
        assertFalse(queue.offer(new Object(), true));
        assertEquals(2, queue.dropped());
        assertSame(urgent, queue.poll());
    }

    @Test
    public void identityNotEqualityControlsDeduplication() {
        Aero_PrewarmPriorityQueue<String> queue = new Aero_PrewarmPriorityQueue<String>(2);
        String first = new String("mesh"), second = new String("mesh");
        assertTrue(queue.offer(first, false));
        assertTrue(queue.offer(second, false));
        assertEquals(2, queue.size());
    }

    @Test
    public void lanesCanDrainIndependentlyUnderPressure() {
        Aero_PrewarmPriorityQueue<Object> queue = new Aero_PrewarmPriorityQueue<Object>(3);
        Object speculative = new Object(), urgent = new Object();
        queue.offer(speculative, false);
        queue.offer(urgent, true);
        assertSame(urgent, queue.pollUrgent());
        assertEquals(1, queue.speculativeSize());
        assertNull(queue.pollUrgent());
        assertSame(speculative, queue.pollSpeculative());
    }
}
