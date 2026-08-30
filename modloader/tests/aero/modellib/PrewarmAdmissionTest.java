package aero.modellib;

import org.junit.Test;

import aero.modellib.render.Aero_PrewarmAdmission;

import static org.junit.Assert.*;

public class PrewarmAdmissionTest {
    @Test
    public void hiddenModelMustBecomeHotBeforeAdmission() {
        Aero_PrewarmAdmission<Object> admission = policy();
        Object model = new Object();
        admission.discover(model);
        assertFalse(admission.observe(model, false));
        assertFalse(admission.observe(model, false));
        assertTrue(admission.observe(model, false));
        assertTrue(admission.shouldDrain(model));
        assertEquals(1, admission.admitted());
        assertEquals(2, admission.rejected());
    }

    @Test
    public void visibleModelIsUrgentImmediately() {
        Aero_PrewarmAdmission<Object> admission = policy();
        Object model = new Object();
        assertTrue(admission.observe(model, true));
        assertTrue(admission.shouldDrain(model));
    }

    @Test
    public void coldScoreDecaysAndQueuedWorkExpires() {
        Aero_PrewarmAdmission<Object> admission = policy();
        Object model = new Object();
        admission.observe(model, false);
        admission.observe(model, false);
        assertTrue(admission.observe(model, false));
        admission.beginFrame();
        admission.beginFrame();
        assertFalse(admission.shouldDrain(model));
        assertEquals(1, admission.expired());
        assertEquals(0, admission.tracked());
    }

    @Test
    public void unseenDiscoveryNeverQualifiesSpeculation() {
        Aero_PrewarmAdmission<Object> admission = policy();
        Object model = new Object();
        admission.discover(model);
        assertFalse(admission.shouldDrain(model));
        assertEquals(1, admission.expired());
    }

    @Test
    public void identitySeparatesEqualModels() {
        Aero_PrewarmAdmission<String> admission = new Aero_PrewarmAdmission<String>(2, 2, 4);
        String first = new String("mesh"), second = new String("mesh");
        assertFalse(admission.observe(first, false));
        assertFalse(admission.observe(second, false));
        assertEquals(2, admission.tracked());
    }

    @Test
    public void explicitAdmissionBypassesLearningButStillExpires() {
        Aero_PrewarmAdmission<Object> admission = policy();
        Object model = new Object();
        admission.admit(model);
        assertTrue(admission.shouldDrain(model));
        assertEquals(1, admission.admitted());
    }

    @Test
    public void pressureGateKeepsUrgentBoundaryIndependent() {
        assertTrue(Aero_PrewarmAdmission.allowsSpeculation(20.0d, 20.0d));
        assertFalse(Aero_PrewarmAdmission.allowsSpeculation(20.1d, 20.0d));
        assertTrue(Aero_PrewarmAdmission.allowsSpeculation(100.0d, 0.0d));
    }

    private static Aero_PrewarmAdmission<Object> policy() {
        return new Aero_PrewarmAdmission<Object>(3, 2, 4);
    }
}
