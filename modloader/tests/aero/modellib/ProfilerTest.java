package aero.modellib;

import aero.modellib.util.Aero_Profiler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ProfilerTest {
    @Test
    public void exposesCompletedCountersWithoutResettingThem() {
        boolean enabled = Aero_Profiler.isEnabled();
        try {
            Aero_Profiler.setEnabled(true);
            Aero_Profiler.reset();
            sample("aero.test");
            sample("aero.test");

            assertEquals(2L, Aero_Profiler.callCount("aero.test"));
            assertTrue(Aero_Profiler.totalNanos("aero.test") > 0L);
            assertEquals(0L, Aero_Profiler.callCount("aero.absent"));
            assertEquals(0L, Aero_Profiler.totalNanos("aero.absent"));

            assertEquals(2L, Aero_Profiler.callCount("aero.test"));
            Aero_Profiler.reset();
            assertEquals(0L, Aero_Profiler.callCount("aero.test"));
        } finally {
            Aero_Profiler.reset();
            Aero_Profiler.setEnabled(enabled);
        }
    }

    private static void sample(String section) {
        Aero_Profiler.start(section);
        for (int i = 0; i < 1000; i++) Math.sqrt(i);
        Aero_Profiler.end(section);
    }
}
