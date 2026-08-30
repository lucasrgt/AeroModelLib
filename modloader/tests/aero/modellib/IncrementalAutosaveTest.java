package aero.modellib;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IncrementalAutosaveTest {
    @After
    public void clearProperties() {
        System.clearProperty("aero.incrementalAutosave");
        System.clearProperty("aero.incrementalAutosave.chunkBudget");
    }

    @Test
    public void disabledPolicyKeepsVanillaLimit() {
        assertEquals(24, Aero_IncrementalAutosave.chunkLimit(24, false));
    }

    @Test
    public void enabledPolicyDefaultsToOneChunk() {
        System.setProperty("aero.incrementalAutosave", "true");
        assertEquals(1, Aero_IncrementalAutosave.chunkLimit(24, false));
    }

    @Test
    public void forcedSaveAlwaysKeepsCompleteVanillaDrain() {
        System.setProperty("aero.incrementalAutosave", "true");
        System.setProperty("aero.incrementalAutosave.chunkBudget", "3");
        assertEquals(24, Aero_IncrementalAutosave.chunkLimit(24, true));
    }

    @Test
    public void configuredBudgetIsClampedToVanillaRange() {
        System.setProperty("aero.incrementalAutosave", "true");
        System.setProperty("aero.incrementalAutosave.chunkBudget", "99");
        assertEquals(24, Aero_IncrementalAutosave.chunkLimit(24, false));
        System.setProperty("aero.incrementalAutosave.chunkBudget", "0");
        assertEquals(1, Aero_IncrementalAutosave.chunkLimit(24, false));
    }
}
